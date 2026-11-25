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
    fun delegatesToMANAOwnedLocalCognition() {
        var calls = 0
        val local = provider { calls++; ManaCognitiveDecision(finalAnswer = "MANA LOCAL") }
        val result = ManaEngine(local).decide(context(), ManaRuntimeControl.NEVER_STOP)
        assertEquals("MANA LOCAL", result.finalAnswer)
        assertEquals(1, calls)
    }

    @Test
    fun hasNoRemoteFallbackWhenLocalCoreFails() {
        val local = provider { error("local runtime unavailable") }
        val result = ManaEngine(local).decide(context(), ManaRuntimeControl.NEVER_STOP)
        assertTrue(result.finalAnswer.orEmpty().contains("локальную обработку"))
    }

    @Test
    fun reportsNotReadyWhenLocalCoreIsAbsent() {
        val result = ManaEngine(null).decide(context(), ManaRuntimeControl.NEVER_STOP)
        assertTrue(result.finalAnswer.orEmpty().contains("локальное мышление"))
    }

    @Test
    fun ownerCancellationPreventsLocalCalls() {
        val signal = ManaCancellationSignal().apply { requestStop() }
        var calls = 0
        val local = provider { calls++; ManaCognitiveDecision(finalAnswer = "unexpected") }
        val result = ManaEngine(local).decide(context(), signal)
        assertTrue(result.finalAnswer.orEmpty().contains("Остановлено"))
        assertEquals(0, calls)
    }

    private fun provider(block: () -> ManaCognitiveDecision): ManaCancellableReasoningModel =
        object : ManaCancellableReasoningModel {
            override fun decide(
                context: ManaCognitiveContext,
                control: ManaRuntimeControl
            ): ManaCognitiveDecision {
                if (control.isStopRequested()) error("cancelled")
                return block()
            }
        }
}
