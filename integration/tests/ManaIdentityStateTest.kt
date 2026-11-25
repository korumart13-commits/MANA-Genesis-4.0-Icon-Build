package com.mana.os.integration

import org.junit.Assert.assertEquals
import org.junit.Test

class ManaIdentityStateTest {
    @Test
    fun observerReceivesCurrentAndSubsequentSharedPresenceStates() {
        val seen = mutableListOf<ManaPresenceState>()
        val registration = ManaIdentityState.observe { seen += it.state }
        try {
            ManaIdentityState.setState(ManaPresenceState.THINKING, "test")
            ManaIdentityState.setState(ManaPresenceState.SPEAKING, "test")
        } finally {
            registration.close()
            ManaIdentityState.setState(ManaPresenceState.IDLE)
        }

        assertEquals(
            listOf(ManaPresenceState.IDLE, ManaPresenceState.THINKING, ManaPresenceState.SPEAKING),
            seen
        )
    }
}
