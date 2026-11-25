package com.mana.os.integration

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.util.Locale

/**
 * Android read-only diagnostics. Exposes coarse battery/storage facts only.
 * Installed-app inventory is intentionally not sent to the remote model because it can reveal
 * private habits; it remains unavailable until a local-only privacy design exists.
 */
object ManaAndroidReadOnlyTools {
    fun create(context: Context): ManaReadOnlyToolRegistry {
        val app = context.applicationContext
        val battery = ManaReadOnlyToolDefinition("device.read_battery") { _, control ->
            if (control.isStopRequested()) return@ManaReadOnlyToolDefinition ManaCognitiveToolResult(false, "Запрос остановлен.")
            val manager = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return@ManaReadOnlyToolDefinition ManaCognitiveToolResult(false, "Данные батареи недоступны.")
            val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level !in 0..100) ManaCognitiveToolResult(false, "Уровень батареи недоступен.")
            else ManaCognitiveToolResult(true, "Заряд батареи: $level%.")
        }
        val storage = ManaReadOnlyToolDefinition("device.read_storage") { _, control ->
            if (control.isStopRequested()) return@ManaReadOnlyToolDefinition ManaCognitiveToolResult(false, "Запрос остановлен.")
            runCatching {
                val stats = StatFs(Environment.getDataDirectory().absolutePath)
                val total = stats.totalBytes.coerceAtLeast(0L)
                val available = stats.availableBytes.coerceAtLeast(0L)
                val used = (total - available).coerceAtLeast(0L)
                ManaCognitiveToolResult(
                    true,
                    "Внутреннее хранилище: занято ${formatBytes(used)} из ${formatBytes(total)}, свободно ${formatBytes(available)}."
                )
            }.getOrElse { ManaCognitiveToolResult(false, "Данные хранилища недоступны.") }
        }
        val status = ManaReadOnlyToolDefinition("device.read_status") { _, control ->
            if (control.isStopRequested()) return@ManaReadOnlyToolDefinition ManaCognitiveToolResult(false, "Запрос остановлен.")
            val manager = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val storageResult = runCatching {
                val stats = StatFs(Environment.getDataDirectory().absolutePath)
                val total = stats.totalBytes.coerceAtLeast(0L)
                val available = stats.availableBytes.coerceAtLeast(0L)
                "свободно ${formatBytes(available)} из ${formatBytes(total)}"
            }.getOrDefault("хранилище недоступно")
            val batteryText = if (level in 0..100) "заряд $level%" else "заряд недоступен"
            ManaCognitiveToolResult(true, "Android ${Build.VERSION.RELEASE}: $batteryText, $storageResult.")
        }
        return ManaReadOnlyToolRegistry(listOf(battery, storage, status))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes Б"
        val units = arrayOf("КБ", "МБ", "ГБ", "ТБ")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }
}
