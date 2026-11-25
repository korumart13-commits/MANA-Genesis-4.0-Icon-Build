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

enum class ManaAudioStream(val key: String, val label: String, val androidStream: Int) {
    RING("ring", "Звонок", android.media.AudioManager.STREAM_RING),
    MEDIA("media", "Мультимедиа", android.media.AudioManager.STREAM_MUSIC)
}

data class ManaAudioVolumeRequest(val stream: ManaAudioStream, val percent: Int) {
    val label: String get() = "${stream.label}: $percent%"
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

    fun audioVolume(action: ManaCognitiveAction): ManaAudioVolumeRequest? {
        if (action.name != "device.set_audio_volume" ||
            action.arguments.keys != setOf("stream", "percent")) return null
        val streamKey = action.arguments["stream"] ?: return null
        val percentText = action.arguments["percent"] ?: return null
        if (!percentText.matches(Regex("0|[1-9][0-9]?|100"))) return null
        val stream = ManaAudioStream.values().firstOrNull { it.key == streamKey } ?: return null
        return ManaAudioVolumeRequest(stream, percentText.toInt())
    }
}
