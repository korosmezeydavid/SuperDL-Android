package com.superdl.launcher.offers

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Müller — a drogéria- és a parfüméria-prospektus (mueller.co.hu).
 *
 * A Windows-oldali `mueller.py` átirata. ⚠️ A helyes cím a `mueller.co.hu`
 * (a `muller.hu` és a `mueller.hu` nem a bolté).
 *
 * A prospektusok oldala a bolt saját tárhelyére mutat (Amazon S3, a Müller
 * „dam-bucket"-je), a PDF-eknek van szövegrétege. Egy termék így jön ki:
 * ```
 *       2.495 Ft          ← eredeti ár
 *       1.795 Ft          ← akciós ár
 *     −28 %
 *     FELIX               ← márka (csupa nagybetű, nem mindig van)
 *     Nedves macskaeledel
 *      12 × 85 g          ← kiszerelés
 *     többféle            ← változat (megjegyzésbe)
 * ```
 * KÉT LÉPÉS:
 *  1. [pageText]: a PDFBox jelölt oldalszövegéből (lásd [PdfTextSettings])
 *     olyan szöveg, amilyet a Windows-oldal pdfminere ad: szövegdobozok,
 *     köztük üres sor, és minden árdoboz után KÖZVETLENÜL a hozzá tartozó
 *     névdoboz (a PDF-ben a név vagy az árdoboz alatt, vagy tőle jobbra áll).
 *  2. [parseLines]: a `termekek()` PONTOS átirata — ugyanarra a szövegre
 *     ugyanazt adja, mint a Python (a gépi próba betűre összeveti).
 *
 * A PDF néhány betűtípusa saját kódolású: a keskeny szóköz „â", a kötőjel
 * „Ë", a nagykötőjel „È" alakban jön. Ezeket visszafordítjuk; ahol ennél több
 * a zagyva jel, azt a sort kihagyjuk (inkább rövidebb név, mint olvashatatlan).
 * Az egységárat NEM vesszük át: a tizedesvessző elvész belőle („154 Ft/1 ml"
 * valójában 1,54) — rossz számot nem mondunk be.
 *
 * ⚠️ A JÁTÉK-prospektus (spielware) szándékosan kimarad: ott a termék neve
 * hol az ár ELŐTT, hol UTÁNA áll, így a név és az ár nem párosítható
 * biztosan — rossz árat pedig nem mondunk be.
 */
object MuellerOffers {

    const val STORE = "Müller"
    const val BASE = "https://www.mueller.co.hu"
    const val PAGE = "$BASE/prospektusok/"

    /** Az újság fajtája (az URL-ben) → a felolvasott neve. */
    val LEAFLET_NAMES = mapOf("drogerie" to "Drogéria", "parfuemerie" to "Parfüméria", "spielware" to "Játékok")

    // ------------------------------------------------ Python-szabályú alapok
    //
    // A Python `re` szövegmintákon Unicode-szerint érti a \d, \s, \b jeleket,
    // a Java alapból csak ASCII-t. Hogy a két oldal UGYANAZT illessze (és a
    // telefon ICU-s regexe is ugyanazt), a jelcsoportokat kiírjuk.

    /** Python `\s` (str.isspace). */
    private const val S = "[\\t\\n\\u000B\\f\\r\\u001C-\\u001F \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]"
    /** Python `\d`. */
    private const val D = "\\p{Nd}"
    /** Python `\D`. */
    private const val ND = "[^\\p{Nd}]"
    /** Python `\b` egy betű UTÁN: a következő jel nem „szókarakter". */
    private const val WB = "(?![\\p{L}\\p{N}_])"

    private fun pyIsSpace(c: Char): Boolean = when (c) {
        '\t', '\n', '\u000B', '\u000C', '\r', '\u001C', '\u001D', '\u001E', '\u001F', ' ',
        '\u0085', ' ', ' ', ' ', ' ', ' ', ' ', '　' -> true
        else -> c in ' '..' '
    }

    /** Python `str.strip()`. */
    private fun pyStrip(s: String): String = s.trim { pyIsSpace(it) }

    /** Python `str.splitlines()` (a sorvégek nélkül). */
    internal fun pySplitLines(s: String): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val brk = c == '\n' || c == '\r' || c == '\u000B' || c == '\u000C' || c == '\u001C' ||
                c == '\u001D' || c == '\u001E' || c == '\u0085' || c == ' ' || c == ' '
            if (brk) {
                out += s.substring(start, i)
                if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                start = i + 1
            }
            i++
        }
        if (start < s.length) out += s.substring(start)
        return out
    }

    /** Python `str.title()` (a Müller-márkanevekre: „DOLCE&GABBANA" → „Dolce&Gabbana", „BURT'S" → „Burt'S"). */
    internal fun pyTitle(s: String): String {
        val sb = StringBuilder()
        var prevCased = false
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val ch = String(Character.toChars(cp))
            if (prevCased) {
                sb.append(ch.lowercase())
            } else {
                val t = Character.toTitleCase(cp)
                if (t != cp) sb.appendCodePoint(t)
                else {
                    val up = ch.uppercase()
                    // teljes leképezés (ß → Ss): az első jel nagy, a többi kicsi
                    if (up.codePointCount(0, up.length) > 1) {
                        val k = up.offsetByCodePoints(0, 1)
                        sb.append(up, 0, k).append(up.substring(k).lowercase())
                    } else sb.append(up)
                }
            }
            prevCased = Character.isUpperCase(cp) || Character.isLowerCase(cp) || Character.isTitleCase(cp)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    /** Az első `n` Unicode-jel (Python `s[:n]`). */
    private fun pyHead(s: String, n: Int): String {
        if (s.codePointCount(0, s.length) <= n) return s
        return s.substring(0, s.offsetByCodePoints(0, n))
    }

    private fun pyLen(s: String): Int = s.codePointCount(0, s.length)

    /** Python `s[:1].upper() + s[1:]`. */
    private fun upperFirst(s: String): String {
        if (s.isEmpty()) return s
        val k = s.offsetByCodePoints(0, 1)
        return s.substring(0, k).uppercase() + s.substring(k)
    }

    /** Python `s[:1] + s[1:].lower()`. */
    private fun lowerRest(s: String): String {
        if (s.isEmpty()) return s
        val k = s.offsetByCodePoints(0, 1)
        return s.substring(0, k) + s.substring(k).lowercase()
    }

    // ------------------------------------------------------ minták (mueller.py)

    private val PDF = Regex(
        "https://mueller-dam-bucket[^\"'\\s<>?\\\\\\u000B\\u001C-\\u001F\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+/prospektusok/" +
            "(drogerie|parfuemerie)/[^\"'\\s<>?\\\\\\u000B\\u001C-\\u001F\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+"
    )
    private val AR = Regex("$D{1,3}(?:\\.$D{3})*$S*Ft")
    private val SZAZALEK = Regex("[−-]$S*$D+$S*%")
    private val MERET = Regex(
        "^($D+(?:[,.]$D+)?$S*[×x]$S*)?$D+(?:[,.]$D+)?$S*" +
            "(?:[–-]$S*$D+(?:[,.]$D+)?$S*)?" +
            "(ml|l|g|kg|db|m|cm|mm|pár|tekercs|lap)$WB",
        RegexOption.IGNORE_CASE
    )
    private val EGYSEGAR = Regex("F[tö]$S*/$S*1", RegexOption.IGNORE_CASE)
    private val ZAGYVA = Regex(
        "[\\u00D8\\u00BB\\u00A1\\u00A3\\u00C5\\u00CE\\u00CF\\u00CC\\u00D0\\u00D1\\u00D2\\u00D4\\u00D5\\u00DE\\u00DF" +
            "\\u00E6\\u00F8\\u00FE\\u00A2\\u00A4\\u00A5\\u00A6\\u00A7\\u00A8\\u00A9\\u00AA\\u00AB\\u00AC\\u00AE\\u00AF" +
            "\\u00B0\\u00B1\\u00B2\\u00B3\\u00B5\\u00B6\\u00B7\\u00B8\\u00B9\\u00BA\\u00BC\\u00BD\\u00BE\\u00BF\\u00E8\\u00EC]"
    )
    private val ERV = Regex("($D{4})$ND($D{2})$ND($D{2})$ND{0,3}T[ÓO]L$S*($D{2})$ND($D{2})", RegexOption.IGNORE_CASE)
    private val FEJLEC = Regex("[A-ZÁÉÍÓÖŐÚÜŰ&,\\- ]{4,}")
    private val STOP = listOf("KEDVEZMÉNY", "AJÁNDÉK", "TOVÁBBI", "*")
    private val SPACES = Regex("$S+")
    private val NON_DIGIT = Regex(ND)
    private val AR_SZAM = Regex("($D{1,3}(?:[ .]$D{3})+|$D+)")
    private val AR_SZAM_SEP = Regex("[ .]")

    /** `termek.ar_szam`: „1.795 Ft" → 1795. */
    private fun arSzam(s: String): Int? {
        val t = s.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')
        val m = AR_SZAM.find(t) ?: return null
        return AR_SZAM_SEP.replace(m.groupValues[1], "").toIntOrNull()
    }

    private fun tiszta(s: String): String =
        pyStrip(s.replace("â", " ").replace("Ë", "-").replace("È", "–").replace(" ", " "))

    private fun nagybetus(s: String): Boolean {
        var letters = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (Character.isLetter(cp)) {
                letters++
                if (!Character.isUpperCase(cp)) return false
            }
            i += Character.charCount(cp)
        }
        return letters >= 2
    }

    // ------------------------------------------------------------ nyilvános

    /** [(fajta, url)] — a jelenlegi drogéria- és parfüméria-újság (a játék nem, lásd fent). */
    fun leaflets(html: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val seen = HashSet<String>()
        for (m in PDF.findAll(html)) {
            val url = m.value.trimEnd('\\')
            if ("InlineBanner" in url || !seen.add(url)) continue
            out += m.groupValues[1] to url
        }
        return out
    }

    /** A prospektus érvényessége: „2026.09.28-TÓL 10.04-IG" → „09.28–10.04.". */
    fun validity(text: String): String {
        val m = ERV.find(text.replace("£", ".").replace("Å", "-")) ?: return ""
        val g = m.groupValues
        return "${g[2]}.${g[3]}–${g[4]}.${g[5]}."
    }

    /**
     * A `termekek()` PONTOS átirata: pdfminer-alakú szövegből (sorok,
     * szövegdobozok között üres sor) a termékek.
     */
    fun parseLines(text: String, leaflet: String = "", validity: String = ""): List<OfferItem> {
        val sorok = pySplitLines(text).map { tiszta(it) }
        val out = mutableListOf<OfferItem>()
        var kat = leaflet
        var i = 0
        val n = sorok.size
        while (i < n) {
            val s = sorok[i]
            // fejezetcím (pl. „TESTÁPOLÁS"): csupa nagybetűs sor, UTÁNA üres sor
            // (a márkanév után rögtön a terméknév jön, nem üres sor)
            if (FEJLEC.matches(s) && "KEDVEZM" !in s && "AJÁNDÉK" !in s && (i + 1 >= n || sorok[i + 1].isEmpty())) {
                kat = lowerRest(s)
            }
            if (!(AR.matches(s) && i + 1 < n && AR.matches(sorok[i + 1]))) {
                i++
                continue
            }
            val regi = arSzam(s)
            val ar = arSzam(sorok[i + 1])
            var j = i + 2
            var kedv = ""
            while (j < n && sorok[j].isEmpty()) j++
            if (j < n && SZAZALEK.matches(sorok[j])) {
                kedv = "-" + NON_DIGIT.replace(sorok[j], "") + "%"
                j++
            }
            val nev = mutableListOf<String>()
            var meret = ""
            val valt = mutableListOf<String>()
            while (j < n && sorok[j].isEmpty()) j++
            while (j < n && sorok[j].isNotEmpty()) {
                val t = sorok[j]
                if (AR.matches(t) || STOP.any { t.startsWith(it) }) break
                if (EGYSEGAR.containsMatchIn(t)) {
                    j++
                    continue
                }
                if (meret.isEmpty() && MERET.containsMatchIn(t)) {
                    meret = SPACES.replace(t, " ")
                } else if (meret.isNotEmpty()) {
                    if (!ZAGYVA.containsMatchIn(t)) valt += t
                } else if (!ZAGYVA.containsMatchIn(t) && nev.size < 5) {
                    nev += t
                }
                j++
            }
            i = j
            if (nev.isEmpty() || ar == null || regi == null || ar >= regi) continue
            if (pyLen(nev.joinToString(" ")) < 4) continue
            val marka = if (nagybetus(nev[0]) && nev.size > 1) nev[0] else ""
            val resz = if (marka.isNotEmpty()) nev.drop(1) else nev
            var cim = pyStrip(resz.joinToString(" "))
            cim = SPACES.replace(cim, " ").trimEnd(' ', ',', '.', ';', ':')
            if (marka.isNotEmpty()) {
                cim = "${if (pyLen(marka) > 4) pyTitle(marka) else marka} $cim"
            }
            val megj = pyStrip(SPACES.replace(valt.joinToString(" "), " "))
            out += OfferItem(
                store = STORE, name = upperFirst(cim), price = ar, oldPrice = regi,
                discount = kedv, packSize = meret, validity = validity,
                category = kat.ifEmpty { leaflet }, note = pyHead(megj, 120)
            )
        }
        return out
    }

    // ------------------------------------------- PDFBox-szöveg → pdfminer-alak

    private fun isPrice(t: String): Boolean = AR.matches(tiszta(t))
    private fun isPercent(t: String): Boolean = SZAZALEK.matches(tiszta(t))
    private fun isToken(t: String): Boolean = isPrice(t) || isPercent(t)

    /** Egy árdoboz: fent az eredeti (kisebb) ár, alatta az akciós, alatta a „−28 %". */
    private class PriceBox(val old: PdfLayout.Seg, val new: PdfLayout.Seg, var pct: PdfLayout.Seg? = null) {
        val box: PdfLayout.Box
            get() {
                val s = listOfNotNull(old, new, pct)
                return PdfLayout.Box(s.minOf { it.x0 }, s.maxOf { it.x1 }, s.minOf { it.top }, s.maxOf { it.bottom })
            }
    }

    private fun overlapX(a: PdfLayout.Seg, b: PdfLayout.Seg, slack: Double = 3.0): Boolean =
        a.x0 < b.x1 + slack && b.x0 < a.x1 + slack

    /**
     * Szövegdobozok: mint [PdfLayout.blocks], de a Müller névdobozában a
     * márkanév sora sokszor feleakkora betűvel áll, mint a terméknév
     * („GUCCI" 3 pont, „FLORA GORGEOUS" 6 pont) — ezért a PONTOSAN egy
     * bal szélre igazított, sűrűn egymás alatti sorok (8–9 pontos sorköz)
     * betűmérettől függetlenül egy dobozba kerülnek.
     */
    private fun blocks(segs: List<PdfLayout.Seg>, lookback: Int = 8): List<PdfLayout.Block> {
        val out = mutableListOf<PdfLayout.Block>()
        for (s in segs) {
            var target: PdfLayout.Block? = null
            if (!isToken(s.text)) {
                for (b in out.takeLast(lookback).asReversed()) {
                    val last = b.segs.last()
                    if (isToken(last.text)) continue
                    val hi = max(last.h, s.h)
                    val lo = min(last.h, s.h)
                    val dy = s.y - last.y
                    val aligned = abs(s.x0 - last.x0) <= 1.5 && dy > 2 && dy <= 12
                    val normal = hi <= 2 * lo && dy > 0.3 * hi && dy <= 2.6 * hi && s.x0 < last.x1 && s.x1 > last.x0
                    if (!aligned && !normal) continue
                    target = b
                    break
                }
            }
            if (target == null) out += PdfLayout.Block(s) else target.segs += s
        }
        return out
    }

    /** Egy termék szövegegysége az oldalon: árdoboz + névdoboz, vagy egy magányos szövegdoboz. */
    internal class Piece(val box: PdfLayout.Box, val lines: List<String>)

    /**
     * Egy oldal pdfminer-alakú szövege. A PDFBox a PDF belső sorrendjében adja
     * a sorokat, és az árdoboz sorait szétszórja (előbb az akciós ár, aztán a
     * százalék, végül az eredeti ár), ezért:
     *  1. árdobozok: két ár egymás alatt (fent az eredeti), alattuk a százalék;
     *  2. a többi sorból szövegdobozok ([blocks]);
     *  3. minden árdobozhoz a névdoboza: KÖZVETLENÜL alatta (ugyanott kezdődik,
     *     pár ponttal balrább) vagy KÖZVETLENÜL jobbra mellette (a teteje egy
     *     magasságban). Amelyik árdoboznak nincs ilyen névdoboza, az kimarad —
     *     inkább hiányozzon egy termék, mint hogy a szomszéd nevét kapja.
     *  4. kiírás olvasási sorrendben (fentről le), dobozok között üres sor.
     */
    internal fun pageUnits(page: String): List<Piece> {
        val segs = PdfLayout.segments(page) { isToken(it) }
        val prices = segs.filter { isPrice(it.text) }
        val pcts = segs.filter { isPercent(it.text) }

        // 1. árpárok: az eredeti ár az akciós fölött, vízszintesen átfedve
        data class PC(val d: Double, val o: Int, val n: Int)
        val pcs = mutableListOf<PC>()
        for ((oi, o) in prices.withIndex()) for ((ni, nw) in prices.withIndex()) {
            if (oi == ni || !overlapX(o, nw)) continue
            val dy = nw.y - o.y
            if (dy > 0.5 * o.h && dy < 4.5 * max(o.h, nw.h) + 4) pcs += PC(dy, oi, ni)
        }
        val usedP = HashSet<Int>()
        val boxes = mutableListOf<PriceBox>()
        for (c in pcs.sortedWith(compareBy<PC>({ it.d }, { it.o }, { it.n }))) {
            if (c.o in usedP || c.n in usedP) continue
            usedP += c.o; usedP += c.n
            boxes += PriceBox(prices[c.o], prices[c.n])
        }
        // a százalék az akciós ár alatt
        val usedPct = HashSet<Int>()
        for (b in boxes.sortedBy { it.new.y }) {
            var best = -1
            var bd = Double.MAX_VALUE
            for ((k, p) in pcts.withIndex()) {
                if (k in usedPct || !overlapX(p, b.new)) continue
                val dy = p.y - b.new.y
                if (dy > 0 && dy < 3.0 * p.h + 4 && dy < bd) { bd = dy; best = k }
            }
            if (best >= 0) { usedPct += best; b.pct = pcts[best] }
        }

        // 2. a többi sor szövegdobozokba
        val taken = HashSet<PdfLayout.Seg>()
        boxes.forEach { taken += it.old; taken += it.new; it.pct?.let { p -> taken += p } }
        val rest = segs.filter { it !in taken }
        val blocks = blocks(rest)

        // 3. árdoboz ↔ névdoboz
        data class NC(val d: Double, val b: Int, val k: Int)
        val ncs = mutableListOf<NC>()
        for ((bi, pb) in boxes.withIndex()) {
            val p = pb.box
            for ((k, blk) in blocks.withIndex()) {
                val f = blk.segs.first()
                val nb = blk.box
                // A 2026-09-28-i két újság 87 termékén mérve: alatta a bal széle
                // 4,7–6,2 ponttal balrább, a teteje 11–41 ponttal lejjebb;
                // jobbra 15–19 ponttal a jobb széle után, a teteje −9…+18 pont.
                val below = f.x0 <= p.x0 + 1 && f.x0 >= p.x0 - 10 && nb.top >= p.bottom - 2 && nb.top <= p.bottom + 48
                val right = f.x0 >= p.x1 + 8 && f.x0 <= p.x1 + 28 &&
                    nb.top >= p.top - 12 && nb.top <= p.top + 0.7 * (p.bottom - p.top)
                if (below || right) ncs += NC(PdfLayout.gap(p, nb), bi, k)
            }
        }
        val nameOf = HashMap<Int, Int>()
        val usedB = HashSet<Int>()
        for (c in ncs.sortedWith(compareBy<NC>({ it.d }, { it.b }, { it.k }))) {
            if (c.b in nameOf || c.k in usedB) continue
            nameOf[c.b] = c.k; usedB += c.k
        }
        // 4. egységek
        val units = mutableListOf<Piece>()
        for ((bi, pb) in boxes.withIndex()) {
            val k = nameOf[bi] ?: continue
            val lines = mutableListOf(pb.old.text, pb.new.text)
            pb.pct?.let { lines += it.text }
            lines += ""
            lines += blocks[k].lines
            units += Piece(pb.box, lines)
        }
        for ((k, blk) in blocks.withIndex()) {
            if (k in usedB) continue
            // magányos ár ne álljon össze egy másikkal árpárrá
            if (blk.segs.size == 1 && isPrice(blk.segs[0].text)) continue
            // Fejezetcím (a Python szabálya: csupa nagybetűs sor, utána üres
            // sor) csak nagyobb betűs sorból lehet — címből (12 pont) vagy
            // matricából („2+1 AKCIÓ", 6 pont). A kedvezmény-szövegek apró
            // (4 pontos) csupa nagybetűs márkasora („minden PHILIPS SONICARE
            // elektromos fogkefére") a pdfminernél nem áll a doboz végén, itt
            // a PDF szétszórt sorai miatt oda kerülhet — az ne nevezze át a
            // következő termékeket.
            if (FEJLEC.matches(tiszta(blk.lines.last())) && blk.segs.last().h < 5) continue
            units += Piece(blk.box, blk.lines)
        }
        return units.sortedWith(compareBy<Piece>({ it.box.top }, { it.box.x0 }))
    }

    /** Sortörésnek számító jelek (Python `splitlines`) egy soron belül: szóköz lesz belőlük. */
    private val LINE_BREAKS = Regex("[\\n\\r\\u000B\\u000C\\u001C\\u001D\\u001E\\u0085\\u2028\\u2029]")

    /**
     * A PDFBox jelölt oldalszövegeiből pdfminer-alakú szöveg (lásd [pageUnits]):
     * dobozonként egymás utáni sorok, a dobozok között üres sor, az oldalak
     * között lapdobás (mint a pdfminernél).
     */
    fun pageText(pages: List<String>): String =
        pages.joinToString("\u000C") { page ->
            pageUnits(page).joinToString("") { u ->
                u.lines.joinToString("") { LINE_BREAKS.replace(it, " ") + "\n" } + "\n"
            }
        }

    /** Egy újság (oldalszövegek) termékei. */
    fun products(pages: List<String>, leaflet: String): List<OfferItem> {
        val text = pageText(pages)
        return parseLines(text, leaflet, validity(text))
    }

    /**
     * A Müller aktuális drogéria- és parfüméria-újságának termékei. HÁTTÉRSZÁLON hívandó.
     * `get`: szöveg, `getBytes`: a PDF, `pdfPages`: oldalszövegek a [PdfTextSettings] szerint.
     */
    fun download(
        get: (String) -> String,
        getBytes: (String) -> ByteArray,
        pdfPages: (ByteArray) -> List<String>,
        progress: (String) -> Unit = {}
    ): List<OfferItem> {
        val out = mutableListOf<OfferItem>()
        for ((kind, url) in leaflets(get(PAGE))) {
            val name = LEAFLET_NAMES[kind] ?: kind
            progress("Müller: a ${name.lowercase()} prospektus letöltése…")
            out += products(pdfPages(getBytes(url)), name)
        }
        return out
    }
}
