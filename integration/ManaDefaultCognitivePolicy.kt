package com.mana.os.integration

/**
 * Conservative baseline policy for the cognitive tool boundary.
 *
 * This policy is intentionally allow-list based: unknown tools are denied, read-only
 * capabilities are explicitly allowed, and user-visible side effects require owner approval.
 * A real adapter must still validate arguments and Android permissions before execution.
 */
class ManaDefaultCognitivePolicy : ManaCognitivePolicy {
    override fun check(action: ManaCognitiveAction): ManaActionVerdict {
        val name = action.name.trim().lowercase()
        if (name.isEmpty() || name.length > 80) return ManaActionVerdict.DENY

        if (name in DENIED_TOOLS || name.startsWith("root.") || name.startsWith("shell.") ||
            name.startsWith("firmware.") || name.startsWith("bootloader.")) {
            return ManaActionVerdict.DENY
        }
        if (name in OWNER_CONFIRMATION_TOOLS) return ManaActionVerdict.REQUIRES_OWNER_CONFIRMATION
        if (name in READ_ONLY_TOOLS) return ManaActionVerdict.ALLOW
        return ManaActionVerdict.DENY
    }

    private companion object {
        val READ_ONLY_TOOLS = setOf(
            "apps.list_installed",
            "apps.find_launchable",
            "device.read_status",
            "device.read_battery",
            "device.read_storage",
            "device.read_notification_status",
            "diary.read",
            "memory.search",
            "updates.check"
        )
        val OWNER_CONFIRMATION_TOOLS = setOf(
            "apps.launch",
            "settings.open_page",
            "device.set_dnd_filter",
            "settings.change",
            "calls.answer",
            "messages.send",
            "calendar.create",
            "calendar.modify",
            "files.create",
            "files.modify",
            "files.delete",
            "network.share",
            "minecraft.join",
            "smart_home.control"
        )
        val DENIED_TOOLS = setOf(
            "security.disable",
            "data.wipe",
            "permissions.grant",
            "credentials.read",
            "secrets.export",
            "app.uninstall",
            "firmware.flash",
            "bootloader.unlock"
        )
    }
}
