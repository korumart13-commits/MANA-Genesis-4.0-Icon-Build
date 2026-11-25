package com.mana.os.integration

import org.junit.Assert.assertEquals
import org.junit.Test

class ManaDefaultCognitivePolicyTest {
    private val policy = ManaDefaultCognitivePolicy()

    @Test
    fun explicitlyReadOnlyToolsAreAllowed() {
        assertEquals(ManaActionVerdict.ALLOW, policy.check(ManaCognitiveAction("device.read_status")))
        assertEquals(ManaActionVerdict.ALLOW, policy.check(ManaCognitiveAction("apps.list_installed")))
        assertEquals(ManaActionVerdict.ALLOW, policy.check(ManaCognitiveAction("apps.find_launchable")))
        assertEquals(ManaActionVerdict.REQUIRES_OWNER_CONFIRMATION, policy.check(ManaCognitiveAction("apps.launch")))
        assertEquals(ManaActionVerdict.REQUIRES_OWNER_CONFIRMATION, policy.check(ManaCognitiveAction("settings.open_page")))
    }

    @Test
    fun sideEffectsRequireOwnerConfirmation() {
        assertEquals(ManaActionVerdict.REQUIRES_OWNER_CONFIRMATION, policy.check(ManaCognitiveAction("apps.launch")))
        assertEquals(ManaActionVerdict.REQUIRES_OWNER_CONFIRMATION, policy.check(ManaCognitiveAction("messages.send")))
        assertEquals(ManaActionVerdict.REQUIRES_OWNER_CONFIRMATION, policy.check(ManaCognitiveAction("smart_home.control")))
    }

    @Test
    fun unknownAndPrivilegedActionsAreDenied() {
        assertEquals(ManaActionVerdict.DENY, policy.check(ManaCognitiveAction("unknown.tool")))
        assertEquals(ManaActionVerdict.DENY, policy.check(ManaCognitiveAction("root.exec")))
        assertEquals(ManaActionVerdict.DENY, policy.check(ManaCognitiveAction("security.disable")))
        assertEquals(ManaActionVerdict.DENY, policy.check(ManaCognitiveAction("credentials.read")))
    }

    @Test
    fun malformedToolNamesAreDenied() {
        assertEquals(ManaActionVerdict.DENY, policy.check(ManaCognitiveAction(" ")))
        assertEquals(ManaActionVerdict.DENY, policy.check(ManaCognitiveAction("x".repeat(81))))
    }
}
