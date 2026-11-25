package com.mana.os.integration

/**
 * MANA's independent reasoning core.
 *
 * No third-party model provider is part of MANA's runtime. The local model is an
 * implementation detail of MANA's own cognitive architecture, not MANA's identity.
 * Memory, drives, persona state, reflection and skills are orchestrated by MANA.
 */
class ManaEngine(
    private val local: ManaCancellableReasoningModel?,
    private val policy: ManaEnginePolicy = ManaEnginePolicy()
) : ManaCancellableReasoningModel {

    override fun decide(
        context: ManaCognitiveContext,
        control: ManaRuntimeControl
    ): ManaCognitiveDecision {
        if (control.isStopRequested()) return stopped()
        if (local == null) {
            return ManaCognitiveDecision(
                summary = "Локальное ядро MANA ещё не готово.",
                finalAnswer = "Моё локальное мышление пока не запущено. Установи или восстанови локальный когнитивный модуль MANA."
            )
        }
        return try {
            val result = local.decide(context, control)
            if (control.isStopRequested()) stopped() else result
        } catch (error: Exception) {
            ManaCognitiveDecision(
                summary = "Локальный когнитивный модуль MANA недоступен: ${error.message?.take(160) ?: "неизвестная ошибка"}",
                finalAnswer = "Сейчас я не могу завершить локальную обработку запроса. Я сохранила доступные локальные данные; попробуй ещё раз после восстановления моего локального ядра."
            )
        }
    }

    private fun stopped() = ManaCognitiveDecision(
        summary = "Запрос остановлен владельцем.",
        finalAnswer = "Остановлено по твоей команде."
    )
}

data class ManaEnginePolicy(
    /** Remote model routing is intentionally absent: MANA is local-first and independent. */
    val localOnly: Boolean = true
)

/** Contract implemented by MANA's on-device inference substrate. */
interface ManaLocalInferenceRuntime {
    fun isReady(): Boolean
    fun generateDecision(context: ManaCognitiveContext, control: ManaRuntimeControl): ManaCognitiveDecision
}

/** Connects the local inference substrate to MANA's own cognitive runtime. */
class ManaLocalModelProvider(
    private val runtime: ManaLocalInferenceRuntime
) : ManaCancellableReasoningModel {
    override fun decide(
        context: ManaCognitiveContext,
        control: ManaRuntimeControl
    ): ManaCognitiveDecision {
        if (control.isStopRequested()) {
            return ManaCognitiveDecision(
                summary = "Локальный запрос остановлен.",
                finalAnswer = "Остановлено по твоей команде."
            )
        }
        check(runtime.isReady()) { "Локальная модель MANA не установлена или не готова." }
        return runtime.generateDecision(context, control)
    }
}

/** Deterministic unavailable state for tests and first-boot diagnostics. */
class ManaUnavailableModelProvider : ManaCancellableReasoningModel {
    override fun decide(
        context: ManaCognitiveContext,
        control: ManaRuntimeControl
    ): ManaCognitiveDecision = ManaCognitiveDecision(
        summary = "Локальное ядро MANA не подключено.",
        finalAnswer = "Моё локальное ядро пока не подключено. Доступны только функции, не требующие генерации ответа."
    )
}
