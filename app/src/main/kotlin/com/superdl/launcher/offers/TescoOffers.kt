package com.superdl.launcher.offers

import org.json.JSONObject
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tesco — a tesco.hu akciós újsága (a bolt saját PDF-je).
 *
 * A `tesco_akciok.py` (kutatás, pypdf) átirata, de a PDF szövegét a pdfbox
 * adja jelölőkkel (lásd [PdfTextSettings]), és a név és az ár HELY szerint
 * párosul:
 *
 *   www.tesco.hu/akciok/katalogusok → `__NEXT_DATA__` → `__APOLLO_STATE__`
 *   „Leaflet:*" (HM = hipermarket, SM = szupermarket; `leafletUrl`,
 *   `validFrom`, `validTo`) → PDF
 *
 * Egy csempe (2026-09-24-i újság):
 *
 *     – 14 %            ← kedvezmény
 *     1173 Ft           ← régi ár (kicsi)
 *     999               ← az ár (NAGY)          Tesco csont nélküli,
 *     Ft                                         szeletelt sertéstarja
 *                                                400 g, 2 933 Ft/1 kg  ← kiszerelés + RÉGI egységár
 *                                                2498 Ft/1 kg          ← AKCIÓS egységár
 *
 * Clubcard-os csempén: „Clubcard nélkül: 899 Ft/cs" (rendes ár), a nagy szám
 * a Clubcard-ár, és a névdobozban „Clubcarddal: 6064 Ft/1 kg".
 *
 * ⚠️ Egy rossz ár rosszabb, mint egy hiányzó: ahol a kiszerelés és az
 * egységár megvan (0,4 kg × 2498 Ft/kg = 999 Ft), az árnak EZT kell kiadnia,
 * különben a termék kimarad. Ahol nincs mivel ellenőrizni (kilós áru, nem
 * élelmiszer), ott csak az egyértelműen legközelebbi ár számít.
 *
 * „Együtt:" csempe (két termék együtt olcsóbb): a nagy szám a KETTŐ együttes
 * ára, ezért azt egyik termékhez sem írjuk oda; a termékek saját, kiírt ára
 * („380 g, 2049 Ft, 5392 Ft/1 kg") megy az árba, az együttes ár a megjegyzésbe.
 */
object TescoOffers {

    const val STORE = "Tesco"
    const val LIST_URL = "https://www.tesco.hu/akciok/katalogusok"
    const val CARD = "Clubcarddal"

    data class Leaflet(val type: String, val pdfUrl: String, val validFrom: String, val validTo: String) {
        val title: String get() = when (type) {
            "HM" -> "Hipermarket újság"
            "SM" -> "Szupermarket újság"
            "EX" -> "Expressz újság"
            else -> "Tesco újság"
        }
        val validity: String get() = LidlOffers.validityOf(validFrom, validTo)
    }

    private val I = setOf(RegexOption.IGNORE_CASE)
    private val NUM_ONLY = Regex("^\\s*(\\d{1,3}(?: \\d{3})*|\\d+)\\s*$")
    private val FT_UNIT = Regex("^\\s*Ft(/[^\\s]+(?: [^\\s]+)?)?\\s*$")
    private val OLD_T = Regex("^\\s*(\\d{1,3}(?: \\d{3})*|\\d+) Ft(/\\S+(?: dkg)?)?\\s*$")
    private val PCT_T = Regex("^\\s*[–-]\\s*(\\d{1,2})\\s*%\\s*$")
    private val LABEL = Regex("^\\s*(Clubcard ár|Clubcard|nélkül:|Együtt:)\\s*$")
    private val UNIT_LINE = Regex("(\\d[\\d ]*\\d|\\d)\\s*Ft/(1 kg|1 l|kg|l|db|m|1 m|mosás|lap|tekercs)\\b")
    private val PACK_HINT = Regex(
        "(^többféle|^csomagolt|^különböző|kapható|\\b\\d+(?:[.,]\\d+)?\\s*(?:x\\s*\\d+(?:/\\d+)?\\s*)?" +
            "(?:g|kg|dkg|l|ml|cl|db|mosás|lap|tekercs)\\b)", I
    )
    private val STOP = Regex(
        "^(Clubcard ár|Clubcard$|nélkül:|Együtt:|A feltüntetett|A termék a|\\* A termék|A kép csak|" +
            "Nálunk garantált|Töltsd le|\\+ visszaváltási)", I
    )
    private val CC_UNIT = Regex("Clubcarddal:\\s*(\\d[\\d ]*\\d|\\d)\\s*Ft/(\\S+ ?\\S*)")
    private val EXPLICIT = Regex("^(.*?),?\\s*(\\d[\\d ]*)\\s*Ft,\\s*(\\d[\\d ]*\\s*Ft/\\S+(?: \\S+)?)\\s*$")
    private val QTY = Regex("(\\d+(?:[,.]\\d+)?)\\s*(?:x\\s*(\\d+(?:[,.]\\d+)?)\\s*)?(kg|dkg|g|ml|cl|l|db)\\b", I)
    private val OWN_DATES = Regex("^(\\d{4})\\.\\s*(\\d{2})\\.\\s*(\\d{2})\\.?\\s*[–-]\\s*(\\d{2})\\.\\s*(\\d{2})\\.?\\s*(.*)$")
    private val FACT = mapOf(
        "g" to ("kg" to 0.001), "dkg" to ("kg" to 0.01), "kg" to ("kg" to 1.0),
        "ml" to ("l" to 0.001), "cl" to ("l" to 0.01), "l" to ("l" to 1.0), "db" to ("db" to 1.0)
    )
    private val W = "[\\p{L}\\p{N}_]"
    private val DEPOSIT = Regex("visszaváltási díj\\D{0,5}(\\d+)\\s*Ft", RegexOption.IGNORE_CASE)

    // ---- az újságok listája -------------------------------------------------

    /** Az újságok a katalógusoldal `__NEXT_DATA__`-jából (HTML vagy maga a JSON). */
    fun leaflets(pageOrJson: String): List<Leaflet> {
        val json = Regex("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(pageOrJson)?.groupValues?.get(1) ?: pageOrJson.trim()
        val state = JSONObject(json).optJSONObject("props")?.optJSONObject("pageProps")
            ?.optJSONObject("__APOLLO_STATE__") ?: return emptyList()
        val out = mutableListOf<Leaflet>()
        for (k in state.keys()) {
            if (!k.startsWith("Leaflet:")) continue
            val v = state.optJSONObject(k) ?: continue
            val url = v.optString("leafletUrl")
            if (url.isBlank()) continue
            out += Leaflet(v.optString("type"), url, v.optString("validFrom"), v.optString("validTo"))
        }
        return out.sortedBy { it.type }
    }

    /** A most érvényes újság: a hipermarketes (abban van a legtöbb termék), ha nincs, a szupermarketes. */
    fun pick(all: List<Leaflet>, now: Instant = Instant.now()): Leaflet? {
        fun ok(l: Leaflet): Boolean {
            val from = runCatching { Instant.parse(l.validFrom) }.getOrNull()
            val to = runCatching { Instant.parse(l.validTo) }.getOrNull()
            return (from == null || !from.isAfter(now)) && (to == null || !to.isBefore(now))
        }
        val good = all.filter(::ok)
        return good.firstOrNull { it.type == "HM" } ?: good.firstOrNull { it.type == "SM" } ?: good.firstOrNull()
    }

    // ---- szöveg -------------------------------------------------------------

    private fun isToken(t: String): Boolean =
        NUM_ONLY.matches(t) || FT_UNIT.matches(t) || OLD_T.matches(t) || PCT_T.matches(t) || LABEL.matches(t)

    private fun standalone(s: PdfLayout.Seg): Boolean = isToken(s.text)

    /** A PDF-es név javítása: „fi nest" → „finest", „R i c e l a n d" → „Riceland", „fil é" → „filé". */
    internal fun clean(s0: String): String {
        var s = Regex("f([il])\\s+(?=[a-záéíóöőúüű])").replace(s0) { "f" + it.groupValues[1] }
        s = Regex("(?<!\\S)(?:$W ){2,}$W(?!\\S)").replace(s) { it.value.replace(" ", "") }
        s = Regex("(?<=$W) ([áéíóöőúüű])(?!\\S)").replace(s) { it.groupValues[1] }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun num(s: String?): Double? = s?.replace(" ", "")?.replace(",", ".")?.toDoubleOrNull()

    private fun trimSC(s: String) = s.trim(' ', ',')

    /** „5x28 g/cs" → (0,14; „kg"). */
    private fun qtyOf(pack: String): Pair<Double, String>? {
        val m = QTY.findAll(pack).lastOrNull() ?: return null
        var q = num(m.groupValues[1]) ?: return null
        if (m.groupValues[2].isNotEmpty()) q *= num(m.groupValues[2]) ?: return null
        val (b, f) = FACT[m.groupValues[3].lowercase()] ?: return null
        return q * f to b
    }

    private class Name(
        val name: String, val pack: String, val units: List<Pair<String, String>>,
        val ccUnit: Pair<String, String>?, val ownValidity: String, val box: PdfLayout.Box,
        val deposit: String
    )

    /** A `parse_leaflet_text` név/részlet-szétválasztása, egy szövegdobozon. */
    private fun nameBlock(lines: List<String>, box: PdfLayout.Box): Name? {
        val nameLines = mutableListOf<String>()
        val details = mutableListOf<String>()
        for (x in lines.map { it.trim() }) {
            if (x.isEmpty()) continue
            if (STOP.containsMatchIn(x)) break
            if (details.isNotEmpty() || PACK_HINT.containsMatchIn(x) || UNIT_LINE.containsMatchIn(x) ||
                x.startsWith("Clubcarddal")
            ) details += x else nameLines += x
        }
        val det = details.joinToString(" ")
        val units = UNIT_LINE.findAll(det).map { it.groupValues[1] to it.groupValues[2] }.toMutableList()
        val cc = CC_UNIT.find(det)
        var pack = ""
        if (details.isNotEmpty()) {
            val pk = trimSC(details[0].split(Regex(",?\\s*\\d[\\d ]*\\s*Ft/"))[0])
            if (pk.isNotEmpty() && !pk.startsWith("Clubcarddal")) pack = clean(pk)
        }
        var name = clean(nameLines.joinToString(" "))
        name = name.split(Regex("\\s(?:Az ajánlat|Ajánlatunk|A választék|Bármely|Számos|Érvényes)\\b"))[0]
        name = trimSC(name.replace(Regex("^(?:\\d[\\d ]*\\s+)+"), ""))
        if (name.isEmpty() || Regex("^(Kiváló alapanyagok|vidék:|Nekünk az étel)").containsMatchIn(name)) return null
        var ccUnit: Pair<String, String>? = null
        if (cc != null) {
            // a Clubcard-os egységárat a sima minta is elkapja: onnan ki
            val v = cc.groupValues[1].replace(" ", "")
            val i = units.indexOfLast { it.first.replace(" ", "") == v }
            if (i >= 0) units.removeAt(i)
            ccUnit = v to cc.groupValues[2].trim(' ', ',', ';')
        }
        var own = ""
        OWN_DATES.matchEntire(name)?.let { m ->
            // „2026. 09. 02 – 09. 29. Head&Shoulders sampon": saját érvényesség a név elején
            val g = m.groupValues
            own = "${g[2]}.${g[3]}-tól ${g[4]}.${g[5]}-ig"
            name = g[6].trim()
        }
        if (name.isEmpty() || name.endsWith(":") || Regex("áruházainkban|kapható:").containsMatchIn(name)) return null
        // „+ visszaváltási díj: 50 Ft/db" — az árban nincs benne, ki kell mondani
        val deposit = lines.firstNotNullOfOrNull { DEPOSIT.find(it) }?.let { "+${it.groupValues[1]} Ft visszaváltási díj" } ?: ""
        return Name(name, pack, units, ccUnit, own, box, deposit)
    }

    /**
     * A nagy szám, amit a névdoboz saját számai szerint látnunk kell
     * (érték, tűrés). `club`: Clubcard-os csempe; `unit`: a nagy szám alatti
     * „Ft/kg", „Ft/10 dkg"… („" = darabár).
     */
    private fun expected(p: Name, club: Boolean, unit: String): Pair<Double, Double>? {
        val u = if (p.ccUnit != null && club) p.ccUnit else p.units.lastOrNull() ?: return null
        val per = num(u.first) ?: return null
        val base = u.second.replace("1 ", "")
        when (unit.trim()) {
            "kg", "l" -> return if (base == unit.trim()) per to 1.01 else null
            "10 dkg" -> return if (base == "kg") per / 10 to 1.01 else null
        }
        val q = qtyOf(p.pack) ?: return null
        if (q.second != base) return null
        val e = q.first * per
        // az egységár 1 Ft-ra, a kiszerelés az utolsó jegyére kerekítve: ennyi fér bele
        return e to q.first * 0.5 + 1.01 + 0.005 * e
    }

    private fun anyExpected(p: Name): Boolean =
        listOf(false, true).any { c -> listOf("", "kg", "l", "10 dkg").any { u -> expected(p, c, u) != null } }

    private class Group(val anchor: PdfLayout.Seg, val key: Int) {
        var unit = ""
        var box = anchor.box
        val items = mutableListOf<PdfLayout.Seg>()
        val texts get() = items.map { it.text.trim() }
        val club get() = texts.any { it.startsWith("Clubcard") || it == "nélkül:" }
        val bundle get() = texts.any { it == "Együtt:" }
        val old get() = texts.firstNotNullOfOrNull { OLD_T.matchEntire(it) }?.groupValues?.get(1)?.replace(" ", "")?.toIntOrNull()
        val pct get() = texts.firstNotNullOfOrNull { PCT_T.matchEntire(it) }?.groupValues?.get(1)?.toIntOrNull()
    }

    // ---- egy oldal ----------------------------------------------------------

    /** Egy PDF-oldal termékei ([PdfTextSettings] szerint kinyert, jelölt szöveg). */
    fun parsePage(pageText: String, category: String, validity: String): List<OfferItem> {
        val segs = PdfLayout.segments(pageText, ::isToken)
        val bl = PdfLayout.blocks(segs, standalone = ::standalone)
        val toks = bl.filter { it.segs.size == 1 && standalone(it.segs[0]) }.map { it.segs[0] }
        val nums = toks.filter { NUM_ONLY.matches(it.text) }
        val maxh = nums.maxOfOrNull { it.h } ?: 0.0
        val anchors = nums.filter { it.h >= 0.5 * maxh && it.h >= 8 }
        val groups = anchors.mapNotNull { a -> a.text.replace(" ", "").trim().toIntOrNull()?.let { Group(a, it) } }
        val others = toks.filter { it !in anchors }.toMutableList()
        // a szám alatti „Ft" / „Ft/kg" / „Ft/10 dkg"
        for (g in groups) {
            val a = g.anchor
            val best = others.filter {
                FT_UNIT.matches(it.text) && it.y - a.y > 0 && it.y - a.y <= a.h && it.x0 < a.x1 && it.x1 > a.x0
            }.minByOrNull { it.y } ?: continue
            g.unit = (FT_UNIT.matchEntire(best.text)?.groupValues?.get(1) ?: "").trimStart('/')
            others.remove(best)
            g.box = PdfLayout.Box(min(g.box.x0, best.x0), max(g.box.x1, best.x1), g.box.top, max(g.box.bottom, best.y))
        }
        for (s in others) {
            val best = groups.minByOrNull { PdfLayout.gap(s.box, it.box) } ?: continue
            if (PdfLayout.gap(s.box, best.box) <= 30) best.items += s
        }

        val prods = mutableListOf<Name>()
        data class Explicit(val name: String, val price: Int, val pack: String, val unit: String, val box: PdfLayout.Box)
        val explicit = mutableListOf<Explicit>()
        for (b in bl) {
            if (b.segs.size == 1 && standalone(b.segs[0])) continue
            val lines = b.lines
            val expLines = lines.indices.filter { EXPLICIT.matches(lines[it].trim()) }
            if (expLines.isNotEmpty()) {
                // „380 g, 2049 Ft, 5392 Ft/1 kg": több termék egy dobozban, mind a saját árával
                var start = 0
                for (i in expLines) {
                    val nm = clean(lines.subList(start, i).map { it.trim() }.filter { !STOP.containsMatchIn(it) }.joinToString(" "))
                    val m = EXPLICIT.matchEntire(lines[i].trim())!!
                    start = i + 1
                    if (nm.isEmpty() || Regex("\\bFt\\b").containsMatchIn(nm)) continue
                    val price = m.groupValues[2].replace(" ", "").toIntOrNull() ?: continue
                    val pb = UNIT_LINE.find(m.groupValues[3]) ?: continue
                    val per = num(pb.groupValues[1]) ?: continue
                    val q = qtyOf(m.groupValues[1]) ?: continue
                    if (q.second != pb.groupValues[2].replace("1 ", "")) continue
                    if (abs(q.first * per - price) > q.first * 0.5 + 1.01) continue
                    explicit += Explicit(nm, price, clean(m.groupValues[1]), m.groupValues[3].trim(), b.box)
                }
                continue
            }
            nameBlock(lines, b.box)?.let { prods += it }
        }

        // 1. számtan: a csempe fajtája (Clubcard, Ft/kg…) szerinti várt ár
        val res = HashMap<Int, Int>()
        val used = HashSet<Int>()
        data class C(val d: Double, val pi: Int, val gi: Int)
        val cands = mutableListOf<C>()
        for ((pi, p) in prods.withIndex()) for ((gi, g) in groups.withIndex()) {
            val e = expected(p, g.club, g.unit) ?: continue
            if (abs(g.key - e.first) <= e.second) cands += C(PdfLayout.gap(p.box, g.box), pi, gi)
        }
        for (c in cands.sortedWith(compareBy<C>({ it.d }, { it.pi }, { it.gi }))) {
            if (c.pi in res || c.gi in used || c.d > 250) continue
            res[c.pi] = c.gi; used += c.gi
        }
        // 2. hely: csak akinek nincs mivel ellenőrizni, és csak egyértelmű esetben
        val freeP = prods.indices.filter { it !in res }
        val freeG = groups.indices.filter { it !in used }
        val sub = PdfLayout.match(
            freeP.map { PdfLayout.Want(prods[it].box, if (anyExpected(prods[it])) listOf(-1e9 to 0.0) else null) },
            freeG.map { PdfLayout.Offer(groups[it].box, groups[it].key) },
            60.0
        )
        for ((k, v) in sub) res[freeP[k]] = freeG[v]

        val out = mutableListOf<OfferItem>()
        for ((pi, p) in prods.withIndex()) {
            val g = groups[res[pi] ?: continue]
            if (g.bundle) continue          // az „Együtt:" ár két termékre szól, nem erre
            val a = g.key
            var price: Int? = null
            var card: Int? = null
            var oldPrice: Int? = null
            var discount = ""
            val old = g.old
            if (g.club) {
                card = a
                if (old != null && old > a && old <= 5 * a) price = old
            } else {
                price = a
                if (old != null && a < old && old <= 5 * a) {
                    val pct = g.pct
                    if (pct == null || abs((100.0 * (1 - a.toDouble() / old)).roundToInt() - pct) <= 3) {
                        oldPrice = old
                        if (pct != null) discount = "-$pct%"
                    }
                }
            }
            val notes = mutableListOf<String>()
            if (p.deposit.isNotEmpty()) notes += p.deposit
            var pack = p.pack
            when (g.unit) {
                // a nagy szám kilóra / 10 dekára szól: ezt a listasorban is ki kell mondani
                "kg" -> { if (pack.isNotEmpty()) notes += pack; pack = "kilónként" }
                "l" -> { if (pack.isNotEmpty()) notes += pack; pack = "literenként" }
                "10 dkg" -> { if (pack.isNotEmpty()) notes += pack; pack = "10 dekánként" }
            }
            var unitPrice = p.units.lastOrNull()?.let { (v, b) -> "1 ${b.replace("1 ", "")} = ${v.replace(" ", "")} Ft" } ?: ""
            p.ccUnit?.let { (v, b) ->
                unitPrice = listOf(unitPrice, "Clubcarddal 1 ${b.replace("1 ", "")} = $v Ft").filter { it.isNotEmpty() }.joinToString(", ")
            }
            out += OfferItem(
                store = STORE, name = p.name, price = price, cardPrice = card,
                cardName = if (card != null) CARD else "", oldPrice = oldPrice, discount = discount,
                packSize = pack, unitPrice = unitPrice, validity = p.ownValidity.ifEmpty { validity },
                category = category, note = notes.joinToString(", ")
            )
        }
        for (e in explicit) {
            // az „Együtt:" csempe ára a megjegyzésbe (ha a doboz mellett ott van)
            val g = groups.filter { it.bundle }.minByOrNull { PdfLayout.gap(e.box, it.box) }
                ?.takeIf { PdfLayout.gap(e.box, it.box) <= 60 }
            val note = g?.let {
                "Csomagajánlat: a párjával együtt " + (if (it.club) "Clubcarddal " else "") + "${it.key} Ft"
            } ?: ""
            out += OfferItem(
                store = STORE, name = e.name, price = e.price, packSize = e.pack,
                unitPrice = UNIT_LINE.find(e.unit)?.let { "1 ${it.groupValues[2].replace("1 ", "")} = ${it.groupValues[1].replace(" ", "")} Ft" } ?: e.unit,
                validity = validity, category = category, note = note
            )
        }
        return out
    }

    /** Egy újság (az oldalak szövege) termékei, név szerint egyszer. */
    fun products(pages: List<String>, category: String, validity: String): List<OfferItem> {
        val out = mutableListOf<OfferItem>()
        val seen = HashSet<String>()
        for (page in pages) for (t in parsePage(page, category, validity)) {
            if (seen.add(t.name.lowercase())) out += t
        }
        return out
    }

    /**
     * A Tesco aktuális hipermarketes újságának termékei. HÁTTÉRSZÁLON hívandó.
     * `get`: szöveg, `getBytes`: a PDF, `pdfPages`: oldalszövegek a [PdfTextSettings] szerint.
     */
    fun download(
        get: (String) -> String,
        getBytes: (String) -> ByteArray,
        pdfPages: (ByteArray) -> List<String>,
        progress: (String) -> Unit = {},
        now: Instant = Instant.now()
    ): List<OfferItem> {
        val lf = pick(leaflets(get(LIST_URL)), now)
            ?: throw IllegalStateException("A Tesco oldalán nem találom az érvényes újságot.")
        progress("Tesco: ${lf.title} letöltése…")
        val pdf = getBytes(lf.pdfUrl)
        progress("Tesco: ${lf.title} olvasása…")
        return products(pdfPages(pdf), lf.title, lf.validity)
    }
}
