package com.mana.os.integration

import org.junit.Assert.*
import org.junit.Test

class ManaOwnerApprovalBrokerTest {
    @Test
    fun ownerConfirmationIssuesOneTimeTokenForExactAction() {
        var now = 10_000L
        val broker = ManaOwnerApprovalBroker(clock = { now })
        val action = ManaCognitiveAction("settings.change", mapOf("setting" to "dark_mode", "value" to "true"))
        val request = broker.requestApproval(action, "Включить тёмную тему")!!

        assertNull(broker.confirmFromOwnerUi(request.requestId, false))
        val second = broker.requestApproval(action, "Включить тёмную тему")!!
        val token = broker.confirmFromOwnerUi(second.requestId, true)!!

        assertTrue(broker.consumeForExactAction(token, action))
        assertFalse(broker.consumeForExactAction(token, action))
    }

    @Test
    fun tokenCannotAuthorizeChangedArgumentsOrDifferentAction() {
        val broker = ManaOwnerApprovalBroker()
        val action = ManaCognitiveAction("files.create", mapOf("name" to "note.txt"))
        val request = broker.requestApproval(action, "Создать файл")!!
        val token = broker.confirmFromOwnerUi(request.requestId, true)!!

        assertFalse(broker.consumeForExactAction(
            token, ManaCognitiveAction("files.create", mapOf("name" to "other.txt"))
        ))
    }

    @Test
    fun approvalExpiresAndCannotBeReused() {
        var now = 5_000L
        val broker = ManaOwnerApprovalBroker(clock = { now }, approvalTtlMillis = 1_000L)
        val action = ManaCognitiveAction("calendar.create", mapOf("title" to "Встреча"))
        val request = broker.requestApproval(action, "Создать встречу")!!
        now += 1_001L

        assertNull(broker.confirmFromOwnerUi(request.requestId, true))
        assertTrue(broker.auditEvents().any { it.event == "expired" })
    }

    @Test
    fun invalidAndOversizedRequestsAreRejected() {
        val broker = ManaOwnerApprovalBroker()
        assertNull(broker.requestApproval(ManaCognitiveAction("shell.exec"), "Команда"))
        assertNull(broker.requestApproval(
            ManaCognitiveAction("files.create", mapOf("name" to "x".repeat(257))),
            "Создать файл"
        ))
    }

    @Test
    fun auditDoesNotStoreArgumentValuesAndIsBounded() {
        val broker = ManaOwnerApprovalBroker()
        repeat(120) {
            broker.requestApproval(
                ManaCognitiveAction("settings.change", mapOf("value" to "private-$it")),
                "Изменить настройку"
            )
        }
        val events = broker.auditEvents()
        assertEquals(100, events.size)
        assertFalse(events.any { it.reason.contains("private-") || it.actionName.contains("private-") })
    }

    @Test
    fun tokenIsBoundToOneExactActionAndRequestIsRemovedAfterConfirmation() {
        val broker = ManaOwnerApprovalBroker()
        val action = ManaCognitiveAction("messages.send", mapOf("to" to "+10000000000", "body" to "Привет"))
        val request = broker.requestApproval(action, "Отправить сообщение")!!
        assertEquals(1, broker.pendingRequests().size)

        val token = broker.confirmFromOwnerUi(request.requestId, true)!!
        assertTrue(broker.pendingRequests().isEmpty())
        assertFalse(broker.consumeForExactAction(
            token, ManaCognitiveAction("messages.send", mapOf("to" to "+10000000000", "body" to "Другое"))
        ))
    }
}
