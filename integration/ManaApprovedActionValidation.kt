package com.mana.os.integration

/**
 * Strict argument validation for the first owner-approved Android actions.
 * Model-provided display labels and arbitrary Android intents are never executable.
 */
enum class ManaSettingsPage(val key: String, val label: String, val androidAction: String) {
    WIFI("wifi", "Wi-Fi", android.provider.Settings.ACTION_WIFI_SETTINGS),
    BLUETOOTH("bluetooth", "Bluetooth", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
    DISPLAY("display", "Экран", android.provider.Settings.ACTION_DISPLAY_SETTINGS),
    SOUND("sound", "Звук", android.provider.Settings.ACTION_SOUND_SETTINGS),
    BATTERY("battery", "Энергосбережение", android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS),
    NOTIFICATIONS("notifications", "Уведомления", android.provider.Settings.ACTION_NOTIFICATION_SETTINGS),
    DO_NOT_DISTURB("do_not_disturb", "Доступ «Не беспокоить»", android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
}


enum class ManaDndFilter(val key: String, val label: String, val androidFilter: Int) {
    PRIORITY("priority", "Только приоритетные уведомления", android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY),
    ALL("all", "Обычные уведомления (выключить «Не беспокоить»)", android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
}

object ManaApprovedActionValidation {
    fun launchPackage(action: ManaCognitiveAction): String? {
        if (action.name != "apps.launch" || action.arguments.keys != setOf("package_name")) return null
        val packageName = action.arguments["package_name"] ?: return null
        if (packageName.length !in 3..255 || packageName == "com.mana.os") return null
        if (!packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) return null
        return packageName
    }

    fun settingsPage(action: ManaCognitiveAction): ManaSettingsPage? {
        if (action.name != "settings.open_page" || action.arguments.keys != setOf("page")) return null
        val key = action.arguments["page"] ?: return null
        return ManaSettingsPage.values().firstOrNull { it.key == key }
    }

    fun dndFilter(action: ManaCognitiveAction): ManaDndFilter? {
        if (action.name != "device.set_dnd_filter" || action.arguments.keys != setOf("filter")) return null
        val key = action.arguments["filter"] ?: return null
        return ManaDndFilter.values().firstOrNull { it.key == key }
    }
}
