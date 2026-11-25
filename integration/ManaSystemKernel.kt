package com.mana.os.integration

/**
 * MANA System Kernel — composition root for the MANA-first operating environment.
 * This is currently an application-layer coordinator, not a replacement Linux kernel.
 */
class ManaSystemKernel(
    private val engine: ManaCancellableReasoningModel,
    private val hostContext: ManaGenesis.RuntimeContext,
    private val memory: ManaCognitiveMemory,
    private val tools: ManaCognitiveTools,
    private val actionPolicy: ManaCognitivePolicy,
    private val dialogueState: ManaDialogueState? = null,
    private val retriever: ManaKnowledgeRetriever? = null,
    private val approvalBroker: ManaOwnerApprovalBroker? = null
) {
    @Volatile private var started = false
    @Volatile private var modulesRegistered = false

    /**
     * Registers MANA Engine as the central brain, then starts available platform adapters.
     * Additional adapters are registered on first start; after stop, the same registry can restart.
     */
    @Synchronized
    fun start(platformModules: List<ManaGenesis.ModuleRuntime> = emptyList()): List<ManaGenesis.ModuleState> {
        check(!started) { "MANA System Kernel is already started." }
        if (!modulesRegistered) {
            ManaGenesis.register(object : ManaGenesis.ModuleRuntime {
                override val id: String = "brain"
                override fun start(context: ManaGenesis.RuntimeContext) {
                    context.report("MANA Engine is registered as the central reasoning core.")
                }
                override fun stop() = Unit
            })
            platformModules.forEach { module ->
                require(module.id != "brain") { "The brain module is reserved for MANA Engine." }
                ManaGenesis.register(module)
            }
            modulesRegistered = true
        } else {
            require(platformModules.isEmpty()) { "Platform modules are registered only on first start." }
        }
        val states = ManaGenesis.start(hostContext)
        started = true
        return states
    }

    /** Every assistant surface delegates through this one cognitive runtime and engine. */
    fun respond(
        input: String,
        conversationId: String,
        control: ManaRuntimeControl = ManaRuntimeControl.NEVER_STOP
    ): ManaCognitiveResult {
        check(started) { "Start MANA System Kernel before handling requests." }
        return ManaCognitiveRuntime(
            model = engine,
            memory = memory,
            tools = tools,
            policy = actionPolicy,
            control = control,
            dialogueState = dialogueState,
            retriever = retriever,
            approvalBroker = approvalBroker
        ).respond(input, conversationId)
    }

    @Synchronized
    fun stop(): List<ManaGenesis.ModuleState> {
        if (!started) return ManaGenesis.snapshot()
        started = false
        return ManaGenesis.stop()
    }

    fun moduleStates(): List<ManaGenesis.ModuleState> = ManaGenesis.snapshot()
}
