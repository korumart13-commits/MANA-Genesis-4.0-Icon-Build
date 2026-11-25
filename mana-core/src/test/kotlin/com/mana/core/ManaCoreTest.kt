package com.mana.core
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
class ManaCoreTest {
    private class Provider(override val id: String, private val answer: String = id, private val fail: Boolean = false) : ModelProvider {
        override fun isAvailable() = true
        override fun generate(context: CoreContext, cancellation: Cancellation): ModelReply { if (cancellation.isCancelled()) error("cancelled"); if (fail) error("provider failed"); return ModelReply(answer, id) }
    }
    @Test fun lifecycle() { val core = ManaCore(listOf(Provider("gpt"))); assertEquals(CoreState.NEW, core.health().state); assertEquals(CoreState.RUNNING, core.boot().state); assertEquals(CoreState.STOPPED, core.shutdown().state) }
    @Test fun primaryProviderWins() { val core = ManaCore(listOf(Provider("gpt", "cloud"), Provider("local"))); core.boot(); val r = core.process(CoreRequest("Привет", "c1")); assertEquals("cloud", r.text); assertEquals("gpt", r.providerId); assertFalse(r.usedFallback) }
    @Test fun fallsBackToLocal() { val core = ManaCore(listOf(Provider("gpt", fail = true), Provider("local", "offline"))); core.boot(); val r = core.process(CoreRequest("Привет", "c1")); assertEquals("offline", r.text); assertTrue(r.usedFallback) }
    @Test fun validatesInput() { val core = ManaCore(listOf(Provider("gpt")), maxInputChars = 4); core.boot(); assertFailsWith<IllegalArgumentException> { core.process(CoreRequest(" ", "c")) }; assertFailsWith<IllegalArgumentException> { core.process(CoreRequest("12345", "c")) } }
    @Test fun remembersCompletedResponses() { val store = InMemoryMemoryStore(); val core = ManaCore(listOf(Provider("gpt")), memory = store); core.boot(); core.process(CoreRequest("question", "thread")); assertEquals(1, store.recent("thread", 10).size); assertEquals(0, store.recent("other", 10).size) }
    @Test fun rejectsSideEffectTools() { val tool = object : CoreTool { override val name = "device.change_setting"; override val risk = ToolRisk.SIDE_EFFECT; override fun execute(arguments: Map<String, String>) = ToolResult(true, "changed") }; assertFailsWith<IllegalArgumentException> { SafeToolRegistry(listOf(tool)) } }
}