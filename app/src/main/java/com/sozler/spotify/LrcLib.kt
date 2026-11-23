package com.sozler.spotify

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min

/** LRCLIB'den söz bulma (masaüstü sürümündeki mantığın aynısı). */
object LrcLib {
    val cache = ConcurrentHashMap<String, Pair<String, String?>>()

    private fun JSONObject.str(k: String): String? =
        if (isNull(k)) null else optString(k).ifBlank { null }

    private fun httpJson(url: String): Any? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "SpotifySozler/1.1")
        c.connectTimeout = 10000
        c.readTimeout = 10000
        val txt = c.inputStream.bufferedReader().use { it.readText() }.trim()
        if (txt.startsWith("[")) JSONArray(txt) else JSONObject(txt)
    } catch (e: Exception) {
        null
    }

    private fun q(p: Map<String, String>) =
        p.entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8") }

    private val re1 = Regex(
        """\s*[(\[][^)\]]*(feat|ft\.|with |remaster|live|version|edit|mix|from |soundtrack|bonus|deluxe|mono|stereo)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val re2 = Regex(
        """\s+-\s+(\d{4}\s+)?(remaster|live|radio|single|mono|stereo|bonus|extended|acoustic|from|feat|ft\.|with).*$""",
        RegexOption.IGNORE_CASE
    )
    private val reArtist = Regex("""\s*[,;/]\s*|\s+feat\.?\s+|\s+ft\.?\s+""")

    private fun cleanTitle(t: String): String {
        val r = re2.replace(re1.replace(t, ""), "").trim()
        return r.ifEmpty { t }
    }

    private fun firstArtist(a: String): String =
        a.split(reArtist, limit = 2)[0].trim().ifEmpty { a }

    private fun pick(res: Any?, duration: Double, synced: Boolean, artist: String?): JSONObject? {
        val arr = res as? JSONArray ?: return null
        val key = if (synced) "syncedLyrics" else "plainLyrics"
        var best: JSONObject? = null
        var bd = 1e9
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            if (d.str(key) == null) continue
            if (artist != null) {
                val an = (d.str("artistName") ?: "").lowercase()
                val al = artist.lowercase()
                if (!an.contains(al) && !al.contains(an)) continue
            }
            val dd = if (duration > 0) abs(d.optDouble("duration", 0.0) - duration) else 0.0
            if (duration > 0 && dd > 10) continue
            if (dd < bd) { best = d; bd = dd }
        }
        return best
    }

    fun parseLrc(text: String): Pair<DoubleArray, List<String>> {
        val re = Regex("""^\s*\[(\d+):(\d+(?:\.\d+)?)](.*)""")
        val items = ArrayList<Pair<Double, String>>()
        for (line in text.lines()) {
            val m = re.find(line) ?: continue
            items.add((m.groupValues[1].toInt() * 60 + m.groupValues[2].toDouble()) to m.groupValues[3].trim())
        }
        items.sortBy { it.first }
        return items.map { it.first }.toDoubleArray() to items.map { it.second }
    }

    fun approxLrc(plain: String, duration: Double): Pair<DoubleArray, List<String>> {
        val tag = Regex("""^\[.*]$""")
        val lines = plain.lines().map { it.trim() }.filter { it.isNotEmpty() && !tag.matches(it) }
        if (lines.isEmpty()) return DoubleArray(0) to emptyList()
        val start = if (duration > 0) min(10.0, duration * 0.08) else 8.0
        val end = if (duration > 40) duration - 8 else start + lines.size * 3
        val step = (end - start) / lines.size
        return DoubleArray(lines.size) { start + it * step } to lines
    }

    /** döner: ("ok"|"approx"|"inst"|"none", metin) */
    fun find(title: String, artist: String, album: String, duration: Double): Pair<String, String?> {
        val ct = cleanTitle(title)
        val fa = firstArtist(artist)
        val cands = ArrayList<Pair<String, String>>()
        for (p in listOf(title to artist, ct to artist, ct to fa, title to fa)) {
            if (p.first.isNotEmpty() && p.second.isNotEmpty() && p !in cands) cands.add(p)
        }
        var plain: String? = null
        var inst = false

        // 1) birebir eşleşme
        for ((i, c) in cands.withIndex()) {
            val params = linkedMapOf("track_name" to c.first, "artist_name" to c.second)
            if (duration > 0) params["duration"] = duration.toInt().toString()
            if (i == 0 && album.isNotEmpty()) params["album_name"] = album
            val d = httpJson("https://lrclib.net/api/get?" + q(params)) as? JSONObject
            if (d != null) {
                val s = d.str("syncedLyrics")
                if (s != null) return "ok" to s
                inst = inst || d.optBoolean("instrumental", false)
                if (plain == null) plain = d.str("plainLyrics")
            }
        }

        // 2) arama
        val queries = ArrayList<Map<String, String>>()
        for (c in cands) queries.add(mapOf("track_name" to c.first, "artist_name" to c.second))
        queries.add(mapOf("q" to "$ct $fa"))
        queries.add(mapOf("track_name" to ct))
        for ((qi, qq) in queries.withIndex()) {
            val res = httpJson("https://lrclib.net/api/search?" + q(qq))
            val art = if (qi == queries.size - 1) fa else null
            val d = pick(res, duration, true, art)
            if (d != null) return "ok" to d.str("syncedLyrics")
            if (plain == null) plain = pick(res, duration, false, art)?.str("plainLyrics")
        }

        return when {
            plain != null -> "approx" to plain
            inst -> "inst" to null
            else -> "none" to null
        }
    }
}
