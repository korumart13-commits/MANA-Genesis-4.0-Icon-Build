package com.mana.os.integration

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The single product identity shared by MANA chat, voice, avatar and system surfaces.
 *
 * This is presentation/state coordination, not a second reasoning agent. All answers and
 * decisions must still come from the shared ManaCognitiveRuntime/ManaEngine.
 */
object ManaIdentity {
    const val NAME: String = "MANA"
    const val DISPLAY_NAME: String = "Мана"
    const val PRINCIPLE: String = "Одна личность, одно сознание-состояние, один общий контекст"

    /** Stable identity descriptor. Outfit/accessories are presentation choices, not identity. */
    val profile: Profile = Profile(
        name = DISPLAY_NAME,
        avatarAsset = "mana-avatar",
        voiceLocale = "ru-RU",
        identityVersion = 1
    )

    data class Profile(
        val name: String,
        val avatarAsset: String,
        val voiceLocale: String,
        val identityVersion: Int
    )
}

/**
 * The avatar mirrors the actual shared MANA runtime. It must never create its own dialogue,
 * memory, planner or model session.
 */
enum class ManaPresenceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ATTENTION,
    PAUSED,
    ERROR
}

data class ManaIdentitySnapshot(
    val state: ManaPresenceState = ManaPresenceState.IDLE,
    val detail: String? = null,
    val changedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Small process-wide state bridge for UI/voice/avatar surfaces.
 * It carries status only; cognitive content remains in the single MANA runtime.
 */
object ManaIdentityState {
    private val listeners = CopyOnWriteArrayList<(ManaIdentitySnapshot) -> Unit>()

    @Volatile
    private var current = ManaIdentitySnapshot()

    fun snapshot(): ManaIdentitySnapshot = current

    fun setState(state: ManaPresenceState, detail: String? = null): ManaIdentitySnapshot {
        val next = ManaIdentitySnapshot(
            state = state,
            detail = detail?.take(240),
            changedAtMillis = System.currentTimeMillis()
        )
        current = next
        listeners.forEach { listener -> runCatching { listener(next) } }
        return next
    }

    /** Registers a listener and immediately supplies the current shared state. */
    fun observe(listener: (ManaIdentitySnapshot) -> Unit): AutoCloseable {
        listeners.add(listener)
        runCatching { listener(current) }
        return AutoCloseable { listeners.remove(listener) }
    }
}
