package com.mana.os.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManaDreamConsolidationTest {
    private class RecordingStore : ManaDreamStore {
        val forgottenMemories = mutableListOf<String>()
        val forgottenCopies = mutableListOf<String>()
        val removedIndexes = mutableListOf<String>()
        override fun forgetMemoryEntry(id: String) { forgottenMemories += id }
        override fun forgetDerivedFileCopy(id: String) { forgottenCopies += id }
        override fun removeSearchIndex(id: String) { removedIndexes += id }
    }

    private val now = 40L * 86_400_000L

    private fun lowValue(
        id: String,
        kind: ManaDreamItemKind = ManaDreamItemKind.MEMORY_ENTRY,
        pinned: Boolean = false
    ) = ManaDreamItem(
        id = id,
        kind = kind,
        createdAtMillis = now - 35L * 86_400_000L,
        lastAccessAtMillis = now - 35L * 86_400_000L,
        accessCount = 0,
        semanticRelevance = 0,
        ownerImportance = 0,
        uniqueness = 0,
        confidence = 0,
        priorLowValueReviews = 1,
        pinnedByOwner = pinned
    )

    @Test
    fun forgetsOnlyItsOwnMemoryAfterRepeatedLowValueReview() {
        val store = RecordingStore()
        val engine = ManaDreamConsolidation(store)
        val result = engine.consolidate(listOf(lowValue("memory-1")), now).single()

        assertEquals(ManaDreamDecision.FORGET_OWN_COPY, result.decision)
        assertEquals(listOf("memory-1"), store.forgottenMemories)
    }

    @Test
    fun neverDeletesOriginalUserFile() {
        val store = RecordingStore()
        val engine = ManaDreamConsolidation(store)
        val result = engine.consolidate(
            listOf(lowValue("user-file-1", ManaDreamItemKind.USER_ORIGINAL_FILE)), now
        ).single()

        assertEquals(ManaDreamDecision.OWNER_REVIEW, result.decision)
        assertTrue(store.forgottenMemories.isEmpty())
        assertTrue(store.forgottenCopies.isEmpty())
        assertTrue(store.removedIndexes.isEmpty())
    }

    @Test
    fun ownerPinnedItemsAreAlwaysKept() {
        val store = RecordingStore()
        val result = ManaDreamConsolidation(store)
            .consolidate(listOf(lowValue("pinned", pinned = true)), now).single()

        assertEquals(ManaDreamDecision.KEEP, result.decision)
        assertTrue(store.forgottenMemories.isEmpty())
    }

    @Test
    fun firstLowValueAssessmentDoesNotForgetImmediately() {
        val store = RecordingStore()
        val candidate = lowValue("first-review").copy(priorLowValueReviews = 0)
        val result = ManaDreamConsolidation(store).consolidate(listOf(candidate), now).single()

        assertEquals(ManaDreamDecision.COMPRESS, result.decision)
        assertTrue(store.forgottenMemories.isEmpty())
    }
}
