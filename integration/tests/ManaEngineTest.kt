package com.mana.os.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManaEngineTest {
    private fun context() = ManaCognitiveContext(
        userInput = "Привет",
        conversationId = "engine-test",
        cycle = 1,
        recalledMemories = emptyList(),
        observations = emptyList(),
        availableTools = emptyList()
    )

    @Test
    fun prefersLocalWhenBothProvidersAreAvailable() {
        var gptCalls = 0
        var localCalls = 0
        val gpt = provider("gpt") { gptCalls++; ManaCognitiveDecision(finalAnswer = "GPT") }
        val local = provider("local") { localCalls++; ManaCognitiveDecision(finalAnswer = "LOCAL") }

        val result = ManaEngine(gpt, local).decide(context(), ManaRuntimeControl.NEVER_STOP)

        assertEquals("LOCAL", result.finalAnswer)
        assertEquals(0, gptCalls)
        assertEquals(1, localCalls)
    }

    @Test
    fun localProviderWorksWithoutCallingCloud() {
        var localCalls = 0
        var gptCalls = 0
        val gpt = provider("gpt") { gptCalls++; ManaCognitiveDecision(finalAnswer = "CLOUD") }
        val local = provider("local") { localCalls++; ManaCognitiveDecision(finalAnswer = "LOCAL") }

        val result = ManaEngine(gpt, local).decide(context(), ManaRuntimeControl.NEVER_STOP)

        assertEquals("LOCAL", result.finalAnswer)
        assertEquals(1, localCalls)
        assertEquals(0, gptCalls)
    }


    @Test
    fun usesCloudOnlyWhenLocalProviderFails() {
        var gptCalls = 0
        val local = provider("local") { error("local runtime unavailable") }
        val gpt = provider("gpt") { gptCalls++; ManaCognitiveDecision(finalAnswer = "CLOUD") }

        val result = ManaEngine(gpt, local).decide(context(), ManaRuntimeControl.NEVER_STOP)

        assertEquals("CLOUD", result.finalAnswer)
        assertEquals(1, gptCalls)
    }

    @Test
    fun canDisableCloudFallbackForStrictOfflineMode() {
        var gptCalls = 0
        val local = provider("local") { error("local runtime unavailable") }
        val gpt = provider("gpt") { gptCalls++; ManaCognitiveDecision(finalAnswer = "CLOUD") }

        val result = ManaEngine(
            gpt, local, ManaEnginePolicy(preferLocal = true, allowCloudFallback = false)
        ).decide(context(), ManaRuntimeControl.NEVER_STOP)

        assertTrue(result.finalAnswer.orEmpty().contains("Не удалось получить ответ"))
        assertEquals(0, gptCalls)
    }

    @Test
    fun ownerCancellationPreventsProviderCalls() {
        val signal = ManaCancellationSignal().apply { requestStop() }
        var calls = 0
        val gpt = provider("gpt") { calls++; ManaCognitiveDecision(finalAnswer = "unexpected") }

        val result = ManaEngine(gpt, null).decide(context(), signal)

        assertTrue(result.finalAnswer.orEmpty().contains("Остановлено"))
        assertEquals(0, calls)
    }

    private fun provider(
        name: String,
        block: () -> ManaCognitiveDecision
    ): ManaCancellableReasoningModel = object : ManaCancellableReasoningModel {
        override fun decide(
            context: ManaCognitiveContext,
            control: ManaRuntimeControl
        ): ManaCognitiveDecision {
            if (control.isStopRequested()) error("cancelled")
            return block()
        }
    }
}
