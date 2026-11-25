package com.mana.os.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManaReadOnlyToolRegistryTest {
    @Test
    fun onlyRegisteredReadOnlyToolsAreAdvertisedAndExecuted() {
        var calls = 0
        val registry = ManaReadOnlyToolRegistry(listOf(
            ManaReadOnlyToolDefinition("device.read_status") { _, _ ->
                calls++
                ManaCognitiveToolResult(true, "Статус устройства доступен.")
            }
        ))

        assertEquals(listOf("device.read_status"), registry.availableTools())
        assertTrue(registry.execute(ManaCognitiveAction("device.read_status")).success)
        assertFalse(registry.execute(ManaCognitiveAction("messages.send")).success)
        assertEquals(1, calls)
    }

    @Test
    fun unknownArgumentsAndMissingRequiredArgumentsAreRejectedBeforeHandler() {
        var calls = 0
        val registry = ManaReadOnlyToolRegistry(listOf(
            ManaReadOnlyToolDefinition(
                name = "memory.search",
                argumentNames = setOf("query", "limit"),
                requiredArguments = setOf("query")
            ) { _, _ ->
                calls++
                ManaCognitiveToolResult(true, "Найдено.")
            }
        ))

        assertFalse(registry.execute(ManaCognitiveAction("memory.search", mapOf("unexpected" to "x"))).success)
        assertFalse(registry.execute(ManaCognitiveAction("memory.search", mapOf("limit" to "5"))).success)
        assertEquals(0, calls)
    }

    @Test
    fun requestedAppSearchIsAnExplicitReadOnlyCapability() {
        val registry = ManaReadOnlyToolRegistry(listOf(
            ManaReadOnlyToolDefinition("apps.find_launchable", setOf("query"), setOf("query")) { args, _ ->
                ManaCognitiveToolResult(true, "Только совпадения для ${args["query"]}.")
            }
        ))
        assertTrue(registry.availableTools().contains("apps.find_launchable"))
        assertTrue(registry.execute(ManaCognitiveAction("apps.find_launchable", mapOf("query" to "Книги"))).success)
    }

    @Test
    fun notificationStatusIsAnExplicitReadOnlyCapability() {
        val registry = ManaReadOnlyToolRegistry(listOf(
            ManaReadOnlyToolDefinition("device.read_notification_status") { _, _ ->
                ManaCognitiveToolResult(true, "Статус доступен; содержимое уведомлений не читается.")
            }
        ))
        assertTrue(registry.availableTools().contains("device.read_notification_status"))
        assertTrue(registry.execute(ManaCognitiveAction("device.read_notification_status")).success)
    }

    @Test
    fun audioStatusIsAnExplicitReadOnlyCapability() {
        val registry = ManaReadOnlyToolRegistry(listOf(
            ManaReadOnlyToolDefinition("device.read_audio_status") { _, _ ->
                ManaCognitiveToolResult(true, "Режим звука доступен.")
            }
        ))
        assertTrue(registry.availableTools().contains("device.read_audio_status"))
        assertTrue(registry.execute(ManaCognitiveAction("device.read_audio_status")).success)
    }

    @Test
    fun controlCharactersAndOversizedArgumentsAreRejected() {
        var calls = 0
        val registry = ManaReadOnlyToolRegistry(listOf(
            ManaReadOnlyToolDefinition("memory.search", setOf("query"), setOf("query")) { _, _ ->
                calls++
                ManaCognitiveToolResult(true, "Найдено.")
            }
        ))

        assertFalse(registry.execute(ManaCognitiveAction("memory.search", mapOf("query" to "bad\nvalue"))).success)
        assertFalse(registry.execute(ManaCognitiveAction("memory.search", mapOf("query" to "x".repeat(257)))).success)
        assertEquals(0, calls)
    }

    @Test
    fun stoppedRequestNeverInvokesHandler() {
        var calls = 0
        val registry = ManaReadOnlyToolRegistry(listOf(
            ManaReadOnlyToolDefinition("device.read_status") { _, _ ->
                calls++
                ManaCognitiveToolResult(true, "unexpected")
            }
        ))
        val stop = ManaCancellationSignal().apply { requestStop() }

        assertFalse(registry.execute(ManaCognitiveAction("device.read_status"), stop).success)
        assertEquals(0, calls)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sideEffectingToolCannotBeRegistered() {
        ManaReadOnlyToolRegistry(listOf(
            ManaReadOnlyToolDefinition("messages.send") { _, _ ->
                ManaCognitiveToolResult(true, "must never run")
            }
        ))
    }
}
