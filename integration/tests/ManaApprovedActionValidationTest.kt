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
}
