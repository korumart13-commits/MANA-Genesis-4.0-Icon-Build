package com.mana.os.integration

/**
 * Strict argument validation for the first approved Android side effect.
 * Only an exact package identifier is accepted; display labels and arbitrary intents are never
 * supplied by the model or copied into an executable Android Intent.
 */
object ManaApprovedActionValidation {
    fun launchPackage(action: ManaCognitiveAction): String? {
        if (action.name != "apps.launch" || action.arguments.keys != setOf("package_name")) return null
        val packageName = action.arguments["package_name"] ?: return null
        if (packageName.length !in 3..255 || packageName == "com.mana.os") return null
        if (!packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) return null
        return packageName
    }
}
