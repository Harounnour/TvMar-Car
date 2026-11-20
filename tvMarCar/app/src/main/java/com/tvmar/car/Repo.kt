package com.tvmar.car

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.util.Locale

/**
 * Sources de données (chargées en ligne, mises en cache sur le téléphone) :
 *  - tous les récitateurs et riwayat : API mp3quran.net v3
 *  - إذاعات القرآن : API mp3quran.net v3 (radios)
 *  - الإذاعات المغربية : annuaire communautaire Radio-Browser (pays = MA)
 */
object Repo {

    data class Moshaf(val id: Int, val name: String, val server: String, val surahs: List<Int>)
    data class Reciter(val id: Int, val name: String, val letter: String, val moshafs: List<Moshaf>)
    data class Radio(val id: String, val name: String, val url: String, val icon: String?)

    private lateinit var appCtx: Context
    private const val DAY = 24L * 3600 * 1000

    private var reciters: List<Reciter>? = null
    private var moshafIndex: Map<Int, Pair<Reciter, Moshaf>> = emptyMap()
    private var quranRadios: List<Radio>? = null
    private var moroccanRadios: List<Radio>? = null

    fun init(ctx: Context) {
        if (!::appCtx.isInitialized) appCtx = ctx.applicationContext
    }

    // ---------- Récitateurs ----------

    @Synchronized
    fun getReciters(): List<Reciter> {
        reciters?.let { return it }
        val txt = cached("reciters_ar.json", 7 * DAY, listOf("https://www.mp3quran.net/api/v3/reciters?language=ar"))
        val parsed = try { txt?.let { parseReciters(it) }.orEmpty() } catch (e: Exception) { emptyList() }
        val list = if (parsed.isNotEmpty()) parsed else Catalog.fallback
        reciters = list
        moshafIndex = list.flatMap { r -> r.moshafs.map { m -> m.id to (r to m) } }.toMap()
        return list
    }

    fun lettersSorted(): List<String> {
        val collator = Collator.getInstance(Locale("ar"))
        return getReciters().map { it.letter }.distinct().sortedWith { a, b -> collator.compare(a, b) }
    }

    fun recitersOfLetter(letter: String): List<Reciter> {
        val collator = Collator.getInstance(Locale("ar"))
        return getReciters().filter { it.letter == letter }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    fun reciter(id: Int): Reciter? = getReciters().firstOrNull { it.id == id }

    fun moshaf(id: Int): Pair<Reciter, Moshaf>? {
        getReciters()
        return moshafIndex[id]
    }

    private fun parseReciters(txt: String): List<Reciter> {
        val arr = JSONObject(txt).getJSONArray("reciters")
        val out = ArrayList<Reciter>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val ms = ArrayList<Moshaf>()
            val ma = o.optJSONArray("moshaf") ?: JSONArray()
            for (j in 0 until ma.length()) {
                val m = ma.getJSONObject(j)
                var server = m.optString("server").trim()
                if (server.isEmpty()) continue
                if (!server.endsWith("/")) server += "/"
                val surahs = m.optString("surah_list").split(",").mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..114 }.sorted()
                if (surahs.isEmpty()) continue
                ms.add(Moshaf(m.optInt("id"), m.optString("name").trim(), server, surahs))
            }
            if (ms.isEmpty()) continue
            val letter = o.optString("letter").trim().ifEmpty { name.take(1) }
            out.add(Reciter(o.optInt("id"), name, letter, ms))
        }
        return out
    }

    // ---------- Radios ----------

    @Synchronized
    fun getQuranRadios(): List<Radio> {
        quranRadios?.let { return it }
        val txt = cached("radios_quran.json", 3 * DAY, listOf("https://www.mp3quran.net/api/v3/radios?language=ar"))
        val list = try {
            val arr = JSONObject(txt ?: "{}").optJSONArray("radios") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val url = o.optString("url").trim()
                val name = o.optString("name").trim()
                if (url.isEmpty() || name.isEmpty()) null else Radio("q" + o.optInt("id"), name, url, null)
            }
        } catch (e: Exception) { emptyList() }
        quranRadios = list
        return list
    }

    @Synchronized
    fun getMoroccanRadios(): List<Radio> {
        moroccanRadios?.let { return it }
        val q = "/json/stations/search?countrycode=MA&hidebroken=true&order=clickcount&reverse=true&limit=500"
        val hosts = listOf("de1", "nl1", "at1").map { "https://$it.api.radio-browser.info$q" }
        val txt = cached("radios_ma.json", 3 * DAY, hosts)
        val list = try {
            val arr = JSONArray(txt ?: "[]")
            val seen = HashSet<String>()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val url = o.optString("url_resolved").ifEmpty { o.optString("url") }.trim()
                val name = o.optString("name").trim()
                if (url.isEmpty() || name.isEmpty() || !seen.add(url)) null
                else Radio("m" + o.optString("stationuuid"), name, url, o.optString("favicon").takeIf { it.startsWith("https://") })
            }
        } catch (e: Exception) { emptyList() }
        moroccanRadios = list
        return list
    }

    fun radio(id: String): Radio? =
        if (id.startsWith("q")) getQuranRadios().firstOrNull { it.id == id }
        else getMoroccanRadios().firstOrNull { it.id == id }

    // ---------- Réseau + cache disque ----------

    private fun cached(name: String, ttl: Long, urls: List<String>): String? {
        val f = File(appCtx.filesDir, name)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < ttl) return f.readText()
        for (u in urls) {
            try {
                val t = http(u)
                if (t.isNotBlank()) { f.writeText(t); return t }
            } catch (_: Exception) { }
        }
        return if (f.exists()) f.readText() else null
    }

    private fun http(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 25_000
        c.setRequestProperty("User-Agent", "tvMarCar/1.0")
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
