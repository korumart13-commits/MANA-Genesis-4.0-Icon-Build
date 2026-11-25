package com.mana.os.integration

import android.app.NotificationManager
import android.content.Context
import android.content.Intent

/** Result returned to the owner UI after a confirmed Android action. */
data class ManaApprovedActionResult(val success: Boolean, val message: String)

/**
 * Narrow side-effect adapter: launch a user-visible app, open a fixed Android settings page,
 * or apply one of two fixed Do Not Disturb filters after exact one-time owner approval.
 */
class ManaAndroidApprovedActionExecutor(
    context: Context,
    private val broker: ManaOwnerApprovalBroker
) {
    private val app = context.applicationContext

    fun previewLaunchLabel(request: ManaApprovalRequest): String? {
        val action = ManaCognitiveAction(request.actionName, request.arguments)
        return when (request.actionName) {
            "apps.launch" -> {
                val packageName = ManaApprovedActionValidation.launchPackage(action) ?: return null
                runCatching {
                    val launchIntent = app.packageManager.getLaunchIntentForPackage(packageName) ?: return null
                    if (launchIntent.component?.packageName != packageName) return null
                    app.packageManager.getApplicationLabel(
                        app.packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                }.getOrNull()
            }
            "settings.open_page" -> ManaApprovedActionValidation.settingsPage(action)?.label
            "device.set_dnd_filter" -> ManaApprovedActionValidation.dndFilter(action)?.label
            else -> null
        }
    }

    /** Must be called synchronously from the owner's explicit confirmation button. */
    fun confirmAndLaunch(requestId: String): ManaApprovedActionResult {
        val request = broker.pendingRequests().firstOrNull { it.requestId == requestId }
            ?: return ManaApprovedActionResult(false, "Запрос истёк или уже обработан.")
        val action = ManaCognitiveAction(request.actionName, request.arguments)

        val targetLabel: String
        var launchIntent: Intent? = null
        var dndManager: NotificationManager? = null
        var dndFilter: ManaDndFilter? = null

        when (request.actionName) {
            "apps.launch" -> {
                val packageName = ManaApprovedActionValidation.launchPackage(action)
                    ?: return reject(requestId, "Некорректная цель запуска. Ничего не изменено.")
                val resolvedIntent = app.packageManager.getLaunchIntentForPackage(packageName)
                    ?: return reject(requestId, "Приложение больше недоступно.")
                if (resolvedIntent.component?.packageName != packageName) {
                    return reject(requestId, "Android не подтвердил цель запуска.")
                }
                launchIntent = resolvedIntent
                targetLabel = runCatching {
                    app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(packageName, 0)).toString()
                }.getOrDefault(packageName)
            }
            "settings.open_page" -> {
                val page = ManaApprovedActionValidation.settingsPage(action)
                    ?: return reject(requestId, "Эта страница настроек не входит в разрешённый список.")
                val resolvedIntent = Intent(page.androidAction)
                if (resolvedIntent.resolveActivity(app.packageManager) == null) {
                    return reject(requestId, "Эта страница настроек недоступна на устройстве.")
                }
                launchIntent = resolvedIntent
                targetLabel = page.label
            }
            "device.set_dnd_filter" -> {
                dndFilter = ManaApprovedActionValidation.dndFilter(action)
                    ?: return reject(requestId, "Недопустимый режим «Не беспокоить».")
                dndManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return reject(requestId, "Управление уведомлениями недоступно.")
                if (dndManager?.isNotificationPolicyAccessGranted != true) {
                    return reject(requestId, "Android ещё не предоставил MANA доступ к режиму «Не беспокоить». Сначала открой раздел доступа в настройках Android.")
                }
                targetLabel = dndFilter!!.label
            }
            else -> return reject(requestId, "Этот тип действия пока не поддерживается.")
        }

        val token = broker.confirmFromOwnerUi(requestId, true)
            ?: return ManaApprovedActionResult(false, "Запрос истёк или уже обработан.")
        if (!broker.consumeForExactAction(token, action)) {
            return ManaApprovedActionResult(false, "Разрешение не прошло проверку точного действия. Ничего не изменено.")
        }

        return try {
            if (dndFilter != null) {
                dndManager!!.setInterruptionFilter(dndFilter!!.androidFilter)
                ManaApprovedActionResult(
                    true,
                    "Режим «Не беспокоить» установлен: $targetLabel. Он действует, пока ты не изменишь режим вручную."
                )
            } else {
                val intent = launchIntent ?: return ManaApprovedActionResult(false, "Цель действия недоступна.")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
                val message = if (action.name == "apps.launch") {
                    "Команда запуска приложения «$targetLabel» передана Android."
                } else {
                    "Открытие раздела «$targetLabel» передано Android. Изменения параметров не выполнялись."
                }
                ManaApprovedActionResult(true, message)
            }
        } catch (_: Exception) {
            ManaApprovedActionResult(false, "Android не смог выполнить действие: «$targetLabel».")
        }
    }

    private fun reject(requestId: String, message: String): ManaApprovedActionResult {
        broker.deny(requestId)
        return ManaApprovedActionResult(false, message)
    }
}
