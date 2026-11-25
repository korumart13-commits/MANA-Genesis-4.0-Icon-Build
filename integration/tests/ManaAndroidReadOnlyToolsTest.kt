package com.mana.os.integration

import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class ManaAndroidReadOnlyToolsTest {
    @Test
    fun registryExposesOnlyCoarseReadOnlyDeviceDiagnostics() {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(context)

        val tools = ManaAndroidReadOnlyTools.create(context)
        assertTrue(tools.availableTools().contains("device.read_battery"))
        assertTrue(tools.availableTools().contains("device.read_storage"))
        assertTrue(tools.availableTools().contains("device.read_status"))
        assertTrue(tools.availableTools().none { it.startsWith("apps.") })
        assertTrue(tools.availableTools().none { it.contains("write") || it.contains("delete") })
    }
}
