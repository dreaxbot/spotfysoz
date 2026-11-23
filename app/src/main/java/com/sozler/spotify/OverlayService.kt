package com.sozler.spotify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class OverlayService : Service() {

    companion object {
        @Volatile var running = false
        const val ACTION_STOP = "STOP"
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private var view: LyricsView? = null
    private lateinit var lp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() {
            view?.tick()
            handler.postDelayed(this, 40)
        }
    }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyPrefs() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        prefs = Cfg.prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        val h = (260 * dm.density).toInt()

        lp = WindowManager.LayoutParams(
            dm.widthPixels, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayFlags(), PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = prefs.getInt("y", dm.heightPixels - h - (140 * dm.density).toInt())
            alpha = prefs.getInt("alpha", 80) / 100f
        }

        val v = LyricsView(this, prefs)
        v.setOnTouchListener(object : View.OnTouchListener {
            var startY = 0
            var touchY = 0f
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { startY = lp.y; touchY = e.rawY }
                    MotionEvent.ACTION_MOVE -> {
                        lp.y = (startY + (e.rawY - touchY)).toInt()
                            .coerceIn(0, dm.heightPixels - (60 * dm.density).toInt())
                        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                    }
                    MotionEvent.ACTION_UP -> prefs.edit().putInt("y", lp.y).apply()
                }
                return true
            }
        })
        wm.addView(v, lp)
        view = v

        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        running = true
        handler.post(loop)
    }

    private fun overlayFlags(): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (prefs.getBoolean("locked", true)) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    private fun applyPrefs() {
        val v = view ?: return
        lp.flags = overlayFlags()
        // Android 12+: dokunuşların alttaki uygulamaya geçmesi için opaklık en fazla %80 olmalı
        lp.alpha = prefs.getInt("alpha", 80).coerceAtMost(80) / 100f
        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("ov", "Söz penceresi", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "ov")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Spotify Sözler çalışıyor")
            .setContentText("Ayarlar için dokun")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(loop)
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        super.onDestroy()
    }
}
