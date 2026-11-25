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
    fun stopDuringModelCallPreventsToolExecutionAndMemoryWrite() {
        val signal = ManaCancellationSignal()
        var cancellationCallbacks = 0
        var toolCalls = 0
        var rememberCalls = 0

        val model = object : ManaCancellableReasoningModel {
            override fun decide(
                context: ManaCognitiveContext,
                control: ManaRuntimeControl
            ): ManaCognitiveDecision {
                control.onStop { cancellationCallbacks++ }
                signal.requestStop()
                return ManaCognitiveDecision(
                    actions = listOf(ManaCognitiveAction("should_not_run")),
                    finalAnswer = "unexpected"
                )
            }
        }
        val memory = object : ManaCognitiveMemory {
            override fun recall(query: String, limit: Int): List<ManaCognitiveMemoryEntry> = emptyList()
            override fun remember(entry: ManaCognitiveMemoryEntry) { rememberCalls++ }
        }
        val tools = object : ManaCognitiveTools {
            override fun availableTools(): List<String> = listOf("should_not_run")
            override fun execute(action: ManaCognitiveAction): ManaCognitiveToolResult {
                toolCalls++
                return ManaCognitiveToolResult(true, "unexpected")
            }
        }
        val policy = object : ManaCognitivePolicy {
            override fun check(action: ManaCognitiveAction): ManaActionVerdict = ManaActionVerdict.ALLOW
        }

        val result = ManaCognitiveRuntime(model, memory, tools, policy, control = signal)
            .respond("Останови операцию", "test-stop-during-model")

        assertEquals("Остановлено по команде владельца.", result.answer)
        assertEquals(1, cancellationCallbacks)
        assertEquals(0, toolCalls)
        assertEquals(0, rememberCalls)
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
    @Test
    fun ownerApprovalRequestIsSurfacedAndActionIsNeverExecuted() {
        val broker = ManaOwnerApprovalBroker()
        var toolCalls = 0
        val action = ManaCognitiveAction("settings.change", mapOf("setting" to "dark_mode", "value" to "true"))
        val model = object : ManaReasoningModel {
            override fun decide(context: ManaCognitiveContext) = ManaCognitiveDecision(
                summary = "Включить тёмную тему",
                actions = listOf(action),
                stopAfterActions = true,
                finalAnswer = "Готово, включено."
            )
        }
        val memory = object : ManaCognitiveMemory {
            override fun recall(query: String, limit: Int) = emptyList<ManaCognitiveMemoryEntry>()
            override fun remember(entry: ManaCognitiveMemoryEntry) {}
        }
        val tools = object : ManaCognitiveTools {
            override fun availableTools() = emptyList<String>()
            override fun execute(action: ManaCognitiveAction): ManaCognitiveToolResult {
                toolCalls++
                return ManaCognitiveToolResult(true, "unexpected")
            }
        }

        val result = ManaCognitiveRuntime(
            model = model,
            memory = memory,
            tools = tools,
            policy = ManaDefaultCognitivePolicy(),
            approvalBroker = broker
        ).respond("Включи тёмную тему", "approval-test")

        assertEquals(0, toolCalls)
        assertEquals(1, result.approvalRequests.size)
        assertEquals(action.arguments, result.approvalRequests.single().arguments)
        assertTrue(result.answer.contains("ничего не изменено"))
    }

    @Test
    fun actionRequiringApprovalNeverGetsExecutedWithoutBroker() {
        var toolCalls = 0
        val model = object : ManaReasoningModel {
            override fun decide(context: ManaCognitiveContext) = ManaCognitiveDecision(
                actions = listOf(ManaCognitiveAction("settings.change", mapOf("setting" to "wifi", "value" to "off"))),
                stopAfterActions = true,
                finalAnswer = "Готово, выключила Wi-Fi."
            )
        }
        val memory = object : ManaCognitiveMemory {
            override fun recall(query: String, limit: Int) = emptyList<ManaCognitiveMemoryEntry>()
            override fun remember(entry: ManaCognitiveMemoryEntry) {}
        }
        val tools = object : ManaCognitiveTools {
            override fun availableTools() = listOf("settings.change")
            override fun execute(action: ManaCognitiveAction): ManaCognitiveToolResult {
                toolCalls++
                return ManaCognitiveToolResult(true, "unexpected")
            }
        }

        val result = ManaCognitiveRuntime(
            model, memory, tools, ManaDefaultCognitivePolicy()
        ).respond("Выключи Wi-Fi", "approval-no-broker")

        assertEquals(0, toolCalls)
        assertTrue(result.answer.contains("требует подтверждения"))
        assertTrue(result.answer.contains("Ничего не изменено"))
    }

}
