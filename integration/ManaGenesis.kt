package com.mana.os.integration

/**
 * Mana Genesis central bootstrap.
 *
 * This is the single composition root for ManaOS feature modules.
 * It does not grant Android permissions, replace firmware, install code,
 * or execute generated updates. Platform modules must be supplied as
 * ModuleRuntime adapters and request permissions through Android APIs.
 *
 * Pure Kotlin/JVM: no Android or third-party dependency is required here.
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

    data class ModuleState(
        val id: String,
        val status: Status,
        val detail: String = ""
    )

    /** Implement this interface in each real Android feature module. */
    interface ModuleRuntime {
        val id: String
        fun start(context: RuntimeContext)
        fun stop()
    }

    /** The Android app supplies a context implementation that checks permissions. */
    interface RuntimeContext {
        fun hasPermission(permission: Permission): Boolean
        fun requestPermission(permission: Permission, reason: String)
        fun report(message: String)
    }

    /**
     * A development idea is a proposal, never executable code.
     * A user must approve it before a trusted build pipeline can implement it.
     */
    data class DevelopmentProposal(
        val id: String,
        val title: String,
        val rationale: String,
        val affectedModules: Set<String>,
        val acceptanceCriteria: List<String>,
        val requiresUserApproval: Boolean = true
    )

    interface DevelopmentStore {
        fun save(proposal: DevelopmentProposal)
        fun pending(): List<DevelopmentProposal>
    }

    private val specs = linkedMapOf<String, ModuleSpec>()
    private val runtimes = linkedMapOf<String, ModuleRuntime>()
    private val states = linkedMapOf<String, ModuleState>()
    private var started = false

    /** All planned Mana capabilities are catalogued here in one place. */
    val moduleCatalog: List<ModuleSpec> = listOf(
        ModuleSpec("brain", "MANA Brain", "Central assistant orchestration and dialogue."),
        ModuleSpec("memory", "Local Memory", "User-controlled local memory and offline knowledge."),
        ModuleSpec("learning", "Adaptive Intelligence", "Source-checked research and knowledge-base updates.", setOf("brain", "memory"), setOf(Permission.NETWORK)),
        ModuleSpec("launcher", "Mana Launcher", "Custom Android-first home screen."),
        ModuleSpec("system_ui", "Mana System UI", "Optional user-selected system-facing surfaces; Android-owned screens remain protected."),
        ModuleSpec("settings", "Mana Settings", "Unified settings and permission explanations."),
        ModuleSpec("control_center", "Mana Control Center", "Shortcuts and user-approved device controls."),
        ModuleSpec("avatar", "Avatar Engine", "Original 3D Mana avatar and animation."),
        ModuleSpec("voice", "Voice", "Speech input and output.", setOf("brain"), setOf(Permission.MICROPHONE)),
        ModuleSpec("yandex", "Yandex Integration", "Official Yandex APIs and user-authorized account links.", setOf("brain"), setOf(Permission.NETWORK)),
        ModuleSpec("vk", "VK Integration", "Official VK APIs and user-authorized account links.", setOf("brain"), setOf(Permission.NETWORK)),
        ModuleSpec("diary", "Mana Diary", "Private notes and reminders."),
        ModuleSpec("life_manager", "Life Manager", "Tasks, schedules and personal organization.", setOf("memory")),
        ModuleSpec("receptionist", "Smart Receptionist", "Call assistance only where Android and carrier APIs allow.", setOf("brain"), setOf(Permission.PHONE, Permission.CONTACTS, Permission.NOTIFICATIONS)),
        ModuleSpec("contact_intelligence", "Contact Intelligence", "User-approved contact context.", setOf("memory"), setOf(Permission.CONTACTS)),
        ModuleSpec("game_intelligence", "Game Intelligence", "Game companion and permitted integrations.", setOf("brain")),
        ModuleSpec("mana_shield", "Mana Shield", "Defensive app/device checks without stealth or privilege escalation."),
        ModuleSpec("developer", "Mana Developer", "Project diagnostics, tests and build reports."),
        ModuleSpec("local_server", "Local Server", "Opt-in LAN services, disabled by default.", setOf("settings"), setOf(Permission.NETWORK)),
        ModuleSpec("wearables", "Connected Devices", "Optional watch, earbuds and car integrations.", setOf("settings"), setOf(Permission.BLUETOOTH)),
        ModuleSpec("updates", "Update Manager", "Signed, verifiable app updates with rollback."),
        ModuleSpec("recovery", "Recovery Planner", "Recovery guidance and compatibility reports; no firmware flashing from this APK."),
        ModuleSpec("self_development", "Self-Development", "Collect ideas, prepare plans, tests and reviewable change proposals.", setOf("developer", "updates"))
    )

    init {
        moduleCatalog.forEach {
            specs[it.id] = it
            states[it.id] = ModuleState(it.id, Status.REGISTERED, "Waiting for a real module adapter.")
        }
    }

    /** Register real module implementations before calling start(). */
    @Synchronized
    fun register(runtime: ModuleRuntime) {
        require(!started) { "Register modules before ManaGenesis.start()." }
        require(specs.containsKey(runtime.id)) { "Unknown ManaOS module: ${runtime.id}" }
        require(!runtimes.containsKey(runtime.id)) { "Module already registered: ${runtime.id}" }
        runtimes[runtime.id] = runtime
    }

    /**
     * Starts available modules in dependency order.
     * Missing adapters remain UNAVAILABLE; they are not represented as working.
     */
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

    /** Save a self-development idea as a reviewable proposal; never execute it. */
    fun submitDevelopmentIdea(
        store: DevelopmentStore,
        proposal: DevelopmentProposal
    ) {
        require(proposal.id.isNotBlank() && proposal.title.isNotBlank())
        require(proposal.affectedModules.all(specs::containsKey)) {
            "Proposal references an unknown ManaOS module."
        }
        require(proposal.acceptanceCriteria.isNotEmpty()) {
            "A proposal must include testable acceptance criteria."
        }
        store.save(proposal.copy(requiresUserApproval = true))
    }

    /** Explicitly exposes the catalog without claiming unimplemented features are active. */
    fun moduleStatus(id: String): ModuleState? = synchronized(this) { states[id] }
}
