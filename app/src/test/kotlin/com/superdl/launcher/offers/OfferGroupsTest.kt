package com.superdl.launcher.offers

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Termékcsoportok: a Kotlin-átiratnak MINDEN mintaterméket ugyanoda kell
 * sorolnia, mint a Windows-oldali `csoport.besorol()`-nak. A minta
 * ([név, kategória, bolt, várt csoport] valódi termékeken) git-ignored
 * mappában van.
 */
class OfferGroupsTest {

    private val file = File("src/test/resources/akcio_minta/csoport/esetek.json")

    @Test
    fun ugyanaAPythonnal() {
        assumeTrue("nincs csoport-minta", file.exists())
        val cases = JSONArray(file.readText(Charsets.UTF_8))
        val bad = mutableListOf<String>()
        val t0 = System.nanoTime()
        for (i in 0 until cases.length()) {
            val c = cases.getJSONArray(i)
            val got = OfferGroups.classify(c.getString(0), c.getString(1), c.getString(2))
            if (got != c.getString(3)) {
                bad += "${c.getString(0)} | ${c.getString(1)} | ${c.getString(2)}: " +
                    "várt ${c.getString(3)}, kapott $got"
            }
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("Csoportok: ${cases.length() - bad.size}/${cases.length()} egyezik ($ms ms)")
        if (bad.isNotEmpty()) {
            bad.take(30).forEach { println("  $it") }
            fail("${bad.size} eltérés a Pythontól")
        }
    }

    @Test
    fun alapesetek() {
        assertEquals("Édesség és snack", OfferGroups.classify("Milka Tejszelet"))
        assertEquals("Édesség és snack", OfferGroups.classify("Almás pite"))  // =pite az édességeknél
        assertEquals(OfferGroups.OTHER, OfferGroups.classify("Borsó"))  // =bor: nem ital
        assertEquals("Hús, hal, felvágott", OfferGroups.classify("Lecsókolbász"))
        assertEquals("Hús, hal, felvágott", OfferGroups.classify("Házi tepertő"))
        assertEquals(OfferGroups.OTHER, OfferGroups.classify("Macskaeledel", store = "Pepco"))
        assertEquals("Ruházat és cipő", OfferGroups.classify("Macskamintás ruha", store = "Pepco"))
        assertEquals("Drogéria és szépségápolás",
            OfferGroups.classify("Citromos tusfürdő", "Testápolás", "Rossmann"))
        assertEquals("Drogéria és szépségápolás", OfferGroups.classify("Valami", "", "dm"))
        assertEquals("Drogéria és szépségápolás", OfferGroups.classify("Valami", "", "Müller"))
        assertEquals(OfferGroups.OTHER, OfferGroups.GROUPS.last())
        // szóhatár a Python szerint: az „ø" betű, a „_" szókarakter, a „™"
        // (kisbetűsítés UTÁN bontva) „TM" – egyik sem szóhatár
        assertEquals("Tejtermék és tojás", OfferGroups.classify("Sajt"))
        assertEquals(OfferGroups.OTHER, OfferGroups.classify("Sajtø"))
        assertEquals(OfferGroups.OTHER, OfferGroups.classify("x_sajt"))
        assertEquals(OfferGroups.OTHER, OfferGroups.classify("Vörös bor™"))
    }

    @Test
    fun ekezetNelkulMintPython() {
        assertEquals("rantott", OfferGroups.plain("Rántott"))
        assertEquals("oszi fuzfa", OfferGroups.plain("ŐSZI FŰZFA"))
        assertEquals("1⁄2 l", OfferGroups.plain("½ l"))
    }

    @Test
    fun darabszamok() {
        val items = listOf(
            OfferItem(store = "Aldi", name = "Trapista sajt"),
            OfferItem(store = "Aldi", name = "Szobapáfrány"),
            OfferItem(store = "Aldi", name = "Gouda sajt"),
            OfferItem(store = "Aldi", name = "Bármi", group = "Pékáru")
        )
        assertEquals(
            listOf("Tejtermék és tojás" to 2, "Pékáru" to 1, OfferGroups.OTHER to 1),
            OfferGroups.counts(items)
        )
    }
}
