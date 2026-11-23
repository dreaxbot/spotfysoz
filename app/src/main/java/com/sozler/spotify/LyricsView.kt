package com.sozler.spotify

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Saydam zemin üstüne, aşağıdan yukarı kayan senkronize söz çizen görünüm. */
class LyricsView(ctx: Context, private val prefs: SharedPreferences) : View(ctx) {

    private class Item(val text: String, val sizeSp: Float, val color: Int, val cy: Float)
    private class Lyr(
        val id: String?, val times: DoubleArray, val lines: List<String>,
        val status: String, val approx: Boolean
    )

    private val LEAD = 0.30
    private val ANIM = 400f
    private val WHITE = Color.rgb(255, 255, 255)
    private val GRAY = Color.rgb(208, 208, 208)
    private val DIM = Color.rgb(150, 150, 150)

    private val dm = resources.displayMetrics
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11 * dm.density
        color = Color.WHITE
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }

    @Volatile private var lyr = Lyr(null, DoubleArray(0), emptyList(), "idle", false)
    private var items: List<Item> = emptyList()
    private var lastSig: String? = null
    private var lastLocked: Boolean? = null
    private var curIdx: Int? = null
    private var curKey: String? = null
    private var animT0 = 0L
    private var n = 0
    private var showApprox = false
    private var showDrag = false

    private fun dp(v: Int) = (v * dm.density).toInt()
    private fun mix(a: Int, b: Int, f: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
    )

    fun tick() {
        if (n++ % 12 == 0) SpotifyReader.poll(context)
        update()
    }

    private fun position(): Double {
        val st = SpotifyReader.state
        var p = st.base
        if (st.playing) p += (SystemClock.elapsedRealtime() - st.t) / 1000.0 * st.speed
        return p + prefs.getFloat("offset", 0f)
    }

    private fun fetch(key: String, st: Track) {
        Thread {
            try {
                Thread.sleep(700) // süre bilgisi gelsin
                val dur = SpotifyReader.state.duration
                var res = LrcLib.cache[key]
                if (res == null) {
                    res = LrcLib.find(st.title, st.artist, st.album, dur)
                    if (res.first != "none") LrcLib.cache[key] = res
                }
                if (lyr.id != key) return@Thread
                var status = res.first
                if (status == "approx" && !prefs.getBoolean("approx", true)) status = "none"
                lyr = when (status) {
                    "ok" -> LrcLib.parseLrc(res.second ?: "").let { Lyr(key, it.first, it.second, "ok", false) }
                    "approx" -> LrcLib.approxLrc(res.second ?: "", dur).let {
                        Lyr(key, it.first, it.second, if (it.second.isNotEmpty()) "ok" else "none", true)
                    }
                    else -> Lyr(key, DoubleArray(0), emptyList(), status, false)
                }
            } catch (e: Exception) {
                if (lyr.id == key) lyr = Lyr(key, DoubleArray(0), emptyList(), "none", false)
            }
        }.start()
    }

    private fun lineText(ly: Lyr, idx: Int, k: Int): String {
        val j = idx + k
        if (j == -1 && idx == -1 && k == 0) return "♪"
        return if (j in ly.lines.indices) ly.lines[j].ifEmpty { "♪" } else ""
    }

    private fun update() {
        if (height == 0) return
        val s = prefs.getInt("size", 22).toFloat()
        val small = max(10f, s * 0.62f)
        val locked = prefs.getBoolean("locked", true)
        if (locked != lastLocked) {
            setBackgroundColor(if (locked) Color.TRANSPARENT else 0x33000000)
            lastLocked = locked
        }

        val st = SpotifyReader.state
        var msg: String? = null
        var sub: String? = null
        var idx = 0
        var key: String? = null

        if (!st.ok) {
            msg = "Spotify bekleniyor…"
        } else {
            key = st.title + "\u0001" + st.artist
            if (lyr.id != key) {
                lyr = Lyr(key, DoubleArray(0), emptyList(), "loading", false)
                fetch(key, st)
            }
            val ly = lyr
            when (ly.status) {
                "loading" -> msg = "Sözler aranıyor…"
                "none" -> { msg = "${st.title} – ${st.artist}"; sub = "Bu şarkı için söz bulunamadı" }
                "inst" -> msg = "♪ Enstrümantal ♪"
                else -> {
                    val target = position() + LEAD
                    var lo = 0
                    var hi = ly.times.size
                    while (lo < hi) { val mid = (lo + hi) / 2; if (ly.times[mid] <= target) lo = mid + 1 else hi = mid }
                    idx = lo - 1
                }
            }
        }

        val ly = lyr
        val cy = height / 2f
        val sc = dm.scaledDensity
        showApprox = ly.approx && ly.status == "ok"
        showDrag = !locked

        if (msg != null) {
            curIdx = null
            val sig = "m|$msg|$sub|$s|$locked|$showApprox"
            if (sig == lastSig) return
            lastSig = sig
            val list = ArrayList<Item>()
            list.add(Item(msg, s, WHITE, cy))
            if (sub != null) list.add(Item(sub, small, GRAY, cy + s * sc * 1.8f))
            items = list
            invalidate()
            return
        }

        // söz modu: satır bir ilerlediyse kayma animasyonu başlat
        val now = SystemClock.elapsedRealtime()
        if (idx != curIdx) {
            val ci = curIdx
            animT0 = if (ci != null && curKey == key && idx == ci + 1) now else 0L
            curIdx = idx
            curKey = key
        }
        var p = 1f
        var animating = false
        if (animT0 != 0L) {
            val x = (now - animT0) / ANIM
            if (x >= 1f) animT0 = 0L else { p = 1 - (1 - x) * (1 - x) * (1 - x); animating = true }
        }
        val sig = "l|$idx|$s|$locked|${ly.approx}|${ly.id}"
        if (!animating && sig == lastSig) return
        lastSig = if (animating) null else sig

        val spc = s * sc * 2.0f
        val list = ArrayList<Item>()
        for (k in -2..2) {
            val t = lineText(ly, idx, k)
            if (t.isEmpty()) continue
            val off = k + (1 - p)
            val d = abs(off)
            if (d >= 2f) continue
            val f = min(d, 1f)
            var size = s - (s - small) * f
            var color = mix(WHITE, GRAY, f)
            if (d > 1f) {
                size = max(8f, small * (1 - 0.35f * (d - 1)))
                color = mix(GRAY, DIM, d - 1)
            }
            list.add(Item(t, size, color, cy + off * spc))
        }
        items = list
        invalidate()
    }

    private fun drawLine(c: Canvas, item: Item) {
        val w = (width - dp(24)).coerceAtLeast(100)
        tp.textSize = item.sizeSp * dm.scaledDensity
        val layout = StaticLayout.Builder.obtain(item.text, 0, item.text.length, tp, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        c.save()
        c.translate((width - w) / 2f, item.cy - layout.height / 2f)
        tp.style = Paint.Style.STROKE
        tp.strokeWidth = dm.density * 4f
        tp.strokeJoin = Paint.Join.ROUND
        tp.color = Color.BLACK
        layout.draw(c)
        tp.style = Paint.Style.FILL
        tp.color = item.color
        layout.draw(c)
        c.restore()
    }

    override fun onDraw(canvas: Canvas) {
        for (it in items) drawLine(canvas, it)
        if (showApprox) {
            hp.textAlign = Paint.Align.RIGHT
            canvas.drawText("≈ tahmini senkron", width - dp(8).toFloat(), height - dp(8).toFloat(), hp)
        }
        if (showDrag) {
            hp.textAlign = Paint.Align.LEFT
            canvas.drawText("Sürükleyerek taşı", dp(8).toFloat(), height - dp(8).toFloat(), hp)
        }
    }
}
