package com.mana.os.integration

import java.security.MessageDigest
import java.util.UUID

/**
 * In-memory, exact-action owner approval broker.
 *
 * Approval is a one-time capability, not an Android permission. The UI must call
 * confirmFromOwnerUi only from an explicit owner confirmation interaction. Tokens are
 * short-lived, bound to the complete action name and arguments, and consumed once.
 * This broker never executes an action or grants an OS permission.
 */
class ManaOwnerApprovalBroker(
    private val clock: () -> Long = System::currentTimeMillis,
    private val approvalTtlMillis: Long = DEFAULT_TTL_MILLIS
) {
    private val pending = linkedMapOf<String, PendingApproval>()
    private val issued = linkedMapOf<String, IssuedApproval>()
    private val audit = ArrayDeque<ManaApprovalAuditEvent>()

    init {
        require(approvalTtlMillis in 1_000L..MAX_TTL_MILLIS) {
            "Approval lifetime must be between 1 second and 2 minutes."
        }
    }

    @Synchronized
    fun requestApproval(action: ManaCognitiveAction, summary: String): ManaApprovalRequest? {
        pruneExpired()
        if (!isValidAction(action) || summary.isBlank() || summary.length > MAX_SUMMARY_LENGTH) {
            record(action.name.take(80), "rejected", "invalid_request")
            return null
        }
        if (pending.size >= MAX_PENDING) {
            record(action.name, "rejected", "pending_limit")
            return null
        }
        val id = UUID.randomUUID().toString()
        val createdAt = clock()
        val request = ManaApprovalRequest(
            requestId = id,
            actionName = action.name,
            summary = summary,
            expiresAtMillis = createdAt + approvalTtlMillis
        )
        pending[id] = PendingApproval(request, fingerprint(action))
        record(action.name, "requested", "awaiting_owner")
        return request
    }

    /**
     * Call only from the explicit owner-facing confirmation button.
     * A model response or background process must never call this on the owner's behalf.
     */
    @Synchronized
    fun confirmFromOwnerUi(requestId: String, ownerConfirmed: Boolean): ManaOwnerApprovalToken? {
        pruneExpired()
        val item = pending.remove(requestId) ?: run {
            record("unknown", "rejected", "request_missing_or_expired")
            return null
        }
        if (!ownerConfirmed || item.request.expiresAtMillis <= clock()) {
            record(item.request.actionName, "declined", "owner_did_not_confirm")
            return null
        }
        if (issued.size >= MAX_ISSUED) {
            record(item.request.actionName, "rejected", "token_limit")
            return null
        }
        val tokenId = UUID.randomUUID().toString()
        issued[tokenId] = IssuedApproval(item.fingerprint, item.request.actionName, item.request.expiresAtMillis)
        record(item.request.actionName, "approved", "one_time_token_issued")
        return ManaOwnerApprovalToken(tokenId)
    }

    /** Consume immediately before a future side-effecting adapter executes. */
    @Synchronized
    fun consumeForExactAction(token: ManaOwnerApprovalToken, action: ManaCognitiveAction): Boolean {
        pruneExpired()
        val item = issued.remove(token.tokenId) ?: run {
            record(action.name.take(80), "blocked", "token_missing_expired_or_used")
            return false
        }
        val matches = isValidAction(action) &&
            item.actionName == action.name &&
            item.fingerprint == fingerprint(action) &&
            item.expiresAtMillis > clock()
        record(action.name.take(80), if (matches) "consumed" else "blocked",
            if (matches) "exact_action_authorized_once" else "action_mismatch_or_expired")
        return matches
    }

    @Synchronized
    fun deny(requestId: String): Boolean {
        val item = pending.remove(requestId) ?: return false
        record(item.request.actionName, "declined", "owner_declined")
        return true
    }

    @Synchronized
    fun pendingRequests(): List<ManaApprovalRequest> {
        pruneExpired()
        return pending.values.map { it.request }
    }

    /** Bounded, in-memory audit metadata; never stores action argument values or secrets. */
    @Synchronized
    fun auditEvents(): List<ManaApprovalAuditEvent> = audit.toList()

    private fun pruneExpired() {
        val now = clock()
        val expiredPending = pending.values.filter { it.request.expiresAtMillis <= now }
        expiredPending.forEach {
            pending.remove(it.request.requestId)
            record(it.request.actionName, "expired", "approval_request_expired")
        }
        val expiredTokens = issued.filterValues { it.expiresAtMillis <= now }.keys.toList()
        expiredTokens.forEach { tokenId ->
            val item = issued.remove(tokenId) ?: return@forEach
            record(item.actionName, "expired", "approval_token_expired")
        }
    }

    private fun isValidAction(action: ManaCognitiveAction): Boolean =
        action.name.matches(Regex("[a-z][a-z0-9_.]{2,79}")) &&
            action.arguments.size <= MAX_ARGUMENTS &&
            action.arguments.all { (key, value) ->
                key.matches(Regex("[a-z][a-z0-9_]{0,39}")) &&
                    value.length <= MAX_ARGUMENT_LENGTH && value.none(Char::isISOControl)
            }

    private fun fingerprint(action: ManaCognitiveAction): String {
        val canonical = buildString {
            append(action.name.length).append(':').append(action.name)
            action.arguments.toSortedMap().forEach { (key, value) ->
                append('|').append(key.length).append(':').append(key)
                append('=').append(value.length).append(':').append(value)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun record(actionName: String, event: String, reason: String) {
        audit.addLast(ManaApprovalAuditEvent(clock(), actionName.take(80), event, reason))
        while (audit.size > MAX_AUDIT_EVENTS) audit.removeFirst()
    }

    private data class PendingApproval(val request: ManaApprovalRequest, val fingerprint: String)
    private data class IssuedApproval(val fingerprint: String, val actionName: String, val expiresAtMillis: Long)

    private companion object {
        const val DEFAULT_TTL_MILLIS = 60_000L
        const val MAX_TTL_MILLIS = 120_000L
        const val MAX_PENDING = 8
        const val MAX_ISSUED = 8
        const val MAX_AUDIT_EVENTS = 100
        const val MAX_ARGUMENTS = 8
        const val MAX_ARGUMENT_LENGTH = 256
        const val MAX_SUMMARY_LENGTH = 240
    }
}

data class ManaApprovalRequest(
    val requestId: String,
    val actionName: String,
    val summary: String,
    val expiresAtMillis: Long
)

/** Opaque capability. Its constructor is internal to the MANA integration module. */
class ManaOwnerApprovalToken internal constructor(internal val tokenId: String)

data class ManaApprovalAuditEvent(
    val timestampMillis: Long,
    val actionName: String,
    val event: String,
    val reason: String
)
