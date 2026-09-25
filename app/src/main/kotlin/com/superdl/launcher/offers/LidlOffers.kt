package com.superdl.launcher.offers

import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A PDF-SZÖVEG KINYERÉSÉNEK BEÁLLÍTÁSAI — a Lidl- és a Tesco-újsághoz.
 *
 * A gyűjtők NEM függenek PDF-könyvtártól: a szöveget az alkalmazás adja
 * (`pdfPages`), a pdfbox-android `PDFTextStripper`-ével. A gépi próba az
 * asztali PDFBox 2.0.27-tel ugyanezt csinálja — a kettő ugyanaz a kód, ezért
 * UGYANAZZAL a beállítással ugyanazt a szöveget adják. Ha bármelyiket
 * megváltoztatod, a gépi próba (LidlTescoParityTest) szól.
 *
 * MIÉRT KELL HELYADAT IS: a nyomdai PDF-ben a név és az ár KÜLÖN
 * szövegdobozban van, és a PDF belső sorrendje nem mindig a látható
 * sorrend (Lidl: a „459 Ft" a Téliszalámi mellett áll, de a szövegben a
 * Smoothie után jön). A pdfminer (Windows) ezt térbeli csoportosítással
 * oldja meg; itt minden szó elé egy apró jelölőt teszünk a helyével, és
 * a gyűjtő maga párosít — a ránézésre legközelebbi árhoz, és ahol lehet,
 * az EGYSÉGÁR × KISZERELÉS számtani ellenőrzésével.
 *
 * AZ ALKALMAZÁS OLDALÁN (pdfbox-android, egyszeri `PDFBoxResourceLoader.init(context)` után):
 * ```
 * class OfferStripper : PDFTextStripper() {
 *     init {
 *         sortByPosition = PdfTextSettings.SORT_BY_POSITION        // false
 *         lineSeparator = PdfTextSettings.LINE_SEPARATOR           // "\n"
 *         wordSeparator = PdfTextSettings.WORD_SEPARATOR           // " "
 *         paragraphStart = PdfTextSettings.PARAGRAPH_START         // "\n"
 *         paragraphEnd = PdfTextSettings.PARAGRAPH_END             // ""
 *         pageStart = PdfTextSettings.PAGE_START                   // ""
 *         pageEnd = PdfTextSettings.PAGE_END                       // ""
 *     }
 *     override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
 *         if (textPositions.isNotEmpty()) {
 *             val a = textPositions.first(); val b = textPositions.last()
 *             output.write(PdfTextSettings.wordMark(a.xDirAdj, a.yDirAdj,
 *                 b.xDirAdj + b.widthDirAdj - a.xDirAdj, a.heightDir))
 *         }
 *         super.writeString(text, textPositions)
 *     }
 * }
 * fun pdfPages(bytes: ByteArray): List<String> = PDDocument.load(bytes).use { doc ->
 *     val s = OfferStripper()
 *     (1..doc.numberOfPages).map { p -> s.startPage = p; s.endPage = p; s.getText(doc) }
 * }
 * ```
 * Minden más a PDFBox alapértéke marad (drop/indent threshold, spacing
 * tolerance, shouldSeparateByBeads, addMoreFormatting=false).
 */
object PdfTextSettings {
    const val SORT_BY_POSITION = false
    const val LINE_SEPARATOR = "\n"
    const val WORD_SEPARATOR = " "
    const val PARAGRAPH_START = "\n"
    const val PARAGRAPH_END = ""
    const val PAGE_START = ""
    const val PAGE_END = ""

    /** A jelölő határa: a nyomtatott szövegben nem fordul elő. */
    const val MARK = '\u001F'

    /** Minden szó elé: „␟x,y,szélesség,magasság␟" (pont, egy tizedesre). */
    fun wordMark(x: Float, y: Float, width: Float, height: Float): String =
        "$MARK${r(x)},${r(y)},${r(width)},${r(height)}$MARK"

    private fun r(v: Float): String = (Math.round(v * 10f) / 10f).toString()

    /** A jelölők nélküli, olvasható szöveg (hibakereséshez). */
    fun plain(pageText: String): String = PdfLayout.RUN.replace(pageText, "")
}

/**
 * A jelölt oldalszövegből nyomtatott sorok, szövegdobozok és távolságok.
 * Tiszta Kotlin — a gépen és a telefonon ugyanúgy fut.
 */
object PdfLayout {

    internal val RUN = Regex("\u001F(-?[\\d.E]+),(-?[\\d.E]+),(-?[\\d.E]+),(-?[\\d.E]+)\u001F")
    private val ODD_SPACE = Regex("[    ]")

    class Run(x: Double, val y: Double, w: Double, h: Double, val text: String) {
        val x0 = min(x, x + w)
        val x1 = max(x, x + w)
        val h = max(h, 0.5)
    }

    /** Egy nyomtatott sor egy darabja: egymáshoz közeli szavak egy alapvonalon. */
    class Seg(runs: List<Run>) {
        var text: String = runs.joinToString("") { it.text }
        val x0 = runs.minOf { it.x0 }
        val x1 = runs.maxOf { it.x1 }
        val y = runs[0].y
        val h = runs.maxOf { it.h }
        val top get() = y - h
        val bottom get() = y
        /** Lidl: a cikkszámhoz tapadt ár („6420205349 Ft" → 349). */
        var glued: Int? = null
        val box get() = Box(x0, x1, top, bottom)
        override fun toString() = "Seg($text @${x0.toInt()},${y.toInt()} h${h.toInt()})"
    }

    /** Egymás alatti sorok egy szövegdobozban. */
    class Block(first: Seg) {
        val segs = mutableListOf(first)
        val lines: List<String> get() = segs.map { it.text }
        val box: Box get() = Box(segs.minOf { it.x0 }, segs.maxOf { it.x1 }, segs.minOf { it.top }, segs.maxOf { it.bottom })
    }

    data class Box(val x0: Double, val x1: Double, val top: Double, val bottom: Double)

    /** Két doboz távolsága (0, ha érintik vagy fedik egymást). */
    fun gap(a: Box, b: Box): Double {
        val dx = maxOf(0.0, a.x0 - b.x1, b.x0 - a.x1)
        val dy = maxOf(0.0, a.top - b.bottom, b.top - a.bottom)
        return hypot(dx, dy)
    }

    /**
     * Hol vágjunk ketté egy „szót", amelynek betűi visszafelé ugranak: két
     * szövegdoboz ragadt össze („1 039 FtHÚSFARM", „Szuper ár!SOLEVITA").
     * -1: nem vágunk.
     */
    internal fun backwardCut(text: String, token: (String) -> Boolean): Int {
        val cands = (1 until text.length).filter { i ->
            val c = text[i]; val p = text[i - 1]
            (p == ' ' && c != ' ') ||
                (c.isUpperCase() && (p.isLowerCase() || p in "0123456789!%*.)")) ||
                (c.isDigit() && p.isLetter())
        }
        for (i in cands) {
            if (token(text.substring(0, i).trim()) || token(text.substring(i).trim())) return i
        }
        for (i in cands) {
            if (text[i - 1] != ' ' && i + 1 < text.length && text[i + 1].isUpperCase()) return i
        }
        return -1
    }

    /**
     * Az oldal sordarabjai a PDF sorrendjében. `token`: ár vagy címke, ami
     * mindig külön darab lesz (akkor is, ha a PDF egy sorba írta a névvel).
     */
    fun segments(pageText: String, token: (String) -> Boolean = { false }): List<Seg> {
        val out = mutableListOf<Seg>()
        for (line in pageText.split('\n')) {
            val ms = RUN.findAll(line).toList()
            if (ms.isEmpty()) continue
            val runs = mutableListOf<Run>()
            for ((k, m) in ms.withIndex()) {
                val end = if (k + 1 < ms.size) ms[k + 1].range.first else line.length
                val text = ODD_SPACE.replace(line.substring(m.range.last + 1, end), " ")
                val x = m.groupValues[1].toDoubleOrNull() ?: continue
                val y = m.groupValues[2].toDoubleOrNull() ?: continue
                val w = m.groupValues[3].toDoubleOrNull() ?: continue
                val h = m.groupValues[4].toDoubleOrNull() ?: continue
                val cut = if (w < 0) backwardCut(text, token) else -1
                if (cut > 0) {
                    val head = text.substring(0, cut)
                    val rest = text.substring(cut)
                    runs += Run(x, y, 0.5 * h * head.length, h, head)
                    runs += Run(x + w, y, -0.5 * h * rest.length, h, rest)
                } else {
                    runs += Run(x, y, w, h, text)
                }
            }
            if (runs.isEmpty()) continue
            var cur = mutableListOf(runs[0])
            for (r in runs.drop(1)) {
                val p = cur.last()
                val lo = min(p.h, r.h)
                val g = r.x0 - p.x1
                if (g < -2 || g > 1.2 * lo + 2 || abs(r.y - p.y) > 0.6 * lo + 1 ||
                    token(r.text.trim()) || token(p.text.trim())
                ) {
                    out += Seg(cur); cur = mutableListOf(r)
                } else {
                    cur += r
                }
            }
            out += Seg(cur)
        }
        // a lapon kívül (a tördelőasztalon) felejtett szöveg nincs kinyomtatva
        return out.filter { it.text.isNotBlank() && it.x1 > 0 && it.y > 0 }
    }

    /**
     * Szövegdobozok: egy sordarab egy friss doboz folytatása, ha közvetlenül
     * az utolsó sora alatt áll (hasonló betűméret, vízszintes átfedés).
     */
    fun blocks(
        segs: List<Seg>,
        closes: (String) -> Boolean = { false },
        standalone: (Seg) -> Boolean = { false },
        lookback: Int = 8
    ): List<Block> {
        val out = mutableListOf<Block>()
        for (s in segs) {
            var target: Block? = null
            if (!standalone(s)) {
                for (b in out.takeLast(lookback).asReversed()) {
                    val last = b.segs.last()
                    if (standalone(last) || closes(last.text)) continue
                    val hi = max(last.h, s.h)
                    val lo = min(last.h, s.h)
                    val dy = s.y - last.y
                    if (hi > 2 * lo || dy <= 0.3 * hi || dy > 2.6 * hi) continue
                    if (s.x0 >= last.x1 || s.x1 <= last.x0) continue
                    target = b
                    break
                }
            }
            if (target == null) out += Block(s) else target.segs += s
        }
        return out
    }

    /** Egy termék a párosításhoz: hol van, és mennyi az ára a saját számai szerint. */
    class Want(val box: Box, val expect: List<Pair<Double, Double>>?)

    /** Egy ár-csoport a párosításhoz. */
    class Offer(val box: Box, val key: Int)

    /**
     * Termék ↔ ár párosítás. Először a SZÁMTAN: ahol a termék saját
     * egységárából kijön az ár, ott az (a legközelebbi ilyen). Utána a HELY,
     * de csak annak, akinek nincs számtani ellenőrzése, és csak kölcsönösen
     * egyértelmű esetben (egymás legközelebbijei, és nincs majdnem ugyanolyan
     * közeli másik). Ami bizonytalan, kimarad.
     */
    fun match(
        wants: List<Want>,
        offers: List<Offer>,
        maxDist: Double,
        ok: (Box, Box) -> Boolean = { _, _ -> true }
    ): Map<Int, Int> {
        val res = HashMap<Int, Int>()
        val used = HashSet<Int>()
        data class C(val d: Double, val pi: Int, val gi: Int)
        val cands = mutableListOf<C>()
        for ((pi, p) in wants.withIndex()) {
            val ex = p.expect ?: continue
            for ((gi, g) in offers.withIndex()) {
                if (ex.any { (v, t) -> abs(g.key - v) <= t }) cands += C(gap(p.box, g.box), pi, gi)
            }
        }
        for (c in cands.sortedWith(compareBy<C>({ it.d }, { it.pi }, { it.gi }))) {
            if (c.pi in res || c.gi in used || c.d > maxDist * 3) continue
            res[c.pi] = c.gi; used += c.gi
        }
        val freeP = wants.indices.filter { it !in res }
        val freeG = offers.indices.filter { it !in used }
        fun near(list: List<Int>, d: (Int) -> Double?): List<Pair<Double, Int>> =
            list.mapNotNull { i -> d(i)?.let { it to i } }.sortedWith(compareBy({ it.first }, { it.second }))
        fun dist(pi: Int, gi: Int): Double? =
            if (ok(wants[pi].box, offers[gi].box)) gap(wants[pi].box, offers[gi].box) else null
        for (pi in freeP) {
            if (wants[pi].expect != null) continue
            val ds = near(freeG) { gi -> dist(pi, gi) }
            if (ds.isEmpty()) continue
            val (d, gi) = ds[0]
            if (d > maxDist) continue
            if (ds.size > 1 && ds[1].first < d * 1.25 + 5) continue
            val back = near(freeP) { p2 -> dist(p2, gi) }
            if (back.isEmpty() || back[0].second != pi) continue
            if (back.size > 1 && back[1].first < d * 1.25 + 5) continue
            res[pi] = gi
        }
        return res
    }
}

/**
 * Lidl — a Lidl hivatalos szórólap-szolgáltatása (Schwarz „leaflets").
 *
 * A Windows-oldali `lidl.py` átirata, de a PDF szövegét a pdfbox adja (nem a
 * pdfminer), ezért a termék és az ára HELY szerint párosul (lásd
 * [PdfTextSettings]):
 *
 *   overview → a heti újságok listája, mindegyikhez `pdfUrl` és dátumok
 *   PDF      → valódi szövegréteg (nem kép)
 *
 * Egy termék szövegdoboza (az utolsó sora a cikkszám):
 *
 *     HÚSFARM
 *     Friss, szeletelt, light karaj
 *     Hártyázott
 *     400 g; 1 kg = 2 948 Ft
 *     6400870
 *
 * Mellette/alatta az árdoboz, háromféle alakban (2026-09-25-i mérés):
 *     „Szuper ár!" + „1999 Ft"                               → egy ár
 *     „-13% 1 359 Ft" + „1179 Ft"                            → kedvezmény, régi, új
 *     „Lidl Plus-szal" + „-30%**" + „1399 Ft" + „1 999 Ft"   → appos és rendes ár
 *
 * ⚠️ Egy rossz ár rosszabb, mint egy hiányzó. Ahol a kiszerelés és az
 * egységár megvan (400 g; 1 kg = 2 948 Ft → 1179 Ft), ott az árnak EZT kell
 * kiadnia — ha egyik közeli ár sem ennyi, a termék kimarad. Ahol nincs mivel
 * ellenőrizni (nem élelmiszer, kilós áru), ott csak az egyértelműen
 * legközelebbi árdoboz számít.
 */
object LidlOffers {

    const val STORE = "Lidl"
    const val OVERVIEW = "https://endpoints.leaflets.schwarz/v4/overview?client_locale=lidl/hu-HU&region_id=0"
    const val CARD = "Lidl Plus-szal"

    data class Flyer(
        val title: String, val category: String, val pdfUrl: String,
        val start: String, val end: String, val fileSize: Long, val regional: Boolean = false
    )

    private val I = setOf(RegexOption.IGNORE_CASE)
    private val PRICE = Regex("^(\\d{1,3}(?: \\d{3})?|\\d{1,6})\\s?Ft(?:/db)?\\**$")
    private val OLD = Regex("^[-–]\\s?(\\d{1,2})\\s?%\\**\\s*(\\d[\\d ]*)\\s?Ft\\**$")
    private val PCT = Regex("^[-–]\\s?(\\d{1,2})\\s?%\\**$")
    private val PLUS = Regex("^lidl plus-szal$", I)
    private val SUPER = Regex("^szuper ár!?$", I)
    private val MULTI = Regex(
        "^(minden|második|termék:|minden második termék:|\\d\\+\\d ingyen|\\d-[aeo]t fizetsz|\\d+-[aeo]t vihetsz!?)$", I
    )
    private val CODE_LINE = Regex("^\\d{4,7}(?:\\s*/\\s*\\d{4,7})*(?:\\s*/)?$")
    private val CODE_GLUED = Regex("^((?:\\d{4,7}\\s*/\\s*)+)(\\d{8,11})\\s?Ft\\**$")
    private val KISZ = Regex(
        "(\\d+(?:[,.]\\d+)?\\s*(?:g|kg|dkg|ml|l|cl|db|m|cm|mm)\\b|/kg|/db|/csomag|" +
            "^\\s*\\d+\\s*x\\s*\\d+|változó kiszerel)", I
    )
    private val ERV = Regex("(\\d{2})\\.\\s?(\\d{2})\\.\\s?\\p{L}*(?:tól|től)\\s+(\\d{2})\\.\\s?(\\d{2})-ig", I)
    private val ERV_FROM = Regex("^(\\d{2})\\.\\s?(\\d{2})\\.\\s?\\p{L}*(?:tól|től)$", I)
    private val SZEMET = Regex(
        "(jó választás|^a hazai$|^az év|kereskedője|még több ajánlat|az árak a " +
            "dekorációt|akciós termékeink|\\.indd|^\\d{4}\\. \\d{2}\\. \\d{2}\\.|^\\d+$|" +
            "^\\d+/\\d{4}$|^\\*|a termékek nem képezik|lidl plus applikáció|" +
            "nyomdai hibákért|^friss pékáru)", I
    )
    private val NAME_TAIL = Regex("\\s(Kizárólag|\\+\\s?\\d|Alkoholtartalom)")
    private val UNIT_EQ = Regex("^1\\s*(kg|l|db|tekercs|m)\\s*=", I)
    private val QTY = Regex("(\\d+(?:[,.]\\d+)?)\\s*(?:x\\s*(\\d+(?:[,.]\\d+)?)\\s*)?(kg|dkg|g|ml|cl|l|db|tekercs|m)\\b", I)
    private val EQ = Regex("1\\s*(kg|l|db|tekercs|m)\\s*=\\s*(\\d[\\d ]*(?:,\\d+)?)\\s*Ft", I)
    private val FACT = mapOf(
        "g" to ("kg" to 0.001), "dkg" to ("kg" to 0.01), "kg" to ("kg" to 1.0),
        "ml" to ("l" to 0.001), "cl" to ("l" to 0.01), "l" to ("l" to 1.0),
        "db" to ("db" to 1.0), "tekercs" to ("tekercs" to 1.0), "m" to ("m" to 1.0)
    )

    // ---- az újságok listája -------------------------------------------------

    /** Az overview összes újsága (ismétlés nélkül), a lista sorrendjében. */
    fun flyers(overviewJson: String): List<Flyer> {
        val out = mutableListOf<Flyer>()
        val seen = HashSet<String>()
        val cats = JSONObject(overviewJson).optJSONArray("categories") ?: return out
        for (i in 0 until cats.length()) {
            val subs = cats.optJSONObject(i)?.optJSONArray("subcategories") ?: continue
            for (j in 0 until subs.length()) {
                val sub = subs.optJSONObject(j) ?: continue
                val regional = sub.optString("name").contains("regionális", ignoreCase = true)
                val fl = sub.optJSONArray("flyers") ?: continue
                for (k in 0 until fl.length()) {
                    val f = fl.optJSONObject(k) ?: continue
                    val url = f.optString("pdfUrl")
                    if (url.isBlank() || !seen.add(url)) continue
                    val title = f.optString("title").ifBlank { f.optString("name") }.ifBlank { "Lidl újság" }
                    // a regionális újság címe csak „Érvényes 09.24-től" — a neve mondja meg, hol
                    val category = if (regional) f.optString("name").ifBlank { title } else title
                    out += Flyer(
                        title, category, url,
                        f.optString("offerStartDate").ifBlank { f.optString("startDate") },
                        f.optString("offerEndDate").ifBlank { f.optString("endDate") },
                        f.optLong("fileSize", 0L), regional
                    )
                }
            }
        }
        return out
    }

    /**
     * Csak a még érvényes újságok (a vége ma vagy később), legfeljebb 4 — mint a Windows-modul.
     * `includeRegional = false`: a „Regionális akciók – Veszprém/Debrecen" újságok nélkül
     * (azok csak ott érvényesek, és együtt ~55 MB).
     */
    fun current(all: List<Flyer>, today: LocalDate = LocalDate.now(), includeRegional: Boolean = true): List<Flyer> {
        val t = today.toString()
        return all.filter { it.end.isBlank() || it.end.take(10) >= t }
            .filter { includeRegional || !it.regional }
            .take(4)
    }

    /** „2026-09-24" + „2026-09-30" → „09.24-tól 09.30-ig". */
    fun validityOf(start: String, end: String): String {
        fun md(s: String) = if (s.length >= 10) s.substring(5, 7) + "." + s.substring(8, 10) else ""
        val a = md(start); val b = md(end)
        return when {
            a.isNotEmpty() && b.isNotEmpty() -> "$a-tól $b-ig"
            a.isNotEmpty() -> "$a-tól"
            else -> ""
        }
    }

    // ---- szövegdarabok ------------------------------------------------------

    private fun isToken(t: String): Boolean =
        PRICE.matches(t) || OLD.matches(t) || PCT.matches(t) || PLUS.matches(t) ||
            SUPER.matches(t) || MULTI.matches(t)

    private fun standalone(s: PdfLayout.Seg): Boolean = isToken(s.text.trim())

    private fun closes(text: String): Boolean {
        val t = text.trim()
        return CODE_LINE.matches(t) && !t.endsWith("/")
    }

    private fun priceOf(s: String): Int? = OfferText.priceNumber(s)

    private fun num(s: String?): Double? = s?.replace(" ", "")?.replace(",", ".")?.toDoubleOrNull()

    /** A tördelt sorok összefűzése: a sorvégi szóköz = folytatás (lidl.py `_nev_sorok`). */
    private fun nameLines(lines: List<String>): List<String> {
        val ki = mutableListOf<String>()
        for (s in lines) {
            val tail = if (s.endsWith(" ")) " " else ""
            if (ki.isNotEmpty() && ki.last().endsWith(" ")) {
                ki[ki.size - 1] = ki.last().trimEnd() + " " + s.trim() + tail
            } else if (ki.isNotEmpty() && Regex("[-/,]$").containsMatchIn(ki.last().trimEnd()) &&
                !ki.last().trimEnd().endsWith(" -")
            ) {
                val elo = ki.last().trimEnd()
                ki[ki.size - 1] = elo + (if (elo.endsWith("-")) "" else " ") + s.trim() + tail
            } else {
                ki += s.trim() + tail
            }
        }
        return ki.map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
    }

    /** Csupa nagybetűs márkanév → „Húsfarm" (a képernyőolvasó a csupa nagy rövid szavakat betűzi). */
    private fun nice(s: String): String =
        s.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { w ->
            if (w.length >= 2 && OfferText.isUpper(w)) OfferText.capitalize(w) else w
        }

    /** „150 g" + „1 kg = 2 527 Ft" → (379,05; tűrés). null, ha nem számolható. */
    internal fun expected(pack: String, unit: String): Pair<Double, Double>? {
        val e = EQ.find(unit) ?: return null
        val q = QTY.find(pack) ?: return null
        val base = e.groupValues[1].lowercase()
        val raw = e.groupValues[2].replace(" ", "")
        val per = num(raw) ?: return null
        val step = if (raw.contains(",")) 0.1 else 1.0
        var qty = num(q.groupValues[1]) ?: return null
        q.groupValues[2].takeIf { it.isNotEmpty() }?.let { qty *= num(it) ?: return null }
        val (b, f) = FACT[q.groupValues[3].lowercase()] ?: return null
        if (b != base) return null
        qty *= f
        if (qty <= 0) return null
        // az egységár kerekítve van kiírva: ennyi eltérés fér bele, több nem
        return qty * per to qty * step / 2 + 1.01
    }

    private class Prod(
        val name: String, val code: String, val pack: String, val unit: String,
        val notes: List<String>, val expect: Pair<Double, Double>?, val box: PdfLayout.Box
    )

    private fun product(lines0: List<String>, box: PdfLayout.Box): Prod? {
        val sorok = lines0.filter { it.isNotBlank() }
        if (sorok.size < 2) return null
        val test = sorok.dropLast(1).toMutableList()
        val codes = mutableListOf(sorok.last().trim())
        // „6809540 / 6813129 /" + „68146 / 36645": a cikkszám-lista több sorban
        while (test.isNotEmpty() && CODE_LINE.matches(test.last().trim()) && test.last().trim().endsWith("/")) {
            codes.add(0, test.removeAt(test.size - 1).trim().trimEnd('/').trim())
        }
        var meret = ""
        var egysegar = ""
        var i = test.size - 1
        while (i >= 0) {
            val sor = test[i].trim()
            if (UNIT_EQ.containsMatchIn(sor)) {
                egysegar = test.removeAt(i).trim().trimEnd(';')
                i--
                continue
            }
            if (KISZ.containsMatchIn(sor)) {
                meret = test.removeAt(i).trim()
                break
            }
            i--
        }
        val nevek = nameLines(test).filter { !SZEMET.containsMatchIn(it) }
        if (nevek.isEmpty()) return null
        var name: String
        var tobbi: List<String>
        if (nevek.size >= 2 && OfferText.isUpper(nevek[0])) {
            name = nice(nevek[0]) + " " + nevek[1]; tobbi = nevek.drop(2)
        } else {
            name = nice(nevek[0]); tobbi = nevek.drop(1)
        }
        // „Coca-Cola Zero Kizárólag 4-es összecsomagolásban… +4 x 50 Ft visszaváltási díj":
        // a tördelt leírás a név végére csúszott — az a megjegyzésbe való
        NAME_TAIL.find(name)?.takeIf { it.range.first >= 3 }?.let {
            tobbi = listOf(name.substring(it.range.first).trim()) + tobbi
            name = name.substring(0, it.range.first).trim()
        }
        val mr = meret.split(";")
        var pack = mr[0].trim()
        var unit = (if (mr.size > 1) mr[1].trim() else "").ifEmpty { egysegar }
        var exp = expected(pack, unit)
        if (exp == null) {
            // „4 tekercs; 1 tekercs = 125 Ft" a leírás közepén
            for (s in sorok.dropLast(1)) {
                if (!s.contains(";") || !EQ.containsMatchIn(s)) continue
                val a = s.substringBefore(";"); val b = s.substringAfter(";")
                exp = expected(a, b) ?: continue
                if (pack.isEmpty()) {
                    pack = a.trim(); unit = b.trim()
                    tobbi = tobbi.filter { it.trim() != s.trim() }
                }
                break
            }
        }
        return Prod(name, codes.joinToString(" / "), pack, unit, tobbi, exp, box)
    }

    private class Group(val anchor: PdfLayout.Seg, val key: Int) {
        val items = mutableListOf<PdfLayout.Seg>()
        val box get() = anchor.box
    }

    // ---- egy oldal ----------------------------------------------------------

    /**
     * Egy PDF-oldal termékei. `pageText`: a [PdfTextSettings] szerint
     * kinyert, jelölt szöveg. `fallbackValidity`: ha az oldalon nincs
     * érvényességi sáv (az újság dátumaiból).
     */
    fun parsePage(pageText: String, category: String, fallbackValidity: String = ""): List<OfferItem> {
        val segs = PdfLayout.segments(pageText, ::isToken)
        // „6420204 / 6420205349 Ft": az ár a cikkszámhoz tapadt
        for (s in segs) {
            val m = CODE_GLUED.matchEntire(s.text.trim()) ?: continue
            val len = Regex("\\d+").find(m.groupValues[1])!!.value.length
            val digits = m.groupValues[2]
            s.text = m.groupValues[1] + digits.substring(0, len)
            s.glued = digits.substring(len).toIntOrNull()
        }
        val bl = PdfLayout.blocks(segs, ::closes, ::standalone)

        // érvényességi sávok
        val valids = mutableListOf<Pair<PdfLayout.Block, String>>()
        for (b in bl) {
            val t = b.lines.joinToString(" ") { it.trim() }
            val m = ERV.find(t)
            if (m != null && b.lines.size <= 2) {
                val g = m.groupValues
                valids += b to "${g[1]}.${g[2]}-tól ${g[3]}.${g[4]}-ig"
                continue
            }
            ERV_FROM.matchEntire(t.trim())?.let { valids += b to "${it.groupValues[1]}.${it.groupValues[2]}-tól" }
        }

        // árdobozok: a nagy ár a horgony, a kis címkék a legközelebbihez tartoznak
        val toks = bl.filter { it.segs.size == 1 && standalone(it.segs[0]) }.map { it.segs[0] }
        val prices = toks.filter { PRICE.matches(it.text.trim()) }
        val maxh = prices.maxOfOrNull { it.h } ?: 0.0
        val anchors = prices.filter { it.h >= 0.5 * maxh }
        val groups = anchors.mapNotNull { a -> priceOf(a.text)?.let { Group(a, it) } }.toMutableList()
        for (s in segs) s.glued?.let { groups += Group(s, it) }
        for (s in toks) {
            if (s in anchors) continue
            val best = groups.minByOrNull { PdfLayout.gap(s.box, it.box) } ?: continue
            if (PdfLayout.gap(s.box, best.box) <= 45) best.items += s
        }

        val prods = bl.filter { it.segs.size >= 2 && CODE_LINE.matches(it.segs.last().text.trim()) }
            .mapNotNull { product(it.lines, it.box) }

        // Lidl-csempe: a nagy ár a termék szövegétől jobbra / alatta áll,
        // soha nem egyértelműen balra, és nem a szöveg közepe fölött
        val res = PdfLayout.match(
            prods.map { PdfLayout.Want(it.box, it.expect?.let { e -> listOf(e) }) },
            groups.map { PdfLayout.Offer(it.box, it.key) },
            80.0
        ) { pb, gb -> gb.x0 >= pb.x0 - 30 && gb.bottom >= (pb.top + pb.bottom) / 2 }

        val out = mutableListOf<OfferItem>()
        for ((pi, p) in prods.withIndex()) {
            val g = groups[res[pi] ?: continue]
            val above = valids.filter { it.first.box.bottom <= p.box.top + 5 }
            val validity = when {
                above.isNotEmpty() -> above.minByOrNull { p.box.top - it.first.box.bottom }!!.second
                valids.map { it.second }.toSet().size == 1 -> valids[0].second
                else -> fallbackValidity
            }
            build(p, g, category, validity)?.let { out += it }
        }
        return out
    }

    private fun build(p: Prod, g: Group, category: String, validity: String): OfferItem? {
        val a = g.key
        val texts = g.items.map { it.text.trim() }
        val plus = texts.any { PLUS.matches(it) }
        val small = texts.filter { PRICE.matches(it) }.mapNotNull { priceOf(it) }
        var old: Int? = null
        var pct: Int? = null
        for (t in texts) OLD.matchEntire(t)?.let { pct = it.groupValues[1].toInt(); old = priceOf(it.groupValues[2]) }
        if (pct == null) texts.firstNotNullOfOrNull { PCT.matchEntire(it) }?.let { pct = it.groupValues[1].toInt() }
        val multi = texts.filter { MULTI.matches(it) }
        val higher = small.filter { it > a }.sorted()
        fun fits(lo: Int, hi: Int): Boolean =
            hi <= lo * 5 && (pct == null || abs((100.0 * (1 - lo.toDouble() / hi)).roundToInt() - pct!!) <= 3)

        val price: Int
        var card: Int? = null
        var oldPrice: Int? = null
        var discount = ""
        val notes = p.notes.toMutableList()
        // két különböző kedvezmény egy árdobozban („-17%" és appal „-23%"):
        // kettős ár, nem tudjuk biztosan, melyik melyik → inkább kimarad
        val pcts = texts.mapNotNull { (OLD.matchEntire(it) ?: PCT.matchEntire(it))?.groupValues?.get(1) }.toSet()
        if (pcts.size > 1) return null
        when {
            plus -> {
                card = a
                // az appos dobozban a rendes árnak és a kedvezménynek ki kell adnia egymást
                price = higher.firstOrNull { fits(a, it) } ?: return null
                pct?.let { discount = "-$it%" }
            }
            multi.isNotEmpty() -> {
                // „Minden második termék -71%": egy darab 699, a második 199 → az ár 699
                val one = higher.firstOrNull() ?: a
                price = one
                val deal = multi.filter { it.lowercase() !in setOf("minden", "második", "termék:") }.joinToString(" ")
                notes += "Többes vásárlási akció" + (if (deal.isNotEmpty()) " ($deal)" else "") +
                    (if (one != a) ": $a Ft/db" else "")
            }
            else -> {
                price = a
                val o = old ?: higher.firstOrNull()
                if (o != null && o > a && fits(a, o)) oldPrice = o
                if (pct != null && (o == null || oldPrice != null)) discount = "-$pct%"
            }
        }
        val note = notes.joinToString(", ").replace(Regex("(,\\s*)?(\\d{5,7}\\s*/?\\s*)+$"), "").trim(' ', ',')
        return OfferItem(
            store = STORE, name = p.name, price = price, cardPrice = card,
            cardName = if (card != null) CARD else "", oldPrice = oldPrice, discount = discount,
            packSize = speakablePack(p.pack), unitPrice = p.unit.trimEnd(';').trim(),
            validity = validity, category = category, code = p.code, note = note
        )
    }

    /** „/kg" → „kilónként", „/db" → „darabonként", „/2 db" → „2 db" (a „/" felolvasva zavaró). */
    private fun speakablePack(pack: String): String {
        val t = pack.trim()
        if (!t.startsWith("/")) return t
        return when (val r = t.removePrefix("/").trim()) {
            "kg" -> "kilónként"
            "db" -> "darabonként"
            else -> r
        }
    }

    /** Egy újság (az oldalak szövege) termékei, cikkszám szerint egyszer. */
    fun products(pages: List<String>, category: String, fallbackValidity: String = ""): List<OfferItem> {
        val out = mutableListOf<OfferItem>()
        val seen = HashSet<String>()
        for (page in pages) for (t in parsePage(page, category, fallbackValidity)) {
            val codes = Regex("\\d{4,7}").findAll(t.code).map { it.value }.toList()
            if (codes.any { it in seen }) continue
            seen += codes
            out += t
        }
        return out
    }

    /**
     * A heti Lidl-újságok termékei. HÁTTÉRSZÁLON hívandó.
     * `get`: szöveg letöltése, `getBytes`: a PDF letöltése, `pdfPages`: a PDF
     * oldalainak szövege a [PdfTextSettings] szerint.
     */
    fun download(
        get: (String) -> String,
        getBytes: (String) -> ByteArray,
        pdfPages: (ByteArray) -> List<String>,
        progress: (String) -> Unit = {},
        today: LocalDate = LocalDate.now(),
        includeRegional: Boolean = true
    ): List<OfferItem> {
        val list = current(flyers(get(OVERVIEW)), today, includeRegional)
        if (list.isEmpty()) throw IllegalStateException("A Lidl újságlistájában nincs érvényes újság.")
        val out = mutableListOf<OfferItem>()
        val seen = HashSet<String>()
        for (f in list) {
            progress("Lidl: ${f.category} letöltése…")
            val pdf = getBytes(f.pdfUrl)
            progress("Lidl: ${f.category} olvasása…")
            for (t in products(pdfPages(pdf), f.category, validityOf(f.start, f.end))) {
                val codes = Regex("\\d{4,7}").findAll(t.code).map { it.value }.toList()
                if (codes.any { it in seen }) continue
                seen += codes
                out += t
            }
        }
        return out
    }
}
