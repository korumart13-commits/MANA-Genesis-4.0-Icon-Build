package com.mana.os.core

import com.mana.genesis.FindingLevel
import com.mana.genesis.ManaConsent
import com.mana.genesis.ManaGuardian
import com.mana.genesis.ManaMemory
import com.mana.genesis.ManaUpdateCenter
import com.mana.genesis.PermissionScope
import com.mana.genesis.ProposalState
import com.mana.genesis.SecurityFinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManaCoreTest {
    @Test
    fun identityAndModuleCatalogAreStable() {
        assertEquals("MANA OS", MANA.NAME)
        assertEquals("4.0.0", MANA.VERSION)
        assertTrue(MANA.modules.contains("MANA Brain"))
        assertTrue(MANA.modules.contains("MANA Avatar"))
    }

    @Test
    fun receptionistKeepsAgreedSafeDefaults() {
        val receptionist = SmartReceptionist()
        assertEquals(10_000L, receptionist.answerDelayMillis())
        assertTrue(receptionist.settings.playful)
        assertTrue(receptionist.settings.summaries)
        assertTrue(receptionist.settings.unknownCallerMode)
        assertFalse(receptionist.settings.audioRecording)
        assertTrue(receptionist.greeting(IncomingCall("+358000000", isKnown = false)).contains("MANA"))
        assertTrue(receptionist.summarize("Вопрос о встрече").contains("Краткое содержание"))
    }

    @Test
    fun lifeManagerCreatesCompletesAndRejectsInvalidTasks() {
        val manager = LifeManager()
        val task = manager.createTask("Проверить обновление", "Только тестовый план")
        assertEquals(1, manager.getTasks().size)
        assertTrue(manager.completeTask(task.id))
        assertTrue(manager.getTasks().single().completed)
        assertFalse(manager.completeTask("missing-task"))
        assertTrue(runCatching { manager.createTask("   ") }.isFailure)
    }

    @Test
    fun financeSummaryIsCalculatedAndRejectsNegativeAmounts() {
        val finance = ManaFinance()
        finance.addOperation(FinanceOperation(description = "Доход", amount = 125.0, isIncome = true))
        finance.addOperation(FinanceOperation(description = "Расход", amount = 25.0, isIncome = false))
        assertEquals(125.0, finance.summary().income, 0.0)
        assertEquals(25.0, finance.summary().expenses, 0.0)
        assertEquals(100.0, finance.summary().balance, 0.0)
        assertFalse(finance.requireConfirmation("transfer"))
        assertTrue(runCatching {
            finance.addOperation(FinanceOperation(description = "Некорректно", amount = -1.0, isIncome = false))
        }.isFailure)
    }

    @Test
    fun permissionConsentCanBeGrantedAndRevoked() {
        val consent = ManaConsent()
        assertFalse(consent.allows(PermissionScope.MICROPHONE))
        consent.grant(PermissionScope.MICROPHONE)
        assertTrue(consent.allows(PermissionScope.MICROPHONE))
        consent.revoke(PermissionScope.MICROPHONE)
        assertFalse(consent.allows(PermissionScope.MICROPHONE))
        consent.grant(PermissionScope.CONTACTS)
        consent.revokeAll()
        assertTrue(consent.snapshot().isEmpty())
    }

    @Test
    fun updatesCannotSkipOwnerApprovalStages() {
        val updates = ManaUpdateCenter()
        val proposal = updates.submit("Тестовое улучшение", "Проверка последовательности")
        assertEquals(ProposalState.IDEA, proposal.state)
        assertTrue(runCatching { updates.setState(proposal.id, ProposalState.PACKAGED) }.isFailure)
        assertEquals(ProposalState.DRAFTED, updates.setState(proposal.id, ProposalState.DRAFTED).state)
        assertEquals(ProposalState.APPROVED, updates.setState(proposal.id, ProposalState.APPROVED).state)
        assertEquals(ProposalState.PACKAGED, updates.setState(proposal.id, ProposalState.PACKAGED).state)
        assertEquals(ProposalState.INSTALLED, updates.setState(proposal.id, ProposalState.INSTALLED).state)
    }

    @Test
    fun localMemoryTrimsAndDeletesEntries() {
        val memory = ManaMemory()
        val entry = memory.add("  Запомнить идею  ", "idea")
        assertEquals("Запомнить идею", entry.text)
        assertEquals("idea", entry.category)
        assertEquals(1, memory.all().size)
        assertTrue(memory.delete(entry.id))
        assertFalse(memory.delete(entry.id))
        assertTrue(memory.all().isEmpty())
    }

    @Test
    fun compatibilityReportRequiresVerifiedDevicePrerequisites() {
        val merge = AdaptiveSystemMerge()
        val incomplete = DeviceProfile(
            manufacturer = "realme",
            model = "RMX5566",
            codename = "unknown",
            androidVersion = 16
        )
        val blocked = merge.analyze(incomplete)
        assertFalse(blocked.compatible)
        assertTrue(blocked.reasons.any { it.contains("vendor") })
        assertTrue(blocked.reasons.any { it.contains("device tree") })

        val verified = incomplete.copy(hasVendorPartition = true, hasDeviceTree = true)
        assertTrue(merge.analyze(verified).compatible)
    }

    @Test
    fun sha256VerifierMatchesKnownVector() {
        val digest = ManaSecurity.sha256("abc".toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest)
        assertTrue(ManaSecurity.verifyHash("abc".toByteArray(), digest.uppercase()))
        assertFalse(ManaSecurity.verifyHash("abd".toByteArray(), digest))
    }

    @Test
    fun guardianCountsWarningsWithoutPerformingActions() {
        val report = ManaGuardian().report(
            listOf(
                SecurityFinding("one", "OK", FindingLevel.OK, "read-only"),
                SecurityFinding("two", "Warning", FindingLevel.WARNING, "review")
            )
        )
        assertEquals(1, report.warnings)
    }
}
