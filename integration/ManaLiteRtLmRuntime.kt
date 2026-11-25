package com.mana.os.integration

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import java.io.File

/**
 * Real on-device MANA inference adapter backed by Google's LiteRT-LM.
 *
 * The .litertlm weights live in app-private filesDir/models, never in the APK.
 * Create and call this adapter from a worker thread. One instance owns one
 * Engine/Conversation pair and serializes all operations.
 */
class ManaLiteRtLmRuntime(
    context: Context,
    private val modelFile: File
) : ManaLocalInferenceRuntime, AutoCloseable {
    private val appContext = context.applicationContext
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    @Volatile private var ready = false

    override fun isReady(): Boolean = ready && modelFile.isFile && modelFile.length() > 0L

    @Synchronized
    fun initialize() {
        check(!ready) { "Локальная модель MANA уже загружена." }
        require(modelFile.isFile && modelFile.length() > 0L) {
            "Файл локальной модели .litertlm не найден или пуст."
        }
        val candidate = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = appContext.cacheDir.absolutePath
            )
        )
        try {
            candidate.initialize()
            val chat = candidate.createConversation()
            engine = candidate
            conversation = chat
            ready = true
        } catch (error: Exception) {
            // Engine is not retained when initialization fails; do not call close
            // on a partially initialized instance (LiteRT-LM lifecycle contract).
            throw IllegalStateException("Не удалось загрузить локальную модель MANA: ${error.message}", error)
        }
    }

    @Synchronized
    override fun generateDecision(
        context: ManaCognitiveContext,
        control: ManaRuntimeControl
    ): ManaCognitiveDecision {
        check(isReady()) { "Локальная модель MANA не загружена." }
        if (control.isStopRequested()) {
            return ManaCognitiveDecision(summary = "Локальный запрос остановлен.", finalAnswer = "Остановлено по твоей команде.")
        }
        val prompt = buildString {
            appendLine("Ты — MANA, русскоязычный персональный помощник. Отвечай точно и честно.")
            appendLine("Не утверждай, что выполнила действие, если оно не было выполнено.")
            if (context.recalledMemories.isNotEmpty()) {
                appendLine("Релевантная локальная память:")
                context.recalledMemories.take(8).forEach { appendLine("- ${it}") }
            }
            appendLine("Сообщение пользователя:")
            append(context.userInput)
        }
        val output = StringBuilder()
        runBlocking {
            requireNotNull(conversation).sendMessageAsync(prompt).collect { chunk ->
                if (!control.isStopRequested()) output.append(chunk)
            }
        }
        if (control.isStopRequested()) {
            return ManaCognitiveDecision(summary = "Локальный запрос остановлен.", finalAnswer = "Остановлено по твоей команде.")
        }
        val answer = output.toString().trim()
        check(answer.isNotBlank()) { "Локальная модель не вернула ответ." }
        return ManaCognitiveDecision(
            summary = "Ответ создан локальной моделью LiteRT-LM без обращения к облаку.",
            finalAnswer = answer
        )
    }

    @Synchronized
    override fun close() {
        ready = false
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }

    companion object {
        const val MODEL_DIRECTORY = "models"
        const val MODEL_FILE_NAME = "mana-local.litertlm"
    }
}
