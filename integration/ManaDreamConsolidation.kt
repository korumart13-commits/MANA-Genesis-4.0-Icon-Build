package com.mana.os.integration

/** Periodic "dream" review of MANA-owned knowledge; not a claim of biological sleep. */
enum class ManaDreamItemKind { MEMORY_ENTRY, DERIVED_FILE_COPY, USER_ORIGINAL_FILE, SEARCH_INDEX }
enum class ManaDreamDecision { KEEP, COMPRESS, FORGET_OWN_COPY, REMOVE_INDEX_ONLY, MOVE_USER_FILE_TO_RECOVERY, OWNER_REVIEW }

data class ManaDreamItem(
    val id: String,
    val kind: ManaDreamItemKind,
    val createdAtMillis: Long,
    val lastAccessAtMillis: Long,
    val accessCount: Int = 0,
    val semanticRelevance: Int = 50,
    val ownerImportance: Int = 50,
    val uniqueness: Int = 50,
    val confidence: Int = 50,
    val priorLowValueReviews: Int = 0,
    val pinnedByOwner: Boolean = false,
    val protectedCategory: Boolean = false,
    /** True only for a folder explicitly included in the owner's cleanup scope. */
    val ownerApprovedForCleanup: Boolean = false
)

data class ManaDreamAssessment(
    val itemId: String,
    val valueScore: Int,
    val decision: ManaDreamDecision,
    val reasons: List<String>
)

interface ManaDreamStore {
    /** Removes only a MANA-created memory record, never an original user document. */
    fun forgetMemoryEntry(id: String)
    /** Removes only a MANA-created extracted/converted copy. */
    fun forgetDerivedFileCopy(id: String)
    /** Removes MANA's index/embedding, leaving the original file untouched. */
    fun removeSearchIndex(id: String)
    /** Moves a user original into a recoverable quarantine/trash; must never permanently purge it. */
    fun moveUserOriginalToRecovery(id: String)
}

/**
 * Conservative two-pass policy. Low-value items must be old and have been assessed as low
 * value in a previous cycle before MANA forgets its own copy. Pins and protected records win.
 * No operation can delete, move, overwrite or trash an original user file.
 */
class ManaDreamConsolidation(
    private val store: ManaDreamStore,
    private val minimumAgeDays: Int = 14,
    private val forgetBelowScore: Int = 20,
    private val compressBelowScore: Int = 42,
    private val allowUserFileCleanup: Boolean = false
) {
    init {
        require(minimumAgeDays >= 1)
        require(forgetBelowScore in 0..100)
        require(compressBelowScore in forgetBelowScore..100)
    }

    fun assess(items: List<ManaDreamItem>, nowMillis: Long = System.currentTimeMillis()): List<ManaDreamAssessment> =
        items.map { item ->
            val ageDays = ((nowMillis - item.createdAtMillis).coerceAtLeast(0L) / DAY_MS).toInt()
            val idleDays = ((nowMillis - item.lastAccessAtMillis).coerceAtLeast(0L) / DAY_MS).toInt()
            val recency = (100 - idleDays * 3).coerceIn(0, 100)
            val access = (item.accessCount * 12).coerceIn(0, 100)
            val score = (
                item.semanticRelevance.coerceIn(0, 100) * 0.30 +
                    item.ownerImportance.coerceIn(0, 100) * 0.25 +
                    item.uniqueness.coerceIn(0, 100) * 0.15 +
                    item.confidence.coerceIn(0, 100) * 0.10 +
                    recency * 0.10 +
                    access * 0.10
                ).toInt().coerceIn(0, 100)

            val reasons = buildList {
                add("Возраст: ${ageDays} дн.; не использовалось: ${idleDays} дн.")
                add("Оценка ценности: ${score}/100.")
                if (item.pinnedByOwner) add("Закреплено владельцем.")
                if (item.protectedCategory) add("Защищённая категория.")
                if (item.priorLowValueReviews > 0) add("Низкая ценность отмечалась ранее ${item.priorLowValueReviews} раз.")
            }

            val decision = when {
                item.pinnedByOwner || item.protectedCategory -> ManaDreamDecision.KEEP
                score < forgetBelowScore && ageDays >= minimumAgeDays &&
                    item.priorLowValueReviews >= 1 -> when (item.kind) {
                        ManaDreamItemKind.MEMORY_ENTRY,
                        ManaDreamItemKind.DERIVED_FILE_COPY -> ManaDreamDecision.FORGET_OWN_COPY
                        ManaDreamItemKind.SEARCH_INDEX -> ManaDreamDecision.REMOVE_INDEX_ONLY
                        ManaDreamItemKind.USER_ORIGINAL_FILE -> if (allowUserFileCleanup && item.ownerApprovedForCleanup) {
                            ManaDreamDecision.MOVE_USER_FILE_TO_RECOVERY
                        } else ManaDreamDecision.OWNER_REVIEW
                    }
                score < compressBelowScore -> ManaDreamDecision.COMPRESS
                else -> ManaDreamDecision.KEEP
            }
            ManaDreamAssessment(item.id, score, decision, reasons)
        }

    /** Executes only MANA-owned cleanup; original user files are never deleted here. */
    fun consolidate(items: List<ManaDreamItem>, nowMillis: Long = System.currentTimeMillis()): List<ManaDreamAssessment> {
        val assessments = assess(items, nowMillis)
        val byId = items.associateBy { it.id }
        assessments.forEach { assessment ->
            val item = byId[assessment.itemId] ?: return@forEach
            when (assessment.decision) {
                ManaDreamDecision.FORGET_OWN_COPY -> when (item.kind) {
                    ManaDreamItemKind.MEMORY_ENTRY -> store.forgetMemoryEntry(item.id)
                    ManaDreamItemKind.DERIVED_FILE_COPY -> store.forgetDerivedFileCopy(item.id)
                    else -> Unit
                }
                ManaDreamDecision.REMOVE_INDEX_ONLY -> {
                    if (item.kind == ManaDreamItemKind.SEARCH_INDEX) store.removeSearchIndex(item.id)
                }
                ManaDreamDecision.MOVE_USER_FILE_TO_RECOVERY -> {
                    if (item.kind == ManaDreamItemKind.USER_ORIGINAL_FILE && allowUserFileCleanup && item.ownerApprovedForCleanup &&
                        !item.pinnedByOwner && !item.protectedCategory) {
                        store.moveUserOriginalToRecovery(item.id)
                    }
                }
                else -> Unit
            }
        }
        return assessments
    }

    companion object { private const val DAY_MS = 86_400_000L }
}
