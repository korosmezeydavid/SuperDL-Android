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
 * 2026-09-27 (a Windows 0.6–0.7 boltjai): Müller — a prospektus PDF-je ·
 *  Pepco — a heti újság oldala · Libri — a Könyvutca lapjai · Illatorium —
 *  a bolt saját oldalának adatai. Mind sima kéréssel elérhető (mérve).
 *  ⚠️ Az Aldi termék-csempéit a Windows böngésző-ujjlenyomattal kéri; sima
 *  kérésre 403 — ezt NEM kerüljük meg, a telefonon marad az Aldi-újság.
 */
object OfferStore {

    /**
     * @param heavy nagy letöltés (Lidl ~58 MB, Tesco ~10 MB, Müller ~8 MB PDF, a
     *        Libri ~16 MB-nyi oldal): mobilneten csak rákérdezés után töltjük
     *        le, egy napig nem kérdezzük újra, és a „minden boltban" keresés
     *        csak a korábban letöltöttet nézi meg belőle.
     * @param approxMb a becsült méret, amíg egyszer le nem töltöttük (utána a mért).
     * @param menuLabel ahogy a menüben szól (a dm-nél nem heti akció, hanem kiárusítás).
     * @param kind a bolt fajtája — a Windows `forrasok.FAJTAK` párja: elelmiszer,
     *        drogeria, vegyes, konyv. A „minden élelmiszerlánc egyszerre" ebből áll össze.
     * @param homepage a bolt akciós oldala — ha a terméknek nincs saját oldala, ez nyílik meg.
     * @param webshop a bolt honlapján online is lehet rendelni (a felület kimondja).
     * @param catalog nem akció, hanem a TELJES kínálat (Illatorium) — egy napig friss.
     */
    data class Store(
        val id: String,
        val name: String,
        val heavy: Boolean = false,
        val menuLabel: String = name,
        val approxMb: Int = 0,
        val kind: String = KIND_FOOD,
        val homepage: String = "",
        val webshop: Boolean = false,
        val catalog: Boolean = false
    )

    const val KIND_FOOD = "elelmiszer"
    const val KIND_DRUG = "drogeria"
    const val KIND_MIXED = "vegyes"
    const val KIND_BOOK = "konyv"

    /** A boltfajták, ahogy a „minden … egyszerre" menüpont kimondja. */
    val KIND_LABELS = linkedMapOf(
        KIND_FOOD to "Minden élelmiszerlánc",
        KIND_DRUG to "Minden drogéria és kozmetika",
        KIND_MIXED to "Minden vegyes áru",
        KIND_BOOK to "Minden könyvesbolt"
    )

    // A honlapok és a webshop-jelzés a Windows `forrasok.BOLT_INFO`-ból.
    val STORES = listOf(
        Store("penny", "Penny", homepage = "https://www.penny.hu/ajanlatok"),
        Store("aldi", "Aldi", homepage = "https://www.aldi.hu"),
        Store("lidl", "Lidl", heavy = true, approxMb = 58, homepage = "https://www.lidl.hu"),
        Store("spar", "Spar", homepage = "https://www.spar.hu/ajanlatok"),
        Store("tesco", "Tesco", heavy = true, approxMb = 10,
            homepage = "https://tesco.hu/akciok/akcios-termekek/", webshop = true),
        Store("auchan", "Auchan", homepage = "https://auchan.hu", webshop = true),
        Store("rossmann", "Rossmann", kind = KIND_DRUG, homepage = "https://shop.rossmann.hu", webshop = true),
        Store("dm", "dm", menuLabel = "dm kiárusítás", kind = KIND_DRUG,
            homepage = "https://www.dm.hu", webshop = true),
        Store("mueller", "Müller", heavy = true, approxMb = 8, kind = KIND_DRUG,
            homepage = MuellerOffers.PAGE),
        // Kőrösmezey Dávid, a SuperDL készítőjének saját parfümboltja: a TELJES
        // kínálat, nem akció (a Windows 0.7.0 döntése, 2026-09-27).
        Store("illatorium", "Illatorium", menuLabel = "Illatorium, saját parfümbolt", kind = KIND_DRUG,
            homepage = IllatoriumOffers.PAGE, webshop = true, catalog = true),
        Store("pepco", "Pepco", kind = KIND_MIXED, homepage = PepcoOffers.PAGE),
        // Ötven oldal, oldalanként egy kis szünettel: két-három perc is lehet.
        Store("libri", "Libri", heavy = true, approxMb = 16, menuLabel = "Libri akciós könyvek",
            kind = KIND_BOOK, homepage = LibriOffers.PAGE, webshop = true)
    )

    fun storesOfKind(kind: String): List<Store> = STORES.filter { it.kind == kind }

    /** A termék boltjának azonosítója a bolt NEVÉBŐL (a termék a nevet hordozza). */
    fun storeIdByName(name: String): String? =
        STORES.firstOrNull { it.name.equals(name, ignoreCase = true) || it.id == name.lowercase() }?.id

    /** Amit megnyitunk: a termék saját oldala, ha van, különben a bolt oldala. */
    fun linkFor(item: OfferItem): String =
        item.link().ifBlank { storeIdByName(item.store)?.let { store(it)?.homepage }.orEmpty() }

    /**
     * Mobilneten nagy PDF-et nem töltünk le kérdés nélkül — a felhasználó
     * adatkeretét óvjuk. Ő dönt: a kérdés megmondja a méretet (Alph kérése,
     * 2026-09-26: „a korlátlan internet korában legyen választható").
     */
    class NeedsWifi(val storeName: String, val sizeMb: Int) : Exception("$storeName: mobilnet, $sizeMb MB")

    /** Nagy újság, és mobilneten vagyunk — rá kell kérdezni a letöltés előtt. */
    fun needsMeteredConfirm(context: Context, id: String): Boolean =
        store(id)?.heavy == true && isMetered(context)

    /** A legutóbb mért méret megabájtban, vagy a becslés, ha még nem töltöttük le. */
    fun sizeMb(context: Context, id: String): Int {
        val measured = try {
            context.getSharedPreferences("akciok_meret", Context.MODE_PRIVATE).getLong(id, 0L)
        } catch (_: Exception) { 0L }
        return if (measured > 0) ((measured + 999_999) / 1_000_000).toInt()
        else store(id)?.approxMb ?: 0
    }

    private fun rememberSize(context: Context, id: String, bytes: Long) {
        if (bytes <= 0) return
        try {
            context.getSharedPreferences("akciok_meret", Context.MODE_PRIVATE).edit().putLong(id, bytes).apply()
        } catch (_: Exception) {}
    }

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
        // A régebbi mentésben még nincs termékcsoport: egyszer itt pótoljuk,
        // hogy a böngészés ne számolja újra minden lépésnél.
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let(OfferItem::fromJson)
                ?.let { if (it.group.isBlank()) it.copy(group = OfferGroups.of(it)) else it }
        } to o.optLong("time", 0L)
    } catch (_: Exception) {
        emptyList<OfferItem>() to 0L
    }

    fun isFresh(time: Long, id: String = ""): Boolean {
        val st = store(id)
        val limit = if (st?.heavy == true || st?.catalog == true) FRESH_HEAVY_MS else FRESH_MS
        return time > 0 && System.currentTimeMillis() - time < limit
    }

    /** Boltonként egy zár: két szál (egy háttérben befejeződő letöltés és egy keresés) ne írja egyszerre. */
    private val saveLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    private fun save(context: Context, id: String, items: List<OfferItem>) {
        val o = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        synchronized(saveLocks.getOrPut(id) { Any() }) {
            val f = file(context, id)
            val tmp = File.createTempFile(id, ".tmp", f.parentFile)
            try {
                tmp.writeText(o.toString())
                if (!tmp.renameTo(f)) {
                    f.delete()
                    tmp.renameTo(f)
                }
            } finally {
                if (tmp.exists()) tmp.delete()
            }
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

    private fun httpGet(url: String): String = httpBytes(url).toString(Charsets.UTF_8)

    private fun httpBytes(url: String): ByteArray {
        val c = open(url)
        try {
            check(c)
            return c.inputStream.use { it.readBytes() }
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
    private fun pdfPagesFromUrl(
        context: Context,
        url: String,
        counter: java.util.concurrent.atomic.AtomicLong? = null
    ): List<String> {
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
            counter?.addAndGet(tmp.length())
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
     * @param allowMetered a felhasználó rábólintott a mobilnetes letöltésre
     * @throws NeedsWifi ha nagy PDF-újság, mobilneten vagyunk, és még nem kérdeztük meg
     * @throws Exception ha a bolt oldala nem érhető el
     */
    fun download(
        context: Context,
        id: String,
        progress: (String) -> Unit = {},
        allowMetered: Boolean = false
    ): List<OfferItem> {
        val st = store(id) ?: throw IllegalArgumentException(id)
        if (st.heavy && !allowMetered && isMetered(context)) throw NeedsWifi(st.name, sizeMb(context, id))
        val pdfBytes = java.util.concurrent.atomic.AtomicLong(0L)
        val pdf: (ByteArray) -> List<String> = { pdfPagesFromUrl(context, String(it, Charsets.UTF_8), pdfBytes) }
        // A nem PDF-es nagy letöltésnél (Libri) a lapokat számoljuk össze.
        val countedBytes: (String) -> ByteArray = { url -> httpBytes(url).also { pdfBytes.addAndGet(it.size.toLong()) } }
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
            "mueller" -> MuellerOffers.download(::httpGet, ::urlAsBytes, pdf, progress)
            "pepco" -> PepcoOffers.download(::httpGet, progress)
            "libri" -> LibriOffers.download(countedBytes, progress)
            "illatorium" -> IllatoriumOffers.download(::httpGet, progress)
            else -> throw IllegalArgumentException(id)
        }.filter { !isGarbled(it.name) }
            // A közös termékcsoportot egyszer, letöltéskor számoljuk ki, és a
            // termékkel együtt mentjük: böngészéskor már nem kell.
            .map { if (it.group.isBlank()) it.copy(group = OfferGroups.of(it)) else it }
        if (st.heavy) rememberSize(context, id, pdfBytes.get())
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

    /** A közös termékcsoportok (Tejtermék és tojás, Ital…) termékszámmal, a rögzített sorrendben. */
    fun groups(items: List<OfferItem>): List<Pair<String, Int>> = OfferGroups.counts(items)

    /** Kategóriák a megjelenés sorrendjében, termékszámmal. */
    fun categories(items: List<OfferItem>): List<Pair<String, Int>> =
        items.groupBy { it.category.ifBlank { "Egyéb" } }
            .map { (name, list) -> name to list.size }

    /** Legolcsóbb elöl; akinek nincs ára, a végére. */
    fun cheapestFirst(items: List<OfferItem>): List<OfferItem> =
        items.sortedWith(compareBy(nullsLast<Int>()) { it.bestPrice() })
}
