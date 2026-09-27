package com.superdl.launcher.offers

import java.nio.charset.Charset

/**
 * Libri — a Könyvutca akciós könyvei (libri.hu, a bolt saját oldala).
 *
 * A Windows-oldali `libri.py` átirata. A lista lapozható
 * (`/konyvutca?page=N`, oldalanként kb. 20 könyv). Minden könyv egy
 * `product-grid-item` doboz, a bolt a saját adatait adatmezőkben adja:
 *
 *     data-url="https://www.libri.hu/konyv/…html" data-name="Cím"
 *     data-category="eletmod-egeszseg/…" data-price="2793"
 *
 * a borító ár pedig a dobozban: „Borító ár: 3 990 Ft".
 *
 * ⚠️ Az oldal ISO-8859-2 kódolású (nem UTF-8) — ha UTF-8-nak olvasnánk,
 * minden ékezet elveszne. Ezért a letöltő BÁJTOKAT kér, és a kódolást az
 * oldal saját fejlécéből olvassuk ki ([decode]).
 */
object LibriOffers {

    const val STORE = "Libri"
    private const val BASE = "https://www.libri.hu"
    const val PAGE = "$BASE/konyvutca"

    /** ~1000 könyv; a bolt jelenleg 51 oldalt ad. */
    private const val MAX_PAGES = 60

    /** Kíméletesen: két oldal között pihenő (ezredmásodperc). */
    const val PAUSE_MS = 600L

    private val BOX = Regex("<div class=\"product-grid-item[^\"]*\"([^>]*)>")
    private val DATA = Regex("data-([a-z-]+)=\"([^\"]*)\"")
    private val AUTHOR = Regex("class=\"authors\"[^>]*>([^<]+)</a>")
    private val COVER = Regex("Borító ár:</span>${PyText.SPACE_CLASS}*<span>([^<]+)</span>")
    private val CATEGORY = Regex("<a href=\"/konyv/([a-z0-9-]+)/\" title=\"([^\"]+)\">")
    private val PAGES = Regex("[?&]page=([0-9]+)")
    private val CHARSET = Regex("charset=[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)

    /**
     * Bájtokból szöveg: a kódolást az első 4000 bájtban álló `charset=`
     * mondja meg; ha nincs, vagy a telefon nem ismeri, UTF-8. A hibás bájt
     * helyére U+FFFD kerül (a Python „replace" hibakezelése).
     */
    fun decode(bytes: ByteArray): String {
        // ISO-8859-1: bájt = karakter, így a minta pontosan a bájtokon fut
        val head = String(bytes, 0, minOf(bytes.size, 4000), Charsets.ISO_8859_1)
        val name = CHARSET.find(head)?.groupValues?.get(1) ?: "utf-8"
        val cs = try {
            Charset.forName(name)
        } catch (e: Exception) {
            Charsets.UTF_8
        }
        return String(bytes, cs)
    }

    /** slug → olvasható név, a bolt saját menüjéből. */
    fun categories(page: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (m in CATEGORY.findAll(page)) out[m.groupValues[1]] = PyText.unescape(m.groupValues[2])
        return out
    }

    fun pageCount(page: String): Int =
        PAGES.findAll(page).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 1

    fun parse(page: String, cats: Map<String, String> = emptyMap()): List<OfferItem> {
        val out = mutableListOf<OfferItem>()
        val seen = mutableSetOf<String>()
        val boxes = BOX.findAll(page).toList()
        for ((n, m) in boxes.withIndex()) {
            val data = HashMap<String, String>()
            for (d in DATA.findAll(m.groupValues[1])) data[d.groupValues[1]] = PyText.unescape(d.groupValues[2])
            val id = data["doc-id"].takeUnless { it.isNullOrEmpty() } ?: data["url"]
            if (id.isNullOrEmpty() || id in seen || data["name"].isNullOrEmpty()) continue
            seen += id
            val end = m.range.last + 1
            val stop = if (n + 1 < boxes.size) boxes[n + 1].range.first else minOf(end + 6000, page.length)
            val part = page.substring(end, maxOf(end, stop))
            val author = AUTHOR.find(part)
            val cover = COVER.find(part)
            val price = PyText.arSzam(data["price"].orEmpty())
            val old = cover?.let { PyText.arSzam(it.groupValues[1]) }
            val slug = data["category"].orEmpty().split("/")[0]
            var name = PyText.strip(data.getValue("name"))
            if (author != null) name = "${PyText.strip(PyText.unescape(author.groupValues[1]))}: $name"
            // MIÉRT a Python round(): a Windows ugyanazt a százalékot mondja (x,5 → páros)
            val discount = if (price != null && price != 0 && old != null && old > price) {
                "-${PyText.round(100.0 * (old - price) / old)}%"
            } else ""
            out += OfferItem(
                store = STORE, name = name, price = price, oldPrice = old, discount = discount,
                category = cats[slug] ?: "Könyv", code = id, url = data["url"].orEmpty(),
                note = if ("Csak online" in part) "Csak online rendelhető" else "",
                group = "Könyv"
            )
        }
        return out
    }

    /**
     * Az összes oldal, legfeljebb [MAX_PAGES]. Ha egy későbbi oldal nem jön le
     * vagy nem hoz újat, megállunk — ami addig jött, az megmarad.
     */
    fun download(
        getBytes: (String) -> ByteArray,
        progress: (String) -> Unit = {},
        pause: Long = PAUSE_MS
    ): List<OfferItem> {
        progress("Libri: a Könyvutca első oldala…")
        val first = decode(getBytes(PAGE))
        val cats = categories(first)
        val out = parse(first, cats).toMutableList()
        val seen = out.mapTo(HashSet()) { it.code }
        val total = minOf(pageCount(first), MAX_PAGES)
        for (page in 2..total) {
            if (pause > 0) Thread.sleep(pause)
            if (page % 10 == 0) progress("Libri: $page. oldal, összesen $total…")
            val fresh = try {
                parse(decode(getBytes("$PAGE?page=$page")), cats)
            } catch (e: Exception) {
                break                   // ami eddig jött, az megmarad
            }.filter { it.code !in seen }
            if (fresh.isEmpty()) break
            fresh.mapTo(seen) { it.code }
            out += fresh
        }
        return out
    }
}
