package com.mana.os.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManaCognitiveRuntimeTest {
    @Test
    fun cancellationCallbacksRunOnceAndCanBeClosed() {
        val signal = ManaCancellationSignal()
        var calls = 0
        val registration = signal.onStop { calls++ }

        assertTrue(signal.requestStop())
        assertFalse(signal.requestStop())
        assertEquals(1, calls)

        registration.close()
        assertEquals(1, calls)
    }

    @Test
    fun listenerRegisteredAfterStopRunsImmediately() {
        val signal = ManaCancellationSignal()
        assertTrue(signal.requestStop())

        var calls = 0
        signal.onStop { calls++ }

        assertEquals(1, calls)
    }

    @Test
    fun dialogueStateRetainsOnlyLatestBoundedTurns() {
        val state = ManaDialogueState(maxTurns = 2, maxTextLength = 32)
        state.add(ManaDialogueTurn("one", "first", "reply one"))
        state.add(ManaDialogueTurn("two", "second", "reply two"))
        state.add(ManaDialogueTurn("three", "third", "reply three"))

        assertEquals(2, state.size())
        assertEquals(listOf("two", "three"), state.recent().map { it.topic })
        assertTrue(state.recent().all { it.userSummary.length <= 32 })
        state.clear()
        assertEquals(0, state.size())
    }

    @Test
    fun stoppedRuntimeDoesNotCallModelToolsOrMemory() {
        val signal = ManaCancellationSignal().apply { requestStop() }
        var modelCalls = 0
        var recallCalls = 0
        var rememberCalls = 0
        var toolCalls = 0

        val model = object : ManaReasoningModel {
            override fun decide(context: ManaCognitiveContext): ManaCognitiveDecision {
                modelCalls++
                return ManaCognitiveDecision(finalAnswer = "unexpected")
            }
        }
        val memory = object : ManaCognitiveMemory {
            override fun recall(query: String, limit: Int): List<ManaCognitiveMemoryEntry> {
                recallCalls++
                return emptyList()
            }

            override fun remember(entry: ManaCognitiveMemoryEntry) {
                rememberCalls++
            }
        }
        val tools = object : ManaCognitiveTools {
            override fun availableTools(): List<String> = listOf("test")
            override fun execute(action: ManaCognitiveAction): ManaCognitiveToolResult {
                toolCalls++
                return ManaCognitiveToolResult(true, "unexpected")
            }
        }
        val policy = object : ManaCognitivePolicy {
            override fun check(action: ManaCognitiveAction): ManaActionVerdict =
                ManaActionVerdict.ALLOW
        }

        val result = ManaCognitiveRuntime(model, memory, tools, policy, control = signal)
            .respond("Проверь задачу", "test-conversation")

        assertEquals("Остановлено по команде владельца.", result.answer)
        assertEquals(0, result.cycles)
        assertEquals(0, modelCalls)
        assertEquals(0, recallCalls)
        assertEquals(0, rememberCalls)
        assertEquals(0, toolCalls)
    }
}
