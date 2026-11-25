package com.mana.os.integration

import android.content.Context
import android.content.Intent

/** Result returned to the owner UI after a confirmed app-launch request. */
data class ManaApprovedActionResult(val success: Boolean, val message: String)

/**
 * The first concrete side-effect adapter. It can launch only a user-visible launcher activity
 * for one exact package name after consuming a matching one-time owner token.
 */
class ManaAndroidApprovedActionExecutor(
    context: Context,
    private val broker: ManaOwnerApprovalBroker
) {
    private val app = context.applicationContext

    fun previewLaunchLabel(request: ManaApprovalRequest): String? {
        if (request.actionName != "apps.launch") return null
        val action = ManaCognitiveAction(request.actionName, request.arguments)
        val packageName = ManaApprovedActionValidation.launchPackage(action) ?: return null
        return runCatching {
            val launchIntent = app.packageManager.getLaunchIntentForPackage(packageName) ?: return null
            if (launchIntent.component?.packageName != packageName) return null
            app.packageManager.getApplicationLabel(
                app.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrNull()
    }

    /** Must be called synchronously from the explicit owner confirmation button. */
    fun confirmAndLaunch(requestId: String): ManaApprovedActionResult {
        val request = broker.pendingRequests().firstOrNull { it.requestId == requestId }
            ?: return ManaApprovedActionResult(false, "Запрос истёк или уже обработан.")
        val action = ManaCognitiveAction(request.actionName, request.arguments)
        val packageName = ManaApprovedActionValidation.launchPackage(action)
            ?: run {
                broker.deny(requestId)
                return ManaApprovedActionResult(false, "Этот тип действия пока не поддерживается. Ничего не изменено.")
            }
        val launchIntent = runCatching {
            app.packageManager.getLaunchIntentForPackage(packageName)
        }.getOrNull()
        if (launchIntent == null || launchIntent.component?.packageName != packageName) {
            broker.deny(requestId)
            return ManaApprovedActionResult(false, "Приложение больше недоступно или не имеет значка запуска.")
        }
        val label = runCatching {
            app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)

        val token = broker.confirmFromOwnerUi(requestId, true)
            ?: return ManaApprovedActionResult(false, "Запрос истёк или уже обработан.")
        if (!broker.consumeForExactAction(token, action)) {
            return ManaApprovedActionResult(false, "Разрешение не прошло проверку точного действия. Ничего не запущено.")
        }
        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launchIntent)
            ManaApprovedActionResult(true, "Команда запуска приложения «$label» передана Android.")
        } catch (_: Exception) {
            ManaApprovedActionResult(false, "Android не смог запустить приложение «$label».")
        }
    }
}
