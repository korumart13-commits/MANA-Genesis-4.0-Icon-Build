package com.mana.os.integration

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

/** Explicitly user-started persistent avatar overlay. No background microphone capture. */
class ManaAvatarService : Service() {
    private var windowManager: WindowManager? = null
    private var overlay: FrameLayout? = null
    private var avatar: WebView? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Фоновый аватар MANA", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (!android.provider.Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        val stop = PendingIntent.getService(this, 0,
            Intent(this, ManaAvatarService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val notification = builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MANA · аватар активен")
            .setContentText("Фоновый аватар работает. Нажми «Остановить», чтобы скрыть его.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Остановить", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)
        if (overlay == null) showOverlay()
        return START_STICKY
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = FrameLayout(this)
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = true
            settings.allowUniversalAccessFromFileURLs = false
            webViewClient = WebViewClient()
            setBackgroundColor(Color.TRANSPARENT)
            loadUrl("file:///android_asset/mana-avatar/index.html")
        }
        avatar = web
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        // Drag the avatar anywhere on screen. A short tap opens MANA's full interface.
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        web.setOnTouchListener { _, event ->
            val layout = overlay?.layoutParams as? WindowManager.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layout?.x ?: 0
                    startY = layout?.y ?: 0
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(8)) dragged = true
                    if (dragged && layout != null) {
                        layout.x = (startX - dx).coerceAtLeast(0)
                        layout.y = (startY - dy).coerceAtLeast(0)
                        runCatching { windowManager?.updateViewLayout(root, layout) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        val open = Intent(this, com.mana.os.app.ManaHomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(open)
                    }
                    true
                }
                else -> true
            }
        }
        val close = TextView(this).apply {
            text = "×"; textSize = 23f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(210, 7, 25, 48))
            setOnClickListener { stopSelf() }
        }
        root.addView(close, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END))
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(dp(184), dp(236), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END; x = dp(10); y = dp(100)
        }
        overlay = root
        runCatching { windowManager?.addView(root, params) }.onFailure { stopSelf() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        overlay?.let { runCatching { windowManager?.removeView(it) } }
        overlay = null
        avatar?.stopLoading(); avatar?.destroy(); avatar = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "mana_avatar_background"
        private const val NOTIFICATION_ID = 4404
        private const val ACTION_STOP = "com.mana.os.action.STOP_AVATAR"
    }
}
