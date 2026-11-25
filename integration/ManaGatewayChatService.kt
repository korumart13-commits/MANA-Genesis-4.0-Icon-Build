package com.mana.os.integration

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Connects the configured gateway model to the bounded MANA cognitive loop.
 * This service exposes read-only diagnostics, owner-approved app launch, and fixed Android settings-page navigation.
 */
class ManaGatewayChatService(
    context: Context,
    private val conversationId: String,
    private val dialogueState: ManaDialogueState
) {
    private val appContext = context.applicationContext
    @Volatile private var activeSignal: ManaCancellationSignal? = null
    private val approvalBroker = ManaOwnerApprovalBroker()
    private val approvedActionExecutor = ManaAndroidApprovedActionExecutor(appContext, approvalBroker)

    fun isConfigured(): Boolean = ManaGatewayDeploymentConfig.isConfigured()

    private val localModelFile by lazy {
        java.io.File(appContext.filesDir, "${ManaLiteRtLmRuntime.MODEL_DIRECTORY}/${ManaLiteRtLmRuntime.MODEL_FILE_NAME}")
    }
    @Volatile private var localRuntime: ManaLiteRtLmRuntime? = null
    private val modelDownloadInProgress = AtomicBoolean(false)

    fun hasLocalModel(): Boolean =
        localModelFile.isFile && localModelFile.length() >= 300_000_000L

    /** Local model is provisioned automatically on first use; no file picker is required. */
    fun canRespondLocally(): Boolean = hasLocalModel()
    fun canStartLocalSetup(): Boolean = true

    fun prepareLocalModel(callback: (success: Boolean, detail: String) -> Unit): Boolean {
        if (hasLocalModel()) {
            callback(true, "Локальная модель MANA уже установлена.")
            return true
        }
        if (!modelDownloadInProgress.compareAndSet(false, true)) {
            callback(false, "Загрузка локальной модели MANA уже выполняется.")
            return false
        }
        Thread({
            try {
                ensureLocalModel()
                callback(true, "Локальный ИИ MANA установлен и проверен.")
            } catch (error: Exception) {
                callback(false, error.message ?: "Не удалось загрузить локальную модель.")
            } finally {
                modelDownloadInProgress.set(false)
            }
        }, "mana-model-provision").start()
        return true
    }

    fun send(message: String, callback: (answer: String?, error: String?, approvals: List<ManaApprovalRequest>) -> Unit): Boolean {
        if (message.isBlank()) return false
        val ownsModelDownload = !hasLocalModel()
        if (ownsModelDownload && !modelDownloadInProgress.compareAndSet(false, true)) return false
        stop()
        val signal = ManaCancellationSignal()
        activeSignal = signal
        Thread({
            try {
                if (!hasLocalModel()) ensureLocalModel()
                // MANA is an independent local intelligence. No external model provider
                // is called, used as fallback, or treated as part of its identity.
                val localProvider = if (hasLocalModel()) {
                    val runtime = localRuntime ?: synchronized(this) {
                        localRuntime ?: ManaLiteRtLmRuntime(appContext, localModelFile).also { candidate ->
                            candidate.initialize()
                            localRuntime = candidate
                        }
                    }
                    ManaLocalModelProvider(runtime)
                } else null
                val model = ManaEngine(
                    local = localProvider,
                    policy = ManaEnginePolicy(localOnly = true)
                )
                val runtime = ManaCognitiveRuntime(
                    model = model,
                    memory = ManaLocalCognitiveMemory(appContext),
                    tools = ManaAndroidReadOnlyTools.create(appContext),
                    policy = ManaDefaultCognitivePolicy(),
                    maxCycles = 3,
                    control = signal,
                    dialogueState = dialogueState,
                    approvalBroker = approvalBroker,
                    growthEngine = ManaGrowthEngine(appContext)
                )
                val result = runtime.respond(message, conversationId)
                if (signal.isStopRequested()) callback(null, "Запрос остановлен.", emptyList())
                else callback(result.answer, null, result.approvalRequests)
            } catch (error: Exception) {
                val messageText = if (signal.isStopRequested()) {
                    "Запрос остановлен."
                } else {
                    error.message?.take(300) ?: "Не удалось запустить локальное ядро MANA."
                }
                callback(null, messageText, emptyList())
            } finally {
                if (activeSignal === signal) activeSignal = null
                if (ownsModelDownload) modelDownloadInProgress.set(false)
            }
        }, "mana-gateway-chat").start()
        return true
    }

    /** Preview is read-only and returns an Android-verified app label only for apps.launch. */
    fun previewApproval(request: ManaApprovalRequest): String? =
        approvedActionExecutor.previewLaunchLabel(request)

    /** Call only from the owner's explicit confirmation button. */
    fun confirmApproval(requestId: String): ManaApprovedActionResult =
        approvedActionExecutor.confirmAndLaunch(requestId)

    fun denyApproval(requestId: String): Boolean = approvalBroker.deny(requestId)

    fun pendingApprovalRequests(): List<ManaApprovalRequest> = approvalBroker.pendingRequests()

    fun approvalAuditEvents(): List<ManaApprovalAuditEvent> = approvalBroker.auditEvents()

    fun stop(): Boolean = activeSignal?.requestStop() ?: false

    @Synchronized
    fun close() {
        stop()
        localRuntime?.close()
        localRuntime = null
    }

    private fun ensureLocalModel() {
        if (hasLocalModel()) return
        val directory = localModelFile.parentFile ?: error("Не удалось определить каталог локальной модели.")
        if (!directory.exists() && !directory.mkdirs()) error("Не удалось создать каталог локальной модели.")
        val partial = java.io.File(directory, localModelFile.name + ".part")
        val connection = (java.net.URL(LOCAL_MODEL_URL).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            connection.connect()
            check(connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                "Сервер модели вернул HTTP ${connection.responseCode}."
            }
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        check(partial.length() >= 300_000_000L) {
            "Размер локальной модели не совпадает с проверенным размером. Файл не установлен."
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        partial.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        check(actualHash == LOCAL_MODEL_SHA256) {
            "Контрольная сумма локальной модели не совпала. Файл отклонён."
        }
        if (localModelFile.exists() && !localModelFile.delete()) error("Не удалось заменить старый файл модели.")
        check(partial.renameTo(localModelFile)) { "Не удалось сохранить проверенную модель." }
    }

    companion object {
        private const val LOCAL_MODEL_URL = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/6aa2daf/qwen3_0.6b_q4_block32_ekv1280.litertlm"
        private const val LOCAL_MODEL_SHA256 = "312ce4e6bc0816edf844b9b7e66542648a89440b54822c3581e48a19e9ff5715"
    }

    private object NoAvailableManaTools : ManaCancellableCognitiveTools {
        override fun availableTools(): List<String> = emptyList()
        override fun execute(action: ManaCognitiveAction): ManaCognitiveToolResult =
            ManaCognitiveToolResult(false, "Для этого действия ещё не подключён системный адаптер.")
        override fun execute(action: ManaCognitiveAction, control: ManaRuntimeControl): ManaCognitiveToolResult {
            if (control.isStopRequested()) return ManaCognitiveToolResult(false, "Запрос остановлен.")
            return execute(action)
        }
    }
}
