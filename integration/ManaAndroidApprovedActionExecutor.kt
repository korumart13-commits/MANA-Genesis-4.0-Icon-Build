package com.mana.os.integration

import android.content.Context
import android.content.Intent

/** Result returned to the owner UI after a confirmed Android action. */
data class ManaApprovedActionResult(val success: Boolean, val message: String)

/**
 * Narrow side-effect adapter: launch a user-visible app or open one fixed Android settings page,
 * only after consuming a matching one-time owner token.
 */
class ManaAndroidApprovedActionExecutor(
    context: Context,
    private val broker: ManaOwnerApprovalBroker
) {
    private val app = context.applicationContext

    fun previewLaunchLabel(request: ManaApprovalRequest): String? {
        val action = ManaCognitiveAction(request.actionName, request.arguments)
        return when {
            request.actionName == "apps.launch" -> {
                val packageName = ManaApprovedActionValidation.launchPackage(action) ?: return null
                runCatching {
                    val launchIntent = app.packageManager.getLaunchIntentForPackage(packageName) ?: return null
                    if (launchIntent.component?.packageName != packageName) return null
                    app.packageManager.getApplicationLabel(
                        app.packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                }.getOrNull()
            }
            request.actionName == "settings.open_page" ->
                ManaApprovedActionValidation.settingsPage(action)?.label
            else -> null
        }
    }

    /** Must be called synchronously from the owner's explicit confirmation button. */
    fun confirmAndLaunch(requestId: String): ManaApprovedActionResult {
        val request = broker.pendingRequests().firstOrNull { it.requestId == requestId }
            ?: return ManaApprovedActionResult(false, "Запрос истёк или уже обработан.")
        val action = ManaCognitiveAction(request.actionName, request.arguments)

        val targetLabel: String
        val launchIntent: Intent
        when (request.actionName) {
            "apps.launch" -> {
                val packageName = ManaApprovedActionValidation.launchPackage(action)
                    ?: return reject(requestId, "Некорректная цель запуска. Ничего не изменено.")
                launchIntent = runCatching {
                    app.packageManager.getLaunchIntentForPackage(packageName)
                }.getOrNull() ?: return reject(requestId, "Приложение больше недоступно.")
                if (launchIntent.component?.packageName != packageName) {
                    return reject(requestId, "Android не подтвердил цель запуска.")
                }
                targetLabel = runCatching {
                    app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(packageName, 0)).toString()
                }.getOrDefault(packageName)
            }
            "settings.open_page" -> {
                val page = ManaApprovedActionValidation.settingsPage(action)
                    ?: return reject(requestId, "Эта страница настроек не входит в разрешённый список.")
                launchIntent = Intent(page.androidAction)
                if (launchIntent.resolveActivity(app.packageManager) == null) {
                    return reject(requestId, "Эта страница настроек недоступна на устройстве.")
                }
                targetLabel = page.label
            }
            else -> return reject(requestId, "Этот тип действия пока не поддерживается.")
        }

        val token = broker.confirmFromOwnerUi(requestId, true)
            ?: return ManaApprovedActionResult(false, "Запрос истёк или уже обработан.")
        if (!broker.consumeForExactAction(token, action)) {
            return ManaApprovedActionResult(false, "Разрешение не прошло проверку точного действия. Ничего не открыто.")
        }
        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launchIntent)
            val message = if (action.name == "apps.launch") {
                "Команда запуска приложения «$targetLabel» передана Android."
            } else {
                "Открытие раздела «$targetLabel» передано Android. Изменения параметров не выполнялись."
            }
            ManaApprovedActionResult(true, message)
        } catch (_: Exception) {
            ManaApprovedActionResult(false, "Android не смог открыть «$targetLabel».")
        }
    }

    private fun reject(requestId: String, message: String): ManaApprovedActionResult {
        broker.deny(requestId)
        return ManaApprovedActionResult(false, message)
    }
}
