package com.mana.core

import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Provider-neutral MANA orchestration core. No Android or vendor APIs are required. */
enum class CoreState { NEW, STARTING, RUNNING, STOPPING, STOPPED, FAILED }
data class CoreRequest(val text: String, val conversationId: String, val createdAt: Instant = Instant.now())
data class CoreContext(val request: CoreRequest, val recentMemory: List<MemoryEntry>, val availableTools: List<String>)
data class ModelReply(val text: String, val providerId: String, val usage: Map<String, Long> = emptyMap())
interface Cancellation { fun isCancelled(): Boolean; companion object { val NEVER = object : Cancellation { override fun isCancelled() = false } } }
interface ModelProvider { val id: String; fun isAvailable(): Boolean; fun generate(context: CoreContext, cancellation: Cancellation): ModelReply }
data class MemoryEntry(val conversationId: String, val userText: String, val assistantText: String, val timestamp: Instant = Instant.now())
interface MemoryStore { fun recent(conversationId: String, limit: Int): List<MemoryEntry>; fun append(entry: MemoryEntry) }
class InMemoryMemoryStore(private val maxEntries: Int = 500) : MemoryStore {
    private val entries = ArrayDeque<MemoryEntry>()
    init { require(maxEntries > 0) }
    @Synchronized override fun recent(conversationId: String, limit: Int): List<MemoryEntry> { require(limit in 0..100); return entries.filter { it.conversationId == conversationId }.takeLast(limit) }
    @Synchronized override fun append(entry: MemoryEntry) { entries.addLast(entry); while (entries.size > maxEntries) entries.removeFirst() }
}
enum class ToolRisk { READ_ONLY, SIDE_EFFECT }
data class ToolCall(val name: String, val arguments: Map<String, String> = emptyMap())
data class ToolResult(val success: Boolean, val summary: String)
interface CoreTool { val name: String; val risk: ToolRisk; fun execute(arguments: Map<String, String>): ToolResult }
/** Only explicitly registered read-only tools can run without a separate approval layer. */
class SafeToolRegistry(tools: List<CoreTool>) {
    private val byName: Map<String, CoreTool>
    init {
        require(tools.size <= 32)
        require(tools.map { it.name }.distinct().size == tools.size)
        tools.forEach {
            require(it.name.matches(Regex("[a-z][a-z0-9_.]{2,79}")))
            require(it.risk == ToolRisk.READ_ONLY) { "Side-effect tools need a separate owner-approval broker." }
        }
        byName = tools.associateBy { it.name }
    }
    fun names(): List<String> = byName.keys.sorted()
    fun execute(call: ToolCall): ToolResult {
        val tool = byName[call.name] ?: return ToolResult(false, "Tool is not registered.")
        if (call.arguments.size > 8 || call.arguments.any { (k, v) -> !k.matches(Regex("[a-z][a-z0-9_]{0,39}")) || v.length > 256 || v.any(Char::isISOControl) }) return ToolResult(false, "Tool arguments rejected.")
        return runCatching { tool.execute(call.arguments.toMap()) }.getOrElse { ToolResult(false, "Tool failed safely.") }
    }
}
data class CoreHealth(val state: CoreState, val providers: List<String>, val detail: String)
data class CoreResult(val text: String, val providerId: String?, val usedFallback: Boolean)

/** Main MANA Core lifecycle and request router. Provider order defines GPT-first/local-fallback. */
class ManaCore(providers: List<ModelProvider>, private val memory: MemoryStore = InMemoryMemoryStore(), private val tools: SafeToolRegistry = SafeToolRegistry(emptyList()), private val maxInputChars: Int = 12_000) {
    private val state = AtomicReference(CoreState.NEW)
    private val cancelled = AtomicBoolean(false)
    private val providerChain = providers.toList()
    init { require(providerChain.isNotEmpty()); require(providerChain.map { it.id }.distinct().size == providerChain.size); require(maxInputChars in 1..100_000) }
    @Synchronized fun boot(): CoreHealth {
        check(state.get() == CoreState.NEW || state.get() == CoreState.STOPPED) { "Cannot boot from ${state.get()}." }
        state.set(CoreState.STARTING); cancelled.set(false); state.set(CoreState.RUNNING); return health()
    }
    fun process(request: CoreRequest, token: Cancellation = Cancellation.NEVER): CoreResult {
        check(state.get() == CoreState.RUNNING) { "MANA Core is not running." }
        require(request.text.isNotBlank()); require(request.text.length <= maxInputChars)
        require(request.conversationId.isNotBlank() && request.conversationId.length <= 160)
        val control = object : Cancellation { override fun isCancelled() = cancelled.get() || token.isCancelled() }
        if (control.isCancelled()) return CoreResult("Остановлено.", null, false)
        val context = CoreContext(request, memory.recent(request.conversationId, 12), tools.names())
        var lastError: Exception? = null
        for ((index, provider) in providerChain.withIndex()) {
            if (control.isCancelled()) return CoreResult("Остановлено.", null, false)
            if (!runCatching { provider.isAvailable() }.getOrDefault(false)) continue
            try {
                val reply = provider.generate(context, control)
                if (control.isCancelled()) return CoreResult("Остановлено.", null, false)
                require(reply.text.isNotBlank()) { "Provider returned an empty answer." }
                memory.append(MemoryEntry(request.conversationId, request.text, reply.text))
                return CoreResult(reply.text, provider.id, index > 0)
            } catch (e: Exception) { if (control.isCancelled()) return CoreResult("Остановлено.", null, false); lastError = e }
        }
        throw IllegalStateException("All MANA providers are unavailable.", lastError)
    }
    fun cancelActiveRequests() { cancelled.set(true) }
    @Synchronized fun shutdown(): CoreHealth { if (state.get() == CoreState.STOPPED) return health(); state.set(CoreState.STOPPING); cancelled.set(true); state.set(CoreState.STOPPED); return health() }
    fun health() = CoreHealth(state.get(), providerChain.map { it.id }, when (state.get()) {
        CoreState.NEW -> "MANA Core is awaiting boot."; CoreState.STARTING -> "MANA Core is starting."; CoreState.RUNNING -> "MANA Core is ready."
        CoreState.STOPPING -> "MANA Core is stopping."; CoreState.STOPPED -> "MANA Core is stopped."; CoreState.FAILED -> "MANA Core failed."
    })
}