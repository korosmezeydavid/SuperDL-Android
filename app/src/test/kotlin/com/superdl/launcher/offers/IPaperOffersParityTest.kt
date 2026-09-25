package com.superdl.launcher.offers

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * EGYEZÉS A PYTHON-REFERENCIÁVAL (Spar: spar_akcio.py, Auchan: auchan_offers.py).
 *
 * A minták a git-ből kizárt `akcio_minta/spar` és `akcio_minta/auchan` mappában
 * vannak (bolti oldalak — nem terjesztjük). Az `elvart.json`-t a Python-referencia
 * írta UGYANEZEKEN a fájlokon; csak a biztos (számolással igazolt) termékek.
 * Ha a minta hiányzik, a próba kimarad.
 */
class IPaperOffersParityTest {

    private val root = File("src/test/resources/akcio_minta")
    private val today = LocalDate.of(2026, 9, 25)

    private fun expected(store: String): JSONObject? {
        val f = File(root, "$store/elvart.json")
        return if (f.exists()) JSONObject(f.readText(Charsets.UTF_8)) else null
    }

    private fun loader(store: String): ((String) -> String)? {
        val idx = File(root, "$store/index.json")
        if (!idx.exists()) return null
        val map = JSONObject(idx.readText(Charsets.UTF_8))
        return { url ->
            val name = map.optString(url, "")
            if (name.isEmpty()) throw java.io.IOException("nincs lementve: $url")
            File(root, "$store/$name").readText(Charsets.UTF_8)
        }
    }

    private fun d(o: JSONObject, k: String): Double? = if (!o.has(k) || o.isNull(k)) null else o.getDouble(k)
    private fun s(o: JSONObject, k: String): String? = if (!o.has(k) || o.isNull(k)) null else o.getString(k)
    private fun i(o: JSONObject, k: String): Int? = if (!o.has(k) || o.isNull(k)) null else o.getInt(k)
    private fun d(a: JSONArray, k: Int): Double? = if (a.isNull(k)) null else a.getDouble(k)
    private fun s(a: JSONArray, k: Int): String? = if (a.isNull(k)) null else a.getString(k)
    private fun r(v: Double?): Int? = v?.let { Math.round(it).toInt() }

    private fun eq(msg: String, a: Double?, b: Double?) {
        if (a == null || b == null) assertEquals(msg, a, b) else assertEquals(msg, a, b, 1e-6)
    }

    // ------------------------------------------------------------------ SPAR
    @Test
    fun sparOldalakUgyanazok() {
        val exp = expected("spar")
        assumeTrue("nincs Spar-minta", exp != null)
        val files = exp!!.getJSONObject("files")
        var total = 0
        for (fname in files.keys()) {
            val e = files.getJSONObject(fname)
            val arr = JSONArray(File(root, "spar/$fname").readText(Charsets.UTF_8))
            val pages = (0 until arr.length()).map { if (arr.isNull(it)) "" else arr.getString(it) }
            val got = SparOffers.parseFlyer(pages)
            val items = e.getJSONArray("items")
            println("SPAR $fname: Kotlin ${got.size}, Python ${e.getInt("count")}")
            total += got.size
            for (k in 0 until minOf(items.length(), got.size)) {
                val x = items.getJSONObject(k)
                val (page, p) = got[k]
                val m = "$fname #$k"
                assertEquals("$m oldal", x.getInt("page"), page)
                assertEquals("$m név", x.getString("name"), p.name)
                eq("$m ár", d(x, "price"), p.price)
                eq("$m kártyás ár", d(x, "card"), p.cardPrice)
                assertEquals("$m kiszerelés", s(x, "pack"), p.pack)
                eq("$m kuponos ár", d(x, "coupon"), p.couponPrice)
                eq("$m régi ár", d(x, "old"), p.oldPrice)
                assertEquals("$m egységár", s(x, "unit"), p.unitPrice)
                assertEquals("$m /", s(x, "per"), p.per)
                assertEquals("$m betétdíj", i(x, "deposit"), p.deposit)
                assertEquals("$m %", i(x, "pct"), p.discountPct)
                assertEquals("$m több db", s(x, "multi"), p.multiBuy)
                assertEquals("$m fajta", s(x, "kind"), p.kind)
            }
            assertEquals("$fname darabszám", e.getInt("count"), got.size)
        }
        println("SPAR összesen (szórólap-fájlok): $total")
    }

    @Test
    fun sparLetoltesUgyanaz() {
        val exp = expected("spar")?.optJSONObject("download")
        val get = loader("spar")
        assumeTrue("nincs Spar letöltés-minta", exp != null && get != null)
        val items = SparOffers.download(get!!, today = today)
        val rows = exp!!.getJSONArray("items")
        println("SPAR letöltés: Kotlin ${items.size}, Python ${exp.getInt("count")}")
        items.take(8).forEach { println("  ${it.speakLine()} | ${it.category} | ${it.validity} | ${it.note}") }
        for (k in 0 until minOf(rows.length(), items.size)) {
            val row = rows.getJSONArray(k)
            val it = items[k]
            assertEquals("#$k kategória", SparOffers.categoryOf(row.getString(0)), it.category)
            assertEquals("#$k név", row.getString(2), it.name)
            assertEquals("#$k ár", r(d(row, 3)), it.price)
            assertEquals("#$k kártyás ár", r(d(row, 4)), it.cardPrice)
            assertEquals("#$k kiszerelés", s(row, 5).orEmpty(), it.packSize)
        }
        assertEquals(exp.getInt("count"), items.size)
    }

    /** A spar.hu/ajanlatok nem jön le (Cloudflare 403) → a csütörtöki szórólap-cím kitalálása. */
    @Test
    fun sparTartalekCimek() {
        val get = loader("spar")
        assumeTrue("nincs Spar letöltés-minta", get != null)
        val asked = mutableListOf<String>()
        val onlySpar: (String) -> String = { url ->
            asked += url
            if (url != "https://szorolap.spar.hu/spar/260924-1-spar-szorolap/") throw java.io.IOException("HTTP 403")
            get!!(url)
        }
        val items = SparOffers.download(onlySpar, today = today)
        println("SPAR tartalék: ${items.size} termék, kérések: $asked")
        assertEquals(3, asked.count { it == "https://www.spar.hu/ajanlatok" })
        assertEquals(5, asked.size)             // 3 felfedezés + 2 kitalált cím, a néző-oldal nem kétszer
        assertEquals(126, items.size)
        assertEquals("SPAR szórólap", items[0].category)
        assertEquals("09.24-tól 09.30-ig", items[0].validity)
    }

    // ------------------------------------------------------------------ AUCHAN
    @Test
    fun auchanOldalakUgyanazok() {
        val exp = expected("auchan")
        assumeTrue("nincs Auchan-minta", exp != null)
        val files = exp!!.getJSONObject("files")
        for (fname in files.keys()) {
            val e = files.getJSONObject(fname)
            val pages = IPaper.pageTexts(File(root, "auchan/$fname").readText(Charsets.UTF_8))!!
            val all = pages.flatMapIndexed { i, t -> AuchanOffers.parsePageAll(t, i + 1) }
            val got = pages.flatMapIndexed { i, t -> AuchanOffers.parsePage(t, i + 1) }
            println("AUCHAN $fname: jelölt ${all.size} (Python ${e.getInt("all")}), megtartott ${got.size} (Python ${e.getInt("count")})")
            val items = e.getJSONArray("items")
            for (k in 0 until minOf(items.length(), got.size)) {
                val x = items.getJSONObject(k)
                val p = got[k]
                val m = "$fname #$k"
                assertEquals("$m oldal", x.getInt("page"), p.page)
                assertEquals("$m név", x.getString("name"), p.name)
                eq("$m ár", d(x, "price"), p.price)
                eq("$m kártyás ár", d(x, "card"), p.cardPrice)
                assertEquals("$m kiszerelés", s(x, "pack"), p.pack)
                assertEquals("$m egységár", s(x, "unit"), p.unitPrice)
                assertEquals("$m több db", s(x, "multi"), p.multi)
                assertEquals("$m feltétel", s(x, "cond"), p.condition)
            }
            assertEquals("$fname jelöltek", e.getInt("all"), all.size)
            assertEquals("$fname darabszám", e.getInt("count"), got.size)
        }
    }

    @Test
    fun auchanLetoltesUgyanaz() {
        val exp = expected("auchan")?.optJSONObject("download")
        val get = loader("auchan")
        assumeTrue("nincs Auchan letöltés-minta", exp != null && get != null)
        val items = AuchanOffers.download(get!!, today = today)
        val rows = exp!!.getJSONArray("items")
        println("AUCHAN letöltés: Kotlin ${items.size}, Python ${exp.getInt("count")}")
        items.take(8).forEach { println("  ${it.speakLine()} | ${it.category} | ${it.validity} | ${it.unitPrice} | ${it.note}") }
        for (k in 0 until minOf(rows.length(), items.size)) {
            val row = rows.getJSONArray(k)
            val it = items[k]
            val name = row.getString(2).let { n -> if (OfferText.isUpper(n)) OfferText.capitalize(n) else n }
            assertEquals("#$k kategória", row.getString(0), it.category)
            assertEquals("#$k név", name, it.name)
            assertEquals("#$k ár", r(d(row, 3)), it.price)
            assertEquals("#$k kártyás ár", r(d(row, 4)), it.cardPrice)
            assertEquals("#$k kiszerelés", s(row, 5).orEmpty(), it.packSize)
        }
        assertEquals(exp.getInt("count"), items.size)
    }

    @Test
    fun pythonG() {
        assertEquals("2999", IPaper.pyG(2999.0))
        assertEquals("1299.9", IPaper.pyG(1299.9))
        assertEquals("1.23457e+06", IPaper.pyG(1234567.0))
        assertEquals("0.5", IPaper.pyG(0.5))
        assertEquals("SPAR szórólap", SparOffers.categoryOf("SPAR szórólap 09.24 - 09.30."))
        assertEquals("Állateledel katalógus", SparOffers.categoryOf("Állateledel katalógus 09.01-től"))
    }
}
