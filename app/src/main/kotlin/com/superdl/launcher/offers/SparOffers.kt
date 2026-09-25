package com.superdl.launcher.offers

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.LocalDate
import java.util.Locale

/**
 * SPAR / INTERSPAR — a heti szórólapok (iPaper) szövegrétegéből.
 *
 * A Windows-oldali kutatás `spar_akcio.py` gyűjtőjének átirata. A szórólap-
 * néző HTML-jében ott a PDF szövegrétege oldalanként (`"pageTexts":[...]`);
 * ebben keressük az ár-blokkokat („1.299 Ft MYSPAR ÁR 999 Ft"), a termékek
 * kiszerelését és egységárát („500 g (2.598 Ft/1 kg)"), és a kettőt SZÁMOLÁSSAL
 * párosítjuk: kiszerelés × egységár = bolti ár.
 *
 * SZÁNDÉKOS SZIGOR: csak a számolással igazolt („biztos") párosítás marad.
 * A Python a közelség alapján is párosít („bizonytalan"), de az rossz árat
 * mondhat egy vak felhasználónak — azt itt el sem végezzük.
 *
 * Felfedezés: www.spar.hu/ajanlatok ld+json OfferCatalog (a Cloudflare néha
 * 403-at ad: újrapróbálás), tartalék: a legutóbbi csütörtöki szórólap-címek
 * kitalálása a szorolap.spar.hu-n.
 */
object SparOffers {

    const val STORE = "Spar"
    private const val DISCOVERY = "https://www.spar.hu/ajanlatok"

    private val S = IPaper.S          // Python \s
    private val NS = IPaper.NS        // Python \S
    private val WA = IPaper.W_AFTER   // Python \b egy szókarakter után

    // ------------------------------------------------------------ szöveg-normalizálás
    private val N1 = Regex("(\\d) F$S*/$S*1$S+t$S+kg")
    private val N2 = Regex("(\\d) F$S+t$WA")
    private val N3 = Regex("(\\d) F(?=$S|\$)")
    private val N4 = Regex("(\\d) \\.(\\d{3})$WA")
    private val N5 = Regex("$S+")

    fun norm(text: String): String {
        var t = text.replace(Char(0xFEFF), ' ').replace(Char(0x200A).toString(), "").replace(Char(0xA0), ' ')
        t = N1.replace(t, "\$1 Ft /1 kg")      // "1.999 F /1 t kg"
        t = N2.replace(t, "\$1 Ft")            // "1.299 F  t"
        t = N3.replace(t, "\$1 Ft")
        t = N4.replace(t, "\$1.\$2")           // "2 .899"
        return N5.replace(t, " ").trim()
    }

    private fun num(s: String): Double = s.replace(".", "").replace(",", ".").toDouble()
    private fun numOrNull(s: String): Double? = s.replace(".", "").replace(",", ".").toDoubleOrNull()

    private fun blank(t: String, a: Int, b: Int): String = t.substring(0, a) + " ".repeat(b - a) + t.substring(b)

    private const val P = "\\d{1,3}(?:\\.\\d{3})*"
    private const val PV = "$P(?:/$P)*"
    private const val UNITIN = "[\\d.,/ ]+? ?Ft/ ?1 ?(?:kg|l|db|tekercs|mosás|pár)(?:; [^)]*)?"
    private const val PER = "(?: /(?<per>10 dkg|1 kg|1 db))?"
    private const val CU = "(?: \\((?<cunit>$UNITIN)\\))?"

    /** Prioritási sorrendben: a korábbi blokk-fajta foglalja le a szöveget. */
    private val BLOCKS: List<Pair<String, IPaper.NamedRegex>> = listOf(
        "myspar" to "(?<reg>$PV) Ft MYSPAR ÁR\\*? (?:-(?<pct>\\d+)% )?(?<card>$P) Ft$PER$CU",
        "kupon" to "(?<reg>$PV) Ft MYSPAR KUPONOS ÁR: (?<coupon>$P) Ft$CU",
        "hetvegi" to "(?:(?<reg>$PV) Ft )?HÉTVÉGI ÁR: (?:-(?<pct>\\d+)% )?(?<weekend>$P) Ft$CU",
        "disc" to "-(?<pct>\\d+)% (?:KEDVEZMÉNY )?(?<old>$PV) Ft (?<price>$P) Ft$PER(?: Spórolás: (?<save>$P) Ft)?",
        "multi" to "(?<price>$PV) Ft (?<n>\\d+) (?<uw>DB|CSOMAG) (?:-TÓL|ESETÉN) (?<multi>$P) Ft",
        "csak" to "CSAK (?:\\d+ napig )?(?<price>$P) Ft$PER(?<tol> -tól)?",
        "plain" to "(?<![(/\\d.:])(?<old>$PV) Ft (?<price>$P) Ft(?![/\\d])",
        "bare" to "(?<![(/\\d.])(?<!: )(?<price>$P) Ft(?![/\\d)])"
    ).map { it.first to IPaper.NamedRegex(it.second) }

    private val MASKS = listOf(
        Regex("\\*Normál kiszerelésű termék: [^:]{3,120}?: [\\d.]+ Ft [\\d.,]+ Ft/ ?1 ?[\\p{L}\\p{N}_]+"),
        Regex("\\d+% -kal kedvezőbb egységár\\*?"),
        Regex("\\(\\+visszaváltási díj: [\\d.]+ Ft\\)")
    )
    private val DEPOSIT = Regex("\\(\\+visszaváltási díj: ([\\d.]+) Ft\\)")
    private val PACK_RE = IPaper.NamedRegex(
        "(?<pack>(?:\\d+×)?\\d+(?:,\\d+)?(?:-\\d+)? ?(?:kg|dkg|g|ml|cl|l|db|tekercs|mosás)" +
            "(?:/(?:csomag|doboz))?(?: 1 (?:csomag|doboz|db|pár))?)" +
            "(?:;? ?(?:1 (?:doboz|csomag) )?ár: (?<ar>$P) Ft)?$S*\$"
    )
    private val PAREN_RE = IPaper.NamedRegex("\\((?<u>$UNITIN)\\)")
    private val ONE_RE = IPaper.NamedRegex("(?<![\\d/,.×])(?<pack>1 (?:kg|l|db|pár|csomag|doboz))$WA(?! \\(\\d)")
    private val UNITPAIR = Regex("((?:[\\d.,]+/)*[\\d.,]+) ?Ft/ ?1 ?(kg|l|db|tekercs|mosás|pár)")
    private val ORPHAN = Regex("(Ft|\\d)$S*\$")
    private val GAP = Regex("$S{3,}")
    private val JUNK = Regex(
        "^(?:éve|új|a zamatos|Jó döntés[^A-ZÁÉÍÓÖŐÚÜŰ]*|CSAK|Spórolás: $NS+ Ft|Először nálunk!|Csak nálunk!|" +
            "Megannyi újdonság|Csomagolt kiszerelésben is!|MYSPAR KUPONOS ÁR|MINDEN MÁSODIK TERMÉK|" +
            "KÖSZÖNJÜK NEKTEK!|\\*+|[-–•,;:]|\\d+ napig|\\d\\d\\. \\d\\d\\. és \\d\\d\\. \\d\\d\\. között|" +
            "\\d{4}\\. \\d\\d\\. \\d\\d[–-]\\d\\d\\.)$S*"
    )
    private val SPLIT1 = Regex("[!?]\\**$S|\\)$S|”$S(?=[A-ZÁÉÍÓÖŐÚÜŰ])")
    private val SPLIT2 = Regex("${IPaper.W_BEFORE}Ft$WA\\)?")
    private val SPLIT3 = Regex("(?<=[a-záéíóöőúüű]{3})\\.$S(?=[A-ZÁÉÍÓÖŐÚÜŰ])")
    private val SPLIT4 = Regex("(?:kiszolgálópultban|csomagolt|frissen sütve|gyorsfagyasztott)\\**$S(?=[A-ZÁÉÍÓÖŐÚÜŰ])")
    private val HAS_WORD = Regex("[A-Za-zÁÉÍÓÖŐÚÜŰáéíóöőúüű]{2}")
    private val PACK_QTY = Regex("^(?:(\\d+)×)?(\\d+(?:,\\d+)?)(?:-\\d+)? ?(kg|dkg|g|ml|cl|l|db|tekercs|mosás)")

    /** Egy termék, ahogy a Python is látja (a mezők jelentése ugyanaz). */
    data class Product(
        val name: String,
        val pack: String?,
        val unitPrice: String?,
        val price: Double?,
        val oldPrice: Double? = null,
        val cardPrice: Double? = null,
        val cardUnitPrice: String? = null,
        val couponPrice: Double? = null,
        val discountPct: Int? = null,
        val multiBuy: String? = null,
        val per: String? = null,
        val deposit: Int? = null,
        val note: String? = null,
        val kind: String? = null
    )

    internal class Block(val kind: String, val s: Int, val e: Int, val g: Map<String, String>) {
        var used = 0
    }

    internal class Anchor(val ps: Int, val pe: Int, val e: Int, val pack: String?, val unit: String?, val ar: String?) {
        var name: String? = null
        var deposit: Int? = null
        var block: Block? = null
    }

    /** '4×95 g' → (0.38, kg); '16 db/csomag 1 csomag' → (16, db); '0,75 l' → (0.75, l). */
    private fun packQty(pack: String): Pair<Double, String>? {
        val m = PACK_QTY.find(pack) ?: return null
        val mult = m.groupValues[1].ifEmpty { "1" }.toInt()
        val v = num(m.groupValues[2]) * mult
        val (f, base) = when (m.groupValues[3]) {
            "g" -> 0.001 to "kg"; "dkg" -> 0.01 to "kg"; "kg" -> 1.0 to "kg"
            "ml" -> 0.001 to "l"; "cl" -> 0.01 to "l"; "l" -> 1.0 to "l"
            else -> 1.0 to m.groupValues[3]
        }
        return v * f to base
    }

    /** Az egységár × kiszerelés → várható bolti ár(ak). */
    private fun impliedPrices(pack: String?, unitText: String?): List<Double> {
        val out = mutableListOf<Double>()
        val q = if (!pack.isNullOrEmpty()) packQty(pack) else null
        for (m in UNITPAIR.findAll(unitText.orEmpty())) {
            val den = m.groupValues[2]
            for (v in m.groupValues[1].split("/")) {
                val u = numOrNull(v) ?: continue
                if (q != null && q.second == den) out += u * q.first
                else if (pack.isNullOrEmpty() && den == "kg") { out += u * 0.1; out += u }   // pultos áru
            }
        }
        return out
    }

    private val PRIMARY = mapOf(
        "myspar" to listOf("reg"), "kupon" to listOf("reg"), "hetvegi" to listOf("reg", "weekend"),
        "disc" to listOf("price"), "plain" to listOf("price"), "multi" to listOf("price"),
        "csak" to listOf("price"), "bare" to listOf("price")
    )

    /** Azok az árak, amelyekre a termék melletti egységár vonatkozik (a nem kártyás bolti ár). */
    private fun blockPrices(b: Block): List<Double> =
        PRIMARY.getValue(b.kind).mapNotNull { b.g[it] }.flatMap { s -> s.split("/").map { num(it) } }

    internal fun findBlocks(t: String): List<Block> {
        val taken = BooleanArray(t.length)
        val blocks = mutableListOf<Block>()
        for ((kind, rx) in BLOCKS) {
            for (m in rx.regex.findAll(t)) {
                val a = m.range.first
                val b = m.range.last + 1
                if ((a until b).any { taken[it] }) continue
                val g = rx.dict(m)
                if (kind == "plain" && num(g.getValue("price")) >= num(g.getValue("old").split("/")[0])) continue
                for (i in a until b) taken[i] = true
                blocks += Block(kind, a, b, g)
            }
        }
        return blocks.sortedBy { it.s }
    }

    internal fun findAnchors(t: String, blocks: List<Block>): List<Anchor> {
        val inBlock = BooleanArray(t.length)
        for (b in blocks) for (i in b.s until b.e) inBlock[i] = true
        val anchors = mutableListOf<Anchor>()
        for (m in PAREN_RE.regex.findAll(t)) {
            val a = m.range.first
            val e = m.range.last + 1
            if (inBlock[a]) continue
            val lo = maxOf(0, a - 70)
            val pm = PACK_RE.regex.find(t.substring(lo, a))
            val unit = PAREN_RE.group(m, "u")
            if (pm != null) {
                anchors += Anchor(lo + pm.range.first, a, e, PACK_RE.group(pm, "pack"), unit, PACK_RE.group(pm, "ar"))
            } else {
                if (ORPHAN.containsMatchIn(t.substring(maxOf(0, a - 6), a))) continue   // árva kártyás egységár
                anchors += Anchor(a, a, e, null, unit, null)
            }
        }
        val covered = BooleanArray(t.length)
        for (an in anchors) for (i in an.ps until an.e) covered[i] = true
        for (m in ONE_RE.regex.findAll(t)) {
            val a = m.range.first
            val e = m.range.last + 1
            if (inBlock[a] || covered[a]) continue
            anchors += Anchor(a, e, e, ONE_RE.group(m, "pack"), null, null)
        }
        return anchors.sortedBy { it.ps }
    }

    private fun extractName(t: String, an: Anchor, bounds: List<Int>, prev: Anchor?): String? {
        val lo = bounds.filter { it <= an.ps }.maxOrNull() ?: 0
        var raw = t.substring(lo, an.ps).trim()
        raw = SPLIT1.split(raw).last()
        raw = SPLIT2.split(raw).last()             // előtte lévő árak / ár-változatok levágása
        raw = SPLIT3.split(raw).last()             // előző mondat vége
        raw = SPLIT4.split(raw).last()             // előző (ár nélküli) termék leírásának vége
        val words = raw.split(N5).filter { it.isNotEmpty() }
        raw = words.takeLast(16).joinToString(" ")
        for (k in 0 until 6) {
            val new = JUNK.replace(raw, "").trim()
            if (new == raw) break
            raw = new
        }
        if (!HAS_WORD.containsMatchIn(raw)) return null
        val prevName = prev?.name
        if (raw.first().isLowerCase() && prev != null && prev.e >= lo - 1 && !prevName.isNullOrEmpty()) {
            // változat (pl. "Ölz – buci tejes")
            raw = prevName.split(" – ")[0].split(N5).first { it.isNotEmpty() } + " – " + raw
        }
        return raw
    }

    private fun first(s: String?): Double? = s?.let { num(it.split("/")[0]) }

    private fun toProduct(an: Anchor, b: Block?): Product {
        val base = Product(
            name = an.name!!, pack = an.pack, unitPrice = an.unit, price = null, deposit = an.deposit,
            kind = b?.kind ?: (if (an.ar != null) "ar" else null)
        )
        if (b == null) return base.copy(price = an.ar?.let { num(it) })
        val g = b.g
        val p = base.copy(per = g["per"], discountPct = g["pct"]?.toInt(), cardUnitPrice = g["cunit"])
        return when (b.kind) {
            "myspar" -> p.copy(price = first(g["reg"]), cardPrice = num(g.getValue("card")))
            "kupon" -> p.copy(price = first(g["reg"]), couponPrice = num(g.getValue("coupon")))
            "hetvegi" -> p.copy(price = num(g.getValue("weekend")), oldPrice = first(g["reg"]),
                note = "hétvégi ár (péntek–vasárnap)")
            "disc", "plain" -> p.copy(price = num(g.getValue("price")), oldPrice = first(g["old"]))
            "multi" -> p.copy(price = first(g["price"]),
                multiBuy = "${g["n"]} ${g.getValue("uw").lowercase()} esetén ${g["multi"]} Ft/db")
            "bare" -> p.copy(price = num(g.getValue("price")))
            "csak" -> p.copy(price = num(g.getValue("price")),
                note = if (g["tol"] != null) "-tól (legolcsóbb változat ára)" else null)
            else -> p
        }
    }

    /**
     * Egy oldal szövege → a BIZTOS termékek (számolással igazolt ár, vagy a
     * kiszerelés mellé nyomtatott „1 doboz ár: N Ft").
     */
    fun parsePage(text: String): List<Product> {
        var t = norm(text)
        val deposits = DEPOSIT.findAll(t).map { it.range.first to it.groupValues[1] }.toList()
        for (rx in MASKS) for (m in rx.findAll(t).toList()) t = blank(t, m.range.first, m.range.last + 1)
        val blocks = findBlocks(t)
        var anchors = findAnchors(t, blocks)
        val bounds = blocks.map { it.e } + anchors.map { it.e } + GAP.findAll(t).map { it.range.last + 1 }
        var prev: Anchor? = null
        for (an in anchors) {
            an.name = extractName(t, an, bounds, prev)
            an.deposit = deposits.firstOrNull { (s, _) -> s in an.e..an.e + 3 }?.let { num(it.second).toInt() }
            prev = an
        }
        anchors = anchors.filter { it.name != null }
        // egységár-matek alapján párosítás (biztos). A Python tartalék, közelség
        // alapú („bizonytalan") párosítását szándékosan NEM végezzük el.
        for (an in anchors) {
            val imp = impliedPrices(an.pack, an.unit)
            var bestD = 0.0
            var best: Block? = null
            for (b in blocks) {
                val ok = blockPrices(b).any { p -> imp.any { x -> Math.abs(p - x) <= maxOf(1.5, 0.004 * p) } }
                if (!ok) continue
                val d = Math.abs((b.s + b.e) / 2.0 - (an.ps + an.e) / 2.0) + b.used * 150
                if (best == null || d < bestD) { bestD = d; best = b }
            }
            an.block = best
            if (best != null) best.used += 1
        }
        return anchors.filter { it.block != null || it.ar != null }.map { toProduct(it, it.block) }
    }

    /** Egy szórólap összes oldala → (oldalszám, termék), a szórólapon belül ismétlés nélkül. */
    fun parseFlyer(pages: List<String>): List<Pair<Int, Product>> {
        val out = mutableListOf<Pair<Int, Product>>()
        val seen = mutableSetOf<List<Any?>>()
        pages.forEachIndexed { i, pt ->
            for (p in parsePage(pt)) {
                if (seen.add(listOf(p.name.lowercase(), p.pack, p.price, p.cardPrice))) out += (i + 1) to p
            }
        }
        return out
    }

    // ------------------------------------------------------------ felfedezés
    /** `prefetched`: a címkitalálás már letöltötte a néző-oldalt — nem kérjük le újra. */
    data class Flyer(
        val name: String, val region: String, val start: LocalDate, val end: LocalDate, val viewer: String,
        val prefetched: String? = null
    )

    private val LD_JSON = Regex("<script type=\"application/ld\\+json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
    private val VIEWER = Regex("^https://szorolap\\.spar\\.hu/([^/]+)/([^/]+)/")

    private fun isoDate(s: String): LocalDate? =
        try { LocalDate.parse(s.take(10)) } catch (e: Exception) { null }

    /** A www.spar.hu/ajanlatok ld+json katalógusából a MA érvényes szórólapok. */
    fun flyersFromAjanlatok(html: String, today: LocalDate): List<Flyer> {
        val out = mutableListOf<Flyer>()
        for (m in LD_JSON.findAll(html)) {
            val j = try { JSONTokener(m.groupValues[1]).nextValue() as? JSONObject } catch (e: Exception) { null } ?: continue
            if (j.optString("@type") != "OfferCatalog") continue
            val items = j.optJSONArray("itemListElement") ?: continue
            for (k in 0 until items.length()) {
                val it = items.optJSONObject(k) ?: continue
                val mm = VIEWER.find(it.optString("image")) ?: continue
                val s = isoDate(it.optString("startDate")) ?: continue
                val e = isoDate(it.optString("endDate")) ?: continue
                if (today < s || today > e) continue
                out += Flyer(it.optString("name"), mm.groupValues[1], s, e, mm.value)
            }
        }
        return out
    }

    private fun discover(get: (String) -> String, today: LocalDate): List<Flyer> {
        var html: String? = null
        for (attempt in 0 until 3) {                       // Cloudflare néha 403-at ad
            html = try { get(DISCOVERY) } catch (e: Exception) { null }
            if (html != null) break
            if (attempt < 2) Thread.sleep(3000)
        }
        val found = html?.let { flyersFromAjanlatok(it, today) }.orEmpty()
        if (found.isNotEmpty()) return found
        // tartalék: a legutóbbi csütörtök (a SPAR/INTERSPAR újság csütörtöktől szerdáig él)
        val thu = today.minusDays(((today.dayOfWeek.value - 4 + 7) % 7).toLong())
        for (d in listOf(thu, thu.minusDays(7))) {
            val ymd = String.format(Locale.ROOT, "%02d%02d%02d", d.year % 100, d.monthValue, d.dayOfMonth)
            val guessed = mutableListOf<Flyer>()
            for ((region, slug) in listOf("spar" to "1-spar-szorolap", "interspar" to "2-interspar-szorolap")) {
                val u = "https://szorolap.spar.hu/$region/$ymd-$slug/"
                val body = try { get(u) } catch (e: Exception) { null }
                if (body != null) guessed += Flyer(region.uppercase() + " szórólap", region, d, d.plusDays(6), u, body)
            }
            if (guessed.isNotEmpty()) return guessed
        }
        return emptyList()
    }

    private val PAGE_TITLE = Regex("\"pageTitle\":\"([^\"]*)\"")
    private val TITLE_DATES = Regex("$S+\\d{2}\\.\\d{2}(?!\\d).*\$")

    /** „SPAR szórólap 09.24 - 09.30." → „SPAR szórólap" (a dátumot az érvényesség mondja). */
    fun categoryOf(title: String): String = TITLE_DATES.replace(title, "").trim().ifBlank { title.trim() }

    private fun mmdd(d: LocalDate) = String.format(Locale.ROOT, "%02d.%02d", d.monthValue, d.dayOfMonth)

    private fun ft(v: Double): String = Math.round(v).toString()

    /** Python-termék → a felület boltfüggetlen terméke. */
    fun toOfferItem(p: Product, category: String, validity: String, code: String): OfferItem {
        val notes = mutableListOf<String>()
        p.couponPrice?.let { notes += "MySPAR kuponnal ${ft(it)} Ft" }
        when (p.per) {
            null -> {}
            "1 db" -> notes += "az ár 1 darabra értendő"
            else -> notes += "az ár ${p.per}-ra értendő"
        }
        p.multiBuy?.let { notes += it }
        p.note?.let { notes += it }
        p.deposit?.let { notes += "plusz $it Ft visszaváltási díj" }
        return OfferItem(
            store = STORE, name = p.name,
            price = p.price?.let { Math.round(it).toInt() },
            cardPrice = p.cardPrice?.let { Math.round(it).toInt() },
            cardName = if (p.cardPrice != null) "MySPAR kártyával" else "",
            oldPrice = p.oldPrice?.let { Math.round(it).toInt() },
            discount = p.discountPct?.let { "-$it%" }.orEmpty(),
            packSize = p.pack.orEmpty(), unitPrice = p.unitPrice.orEmpty(),
            validity = validity, category = category, code = code, note = notes.joinToString("; ")
        )
    }

    /**
     * Az összes MA érvényes SPAR / INTERSPAR szórólap biztos termékei.
     * Hálózat: 1 felfedező oldal (+ legfeljebb 2 újrapróbálás) + szórólaponként 1 néző-oldal.
     */
    fun download(
        get: (String) -> String,
        progress: (String) -> Unit = {},
        today: LocalDate = LocalDate.now()
    ): List<OfferItem> {
        val out = mutableListOf<OfferItem>()
        for (fl in discover(get, today)) {
            val html = fl.prefetched ?: try { get(fl.viewer) } catch (e: Exception) {
                try { get(fl.viewer) } catch (e2: Exception) { null }
            } ?: continue
            val pages = IPaper.pageTexts(html) ?: continue
            val title = PAGE_TITLE.find(html)?.groupValues?.get(1).orEmpty().ifEmpty { fl.name }
            val category = categoryOf(title)
            progress(category)
            val validity = "${mmdd(fl.start)}-tól ${mmdd(fl.end)}-ig"
            val slug = fl.viewer.trimEnd('/').substringAfterLast('/')
            var lastPage = 0
            var n = 0
            for ((page, p) in parseFlyer(pages)) {
                if (page != lastPage) { lastPage = page; n = 0 }
                n++
                out += toOfferItem(p, category, validity, "spar:$slug:$page:$n")
            }
        }
        return out
    }
}

/**
 * Közös eszközök az iPaper-alapú újságokhoz (Spar, Auchan) — és a Python `re`
 * viselkedésének hű utánzása. A Java/Android reguláris kifejezés más:
 *  - a `\s`, `\S`, `\b`, `\w` a Javában csak ASCII, a Pythonban Unicode.
 *    Ezért itt kifejezett karakterosztályokat használunk (a Python str.isspace()
 *    halmaza, illetve betű/szám/aláhúzás).
 *  - a nevesített csoportokat (`(?<nev>…)`) az Android csak API 34-től tudja név
 *    szerint visszaadni a Kotlinnak — ezért számozott csoportokká alakítjuk.
 */
internal object IPaper {

    /** A Python `str.isspace()` karakterei (ezt jelenti a `\s` a Python `re`-ben). */
    private val WS_CHARS: String = buildString {
        for (c in listOf(0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x85, 0xA0, 0x1680) +
            (0x2000..0x200A) + listOf(0x2028, 0x2029, 0x202F, 0x205F, 0x3000)) append(Char(c))
    }
    val S = "[$WS_CHARS]"
    val NS = "[^$WS_CHARS]"
    const val W_AFTER = "(?![\\p{L}\\p{N}_])"
    const val W_BEFORE = "(?<![\\p{L}\\p{N}_])"

    /** A néző-oldal `"pageTexts":[…]` tömbje (oldalanként egy szöveg); nincs ilyen: null. */
    fun pageTexts(html: String): List<String>? {
        val key = "\"pageTexts\":"
        val i = html.indexOf(key)
        if (i < 0) return null
        val arr = try { JSONTokener(html.substring(i + key.length)).nextValue() as? JSONArray } catch (e: Exception) { null }
            ?: return null
        return (0 until arr.length()).map { if (arr.isNull(it)) "" else arr.optString(it) }
    }

    /** Python-stílusú nevesített csoportok számozott csoportokkal (Android API 26+). */
    class NamedRegex(pattern: String) {
        val regex: Regex
        private val index: Map<String, Int>

        init {
            val sb = StringBuilder()
            val idx = mutableMapOf<String, Int>()
            var n = 0
            var i = 0
            var inClass = false
            while (i < pattern.length) {
                val c = pattern[i]
                when {
                    c == '\\' -> { sb.append(c).append(pattern[i + 1]); i += 2; continue }
                    inClass -> { if (c == ']') inClass = false }
                    c == '[' -> inClass = true
                    c == '(' && pattern.startsWith("(?<", i) && pattern[i + 3] != '=' && pattern[i + 3] != '!' -> {
                        val end = pattern.indexOf('>', i)
                        n++
                        idx[pattern.substring(i + 3, end)] = n
                        sb.append('(')
                        i = end + 1
                        continue
                    }
                    c == '(' && (i + 1 >= pattern.length || pattern[i + 1] != '?') -> n++
                }
                sb.append(c)
                i++
            }
            regex = Regex(sb.toString())
            index = idx
        }

        fun group(m: MatchResult, name: String): String? = m.groups[index.getValue(name)]?.value

        /** Mint a Python `{k: v for k, v in m.groupdict().items() if v}`. */
        fun dict(m: MatchResult): Map<String, String> =
            index.mapNotNull { (k, g) -> m.groups[g]?.value?.takeIf { it.isNotEmpty() }?.let { k to it } }.toMap()
    }

    /** Python `"%g" % v`: 6 értékes jegy, felesleges nullák nélkül. */
    fun pyG(v: Double): String {
        if (v == 0.0) return "0"
        val bd = java.math.BigDecimal(v).round(java.math.MathContext(6, java.math.RoundingMode.HALF_EVEN))
        val exp = bd.precision() - bd.scale() - 1
        if (exp < -4 || exp >= 6) {
            val mant = bd.movePointLeft(exp).stripTrailingZeros().toPlainString()
            return mant + "e" + (if (exp < 0) "-" else "+") + Math.abs(exp).toString().padStart(2, '0')
        }
        return bd.stripTrailingZeros().toPlainString()
    }

    /** „2026-09-24" → „09.24". */
    fun mmdd(iso: String): String = if (iso.length >= 10) iso.substring(5, 7) + "." + iso.substring(8, 10) else iso
}
