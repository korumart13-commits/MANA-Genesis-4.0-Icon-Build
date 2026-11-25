package com.mana.os.integration

/**
 * MANA cognitive orchestration layer.
 *
 * This is the control loop around a replaceable language model, not a language model itself.
 * It deliberately stores concise conclusions and events, never private chain-of-thought.
 */
class ManaCognitiveRuntime(
    private val model: ManaReasoningModel,
    private val memory: ManaCognitiveMemory,
    private val tools: ManaCognitiveTools,
    private val policy: ManaCognitivePolicy,
    private val maxCycles: Int = 4,
    private val control: ManaRuntimeControl = ManaRuntimeControl.NEVER_STOP
) {
    init { require(maxCycles in 1..8) }

    fun respond(input: String, conversationId: String): ManaCognitiveResult {
        require(input.isNotBlank()) { "Input must not be blank." }
        val observations = mutableListOf<String>()
        val toolEvents = mutableListOf<ManaToolEvent>()
        val recalled = memory.recall(input, limit = 8)
        var answer: String? = null
        var cycles = 0

        while (cycles < maxCycles && answer == null) {
            if (control.isStopRequested()) {
                answer = "Остановлено по команде владельца."
                break
            }
            cycles++
            val decision = model.decide(
                ManaCognitiveContext(
                    userInput = input,
                    conversationId = conversationId,
                    cycle = cycles,
                    recalledMemories = recalled,
                    observations = observations.toList(),
                    availableTools = tools.availableTools()
                )
            )
            decision.summary.takeIf { it.isNotBlank() }?.let(observations::add)

            if (decision.actions.isEmpty()) {
                answer = decision.finalAnswer?.takeIf { it.isNotBlank() }
                    ?: "Я пока не смогла подготовить надёжный ответ. Попробуй уточнить задачу."
                break
            }

            for (action in decision.actions.take(3)) {
                if (control.isStopRequested()) {
                    answer = "Остановлено по команде владельца."
                    break
                }
                val verdict = policy.check(action)
                if (verdict != ManaActionVerdict.ALLOW) {
                    val message = when (verdict) {
                        ManaActionVerdict.REQUIRES_OWNER_CONFIRMATION ->
                            "Действие «${action.name}» ожидает подтверждения владельца."
                        ManaActionVerdict.DENY ->
                            "Действие «${action.name}» заблокировано правилами безопасности."
                        else -> "Действие недоступно."
                    }
                    toolEvents += ManaToolEvent(action.name, false, message)
                    observations += message
                    continue
                }

                if (control.isStopRequested()) {
                    answer = "Остановлено по команде владельца."
                    break
                }
                // Cancellable adapters can stop an in-flight operation when the host requests it.
                // Legacy adapters remain supported, but can only be stopped between tool calls.
                val result = if (tools is ManaCancellableCognitiveTools) {
                    tools.execute(action, control)
                } else {
                    tools.execute(action)
                }
                if (control.isStopRequested()) {
                    answer = "Остановлено по команде владельца."
                    break
                }
                toolEvents += ManaToolEvent(action.name, result.success, result.summary)
                observations += "Инструмент ${action.name}: ${result.summary.take(500)}"
            }

            if (answer == null && decision.stopAfterActions) {
                answer = decision.finalAnswer?.takeIf { it.isNotBlank() }
                    ?: "Готово. Я выполнила доступные действия и проверила их результат."
            }
        }

        val finalText = answer ?: "Я остановилась после безопасного лимита шагов. Вот что удалось выяснить: " +
            observations.takeLast(3).joinToString("; ").ifBlank { "пока недостаточно данных." }

        // Save user input and a short outcome summary, not hidden internal reasoning.
        memory.remember(
            ManaCognitiveMemoryEntry(
                conversationId = conversationId,
                kind = "interaction_summary",
                text = "Запрос: ${input.take(500)}\nИтог: ${finalText.take(1000)}"
            )
        )
        return ManaCognitiveResult(finalText, cycles, toolEvents, observations.takeLast(5))
    }
}

data class ManaCognitiveContext(
    val userInput: String,
    val conversationId: String,
    val cycle: Int,
    val recalledMemories: List<ManaCognitiveMemoryEntry>,
    val observations: List<String>,
    val availableTools: List<String>
)

data class ManaCognitiveAction(val name: String, val arguments: Map<String, String> = emptyMap())

data class ManaCognitiveDecision(
    /** Short user-safe summary of the current plan; never private chain-of-thought. */
    val summary: String = "",
    val actions: List<ManaCognitiveAction> = emptyList(),
    val finalAnswer: String? = null,
    val stopAfterActions: Boolean = false
)

data class ManaCognitiveMemoryEntry(
    val conversationId: String,
    val kind: String,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class ManaCognitiveToolResult(val success: Boolean, val summary: String)

data class ManaToolEvent(val name: String, val success: Boolean, val summary: String)

data class ManaCognitiveResult(
    val answer: String,
    val cycles: Int,
    val toolEvents: List<ManaToolEvent>,
    val progressSummaries: List<String>
)

enum class ManaActionVerdict { ALLOW, REQUIRES_OWNER_CONFIRMATION, DENY }

interface ManaReasoningModel {
    /** Implement with a real configured model adapter; never pretend this interface is an LLM. */
    fun decide(context: ManaCognitiveContext): ManaCognitiveDecision
}

interface ManaCognitiveMemory {
    fun recall(query: String, limit: Int): List<ManaCognitiveMemoryEntry>
    fun remember(entry: ManaCognitiveMemoryEntry)
}

interface ManaCognitiveTools {
    fun availableTools(): List<String>
    fun execute(action: ManaCognitiveAction): ManaCognitiveToolResult
}

/**
 * Optional contract for adapters that can interrupt a running operation.
 * Implementations must poll control during long work and stop before further side effects.
 */
interface ManaCancellableCognitiveTools : ManaCognitiveTools {
    fun execute(action: ManaCognitiveAction, control: ManaRuntimeControl): ManaCognitiveToolResult
}

interface ManaCognitivePolicy {
    /** Unknown tools and sensitive actions must not be allowed by default. */
    fun check(action: ManaCognitiveAction): ManaActionVerdict
}

/** Host-owned interrupt signal. The model cannot modify this control. */
interface ManaRuntimeControl {
    fun isStopRequested(): Boolean

    companion object {
        val NEVER_STOP = object : ManaRuntimeControl {
            override fun isStopRequested(): Boolean = false
        }
    }
}
