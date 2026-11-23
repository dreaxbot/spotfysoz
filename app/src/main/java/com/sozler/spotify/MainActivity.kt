package com.sozler.spotify

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Masaüstündeki klavye kısayollarının yerine geçen ayar ekranı. */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvPerm: TextView
    private lateinit var btnToggle: Button
    private lateinit var tvSize: TextView
    private lateinit var tvAlpha: TextView
    private lateinit var tvOffset: TextView
    private lateinit var seekOffset: SeekBar

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 15f, top: Int = 16): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, dp(top), 0, dp(4))
        }

    private fun seek(min: Int, max: Int, cur: Int, onChange: (Int) -> Unit): SeekBar =
        SeekBar(this).apply {
            this.max = max - min
            progress = cur.coerceIn(min, max) - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) = onChange(p + min)
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

    private fun listenerOn() =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Cfg.prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        root.addView(label("Spotify Sözler", 26f, 0))

        // --- izinler + başlat/durdur ---
        tvPerm = label("", 14f, 12)
        root.addView(tvPerm)
        root.addView(Button(this).apply {
            text = "1) Bildirim erişimini aç"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "2) Üstte gösterme iznini aç"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
        })
        btnToggle = Button(this).apply { setOnClickListener { toggleService() } }
        root.addView(btnToggle)

        // --- ayarlar (eski kısayolların karşılığı) ---
        root.addView(label("Ayarlar", 20f, 24))

        root.addView(Switch(this).apply {
            text = "Kilitli (dokunuşlar alttaki uygulamaya geçer)"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("locked", true)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("locked", c).apply() }
        })
        root.addView(label("Kilidi açınca sözleri parmağınla yukarı/aşağı sürükleyip taşıyabilirsin.", 12f, 0))

        tvSize = label("", 15f, 16)
        root.addView(tvSize)
        root.addView(seek(12, 48, prefs.getInt("size", 22)) {
            prefs.edit().putInt("size", it).apply(); tvSize.text = "Yazı boyutu: $it"
        })
        tvSize.text = "Yazı boyutu: ${prefs.getInt("size", 22)}"

        tvAlpha = label("", 15f, 16)
        root.addView(tvAlpha)
        root.addView(seek(20, 80, prefs.getInt("alpha", 80).coerceAtMost(80)) {
            prefs.edit().putInt("alpha", it).apply(); tvAlpha.text = "Şeffaflık (opaklık): %$it"
        })
        tvAlpha.text = "Şeffaflık (opaklık): %${prefs.getInt("alpha", 80).coerceAtMost(80)}"

        tvOffset = label("", 15f, 16)
        root.addView(tvOffset)
        seekOffset = seek(-50, 50, Math.round(prefs.getFloat("offset", 0f) * 10)) {
            prefs.edit().putFloat("offset", it / 10f).apply(); showOffset()
        }
        root.addView(seekOffset)
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(Button(context).apply {
                text = "− 0.1 sn (geciktir)"
                setOnClickListener { seekOffset.progress = seekOffset.progress - 1 }
            })
            addView(Button(context).apply {
                text = "+ 0.1 sn (öne al)"
                setOnClickListener { seekOffset.progress = seekOffset.progress + 1 }
            })
        })
        showOffset()

        root.addView(Switch(this).apply {
            text = "Tahmini senkron (senkronsuz sözleri şarkıya yay)"
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, 0)
            isChecked = prefs.getBoolean("approx", true)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("approx", c).apply() }
        })

        root.addView(Button(this).apply {
            text = "Konumu sıfırla"
            setOnClickListener { prefs.edit().remove("y").apply(); restartIfRunning() }
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            addView(root)
        })

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun showOffset() {
        val v = prefs.getFloat("offset", 0f)
        tvOffset.text = "Senkron ayarı: ${if (v > 0) "+" else ""}${"%.1f".format(v)} sn"
    }

    private fun toggleService() {
        if (OverlayService.running) {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        } else if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Önce “Üstte gösterme” iznini aç", Toast.LENGTH_LONG).show()
        } else {
            if (!listenerOn()) {
                Toast.makeText(this, "Spotify'ı okuyabilmek için bildirim erişimi gerekli", Toast.LENGTH_LONG).show()
            }
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 400)
    }

    private fun restartIfRunning() {
        if (!OverlayService.running) return
        startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        Handler(Looper.getMainLooper()).postDelayed({
            startForegroundService(Intent(this, OverlayService::class.java)); refresh()
        }, 500)
    }

    private fun refresh() {
        val ok = "✓ açık"; val no = "✗ kapalı"
        tvPerm.text = "Bildirim erişimi: ${if (listenerOn()) ok else no}\n" +
                "Üstte gösterme izni: ${if (Settings.canDrawOverlays(this)) ok else no}"
        btnToggle.text = if (OverlayService.running) "Durdur" else "Başlat"
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }
}
