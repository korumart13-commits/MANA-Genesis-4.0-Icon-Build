package com.mana.os.integration

/**
 * MANA Core Kernel — the single application-layer entry point for MANA Genesis.
 *
 * Owns boot/shutdown state and routes every assistant request through the same
 * MANA Engine -> cognitive runtime -> guarded tools/memory pipeline.
 *
 * This is an Android application/system-service layer. It is not a Linux kernel,
 * does not replace Android's kernel, and never changes boot, AVB, partitions or firmware.
 */
class ManaCoreKernel(
    engine: ManaCancellableReasoningModel,
    hostContext: ManaGenesis.RuntimeContext,
    memory: ManaCognitiveMemory,
    tools: ManaCognitiveTools,
    actionPolicy: ManaCognitivePolicy,
    dialogueState: ManaDialogueState? = null,
    retriever: ManaKnowledgeRetriever? = null,
    approvalBroker: ManaOwnerApprovalBroker? = null
) {
    enum class State { NEW, STARTING, RUNNING, STOPPING, STOPPED, FAILED }

    data class Health(
        val state: State,
        val version: String,
        val engineConfigured: Boolean,
        val modules: List<ManaGenesis.ModuleState>,
        val detail: String
    )

    private val system = ManaSystemKernel(
        engine = engine,
        hostContext = hostContext,
        memory = memory,
        tools = tools,
        actionPolicy = actionPolicy,
        dialogueState = dialogueState,
        retriever = retriever,
        approvalBroker = approvalBroker
    )

    @Volatile private var currentState: State = State.NEW
    @Volatile private var lastFailure: String = ""

    @Synchronized
    fun boot(platformModules: List<ManaGenesis.ModuleRuntime> = emptyList()): Health {
        check(currentState == State.NEW || currentState == State.STOPPED) {
            "MANA Core cannot boot from state $currentState."
        }
        currentState = State.STARTING
        lastFailure = ""
        return try {
            val modules = system.start(platformModules)
            val failed = modules.filter { it.status == ManaGenesis.Status.FAILED }
            currentState = if (failed.isEmpty()) State.RUNNING else State.FAILED
            if (failed.isNotEmpty()) lastFailure = failed.joinToString { it.id + ": " + it.detail }
            health()
        } catch (error: Exception) {
            currentState = State.FAILED
            lastFailure = error.message?.take(300) ?: "Не удалось запустить ядро MANA."
            throw error
        }
    }

    /** All MANA chat/assistant surfaces should call this method, not providers directly. */
    fun process(
        input: String,
        conversationId: String,
        control: ManaRuntimeControl = ManaRuntimeControl.NEVER_STOP
    ): ManaCognitiveResult {
        check(currentState == State.RUNNING) { "MANA Core is not running (state=$currentState)." }
        require(input.isNotBlank()) { "Запрос не должен быть пустым." }
        require(input.length <= MAX_INPUT_LENGTH) { "Запрос слишком длинный." }
        require(conversationId.isNotBlank() && conversationId.length <= 160) {
            "Некорректный идентификатор диалога."
        }
        return system.respond(input, conversationId, control)
    }

    @Synchronized
    fun shutdown(): Health {
        if (currentState == State.NEW || currentState == State.STOPPED) {
            currentState = State.STOPPED
            return health()
        }
        currentState = State.STOPPING
        return try {
            val modules = system.stop()
            currentState = State.STOPPED
            health(modules)
        } catch (error: Exception) {
            currentState = State.FAILED
            lastFailure = error.message?.take(300) ?: "Ошибка остановки ядра MANA."
            throw error
        }
    }

    fun health(): Health = health(system.moduleStates())

    private fun health(modules: List<ManaGenesis.ModuleState>): Health = Health(
        state = currentState,
        version = ManaGenesis.VERSION,
        engineConfigured = true,
        modules = modules.toList(),
        detail = lastFailure.ifBlank {
            when (currentState) {
                State.RUNNING -> "MANA Core работает; запросы маршрутизируются через центральный движок."
                State.STOPPED -> "MANA Core остановлено."
                State.NEW -> "MANA Core ожидает первого запуска."
                State.STARTING -> "MANA Core запускается."
                State.STOPPING -> "MANA Core останавливается."
                State.FAILED -> "Ядро MANA завершилось с ошибкой."
            }
        }
    )

    private companion object {
        const val MAX_INPUT_LENGTH = 12_000
    }
}
