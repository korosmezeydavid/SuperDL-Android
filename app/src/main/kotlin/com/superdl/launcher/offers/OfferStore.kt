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
 *
 * FORRÁSOK (2026-09-25-i mérés, mind a bolt SAJÁT oldala vagy hivatalos
 * újság-szolgáltatója, böngésző-ujjlenyomat nélkül is elérhető):
 *  Penny — weboldal · Aldi — Publitas-újság szövege · Spar, Auchan — iPaper-
 *  újság szövege · Lidl, Tesco — az újság PDF-je · Rossmann — a webshop
 *  adatszolgáltatása · dm — a kiárusítás a saját keresőjéből.
 */
object OfferStore {

    /**
     * @param heavy nagy PDF-újság (Lidl ~58 MB, Tesco ~10 MB hetente): csak
     *        wifin töltjük le, és egy napig nem kérdezzük újra.
     * @param menuLabel ahogy a menüben szól (a dm-nél nem heti akció, hanem kiárusítás).
     */
    data class Store(val id: String, val name: String, val heavy: Boolean = false, val menuLabel: String = name)

    val STORES = listOf(
        Store("penny", "Penny"),
        Store("aldi", "Aldi"),
        Store("lidl", "Lidl", heavy = true),
        Store("spar", "Spar"),
        Store("tesco", "Tesco", heavy = true),
        Store("auchan", "Auchan"),
        Store("rossmann", "Rossmann"),
        Store("dm", "dm", menuLabel = "dm kiárusítás")
    )

    /** Mobilneten nem töltünk le nagy PDF-et — a felhasználó adatkeretét óvjuk. */
    class NeedsWifi(val storeName: String) : Exception("$storeName: csak wifin")

    private const val FRESH_MS = 6 * 60 * 60 * 1000L
    private const val FRESH_HEAVY_MS = 24 * 60 * 60 * 1000L

    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36 SuperDL"

    fun store(id: String): Store? = STORES.firstOrNull { it.id == id }

    fun storeName(id: String): String = store(id)?.name ?: id

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

    fun isFresh(time: Long, id: String = ""): Boolean {
        val limit = if (store(id)?.heavy == true) FRESH_HEAVY_MS else FRESH_MS
        return time > 0 && System.currentTimeMillis() - time < limit
    }

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

    // ── HÁLÓZAT ─────────────────────────────────────────────────────────────

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "hu-HU,hu;q=0.9")
        }

    private fun check(c: HttpURLConnection) {
        val code = c.responseCode
        if (code !in 200..299) throw java.io.IOException("HTTP $code")
    }

    private fun httpGet(url: String): String {
        val c = open(url)
        try {
            check(c)
            return c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }

    private fun httpPost(url: String, body: String): String {
        val c = open(url)
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            check(c)
            return c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }

    /** Mért (fizetős) hálózaton vagyunk-e — mobilnet. Ha nem tudjuk, óvatosan igen. */
    private fun isMetered(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.isActiveNetworkMetered
    } catch (_: Exception) {
        true
    }

    // ── PDF (Lidl, Tesco) ───────────────────────────────────────────────────

    /**
     * Ugyanaz a szövegkinyerés, amit a gépi próba az asztali PDFBox-szal
     * csinál — a beállítások a PdfTextSettings-ben (LidlOffers.kt). Ha ezen
     * bármi változik, a gyűjtő mást lát, mint amire a próba igazolta.
     */
    private class OfferStripper : com.tom_roush.pdfbox.text.PDFTextStripper() {
        init {
            sortByPosition = PdfTextSettings.SORT_BY_POSITION
            lineSeparator = PdfTextSettings.LINE_SEPARATOR
            wordSeparator = PdfTextSettings.WORD_SEPARATOR
            paragraphStart = PdfTextSettings.PARAGRAPH_START
            paragraphEnd = PdfTextSettings.PARAGRAPH_END
            pageStart = PdfTextSettings.PAGE_START
            pageEnd = PdfTextSettings.PAGE_END
        }

        override fun writeString(text: String, textPositions: MutableList<com.tom_roush.pdfbox.text.TextPosition>) {
            if (textPositions.isNotEmpty()) {
                val a = textPositions.first()
                val b = textPositions.last()
                output.write(
                    PdfTextSettings.wordMark(a.xDirAdj, a.yDirAdj, b.xDirAdj + b.widthDirAdj - a.xDirAdj, a.heightDir)
                )
            }
            super.writeString(text, textPositions)
        }
    }

    /**
     * A PDF-et NEM a memóriába töltjük (a Lidl-újság 44 MB — egy telefonon ez
     * kifogyáshoz vezetne), hanem fájlba, és a PDFBox is ideiglenes fájlokkal
     * dolgozik. Oldalanként olvasunk. A végén a fájl törlődik.
     */
    private fun pdfPagesFromUrl(context: Context, url: String): List<String> {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        val tmp = File.createTempFile("akcio", ".pdf", context.cacheDir)
        try {
            val c = open(url)
            try {
                check(c)
                c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                c.disconnect()
            }
            return com.tom_roush.pdfbox.pdmodel.PDDocument.load(
                tmp, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()
            ).use { doc ->
                val s = OfferStripper()
                (1..doc.numberOfPages).map { p ->
                    s.startPage = p
                    s.endPage = p
                    s.getText(doc)
                }
            }
        } finally {
            tmp.delete()
        }
    }

    /**
     * A PDF-gyűjtők `pdfPages(getBytes(url))` alakban kérik a szöveget. Hogy a
     * nagy PDF ne kerüljön a memóriába, a „bájtok" itt csak az URL-t viszik
     * át, és a szöveg-kinyerő maga tölti le fájlba. (A gépi próba a valódi
     * bájtokkal hívja ugyanezeket a gyűjtőket.)
     */
    private fun urlAsBytes(url: String): ByteArray = url.toByteArray(Charsets.UTF_8)

    // ── LETÖLTÉS ────────────────────────────────────────────────────────────

    /**
     * Letölti és elmenti. HÁTTÉRSZÁLON hívandó. Üres eredményt nem ment.
     * @throws NeedsWifi ha nagy PDF-újság, és mobilneten vagyunk
     * @throws Exception ha a bolt oldala nem érhető el
     */
    fun download(context: Context, id: String, progress: (String) -> Unit = {}): List<OfferItem> {
        val st = store(id) ?: throw IllegalArgumentException(id)
        if (st.heavy && isMetered(context)) throw NeedsWifi(st.name)
        val pdf: (ByteArray) -> List<String> = { pdfPagesFromUrl(context, String(it, Charsets.UTF_8)) }
        val items = when (id) {
            "penny" -> PennyOffers.download(::httpGet, progress)
            "aldi" -> AldiOffers.download(::httpGet, progress)
            "spar" -> SparOffers.download(::httpGet, progress)
            "auchan" -> AuchanOffers.download(::httpGet, progress)
            "rossmann" -> RossmannOffers.download(::httpPost, progress)
            "dm" -> DmOffers.download(::httpGet, progress)
            // A regionális (Veszprém, Debrecen) Lidl-újságok nélkül: ~55 MB-tal kevesebb.
            "lidl" -> LidlOffers.download(::httpGet, ::urlAsBytes, pdf, progress, includeRegional = false)
            "tesco" -> TescoOffers.download(::httpGet, ::urlAsBytes, pdf, progress)
            else -> throw IllegalArgumentException(id)
        }.filter { !isGarbled(it.name) }
        if (items.isNotEmpty()) save(context, id, items)
        return items
    }

    /**
     * ELRONTOTT BETŰK A PDF SZÖVEGRÉTEGÉBEN. Egyes újságokban (Spar INTERSPAR
     * Home katalógus) a nyomda saját betűkészlete miatt görög vagy cirill jelek
     * kerülnek a magyar szavak közé: „N Γ i alsó", „Fér Ѓ atléta". Ezt
     * felolvasva értelmetlen — az ilyen terméket inkább kihagyjuk. (A
     * gyűjtőkben szándékosan nem szűrünk, hogy a gépi egyezés-próba a Windows-
     * modullal összevethető maradjon.)
     */
    private fun isGarbled(name: String): Boolean = name.any { c ->
        val script = try {
            Character.UnicodeScript.of(c.code)
        } catch (_: Exception) {
            Character.UnicodeScript.LATIN
        }
        script == Character.UnicodeScript.GREEK || script == Character.UnicodeScript.CYRILLIC
    }

    /** Kategóriák a megjelenés sorrendjében, termékszámmal. */
    fun categories(items: List<OfferItem>): List<Pair<String, Int>> =
        items.groupBy { it.category.ifBlank { "Egyéb" } }
            .map { (name, list) -> name to list.size }

    /** Legolcsóbb elöl; akinek nincs ára, a végére. */
    fun cheapestFirst(items: List<OfferItem>): List<OfferItem> =
        items.sortedWith(compareBy(nullsLast<Int>()) { it.bestPrice() })
}
