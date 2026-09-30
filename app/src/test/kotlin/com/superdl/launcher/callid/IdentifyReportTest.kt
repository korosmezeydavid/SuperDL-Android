package com.superdl.launcher.callid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A „Szám azonosítása" felolvasott összefoglalója és műveletlistája. */
class IdentifyReportTest {

    private fun entry(name: String, type: String = "gyógyszertár", place: String = "Szeged") =
        PhoneIndexEntry("+3662123456", name, type, place)

    private val landline = NumberDescriber.describe("+3662123456")

    @Test
    fun contact_found() {
        val report = IdentifyReport(landline, "Kiss Anna", emptyList())
        assertEquals(
            "Ez a szám már a névjegyeid között van: Kiss Anna. Magyar vezetékes szám, körzet: Szeged.",
            report.summary()
        )
        // Már névjegy: nem kínálunk mentést.
        assertTrue(NumberIdentifyAction.forReport(report).none { it is NumberIdentifyAction.SaveContact })
    }

    @Test
    fun one_osm_hit() {
        val report = IdentifyReport(landline, null, listOf(entry("Arany Patika")))
        assertEquals(
            "Nyilvános cégadat (OpenStreetMap): Arany Patika, gyógyszertár, Szeged. " +
                "OpenStreetMap közösségi adat, lehet elavult. Magyar vezetékes szám, körzet: Szeged.",
            report.summary()
        )
        assertEquals(
            listOf(
                "Mentés a névjegyek közé: Arany Patika",
                "Keresés a nemzeti tudakozóban",
                "Hozzászólások keresése (telefonszam-tudakozo.hu)",
                "Eredmény újra",
                "Vissza a hívásnaplóhoz"
            ),
            NumberIdentifyAction.forReport(report).map { it.label }
        )
    }

    @Test
    fun five_osm_hits_lists_three_and_more() {
        val hits = (1..5).map { entry("Cég $it", "iroda", "") }
        val report = IdentifyReport(landline, null, hits)
        assertEquals(
            "Nyilvános cégadat (OpenStreetMap): Cég 1, iroda; Cég 2, iroda; Cég 3, iroda, és még 2. " +
                "OpenStreetMap közösségi adat, lehet elavult. Magyar vezetékes szám, körzet: Szeged.",
            report.summary()
        )
        assertEquals(3, NumberIdentifyAction.forReport(report).count { it is NumberIdentifyAction.SaveContact })
    }

    @Test
    fun nothing_found() {
        val report = IdentifyReport(NumberDescriber.describe("+36201234567"), null, emptyList())
        assertEquals(
            "Nevet nem találtam. Ez a szám nincs a névjegyeid között, és a nyilvános cégadatokban " +
                "sem szerepel. Magánszemélyek mobilszáma csak a nemzeti tudakozóban kereshető, és csak " +
                "ha a tulajdonosa hozzájárult. Amit tudok: Magyar mobilszám, eredetileg a Yettel " +
                "számtartományából — a szám azóta átvihető másik szolgáltatóhoz.",
            report.summary()
        )
    }

    @Test
    fun hidden_and_short_have_no_directory_search() {
        val hidden = IdentifyReport(NumberDescriber.describe("-2"), null, emptyList())
        assertEquals("Rejtett számot nem lehet azonosítani.", hidden.summary())
        assertEquals(
            listOf("Eredmény újra", "Vissza a hívásnaplóhoz"),
            NumberIdentifyAction.forReport(hidden).map { it.label }
        )
        val short = IdentifyReport(NumberDescriber.describe("112"), null, emptyList())
        assertEquals("A 112 az egységes segélyhívó szám.", short.summary())
        assertEquals(
            "Ez nem érvényes telefonszám: hiányos, elírt, vagy nem illik egyik ismert számtervbe sem.",
            IdentifyReport(NumberDescriber.describe("+36123"), null, emptyList()).summary()
        )
        assertEquals(
            listOf("Eredmény újra", "Vissza a hívásnaplóhoz"),
            NumberIdentifyAction.forReport(short).map { it.label }
        )
    }
}
