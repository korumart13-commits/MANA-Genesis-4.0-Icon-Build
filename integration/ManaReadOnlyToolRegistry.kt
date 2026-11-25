package com.mana.os.integration

/**
 * Small, fail-closed execution boundary for explicitly registered read-only tools.
 *
 * The model may propose an action, but it cannot register handlers or widen argument schemas.
 * Side-effecting capabilities are intentionally excluded from this registry and must use a
 * separate owner-confirmation workflow in a future implementation.
 */
class ManaReadOnlyToolRegistry(
    definitions: List<ManaReadOnlyToolDefinition>
) : ManaCancellableCognitiveTools {
    private val handlers: Map<String, ManaReadOnlyToolDefinition>

    init {
        require(definitions.size <= MAX_TOOLS) { "Too many tool definitions." }
        require(definitions.map { it.name }.distinct().size == definitions.size) {
            "Duplicate tool names are not allowed."
        }
        definitions.forEach { definition ->
            require(definition.name in ALLOWED_READ_ONLY_TOOLS) {
                "Tool is not in the read-only allowlist."
            }
            require(definition.name.matches(Regex("[a-z][a-z0-9_.]{2,79}"))) {
                "Invalid tool name."
            }
            require(definition.argumentNames.size <= MAX_ARGUMENTS)
            require(definition.argumentNames.all { it.matches(Regex("[a-z][a-z0-9_]{0,39}")) })
        }
        handlers = definitions.associateBy { it.name }
    }

    override fun availableTools(): List<String> = handlers.keys.sorted()

    override fun execute(action: ManaCognitiveAction): ManaCognitiveToolResult =
        execute(action, ManaRuntimeControl.NEVER_STOP)

    override fun execute(
        action: ManaCognitiveAction,
        control: ManaRuntimeControl
    ): ManaCognitiveToolResult {
        if (control.isStopRequested()) return ManaCognitiveToolResult(false, "Запрос остановлен.")
        val definition = handlers[action.name]
            ?: return ManaCognitiveToolResult(false, "Инструмент не зарегистрирован.")
        if (action.arguments.size > MAX_ARGUMENTS ||
            action.arguments.keys.any { it !in definition.argumentNames } ||
            action.arguments.values.any { it.length > MAX_ARGUMENT_LENGTH || it.any(Char::isISOControl) }
        ) {
            return ManaCognitiveToolResult(false, "Аргументы инструмента отклонены.")
        }
        if (!action.arguments.keys.containsAll(definition.requiredArguments)) {
            return ManaCognitiveToolResult(false, "Не хватает обязательных аргументов.")
        }
        if (control.isStopRequested()) return ManaCognitiveToolResult(false, "Запрос остановлен.")
        return runCatching { definition.handler(action.arguments.toMap(), control) }
            .getOrElse { ManaCognitiveToolResult(false, "Инструмент завершился с безопасной ошибкой.") }
    }

    private companion object {
        const val MAX_TOOLS = 12
        const val MAX_ARGUMENTS = 8
        const val MAX_ARGUMENT_LENGTH = 256
        val ALLOWED_READ_ONLY_TOOLS = setOf(
            "apps.list_installed",
            "apps.find_launchable",
            "device.read_status",
            "device.read_battery",
            "device.read_storage",
            "device.read_notification_status",
            "device.read_audio_status",
            "diary.read",
            "memory.search",
            "updates.check"
        )
    }
}

data class ManaReadOnlyToolDefinition(
    val name: String,
    val argumentNames: Set<String> = emptySet(),
    val requiredArguments: Set<String> = emptySet(),
    val handler: (Map<String, String>, ManaRuntimeControl) -> ManaCognitiveToolResult
) {
    init {
        require(requiredArguments.all { it in argumentNames }) {
            "Required arguments must be declared in the argument schema."
        }
    }
}
