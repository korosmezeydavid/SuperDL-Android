package com.superdl.launcher.callid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Az OSM-cégindex beolvasása és keresése.
 *
 * MIÉRT: a bináris keresés rendezetlen vagy hibás sorokon csendben rossz
 * (vagy semmilyen) választ adna — egy rosszul felolvasott cégnév pedig
 * rosszabb, mint a „nem találtam".
 */
class PhoneIndexTest {

    // A tabulátorok \t-vel írva: a szerkesztők szóközzé alakíthatják a valódi tabot.
    private val sample = listOf(
        "# forrás: © OpenStreetMap-közreműködők, ODbL 1.0 — letöltve: 2026-09-30T00:00:00Z",
        "+3612345678\tArany Patika\tgyógyszertár\tBudapest",
        "+3612345678\tArany Patika Webshop\tdrogéria\t",
        "+36301234567\tSzegedi Rendelő\torvosi rendelő\tSzeged",
        "+3662123456\tSzegedi Rendelő\torvosi rendelő",
        "ez nem sor",
        "+36999\ttúl rövid kulcs\tx\ty",
        "+36AB1234567\tbetű a számban\tx\ty",
        "+3611111111\t\tnév nélkül\tBudapest"
    ).joinToString("\n")

    @Test
    fun header_is_skipped_and_kept_as_source_note() {
        val index = PhoneIndex.parse(sample)
        assertEquals(4, index.size)
        assertEquals(
            "© OpenStreetMap-közreműködők, ODbL 1.0 — letöltve: 2026-09-30T00:00:00Z",
            index.sourceNote
        )
    }

    @Test
    fun multiple_entries_for_one_number() {
        val hits = PhoneIndex.parse(sample).lookup("+3612345678")
        assertEquals(listOf("Arany Patika", "Arany Patika Webshop"), hits.map { it.name })
        assertEquals("Arany Patika, gyógyszertár, Budapest", hits[0].speak())
        assertEquals("Arany Patika Webshop, drogéria", hits[1].speak())
    }

    @Test
    fun missing_place_column_is_tolerated() {
        val hits = PhoneIndex.parse(sample).lookup("+3662123456")
        assertEquals(1, hits.size)
        assertEquals("", hits[0].place)
        assertEquals("Szegedi Rendelő, orvosi rendelő", hits[0].speak())
    }

    @Test
    fun malformed_lines_and_misses() {
        val index = PhoneIndex.parse(sample)
        assertTrue(index.lookup("+3611111111").isEmpty())
        assertTrue(index.lookup("+36999").isEmpty())
        assertTrue(index.lookup("+3699999999").isEmpty())
        assertTrue(index.lookup(null).isEmpty())
        assertTrue(index.lookup("").isEmpty())
        assertTrue(PhoneIndex.EMPTY.lookup("+3612345678").isEmpty())
    }

    @Test
    fun unsorted_input_still_found() {
        val index = PhoneIndex.parse(
            sequenceOf(
                "+3690000000\tZ\t\t",
                "+3610000000\tA\t\t",
                "+3650000000\tM\t\t",
                "+3610000000\tA2\t\t",
                "+3610000000\tA\tduplikátum\t"
            )
        )
        assertEquals(listOf("A", "A2"), index.lookup("+3610000000").map { it.name })
        assertEquals(listOf("M"), index.lookup("+3650000000").map { it.name })
        assertEquals(listOf("Z"), index.lookup("+3690000000").map { it.name })
    }

    @Test
    fun crlf_lines() {
        val index = PhoneIndex.parse("+3612345678\tCRLF Bolt\tbolt\tPécs\r\n")
        assertEquals("CRLF Bolt, bolt, Pécs", index.lookup("+3612345678").single().speak())
    }
}
