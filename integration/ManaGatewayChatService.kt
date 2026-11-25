package com.mana.os.integration

import android.content.Context

/**
 * Connects the configured gateway model to the bounded MANA cognitive loop.
 * This service exposes read-only diagnostics and one narrowly scoped, owner-approved app-launch action.
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

    fun send(message: String, callback: (answer: String?, error: String?, approvals: List<ManaApprovalRequest>) -> Unit): Boolean {
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
                    tools = ManaAndroidReadOnlyTools.create(appContext),
                    policy = ManaDefaultCognitivePolicy(),
                    maxCycles = 3,
                    control = signal,
                    dialogueState = dialogueState,
                    approvalBroker = approvalBroker
                )
                val result = runtime.respond(message, conversationId)
                if (signal.isStopRequested()) callback(null, "Запрос остановлен.", emptyList())
                else callback(result.answer, null, result.approvalRequests)
            } catch (error: Exception) {
                val messageText = if (signal.isStopRequested()) {
                    "Запрос остановлен."
                } else {
                    error.message?.take(300) ?: "Не удалось связаться со шлюзом MANA."
                }
                callback(null, messageText, emptyList())
            } finally {
                if (activeSignal === signal) activeSignal = null
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
