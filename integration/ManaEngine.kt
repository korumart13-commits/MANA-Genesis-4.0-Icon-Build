package com.mana.os.integration

/**
 * MANA Engine — the central intelligence orchestrator of MANA Genesis 4.0.
 *
 * MANA owns the routing, context policy, memory/tool boundaries, provider lifecycle and fallback.
 * GPT is a hosted proprietary model reached through the MANA gateway; its weights are not copied
 * into the APK. The gateway keeps the OpenAI API key server-side. The phone stores only a revocable
 * MANA device credential using the existing Android Keystore-backed credential store.
 *
 * The same engine can run on Android today and later be hosted by a MANA OS system service.
 */
class ManaEngine(
    private val gpt: ManaCancellableReasoningModel?,
    private val local: ManaCancellableReasoningModel?,
    private val policy: ManaEnginePolicy = ManaEnginePolicy()
) : ManaCancellableReasoningModel {

    override fun decide(
        context: ManaCognitiveContext,
        control: ManaRuntimeControl
    ): ManaCognitiveDecision {
        if (control.isStopRequested()) return stopped()
        val providers = buildList {
            if (policy.preferLocal && local != null) add(local)
            if (policy.preferLocal && gpt != null && policy.allowCloudFallback) add(gpt)
            if (!policy.preferLocal && gpt != null) add(gpt)
            if (!policy.preferLocal && local != null) add(local)
        }.distinct()

        if (providers.isEmpty()) {
            return ManaCognitiveDecision(
                summary = "Не подключён ни один интеллектуальный провайдер.",
                finalAnswer = "Движок MANA пока не настроен. Подключи GPT через шлюз MANA или установи совместимую локальную модель."
            )
        }

        var lastFailure = "неизвестная ошибка"
        for ((index, provider) in providers.withIndex()) {
            if (control.isStopRequested()) return stopped()
            try {
                val result = provider.decide(context, control)
                if (control.isStopRequested()) return stopped()
                return result
            } catch (error: Exception) {
                if (control.isStopRequested()) return stopped()
                lastFailure = error.message?.take(180) ?: "ошибка провайдера"
                if (!policy.allowFallback || index == providers.lastIndex) break
            }
        }
        return ManaCognitiveDecision(
            summary = "Все подключённые модели MANA временно недоступны: $lastFailure",
            finalAnswer = "Не удалось получить ответ. Проверь подключение GPT-шлюза MANA или доступность локальной модели."
        )
    }

    private fun stopped() = ManaCognitiveDecision(
        summary = "Запрос остановлен владельцем.",
        finalAnswer = "Остановлено по твоей команде."
    )
}

data class ManaEnginePolicy(
    /** The on-device model is MANA's primary brain and works without a server. */
    val preferLocal: Boolean = true,
    /** Optional cloud reasoning is a secondary fallback when explicitly configured. */
    val allowCloudFallback: Boolean = true,
    /** Compatibility field for older call sites; provider order is controlled by preferLocal. */
    @Deprecated("Use preferLocal") val preferGpt: Boolean = false,
    val allowFallback: Boolean = true
)

/**
 * GPT adapter for the phone. The actual OpenAI API key remains on the MANA server.
 * Existing gateway validates a revocable device token and returns a structured MANA decision.
 */
class ManaGatewayGptProvider(
    config: ManaGatewayConfig,
    credentials: ManaGatewayCredentialProvider,
    transport: ManaGatewayHttpTransport = ManaHttpsUrlConnectionTransport(),
    codec: ManaGatewayJsonCodec = ManaAndroidGatewayJsonCodec()
) : ManaCancellableReasoningModel {
    private val gateway = ManaGatewayReasoningModel(config, credentials, transport, codec)

    override fun decide(
        context: ManaCognitiveContext,
        control: ManaRuntimeControl
    ): ManaCognitiveDecision = gateway.decide(context, control)
}

/**
 * Contract implemented by the selected on-device inference runtime.
 *
 * A LiteRT-LM adapter must be supplied only after its Android artifact, model format and
 * device/accelerator compatibility have been verified. This interface is not itself LiteRT-LM.
 */
interface ManaLocalInferenceRuntime {
    fun isReady(): Boolean
    fun generateDecision(context: ManaCognitiveContext, control: ManaRuntimeControl): ManaCognitiveDecision
}

/** Adapts the verified local runtime into the same reasoning contract used by GPT. */
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

/**
 * Optional deterministic provider for tests and first-boot diagnostics.
 * It is not presented as an AI model and never fabricates a model-generated answer.
 */
class ManaUnavailableModelProvider : ManaCancellableReasoningModel {
    override fun decide(
        context: ManaCognitiveContext,
        control: ManaRuntimeControl
    ): ManaCognitiveDecision = ManaCognitiveDecision(
        summary = "Нет подключённой языковой модели.",
        finalAnswer = "Интеллектуальная модель пока не подключена. Я могу выполнить только доступные локальные функции."
    )
}
