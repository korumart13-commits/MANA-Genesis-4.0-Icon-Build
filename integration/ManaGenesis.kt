package com.mana.os.integration

/**
 * Mana Genesis central bootstrap and self-development policy.
 *
 * This file defines orchestration contracts. Real Android modules must provide
 * adapters. It never grants permissions, executes content from the Internet,
 * modifies Android firmware, bypasses platform security, or installs an update.
 */
object ManaGenesis {
    const val VERSION = "4.0.0"
    const val DISPLAY_NAME = "ManaOS"

    enum class Status { REGISTERED, STARTING, ACTIVE, UNAVAILABLE, FAILED, STOPPED }
    enum class Permission { MICROPHONE, CONTACTS, PHONE, NOTIFICATIONS, LOCATION, BLUETOOTH, NETWORK, FILES }

    data class ModuleSpec(
        val id: String,
        val title: String,
        val description: String,
        val dependencies: Set<String> = emptySet(),
        val permissions: Set<Permission> = emptySet()
    )

    data class ModuleState(val id: String, val status: Status, val detail: String = "")

    interface ModuleRuntime {
        val id: String
        fun start(context: RuntimeContext)
        fun stop()
    }

    interface RuntimeContext {
        fun hasPermission(permission: Permission): Boolean
        fun requestPermission(permission: Permission, reason: String)
        fun report(message: String)
    }

    // ---------- Self-development: collect -> analyze -> implement -> test -> release ----------

    /** Ideas may originate anywhere. The source is metadata, never trusted executable input. */
    enum class IdeaSource {
        OWNER, INTERNET, GAME, APP, BUG_REPORT, SECURITY_ALERT, LEARNED_KNOWLEDGE, DEVICE_DIAGNOSTIC, OTHER
    }

    enum class ImprovementTarget { MODULE, MANA_OS_LAYER, MANA_SOURCE_CODE, PERFORMANCE, ACCESSIBILITY, SECURITY, OTHER }
    enum class ChangeScope { MINOR_PATCH, FEATURE_ADDITION, FULL_SYSTEM_UPDATE }
    enum class ChangeRisk { LOW, ELEVATED, SECURITY_CRITICAL, SYSTEM_WIDE }
    enum class ProposalStatus { COLLECTED, ANALYZED, READY_FOR_TESTS, TESTING, APPROVAL_REQUIRED, APPLIED, REJECTED, FAILED }

    data class IdeaRecord(
        val id: String,
        val source: IdeaSource,
        val summary: String,
        val evidence: String = "",
        val sourceReference: String? = null
    )

    data class DevelopmentProposal(
        val id: String,
        val title: String,
        val rationale: String,
        val affectedModules: Set<String>,
        val targets: Set<ImprovementTarget>,
        val scope: ChangeScope,
        val risk: ChangeRisk,
        val acceptanceCriteria: List<String>,
        val basedOnIdeaIds: Set<String>,
        val requiresUserApproval: Boolean,
        val status: ProposalStatus = ProposalStatus.COLLECTED
    )

    data class OwnerDevelopmentPolicy(
        /** Owner can switch self-development off at any time. */
        val selfDevelopmentEnabled: Boolean = true,
        /** If false, Mana collects and analyzes ideas but does not apply minor patches. */
        val autonomousMinorPatchesEnabled: Boolean = true
    )

    data class ChangeResult(
        val applied: Boolean,
        val testsPassed: Boolean,
        val rollbackAvailable: Boolean,
        val detail: String
    )

    interface DevelopmentStore {
        fun saveIdea(idea: IdeaRecord)
        fun ideas(): List<IdeaRecord>
        fun saveProposal(proposal: DevelopmentProposal)
        fun proposals(): List<DevelopmentProposal>
        fun updateProposal(proposal: DevelopmentProposal)
    }

    /** Analysis is performed by a replaceable local/remote intelligence adapter. */
    interface DevelopmentAnalyzer {
        fun analyze(ideas: List<IdeaRecord>, knownModules: List<ModuleSpec>): List<DevelopmentProposal>
    }

    /**
     * Implementations must operate on an isolated project/worktree.
     * Minor changes must not touch firmware, boot chain, security controls,
     * Android permissions, private user data, or execute untrusted payloads.
     */
    interface DevelopmentExecutor {
        fun applyMinorPatch(proposal: DevelopmentProposal): ChangeResult
        fun prepareFullSystemUpdate(proposal: DevelopmentProposal): ChangeResult
        fun applyApprovedFullSystemUpdate(proposal: DevelopmentProposal): ChangeResult
    }

    data class DevelopmentReport(
        val collectedIdeas: Int,
        val proposals: Int,
        val appliedMinorPatches: Int,
        val awaitingOwnerApproval: Int,
        val message: String
    )

    class SelfDevelopment(
        private val store: DevelopmentStore,
        private val analyzer: DevelopmentAnalyzer,
        private val executor: DevelopmentExecutor,
        initialPolicy: OwnerDevelopmentPolicy = OwnerDevelopmentPolicy()
    ) {
        @Volatile private var policy = initialPolicy
        private val approvedFullUpdates = mutableSetOf<String>()

        @Synchronized
        fun setPolicy(newPolicy: OwnerDevelopmentPolicy) {
            policy = newPolicy
        }

        fun policy(): OwnerDevelopmentPolicy = policy

        /** Accepts reports/ideas from any source, but stores them only as inert text and metadata. */
        fun collectIdea(idea: IdeaRecord) {
            require(idea.id.isNotBlank() && idea.summary.isNotBlank())
            require(idea.summary.length <= 20_000 && idea.evidence.length <= 50_000)
            store.saveIdea(idea)
        }

        /**
         * Mana chooses what to improve: individual modules, its own application/system layer,
         * source code, performance, accessibility or security. Analysis does not change code.
         */
        fun analyzeCollectedIdeas(): List<DevelopmentProposal> {
            if (!policy.selfDevelopmentEnabled) return emptyList()
            val generated = analyzer.analyze(store.ideas(), moduleCatalog)
            return generated.map { proposal ->
                validateProposal(proposal)
                val approvalRequired = proposal.scope == ChangeScope.FULL_SYSTEM_UPDATE ||
                    proposal.risk != ChangeRisk.LOW
                val normalized = proposal.copy(
                    requiresUserApproval = approvalRequired,
                    status = if (approvalRequired) ProposalStatus.APPROVAL_REQUIRED else ProposalStatus.READY_FOR_TESTS
                )
                store.saveProposal(normalized)
                normalized
            }
        }

        /**
         * Low-risk, reversible application-layer fixes may be applied autonomously
         * when the owner has enabled that mode. They still require passing tests
         * and a rollback point. This method never applies full updates.
         */
        @Synchronized
        fun applyEligibleMinorPatch(proposalId: String): DevelopmentProposal {
            val proposal = store.proposals().firstOrNull { it.id == proposalId }
                ?: error("Unknown development proposal.")
            check(policy.selfDevelopmentEnabled) { "Self-development is disabled by the owner." }
            check(policy.autonomousMinorPatchesEnabled) { "Autonomous minor patches are disabled by the owner." }
            check(proposal.scope == ChangeScope.MINOR_PATCH && proposal.risk == ChangeRisk.LOW) {
                "Only low-risk minor patches qualify for autonomous application."
            }
            check(!proposal.requiresUserApproval)
            check(proposal.acceptanceCriteria.isNotEmpty())
            val testing = proposal.copy(status = ProposalStatus.TESTING)
            store.updateProposal(testing)
            val result = executor.applyMinorPatch(testing)
            val final = when {
                result.applied && result.testsPassed && result.rollbackAvailable ->
                    testing.copy(status = ProposalStatus.APPLIED, rationale = testing.rationale + " Result: " + result.detail)
                else -> testing.copy(status = ProposalStatus.FAILED, rationale = testing.rationale + " Rejected/rolled back: " + result.detail)
            }
            store.updateProposal(final)
            return final
        }

        /** Prepare, but do not install, a full update. Owner approval is still mandatory. */
        fun prepareFullUpdate(proposalId: String): ChangeResult {
            val proposal = requireProposal(proposalId)
            check(proposal.scope == ChangeScope.FULL_SYSTEM_UPDATE)
            check(policy.selfDevelopmentEnabled)
            return executor.prepareFullSystemUpdate(proposal)
        }

        /** Calling this method represents the owner's explicit approval action. */
        @Synchronized
        fun approveFullUpdate(proposalId: String) {
            val proposal = requireProposal(proposalId)
            check(proposal.scope == ChangeScope.FULL_SYSTEM_UPDATE)
            check(policy.selfDevelopmentEnabled) { "Self-development is disabled." }
            approvedFullUpdates += proposalId
        }

        /** Full update can run only after the separate owner approval step. */
        @Synchronized
        fun applyApprovedFullUpdate(proposalId: String): ChangeResult {
            val proposal = requireProposal(proposalId)
            check(proposal.scope == ChangeScope.FULL_SYSTEM_UPDATE)
            check(approvedFullUpdates.remove(proposalId)) { "Explicit owner approval is required." }
            check(policy.selfDevelopmentEnabled)
            return executor.applyApprovedFullSystemUpdate(proposal)
        }

        private fun requireProposal(id: String): DevelopmentProposal =
            store.proposals().firstOrNull { it.id == id } ?: error("Unknown development proposal.")

        private fun validateProposal(proposal: DevelopmentProposal) {
            require(proposal.id.isNotBlank() && proposal.title.isNotBlank())
            require(proposal.affectedModules.all { module -> moduleCatalog.any { it.id == module } })
            require(proposal.acceptanceCriteria.isNotEmpty()) { "Every change needs testable acceptance criteria." }
            require(proposal.basedOnIdeaIds.isNotEmpty()) { "Every proposal must link to at least one collected idea." }
        }
    }

    // ---------- Module composition root ----------

    private val specs = linkedMapOf<String, ModuleSpec>()
    private val runtimes = linkedMapOf<String, ModuleRuntime>()
    private val states = linkedMapOf<String, ModuleState>()
    private var started = false

    val moduleCatalog: List<ModuleSpec> = listOf(
        ModuleSpec("brain", "MANA Brain", "Central assistant orchestration and dialogue."),
        ModuleSpec("memory", "Local Memory", "User-controlled local memory and offline knowledge."),
        ModuleSpec("learning", "Adaptive Intelligence", "Source-checked research and knowledge-base updates.", setOf("brain", "memory"), setOf(Permission.NETWORK)),
        ModuleSpec("launcher", "Mana Launcher", "Custom Android-first home screen."),
        ModuleSpec("system_ui", "Mana System UI", "User-selected system-facing surfaces; Android-owned screens remain protected."),
        ModuleSpec("settings", "Mana Settings", "Unified settings and permission explanations."),
        ModuleSpec("control_center", "Mana Control Center", "Shortcuts and user-approved device controls."),
        ModuleSpec("avatar", "Avatar Engine", "Original 3D Mana avatar and animation."),
        ModuleSpec("voice", "Voice", "Speech input and output.", setOf("brain"), setOf(Permission.MICROPHONE)),
        ModuleSpec("yandex", "Yandex Integration", "Official Yandex APIs and user-authorized links.", setOf("brain"), setOf(Permission.NETWORK)),
        ModuleSpec("vk", "VK Integration", "Official VK APIs and user-authorized links.", setOf("brain"), setOf(Permission.NETWORK)),
        ModuleSpec("diary", "Mana Diary", "Private notes and reminders."),
        ModuleSpec("life_manager", "Life Manager", "Tasks, schedules and personal organization.", setOf("memory")),
        ModuleSpec("receptionist", "Smart Receptionist", "Call assistance where Android and carrier APIs allow.", setOf("brain"), setOf(Permission.PHONE, Permission.CONTACTS, Permission.NOTIFICATIONS)),
        ModuleSpec("contact_intelligence", "Contact Intelligence", "User-approved contact context.", setOf("memory"), setOf(Permission.CONTACTS)),
        ModuleSpec("game_intelligence", "Game Intelligence", "Game companion and permitted integrations.", setOf("brain")),
        ModuleSpec("mana_shield", "Mana Shield", "Defensive checks without stealth or privilege escalation."),
        ModuleSpec("developer", "Mana Developer", "Project diagnostics, tests and build reports."),
        ModuleSpec("local_server", "Local Server", "Opt-in LAN services, disabled by default.", setOf("settings"), setOf(Permission.NETWORK)),
        ModuleSpec("wearables", "Connected Devices", "Optional watch, earbuds and car integrations.", setOf("settings"), setOf(Permission.BLUETOOTH)),
        ModuleSpec("updates", "Update Manager", "Signed, verifiable app updates with rollback."),
        ModuleSpec("recovery", "Recovery Planner", "Recovery guidance; no firmware flashing from this APK."),
        ModuleSpec("self_development", "Self-Development", "Collect ideas, analyze, implement safe minor fixes and prepare owner-approved full updates.", setOf("developer", "updates"))
    )

    init {
        moduleCatalog.forEach {
            specs[it.id] = it
            states[it.id] = ModuleState(it.id, Status.REGISTERED, "Waiting for a real module adapter.")
        }
    }

    @Synchronized
    fun register(runtime: ModuleRuntime) {
        require(!started) { "Register modules before ManaGenesis.start()." }
        require(specs.containsKey(runtime.id)) { "Unknown ManaOS module: ${runtime.id}" }
        require(!runtimes.containsKey(runtime.id)) { "Module already registered: ${runtime.id}" }
        runtimes[runtime.id] = runtime
    }

    @Synchronized
    fun start(context: RuntimeContext): List<ModuleState> {
        if (started) return snapshot()
        started = true
        val completed = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()

        fun activate(id: String) {
            if (id in completed) return
            val spec = specs.getValue(id)
            if (!visiting.add(id)) {
                states[id] = ModuleState(id, Status.FAILED, "Dependency cycle detected.")
                return
            }
            spec.dependencies.forEach(::activate)
            visiting.remove(id)
            val runtime = runtimes[id]
            if (runtime == null) {
                states[id] = ModuleState(id, Status.UNAVAILABLE, "Implementation adapter has not been connected yet.")
                return
            }
            val missing = spec.permissions.filterNot(context::hasPermission)
            if (missing.isNotEmpty()) {
                missing.forEach { context.requestPermission(it, "Required by ${spec.title}") }
                states[id] = ModuleState(id, Status.UNAVAILABLE, "Waiting for user-approved permissions: ${missing.joinToString()}")
                return
            }
            if (spec.dependencies.any { states[it]?.status != Status.ACTIVE }) {
                states[id] = ModuleState(id, Status.UNAVAILABLE, "A required module is not active.")
                return
            }
            states[id] = ModuleState(id, Status.STARTING)
            try {
                runtime.start(context)
                states[id] = ModuleState(id, Status.ACTIVE, "Started.")
                completed += id
            } catch (error: Exception) {
                states[id] = ModuleState(id, Status.FAILED, error.message ?: error::class.java.simpleName)
                context.report("ManaOS module ${spec.title} failed to start.")
            }
        }

        moduleCatalog.forEach { activate(it.id) }
        context.report("ManaOS bootstrap completed. Active modules: ${states.values.count { it.status == Status.ACTIVE }} / ${specs.size}.")
        return snapshot()
    }

    @Synchronized
    fun stop(): List<ModuleState> {
        runtimes.values.toList().asReversed().forEach { runtime ->
            try { runtime.stop() } catch (_: Exception) { /* continue orderly shutdown */ }
            states[runtime.id] = ModuleState(runtime.id, Status.STOPPED)
        }
        started = false
        return snapshot()
    }

    @Synchronized
    fun snapshot(): List<ModuleState> = states.values.toList()

    fun moduleStatus(id: String): ModuleState? = synchronized(this) { states[id] }
}
