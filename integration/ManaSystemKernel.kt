package com.mana.os.integration

/**
 * MANA System Kernel — the composition root for the MANA-first operating environment.
 *
 * The AI Engine is registered as the "brain" module and is the primary reasoning dependency
 * for voice, assistant, launcher and system-facing modules. Android still owns the actual
 * Linux kernel, HALs, permissions and boot chain in the current app-layer implementation.
 * A future AOSP port can host this same composition root as a privileged system service only
 * after device-specific platform integration and signing are completed.
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

    /**
     * Registers the MANA Engine as the central brain before dependent modules are started.
     * Additional real module adapters are supplied by the Android host.
     */
    @Synchronized
    fun start(platformModules: List<ManaGenesis.ModuleRuntime> = emptyList()): List<ManaGenesis.ModuleState> {
        check(!started) { "MANA System Kernel is already started." }
        ManaGenesis.register(object : ManaGenesis.ModuleRuntime {
            override val id: String = "brain"
            override fun start(context: ManaGenesis.RuntimeContext) {
                context.report("MANA Engine is registered as the central reasoning core.")
            }
            override fun stop() = Unit
        })
        platformModules.forEach { module ->
            require(module.id != "brain") { "The brain module is reserved for MANA Engine." }
            genesis.register(module)
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

    fun moduleStates(): List<ManaGenesis.ModuleState> = genesis.snapshot()
}
