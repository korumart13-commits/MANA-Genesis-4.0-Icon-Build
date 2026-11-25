package com.mana.os.integration

import android.content.Context

/**
 * Connects the configured gateway model to the bounded MANA cognitive loop.
 * This service exposes no tools until concrete Android adapters and permission checks exist.
 */
class ManaGatewayChatService(
    context: Context,
    private val conversationId: String,
    private val dialogueState: ManaDialogueState
) {
    private val appContext = context.applicationContext
    @Volatile private var activeSignal: ManaCancellationSignal? = null

    fun isConfigured(): Boolean = ManaGatewayDeploymentConfig.isConfigured()

    fun send(message: String, callback: (answer: String?, error: String?) -> Unit): Boolean {
        if (!isConfigured() || message.isBlank()) return false
        stop()
        val signal = ManaCancellationSignal()
        activeSignal = signal
        Thread({
            try {
                val config = ManaGatewayConfig(
                    endpoint = ManaGatewayDeploymentConfig.ENDPOINT,
                    expectedHost = ManaGatewayDeploymentConfig.EXPECTED_HOST
                )
                val model = ManaGatewayReasoningModel(
                    config = config,
                    credentials = ManaAndroidGatewayCredentialStore(appContext)
                )
                val runtime = ManaCognitiveRuntime(
                    model = model,
                    memory = ManaLocalCognitiveMemory(appContext),
                    tools = NoAvailableManaTools,
                    policy = ManaDefaultCognitivePolicy(),
                    maxCycles = 3,
                    control = signal,
                    dialogueState = dialogueState
                )
                val result = runtime.respond(message, conversationId)
                if (signal.isStopRequested()) callback(null, "Запрос остановлен.")
                else callback(result.answer, null)
            } catch (error: Exception) {
                val messageText = if (signal.isStopRequested()) {
                    "Запрос остановлен."
                } else {
                    error.message?.take(300) ?: "Не удалось связаться со шлюзом MANA."
                }
                callback(null, messageText)
            } finally {
                if (activeSignal === signal) activeSignal = null
            }
        }, "mana-gateway-chat").start()
        return true
    }

    fun stop(): Boolean = activeSignal?.requestStop() ?: false

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
