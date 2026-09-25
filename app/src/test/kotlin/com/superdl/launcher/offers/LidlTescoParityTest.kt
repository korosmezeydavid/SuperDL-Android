package com.superdl.launcher.offers

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * EGYEZÉS A WINDOWS-OLDALI PDF-OLVASÓKKAL (Lidl, Tesco).
 *
 * A mintafájlok (a boltok PDF-jei) NEM kerülnek a tárolóba: a próba kimarad,
 * ha nincsenek meg. Lementésük és a Python-oldali elvárt eredmény:
 *   akcio_minta/lidl/  — overview.json, index.json (URL → fájl), lidl_N.pdf,
 *                        expected_python.json (lidl.py `termekek`, pdfminer)
 *   akcio_minta/tesco/ — katalogusok_next_data.json, hm.pdf,
 *                        expected_python.json (tesco_akciok.py `parse_leaflet_text`, pypdf)
 *
 * A két oldal más PDF-szövegkinyerőt használ, ezért a termékszám eltér. Amit
 * megkövetelünk:
 *  - Lidl: újságonként a Python-szám 90–125 %-a; a cikkszámmal párosított
 *    termékeknél a név és az ár (rendes + appos) EGYEZIK — kivéve a lent
 *    felsorolt, a PDF képén kézzel ellenőrzött eseteket, ahol a Python téved.
 *  - Tesco: legalább a Python-szám 85 %-a; a névvel párosított termékek ára
 *    egyezik — kivéve a kézzel ellenőrzött eltéréseket.
 */
class LidlTescoParityTest {

    private val dir = File("src/test/resources/akcio_minta")

    private fun load(f: File): String = f.readText(Charsets.UTF_8)

    /** Az eredmény gépi összevetéshez (a build mappába, nem a tárolóba). */
    private fun dump(name: String, items: List<OfferItem>) {
        val out = File("build/akcio_kotlin").apply { mkdirs() }
        File(out, name).writeText(JSONArray().apply { items.forEach { put(it.toJson()) } }.toString(1), Charsets.UTF_8)
    }

    // ------------------------------------------------------------------ LIDL

    /** cikkszám → (név vagy null, ár, appos ár, miért tér el a Python). A PDF képén ellenőrizve. */
    private val lidlTruth: Map<String, Truth> = mapOf(
        // a „Szuper ár!" / „-NN%" rendes akciós ár — a Python appos árnak olvassa
        "83248" to Truth(null, 2390, null, "Piros burgonya: Szuper ár (10 kg × 239 Ft/kg)"),
        "6414733" to Truth(null, 499, null, "Floralys papírtörlő: -33%, 755 Ft helyett"),
        "6403162" to Truth(null, 169, null, "Belga párna: -32%, 249 Ft helyett"),
        "3161" to Truth(null, 299, null, "Borsodi: -25%, 399 Ft helyett"),
        "2450" to Truth(null, 349, null, "Fin Carré: -30%, 499 Ft helyett"),
        "55413" to Truth(null, 1299, null, "Gömbfejű krizantém: rendes ár"),
        "65136" to Truth(null, 999, null, "Vitasia Wok zöldségmix: rendes ár"),
        "5235925" to Truth(null, 1499, null, "Vitasia garnélarák: rendes ár"),
        "6405302" to Truth(null, 919, null, "Kometa húsgolyó: -29%, 1299 Ft helyett"),
        "29353" to Truth(null, 959, null, "Floralys toalettpapír: -13%, 1115 Ft helyett"),
        "516940" to Truth(null, 4999, null, "Kézi robotgép: Szuper ár"),
        // a Python a SZOMSZÉD termék árát veszi (a pdfminer sorrendje miatt)
        "6415906" to Truth(null, 1199, null, "Pápai Hús: 1199 (300 g × 3997); a 2990 a szalámivégé"),
        "137390" to Truth(null, 1889, 1199, "Old Holland: Plus 1199 / 1889; a 2299/1749 a Pilos Goudáé"),
        "1020956" to Truth(null, 199, null, "Jogobella: Szuper ár 199, nincs Plus-ár"),
        "6420268" to Truth(null, 549, null, "Nashiday: a 439 a Kronenbourg sör ára"),
        "211843" to Truth(null, 899, 669, "Kania ketchup: Plus 669 (552 g × 1212) / 899"),
        "119564" to Truth(null, 699, null, "Maribel: 699 (250 g × 2796); az 1299 a gyömbér shoté"),
        "60164" to Truth(null, 3999, null, "Ünnepi csokor: 3999; a 14999 nem az övé"),
        "497016" to Truth(null, 24999, null, "Akkus ütvecsavarozó: 24999; a 6999 a nyomatékkulcsé"),
        "500024" to Truth(null, 3999, null, "Lupilu cipő: 3999; a 2499 a pulóveré"),
        "506338" to Truth(null, 24999, null, "Silvercrest porszívó: 24999; a 39999 a Bosch-é"),
        "506416" to Truth(null, 11999, null, "Parkside vésőkalapács: 11999; a 3999 a nadrágé"),
        // a Python az ÁTHÚZOTT eredeti árat („Eredeti országos ár") mondaná
        "516933" to Truth(null, 6999, null, "Vízforraló: -22%, áthúzva 8999"),
        "516932" to Truth(null, 6999, null, "Kenyérpirító: -22%, áthúzva 8999"),
        "517448" to Truth(null, 1799, null, "Hőtartó pohár: -28%, áthúzva 2499"),
        "525398" to Truth(null, 9999, null, "LED-es állólámpa: -16%, áthúzva 11999"),
        "494800" to Truth(null, 12999, null, "Elektromos gyalu: -13%, áthúzva 14999"),
        "497013" to Truth(null, 2499, null, "Ütvecsavarozó kiegészítő: -16%, áthúzva 2999"),
        "494754" to Truth(null, 2499, null, "Spiráltömlő: -16%, áthúzva 2999"),
        "517358" to Truth(null, 3999, null, "Indítókábel: -20%, áthúzva 4999"),
        // a Python a rendes árat elveszti vagy rosszat ad mellé
        "6418919" to Truth(null, 1999, 1499, "Roppant 400 g: Plus 1499 / 1999"),
        "145229" to Truth(null, 1499, 1119, "Alaszkai tőkehal: Plus 1119 / 1499"),
        "3005" to Truth(null, 159, null, "Argus sör: -27%, 219 Ft helyett (500 ml × 318)"),
        // szándékos eltérés: „Minden második -71%": egy darab 699, a 199 a megjegyzésben
        "80711" to Truth(null, 699, null, "Mangó: egy darab 699 Ft; a 2. darab 199 Ft a megjegyzésben"),
        // névbeli eltérések
        "6076" to Truth("Friss csirke egészcomb", 899, null,
            "a PDF-ben a HÚSFARM márkanév az árdobozhoz tapadt: a név márka nélkül marad"),
        "6417174" to Truth("Jägermeister Orange", 5999, null, "az „Alkoholtartalom: 33%” a megjegyzésbe kerül, nem a névbe"),
        "516304" to Truth("Parkside Magasnyomású tisztító*", 16999, null, "a Python neve a garancia-matricából jön"),
        "515014" to Truth("Kisgyermek foglalkoztatókönyv", 1499, null, "a Python neve csonka: Kisgyermek")
    )

    data class Truth(val name: String?, val price: Int?, val card: Int?, val why: String)

    @Test
    fun lidlUgyanaz() {
        val ld = File(dir, "lidl")
        val idxF = File(ld, "index.json")
        val expF = File(ld, "expected_python.json")
        assumeTrue("nincs Lidl-minta", idxF.exists() && expF.exists())
        val index = JSONObject(load(idxF))
        val all = LidlOffers.flyers(load(File(ld, index.getString(LidlOffers.OVERVIEW))))
        val flyers = LidlOffers.current(all, LocalDate.parse("2026-09-25"))
        val exp = JSONObject(load(expF)).getJSONArray("flyers")
        assertEquals("ugyanazok az újságok", exp.length(), flyers.size)
        val national = LidlOffers.current(all, LocalDate.parse("2026-09-25"), includeRegional = false)
        assertTrue(national.isNotEmpty() && national.none { it.regional })
        assertTrue(flyers.filter { it.regional }.all { it.category.startsWith("Regionális akciók") })

        var agree = 0
        var truthOk = 0
        val problems = mutableListOf<String>()
        val allItems = mutableListOf<OfferItem>()
        for (fi in 0 until exp.length()) {
            val ef = exp.getJSONObject(fi)
            val f = flyers[fi]
            assertEquals(ef.getString("url"), f.pdfUrl)
            val t0 = System.currentTimeMillis()
            val pages = PdfTestText.pages(File(ld, index.getString(f.pdfUrl)).readBytes())
            val t1 = System.currentTimeMillis()
            val items = LidlOffers.products(pages, f.category, LidlOffers.validityOf(f.start, f.end))
            val t2 = System.currentTimeMillis()
            allItems += items
            val py = ef.getJSONArray("items")
            println("LIDL ${f.category}: Kotlin ${items.size}, Python ${py.length()} " +
                "(${pages.size} oldal, szöveg ${t1 - t0} ms, értelmezés ${t2 - t1} ms)")
            assertTrue("${f.category}: túl kevés (${items.size} < 90% of ${py.length()})", items.size >= 0.9 * py.length())
            assertTrue("${f.category}: gyanúsan sok (${items.size})", items.size <= 1.25 * py.length() + 3)
            val byCode = HashMap<String, OfferItem>()
            for (it in items) Regex("\\d{4,7}").findAll(it.code).forEach { m -> byCode.putIfAbsent(m.value, it) }
            for (i in 0 until py.length()) {
                val p = py.getJSONObject(i)
                val code = p.getString("kod")
                val k = byCode[code] ?: continue
                val pPrice = if (p.isNull("ar")) null else p.getInt("ar")
                val pCard = if (p.isNull("kartyas_ar")) null else p.getInt("kartyas_ar")
                val t = lidlTruth[code]
                if (t != null) {
                    if ((t.name == null || t.name == k.name) && t.price == k.price && t.card == k.cardPrice) truthOk++
                    else problems += "IGAZ $code: ${k.name} ${k.price}/${k.cardPrice} ≠ ${t.name} ${t.price}/${t.card}"
                } else if (k.name == p.getString("nev") && k.price == pPrice && k.cardPrice == pCard) {
                    agree++
                } else {
                    problems += "ELTÉR $code: K=${k.name} | ${k.price} | ${k.cardPrice}  P=${p.getString("nev")} | $pPrice | $pCard"
                }
            }
        }
        dump("lidl.json", allItems)
        println("LIDL cikkszámmal párosítva: $agree egyezik, $truthOk a kézzel ellenőrzött igazsággal egyezik, ${problems.size} hiba")
        problems.forEach { println("  $it") }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun lidlLetoltesUgyanazt() {
        val ld = File(dir, "lidl")
        val idxF = File(ld, "index.json")
        assumeTrue("nincs Lidl-minta", idxF.exists())
        val index = JSONObject(load(idxF))
        val file = { url: String -> File(ld, index.optString(url).ifEmpty { throw java.io.IOException("nincs: $url") }) }
        val steps = mutableListOf<String>()
        val items = LidlOffers.download(
            get = { load(file(it)) }, getBytes = { file(it).readBytes() },
            pdfPages = PdfTestText::pages, progress = { steps += it }, today = LocalDate.parse("2026-09-25")
        )
        println("LIDL letöltés: ${items.size} termék, lépések: $steps")
        assertTrue(items.size > 300)
        // egy cikkszám csak egyszer (a nonfood-újság a fő újság oldalait is tartalmazza)
        val codes = items.flatMap { Regex("\\d{4,7}").findAll(it.code).map { m -> m.value }.toList() }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(items.all { it.price != null || it.cardPrice != null })
        assertTrue(items.all { it.cardPrice == null || it.cardName == LidlOffers.CARD })
        assertTrue(items.all { it.oldPrice == null || it.oldPrice!! > (it.price ?: 0) })
    }

    // ----------------------------------------------------------------- TESCO

    /** kisbetűs név → az igazság (a PDF képén ellenőrizve) és hogy miért tér el a Python. */
    private val tescoTruth: Map<String, Truth> = mapOf(
        // „Együtt:" csempe: a Python a KÉT termék együttes árát adja az elsőnek
        "tesco finest prosciutto cotto pizza paradicsomos alappal" to
            Truth(null, 2049, null, "saját ára 2049 (380 g × 5392 Ft/kg); a 2199/2548 a pizza + üdítő együtt"),
        "tesco rakott tészta csirkével és baconnel" to
            Truth(null, 2639, null, "saját ára 2639 (800 g × 3299 Ft/kg); a 3599/4538 a tészta + pezsgő együtt"),
        "virágos-leveles függőkoszorú 30 cm" to Truth(null, 5999, 4199, "Clubcard 4199, nélküle 5999"),
        "qikfood instant alap" to Truth(null, 499, 359, "Clubcard 359, nélküle 499; a 999 a ketchupé")
    )

    private val tescoNow = Instant.parse("2026-09-25T10:00:00Z")

    @Test
    fun tescoUgyanaz() {
        val td = File(dir, "tesco")
        val pdfF = File(td, "hm.pdf")
        val expF = File(td, "expected_python.json")
        val ndF = File(td, "katalogusok_next_data.json")
        assumeTrue("nincs Tesco-minta", pdfF.exists() && expF.exists() && ndF.exists())
        val lf = TescoOffers.pick(TescoOffers.leaflets(load(ndF)), tescoNow)!!
        assertEquals("HM", lf.type)
        assertEquals("09.24-tól 09.30-ig", lf.validity)
        val t0 = System.currentTimeMillis()
        val pages = PdfTestText.pages(pdfF.readBytes())
        val t1 = System.currentTimeMillis()
        val items = TescoOffers.products(pages, lf.title, lf.validity)
        val t2 = System.currentTimeMillis()
        dump("tesco.json", items)
        val py = JSONObject(load(expF)).getJSONArray("HM")
        val byName = LinkedHashMap<String, JSONObject>()
        for (i in 0 until py.length()) py.getJSONObject(i).let { byName.putIfAbsent(it.getString("name").lowercase(), it) }
        println("TESCO ${lf.title}: Kotlin ${items.size}, Python ${py.length()} (egyedi név: ${byName.size}); " +
            "${pages.size} oldal, szöveg ${t1 - t0} ms, értelmezés ${t2 - t1} ms")
        assertTrue("túl kevés: ${items.size} < 85% of ${py.length()}", items.size >= 0.85 * py.length())

        var agree = 0
        var truthOk = 0
        val problems = mutableListOf<String>()
        for (k in items) {
            val key = k.name.lowercase()
            val p = byName[key] ?: continue
            val pCard = if (p.isNull("clubcard_price")) null else p.getInt("clubcard_price")
            val pPrice = if (pCard != null) (if (p.isNull("regular_price")) null else p.getInt("regular_price"))
            else (if (p.isNull("price")) null else p.getInt("price"))
            val t = tescoTruth[key]
            if (t != null) {
                if (t.price == k.price && t.card == k.cardPrice) truthOk++
                else problems += "IGAZ ${k.name}: ${k.price}/${k.cardPrice} ≠ ${t.price}/${t.card}"
            } else if (k.price == pPrice && k.cardPrice == pCard) {
                agree++
            } else {
                problems += "ELTÉR ${k.name}: K=${k.price}/${k.cardPrice}  P=$pPrice/$pCard"
            }
        }
        println("TESCO névvel párosítva: $agree egyezik, $truthOk a kézzel ellenőrzött igazsággal egyezik, ${problems.size} hiba")
        problems.forEach { println("  $it") }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue(items.all { it.price != null || it.cardPrice != null })
        assertTrue(items.all { it.cardPrice == null || it.cardName == TescoOffers.CARD })
    }

    @Test
    fun tescoLetoltesUgyanazt() {
        val td = File(dir, "tesco")
        val pdfF = File(td, "hm.pdf")
        val ndF = File(td, "katalogusok_next_data.json")
        assumeTrue("nincs Tesco-minta", pdfF.exists() && ndF.exists())
        val html = "<html><script id=\"__NEXT_DATA__\" type=\"application/json\">${load(ndF)}</script></html>"
        val lf = TescoOffers.pick(TescoOffers.leaflets(html), tescoNow)!!
        val asked = mutableListOf<String>()
        val items = TescoOffers.download(
            get = { asked += it; html },
            getBytes = { assertEquals(lf.pdfUrl, it); pdfF.readBytes() },
            pdfPages = PdfTestText::pages, now = tescoNow
        )
        assertEquals(listOf(TescoOffers.LIST_URL), asked)
        assertTrue(items.size > 200)
        assertEquals(items.size, items.map { it.name.lowercase() }.toSet().size)
        assertTrue(items.all { it.category == "Hipermarket újság" })
    }

    @Test
    fun szovegJelolo() {
        // a jelölő formája: ezt írja az alkalmazás is, a gyűjtő ezt olvassa vissza
        val t = PdfTextSettings.wordMark(12.34f, 700f, -5f, 4.56f) + "Ár 199 Ft"
        assertEquals("\u001F12.3,700.0,-5.0,4.6\u001FÁr 199 Ft", t)
        assertEquals("Ár 199 Ft", PdfTextSettings.plain(t))
        val segs = PdfLayout.segments(t)
        assertEquals(1, segs.size)
        assertEquals("Ár 199 Ft", segs[0].text)
        assertEquals(7.3, segs[0].x0, 0.01)
    }

    @Test
    fun lidlEgysegarSzamtan() {
        assertEquals(379.05, LidlOffers.expected("150 g", "1 kg = 2 527 Ft")!!.first, 0.01)
        assertEquals(2600.0, LidlOffers.expected("4 x 2 l", "1 l = 325 Ft")!!.first, 0.01)
        assertEquals(180.0, LidlOffers.expected("100 db", "1 db = 1,8 Ft")!!.first, 0.01)
        assertEquals(null, LidlOffers.expected("150 g", "1 l = 300 Ft"))
        assertEquals("Tesco finest", TescoOffers.clean("Tesco fi nest"))
        assertEquals("Riceland", TescoOffers.clean("R i c e l a n d"))
    }
}
