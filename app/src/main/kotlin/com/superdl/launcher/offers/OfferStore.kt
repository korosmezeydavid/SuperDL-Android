package com.superdl.launcher.offers

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A boltok listája, a letöltés és a helyi gyorsítótár.
 *
 * A gyorsítótár azért kell, hogy a lista AZONNAL megnyíljon, és net nélkül is
 * böngészhető legyen. A felhasználó saját telefonján marad, sehová nem kerül.
 *
 * ⚠️ ÜRES EREDMÉNY NEM ÍRJA FELÜL A JÓT. A tévéműsor 2026-09-21-i tanulsága:
 * egy hiányos forrás egyszer már felülírta a jó adatot. Ha a bolt oldala épp
 * nem válaszol, a korábbi (még érvényes) ajánlat marad.
 */
object OfferStore {

    data class Store(val id: String, val name: String)

    val STORES = listOf(Store("penny", "Penny"), Store("aldi", "Aldi"))

    /** Ennél frissebb adatot nem töltünk újra. */
    private const val FRESH_MS = 6 * 60 * 60 * 1000L

    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36 SuperDL"

    fun storeName(id: String): String = STORES.firstOrNull { it.id == id }?.name ?: id

    private fun file(context: Context, id: String): File =
        File(File(context.filesDir, "akciok").apply { mkdirs() }, "$id.json")

    /** (termékek, a letöltés ideje ezredmásodpercben vagy 0). */
    fun saved(context: Context, id: String): Pair<List<OfferItem>, Long> = try {
        val o = JSONObject(file(context, id).readText())
        val arr = o.optJSONArray("items") ?: JSONArray()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(OfferItem::fromJson) } to
            o.optLong("time", 0L)
    } catch (_: Exception) {
        emptyList<OfferItem>() to 0L
    }

    fun isFresh(time: Long): Boolean =
        time > 0 && System.currentTimeMillis() - time < FRESH_MS

    private fun save(context: Context, id: String, items: List<OfferItem>) {
        val o = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        val f = file(context, id)
        val tmp = File(f.parentFile, "$id.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 40_000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "hu-HU,hu;q=0.9")
            val code = c.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }

    /**
     * Letölti és elmenti. HÁTTÉRSZÁLON hívandó. Üres eredményt nem ment.
     * @throws Exception ha a bolt oldala nem érhető el
     */
    fun download(context: Context, id: String, progress: (String) -> Unit = {}): List<OfferItem> {
        val items = when (id) {
            "penny" -> PennyOffers.download(::httpGet, progress)
            "aldi" -> AldiOffers.download(::httpGet, progress)
            else -> throw IllegalArgumentException(id)
        }
        if (items.isNotEmpty()) save(context, id, items)
        return items
    }

    /** Kategóriák a megjelenés sorrendjében, termékszámmal. */
    fun categories(items: List<OfferItem>): List<Pair<String, Int>> =
        items.groupBy { it.category.ifBlank { "Egyéb" } }
            .map { (name, list) -> name to list.size }

    /** Legolcsóbb elöl; akinek nincs ára, a végére. */
    fun cheapestFirst(items: List<OfferItem>): List<OfferItem> =
        items.sortedWith(compareBy(nullsLast<Int>()) { it.bestPrice() })
}
