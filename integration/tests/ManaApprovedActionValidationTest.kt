package com.mana.os.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManaApprovedActionValidationTest {
    @Test
    fun acceptsOnlyExactLaunchPackageArgument() {
        val action = ManaCognitiveAction("apps.launch", mapOf("package_name" to "com.example.reader"))
        assertEquals("com.example.reader", ManaApprovedActionValidation.launchPackage(action))
    }

    @Test
    fun rejectsOtherActionsAndExtraArguments() {
        assertNull(ManaApprovedActionValidation.launchPackage(
            ManaCognitiveAction("settings.change", mapOf("package_name" to "com.example.reader"))
        ))
        assertNull(ManaApprovedActionValidation.launchPackage(
            ManaCognitiveAction("apps.launch", mapOf("package_name" to "com.example.reader", "intent" to "arbitrary"))
        ))
    }

    @Test
    fun rejectsMalformedOrManaPackageNames() {
        listOf("", "com", "com..bad", "com.example/Activity", "com.mana.os", "x".repeat(256)).forEach { value ->
            assertNull(ManaApprovedActionValidation.launchPackage(
                ManaCognitiveAction("apps.launch", mapOf("package_name" to value))
            ))
        }
    }

    @Test
    fun acceptsOnlyFixedAndroidSettingsPages() {
        ManaSettingsPage.values().forEach { page ->
            assertEquals(page, ManaApprovedActionValidation.settingsPage(
                ManaCognitiveAction("settings.open_page", mapOf("page" to page.key))
            ))
        }
    }

    @Test
    fun includesOnlyFixedNotificationSettingsDestinations() {
        assertEquals(ManaSettingsPage.NOTIFICATIONS, ManaApprovedActionValidation.settingsPage(
            ManaCognitiveAction("settings.open_page", mapOf("page" to "notifications"))
        ))
        assertEquals(ManaSettingsPage.DO_NOT_DISTURB, ManaApprovedActionValidation.settingsPage(
            ManaCognitiveAction("settings.open_page", mapOf("page" to "do_not_disturb"))
        ))
    }

    @Test
    fun acceptsOnlyTwoExplicitDoNotDisturbFilters() {
        assertEquals(ManaDndFilter.PRIORITY, ManaApprovedActionValidation.dndFilter(
            ManaCognitiveAction("device.set_dnd_filter", mapOf("filter" to "priority"))
        ))
        assertEquals(ManaDndFilter.ALL, ManaApprovedActionValidation.dndFilter(
            ManaCognitiveAction("device.set_dnd_filter", mapOf("filter" to "all"))
        ))
        assertNull(ManaApprovedActionValidation.dndFilter(
            ManaCognitiveAction("device.set_dnd_filter", mapOf("filter" to "none"))
        ))
        assertNull(ManaApprovedActionValidation.dndFilter(
            ManaCognitiveAction("device.set_dnd_filter", mapOf("filter" to "priority", "duration" to "1h"))
        ))
    }

    @Test
    fun acceptsOnlyExactRingOrMediaVolumePercentages() {
        assertEquals("Звонок: 65%", ManaApprovedActionValidation.audioVolume(
            ManaCognitiveAction("device.set_audio_volume", mapOf("stream" to "ring", "percent" to "65"))
        )?.label)
        assertEquals("Мультимедиа: 0%", ManaApprovedActionValidation.audioVolume(
            ManaCognitiveAction("device.set_audio_volume", mapOf("stream" to "media", "percent" to "0"))
        )?.label)
        listOf("-1", "101", "01", "50.5", "loud", "").forEach { value ->
            assertNull(ManaApprovedActionValidation.audioVolume(
                ManaCognitiveAction("device.set_audio_volume", mapOf("stream" to "ring", "percent" to value))
            ))
        }
        assertNull(ManaApprovedActionValidation.audioVolume(
            ManaCognitiveAction("device.set_audio_volume", mapOf("stream" to "alarm", "percent" to "50"))
        ))
        assertNull(ManaApprovedActionValidation.audioVolume(
            ManaCognitiveAction("device.set_audio_volume", mapOf("stream" to "ring", "percent" to "50", "extra" to "x"))
        ))
    }

    @Test
    fun rejectsArbitrarySettingsActionsAndArguments() {
        assertNull(ManaApprovedActionValidation.settingsPage(
            ManaCognitiveAction("settings.change", mapOf("page" to "wifi"))
        ))
        assertNull(ManaApprovedActionValidation.settingsPage(
            ManaCognitiveAction("settings.open_page", mapOf("page" to "wifi", "enabled" to "true"))
        ))
        assertNull(ManaApprovedActionValidation.settingsPage(
            ManaCognitiveAction("settings.open_page", mapOf("page" to "developer_options"))
        ))
    }
}
