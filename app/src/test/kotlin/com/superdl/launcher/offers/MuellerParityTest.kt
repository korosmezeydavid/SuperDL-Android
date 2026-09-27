package com.superdl.launcher.offers

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * EGYEZÉS A WINDOWS-OLDALI MÜLLER-OLVASÓVAL (`mueller.py`).
 *
 * A minták (a prospektusok oldala és a két PDF) NEM kerülnek a tárolóba: a
 * próba kimarad, ha nincsenek meg. `akcio_minta/mueller/`:
 *   index.json                 — URL → lementett fájl
 *   elvart.json                — a Windows-oldal eredménye (`letolt`, magyar kulcsok)
 *   pdfminer_<sha1[:16]>.txt   — a pdfminer `extract_text` pontos kimenete PDF-enként
 *
 * 1. `sorokUgyanaz`: a sor-alapú értelmező ([MuellerOffers.parseLines]) a
 *    pdfminer-szövegen BETŰRE ugyanazt adja, mint a Python.
 * 2. `pdfboxbol`: a teljes út a PDFBox-szövegből. A PDFBox más sorrendben és
 *    más dobozokban adja a szöveget, ezért itt mérünk: hány termék jön ki
 *    ugyanazzal a névvel, árral és régi árral — és hogy egyetlen ROSSZ ár
 *    sincs (egy rossz ár rosszabb, mint egy hiányzó termék).
 */
class MuellerParityTest {

    private val dir = File("src/test/resources/akcio_minta/mueller")

    private fun load(f: File): String = f.readText(Charsets.UTF_8)

    private fun sha16(b: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }.substring(0, 16)

    private fun intOrNull(o: JSONObject, k: String): Int? = if (!o.has(k) || o.isNull(k)) null else o.getInt(k)

    private fun expected(): List<OfferItem> {
        val a = JSONArray(load(File(dir, "elvart.json")))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            OfferItem(
                store = o.getString("bolt"), name = o.getString("nev"),
                price = intOrNull(o, "ar"), cardPrice = intOrNull(o, "kartyas_ar"),
                cardName = o.getString("kartya_nev"), oldPrice = intOrNull(o, "regi_ar"),
                discount = o.getString("kedvezmeny"), packSize = o.getString("kiszereles"),
                unitPrice = o.getString("egysegar"), validity = o.getString("ervenyes"),
                category = o.getString("kategoria"), code = o.getString("kod"),
                note = o.getString("megjegyzes"), group = o.getString("csoport"), url = o.getString("url")
            )
        }
    }

    private fun ready(): Boolean =
        File(dir, "index.json").exists() && File(dir, "elvart.json").exists()

    /** A lementett fájlok a letöltő helyett. */
    private fun files(): Pair<(String) -> String, (String) -> ByteArray> {
        val index = JSONObject(load(File(dir, "index.json")))
        val file = { url: String -> File(dir, index.optString(url).ifEmpty { throw java.io.IOException("nincs: $url") }) }
        return Pair({ url: String -> load(file(url)) }, { url: String -> file(url).readBytes() })
    }

    @Test
    fun sorokUgyanaz() {
        assumeTrue("nincs Müller-minta", ready())
        val (get, getBytes) = files()
        val lfs = MuellerOffers.leaflets(get(MuellerOffers.PAGE))
        assertEquals(listOf("drogerie", "parfuemerie"), lfs.map { it.first })
        val items = mutableListOf<OfferItem>()
        for ((kind, url) in lfs) {
            val txt = File(dir, "pdfminer_${sha16(getBytes(url))}.txt")
            assumeTrue("nincs pdfminer-szöveg: ${txt.name}", txt.exists())
            val text = load(txt)
            items += MuellerOffers.parseLines(text, MuellerOffers.LEAFLET_NAMES.getValue(kind), MuellerOffers.validity(text))
        }
        val exp = expected()
        println("MÜLLER sorokból: Kotlin ${items.size}, Python ${exp.size}")
        for (k in 0 until minOf(exp.size, items.size)) assertEquals("$k. termék", exp[k], items[k])
        assertEquals(exp.size, items.size)
    }

    @Test
    fun segedek() {
        assertEquals(listOf("a", "", "b", "c", "d"), MuellerOffers.pySplitLines("a\r\n\u000Cb\rc\nd\n"))
        assertEquals("Dolce&Gabbana", MuellerOffers.pyTitle("DOLCE&GABBANA"))
        assertEquals("Burt'S Bees", MuellerOffers.pyTitle("BURT'S BEES"))
        assertEquals("Annemarie Börlind", MuellerOffers.pyTitle("ANNEMARIE BÖRLIND"))
        assertEquals("09.28–10.04.", MuellerOffers.validity("MÜLLER AJÁNLATOK 2026£09£28ÅTÓL 10£04ÅIG"))
        assertEquals("09.28–10.11.", MuellerOffers.validity("A PARFÜMÉRIA VILÁGA 2026.09.28-TÓL 10.11-IG"))
        val b = "https://mueller-dam-bucket.s3.eu-central-1.amazonaws.com/prod/public/hu/prospektusok"
        val html = "\"$b/drogerie/D07/Drogerie_12_Seiten\\\" \"$b/drogerie/D07/Drogerie_12_Seiten\" " +
            "\"$b/drogerie/D07/InlineBanner_D07\" \"$b/spielware/SPW/Spielwaren_8_Seiten\" \"$b/parfuemerie/PF04/PF04_6_Seiten?x=1\""
        assertEquals(
            listOf("drogerie" to "$b/drogerie/D07/Drogerie_12_Seiten", "parfuemerie" to "$b/parfuemerie/PF04/PF04_6_Seiten"),
            MuellerOffers.leaflets(html)
        )
    }

    @Test
    fun pdfboxbol() {
        assumeTrue("nincs Müller-minta", ready())
        val (get, getBytes) = files()
        val steps = mutableListOf<String>()
        val t0 = System.currentTimeMillis()
        val items = MuellerOffers.download(get, getBytes, PdfTestText::pages) { steps += it }
        println("MÜLLER PDFBox-ból: ${items.size} termék, ${System.currentTimeMillis() - t0} ms, lépések: $steps")
        val exp = expected()
        fun key(o: OfferItem) = Triple(o.name, o.price, o.oldPrice)
        val expKeys = exp.map { key(it) }.toMutableList()
        val pricesByName = exp.groupBy { it.name.lowercase() }.mapValues { e -> e.value.map { it.price to it.oldPrice }.toSet() }
        val expPrices = exp.map { it.price to it.oldPrice }.toSet()
        var matched = 0
        val extra = mutableListOf<OfferItem>()
        val wrong = mutableListOf<String>()
        for (it in items) {
            if (expKeys.remove(key(it))) { matched++; continue }
            extra += it
            val byName = pricesByName[it.name.lowercase()]
            if (byName != null && (it.price to it.oldPrice) !in byName) wrong += "ROSSZ ÁR: ${it.name} ${it.price}/${it.oldPrice} ≠ $byName"
            else if (byName == null && (it.price to it.oldPrice) !in expPrices) wrong += "ISMERETLEN ÁR: ${it.name} ${it.price}/${it.oldPrice}"
        }
        println("MÜLLER egyezik (név+ár+régi ár): $matched / ${exp.size}; többlet: ${extra.size}; rossz ár: ${wrong.size}")
        expKeys.forEach { println("  HIÁNYZIK: ${it.first} | ${it.second}/${it.third}") }
        extra.forEach { println("  TÖBBLET:  ${it.name} | ${it.price}/${it.oldPrice}") }
        wrong.forEach { println("  $it") }
        // az egyező termékek többi mezője: a kategória a fejezetcímek sorrendjén
        // múlik (a pdfminer a lap közepén álló címet máshová sorolja) — csak kiírjuk
        val byKey = exp.associateBy { key(it) }
        val fieldDiff = mutableListOf<String>()
        var catDiff = 0
        for (it in items) {
            val e = byKey[key(it)] ?: continue
            // Kutyapiszok: a pdfminer-szövegben a zagyva sor elején egy \u000B
            // (függőleges tab) áll, a Python `splitlines` ott üres sort lát, és
            // a „80 db" kiszerelés elvész. Mi a soron belüli sortörés-jelet
            // szóközzé tesszük (a PDF szövegrétegében ott a „80 db").
            val e2 = if (e.name == "Kutyapiszok" && e.packSize == "") e.copy(packSize = "80 db") else e
            if (e2.copy(category = "") != it.copy(category = "")) fieldDiff += "MEZŐ: $it ≠ $e"
            if (e.category != it.category) { catDiff++; println("  KATEGÓRIA: ${it.name}: ${it.category} (Python: ${e.category})") }
        }
        fieldDiff.forEach { println("  $it") }
        println("MÜLLER eltérő kategória: $catDiff, egyéb eltérő mező: ${fieldDiff.size}")

        // ⚠️ rossz ár egy sem lehet
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
        // elért szint (2026-09-28-i újságok): mind a 87 egyezik, többlet nincs
        assertTrue("egyezik: $matched / ${exp.size}", matched >= 87)
        assertTrue("többlet: ${extra.size}", extra.isEmpty())
        assertTrue(fieldDiff.joinToString("\n"), fieldDiff.isEmpty())
        assertTrue("eltérő kategória: $catDiff", catDiff <= 4)
        assertTrue(items.all { it.store == MuellerOffers.STORE && it.price != null && it.oldPrice != null && it.price!! < it.oldPrice!! })
    }
}
