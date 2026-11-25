package com.mana.os.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.util.Locale

/**
 * Android read-only diagnostics plus a narrowly scoped local app-name search.
 * Full installed-app inventory is never sent. A narrowly scoped search returns at most five
 * matches for an explicit owner request, so MANA can identify a requested launch target.
 */
object ManaAndroidReadOnlyTools {
    fun create(context: Context): ManaReadOnlyToolRegistry {
        val app = context.applicationContext
        val findApps = ManaReadOnlyToolDefinition(
            name = "apps.find_launchable",
            argumentNames = setOf("query"),
            requiredArguments = setOf("query")
        ) { arguments, control ->
            if (control.isStopRequested()) return@ManaReadOnlyToolDefinition ManaCognitiveToolResult(false, "Запрос остановлен.")
            val query = arguments["query"]?.trim().orEmpty()
            if (query.length !in 2..80) {
                return@ManaReadOnlyToolDefinition ManaCognitiveToolResult(false, "Для поиска приложения укажи не менее двух символов.")
            }
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val matches = app.packageManager
                .queryIntentActivities(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .asSequence()
                .filter { !control.isStopRequested() }
                .distinctBy { it.activityInfo.packageName }
                .map { it to it.loadLabel(app.packageManager).toString() }
                .filter { (_, label) -> label.contains(query, ignoreCase = true) }
                .sortedBy { (_, label) -> label.lowercase(Locale.ROOT) }
                .take(5)
                .toList()
            if (control.isStopRequested()) {
                ManaCognitiveToolResult(false, "Запрос остановлен.")
            } else if (matches.isEmpty()) {
                ManaCognitiveToolResult(true, "Не нашла приложение с названием «$query».")
            } else {
                val result = matches.joinToString("; ") { (info, label) ->
                    "$label (package_name=${info.activityInfo.packageName})"
                }
                ManaCognitiveToolResult(true, "Подходящие приложения (не более пяти): $result")
            }
        }
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
        return ManaReadOnlyToolRegistry(listOf(findApps, battery, storage, status))
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
