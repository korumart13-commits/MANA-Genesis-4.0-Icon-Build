package com.mana.os.integration

/**
 * Small, bounded dialogue context. It stores explicit conversation metadata only;
 * it is not an emotion detector and must not infer sensitive traits.
 */
class ManaDialogueState(
    private val maxTurns: Int = DEFAULT_MAX_TURNS,
    private val maxTextLength: Int = DEFAULT_MAX_TEXT
) {
    init {
        require(maxTurns in 1..100)
        require(maxTextLength in 32..2000)
    }

    private val turns = ArrayDeque<ManaDialogueTurn>()

    @Synchronized
    fun add(turn: ManaDialogueTurn) {
        val safeTurn = turn.copy(
            topic = turn.topic.take(maxTextLength),
            userSummary = turn.userSummary.take(maxTextLength),
            assistantSummary = turn.assistantSummary.take(maxTextLength)
        )
        turns.addLast(safeTurn)
        while (turns.size > maxTurns) turns.removeFirst()
    }

    @Synchronized
    fun recent(limit: Int = 8): List<ManaDialogueTurn> {
        if (limit <= 0) return emptyList()
        return turns.toList().takeLast(limit.coerceAtMost(maxTurns))
    }

    @Synchronized
    fun clear() {
        turns.clear()
    }

    @Synchronized
    fun size(): Int = turns.size

    companion object {
        private const val DEFAULT_MAX_TURNS = 30
        private const val DEFAULT_MAX_TEXT = 500
    }
}

enum class ManaDialogueTone { NEUTRAL, PLAYFUL, SERIOUS, SUPPORTIVE }

data class ManaDialogueTurn(
    val topic: String,
    val userSummary: String,
    val assistantSummary: String,
    val tone: ManaDialogueTone = ManaDialogueTone.NEUTRAL,
    val timestampMillis: Long = System.currentTimeMillis()
)
