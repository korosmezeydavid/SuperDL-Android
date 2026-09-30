package com.superdl.launcher.callfilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A feketelista-rejtés és a szűrt hívások rejtésének tiszta döntései.
 *
 * MIÉRT FONTOS: ha itt hibázunk, vagy megszólal az, akit Dávid épp
 * elhallgattatni akart, vagy — rosszabb — egy ártatlan szám sora törlődik a
 * hívásnaplóból.
 */
class CallLogVisibilityTest {

    private val t0 = 1_780_000_000_000L

    // ── Szám-alakok ──────────────────────────────────────────────────────

    @Test
    fun canonical_hungarian_forms_agree() {
        assertEquals("+36301234567", CallLogVisibility.canonical("06 30 123 4567"))
        assertEquals("+36301234567", CallLogVisibility.canonical("+36 (30) 123-4567"))
        assertEquals("+36301234567", CallLogVisibility.canonical("0036301234567"))
        assertEquals("301234567", CallLogVisibility.canonical("30/123-4567"))
        assertEquals("", CallLogVisibility.canonical("   "))
    }

    @Test
    fun samePhone_matches_06_and_plus36_and_0036() {
        assertTrue(CallLogVisibility.samePhone("06301234567", "+36301234567"))
        assertTrue(CallLogVisibility.samePhone("+36 30 123 4567", "0036 30 123 4567"))
        assertTrue(CallLogVisibility.samePhone("06-1-234-5678", "+3612345678"))
        // előhívó nélkül beírt belföldi szám (legalább nyolc számjegy)
        assertTrue(CallLogVisibility.samePhone("301234567", "+36301234567"))
    }

    @Test
    fun hiding_match_rejects_truncated_and_cross_country() {
        // a felülvizsgálat ellenpéldái: a lemaradó rész csak a +36 lehet
        assertFalse(CallLogVisibility.samePhone("+36301234567", "01234567"))
        assertFalse(CallLogVisibility.samePhone("301234567", "+43301234567"))
        assertFalse(CallLogVisibility.samePhone("1234567", "+36301234567"))
        assertFalse(CallLogVisibility.samePhone("1234567", "+36201234567"))
        assertFalse(CallLogVisibility.isBlacklistedNumber("+36701234567", listOf("1234567"), emptyList()))
    }

    @Test
    fun samePhone_does_not_mix_countries_or_neighbours() {
        // azonos végű, de más országbeli szám: SOHA nem ugyanaz (törlést enged!)
        assertFalse(CallLogVisibility.samePhone("+49301234567", "+36301234567"))
        assertFalse(CallLogVisibility.samePhone("06301234567", "06301234568"))
        assertFalse(CallLogVisibility.samePhone("06201234567", "06301234567"))
        // rövid szám csak pontos egyezéssel
        assertFalse(CallLogVisibility.samePhone("1234", "+361234"))
        assertTrue(CallLogVisibility.samePhone("1234", "1234"))
    }

    @Test
    fun hidden_numbers_never_match() {
        assertTrue(CallLogVisibility.isHiddenNumber(""))
        assertTrue(CallLogVisibility.isHiddenNumber("-1"))
        assertTrue(CallLogVisibility.isHiddenNumber("-2"))
        assertTrue(CallLogVisibility.isHiddenNumber("Private"))
        assertFalse(CallLogVisibility.isHiddenNumber("+36301234567"))
        assertFalse(CallLogVisibility.samePhone("", ""))
        assertFalse(CallLogVisibility.samePhone("-1", "-1"))
    }

    // ── Feketelista, fehérlista ──────────────────────────────────────────

    @Test
    fun blacklisted_detected_in_other_format() {
        val black = listOf("06301234567")
        assertTrue(CallLogVisibility.isBlacklistedNumber("+36301234567", black, emptyList()))
        assertFalse(CallLogVisibility.isBlacklistedNumber("+36301234568", black, emptyList()))
    }

    @Test
    fun whitelist_wins_over_blacklist() {
        val black = listOf("06301234567")
        val white = listOf("+36 30 123 4567")
        assertFalse(CallLogVisibility.isBlacklistedNumber("06301234567", black, white))
        assertFalse(
            CallLogVisibility.shouldHide(
                "06301234567", t0, filterable = true,
                blacklist = black, whitelist = white,
                hideFiltered = true,
                filtered = listOf(CallLogVisibility.FilteredMark("06301234567", t0, "feketelista"))
            )
        )
    }

    // ── A rejtés döntése ─────────────────────────────────────────────────

    @Test
    fun blacklisted_always_hidden_any_type_regardless_of_setting() {
        val black = listOf("+36301234567")
        for (filterable in listOf(true, false)) {
            for (hideFiltered in listOf(true, false)) {
                assertTrue(
                    CallLogVisibility.shouldHide(
                        "06 30 123 4567", t0, filterable, black, emptyList(),
                        hideFiltered, emptyList()
                    )
                )
            }
        }
    }

    @Test
    fun filtered_call_visible_by_default_hidden_when_setting_says() {
        val marks = listOf(CallLogVisibility.FilteredMark("+36201112233", t0 + 1_500, "nezavarj"))
        assertFalse(
            CallLogVisibility.shouldHide(
                "06201112233", t0, true, emptyList(), emptyList(),
                hideFiltered = false, filtered = marks
            )
        )
        assertTrue(
            CallLogVisibility.shouldHide(
                "06201112233", t0, true, emptyList(), emptyList(),
                hideFiltered = true, filtered = marks
            )
        )
    }

    @Test
    fun filtered_matching_respects_time_window() {
        val marks = listOf(CallLogVisibility.FilteredMark("+36201112233", t0, "ismeretlen"))
        // ugyanaz a szám, de egy órával később: rendes hívás, látszik
        assertFalse(
            CallLogVisibility.shouldHide(
                "+36201112233", t0 + 3_600_000, true, emptyList(), emptyList(), true, marks
            )
        )
        // az ablak szélén még egyezik, azon túl már nem
        val w = CallLogVisibility.FILTER_MATCH_WINDOW_MS
        assertTrue(CallLogVisibility.shouldHide("+36201112233", t0 - w, true, emptyList(), emptyList(), true, marks))
        assertFalse(CallLogVisibility.shouldHide("+36201112233", t0 - w - 1, true, emptyList(), emptyList(), true, marks))
    }

    @Test
    fun non_filterable_row_never_hidden_as_filtered() {
        val marks = listOf(CallLogVisibility.FilteredMark("+36201112233", t0, "ismeretlen"))
        assertFalse(
            CallLogVisibility.shouldHide(
                "+36201112233", t0 + 30_000, filterable = false,
                blacklist = emptyList(), whitelist = emptyList(),
                hideFiltered = true, filtered = marks
            )
        )
    }

    @Test
    fun hidden_number_rows_match_hidden_filtered_calls_only() {
        val marks = listOf(CallLogVisibility.FilteredMark("", t0, "rejtett"))
        assertTrue(CallLogVisibility.shouldHide("-2", t0 + 2_000, true, emptyList(), emptyList(), true, marks))
        assertTrue(CallLogVisibility.shouldHide("", t0 + 2_000, true, emptyList(), emptyList(), true, marks))
        assertFalse(
            CallLogVisibility.shouldHide("+36301234567", t0 + 2_000, true, emptyList(), emptyList(), true, marks)
        )
        // beállítás „látszik": a rejtett számú szűrt hívás is marad
        assertFalse(CallLogVisibility.shouldHide("-2", t0 + 2_000, true, emptyList(), emptyList(), false, marks))
    }

    @Test
    fun whitelisted_number_never_hidden_as_filtered() {
        val marks = listOf(CallLogVisibility.FilteredMark("+36201112233", t0, "nezavarj"))
        assertFalse(
            CallLogVisibility.shouldHide(
                "+36201112233", t0, true, emptyList(), listOf("06201112233"), true, marks
            )
        )
    }

    // ── Szűrt hívások listája ────────────────────────────────────────────

    @Test
    fun filtered_list_drops_blacklisted_and_keeps_others() {
        val items = listOf(
            FilteredCall(1, "+36301234567", "", t0, "feketelista"),
            FilteredCall(2, "", "", t0, "rejtett"),
            FilteredCall(3, "+36201112233", "Anna", t0, "nezavarj"),
            // korábban feketelistás volt, azóta levették: újra látszik
            FilteredCall(4, "+36701110000", "", t0, "feketelista")
        )
        val visible = CallLogVisibility.visibleFilteredCalls(items, listOf("06301234567"), emptyList())
        assertEquals(listOf(2, 3, 4), visible.map { it.id })
    }

    // ── Egy szűrt hívás legfeljebb egy sort rejt ─────────────────────────

    @Test
    fun each_mark_hides_only_the_nearest_row() {
        val rows = listOf(
            CallLogVisibility.LogRow("+36201112233", t0 + 50_000, filterable = true), // valódi, később
            CallLogVisibility.LogRow("+36201112233", t0 + 800, filterable = true),    // a szűrt hívás nyoma
            CallLogVisibility.LogRow("+36201112233", t0 - 70_000, filterable = true)  // valódi, korábban
        )
        val marks = listOf(CallLogVisibility.FilteredMark("+36201112233", t0, "nezavarj"))
        assertEquals(
            setOf(1),
            CallLogVisibility.hiddenRowIndices(rows, emptyList(), emptyList(), true, marks)
        )
        // két szűrt hívás két sort rejt, egyet-egyet
        val two = marks + CallLogVisibility.FilteredMark("+36201112233", t0 + 49_000, "nezavarj")
        assertEquals(
            setOf(0, 1),
            CallLogVisibility.hiddenRowIndices(rows, emptyList(), emptyList(), true, two)
        )
    }

    @Test
    fun answered_row_is_not_claimed_even_if_nearest() {
        val rows = listOf(
            CallLogVisibility.LogRow("+36201112233", t0 + 100, filterable = false), // fogadott
            CallLogVisibility.LogRow("+36201112233", t0 + 5_000, filterable = true)  // elutasított
        )
        val marks = listOf(CallLogVisibility.FilteredMark("+36201112233", t0, "ismeretlen"))
        assertEquals(setOf(1), CallLogVisibility.hiddenRowIndices(rows, emptyList(), emptyList(), true, marks))
        assertEquals(emptySet<Int>(), CallLogVisibility.hiddenRowIndices(rows, emptyList(), emptyList(), false, marks))
    }

    @Test
    fun blacklist_hiding_uses_normalized_number_too() {
        val rows = listOf(CallLogVisibility.LogRow("30 123 4567", t0, false, normalized = "+36301234567"))
        assertEquals(
            setOf(0),
            CallLogVisibility.hiddenRowIndices(rows, listOf("06301234567"), emptyList(), false, emptyList())
        )
    }

    // ── Törlés: szigorú egyezés ──────────────────────────────────────────

    @Test
    fun strict_e164_forms() {
        assertEquals("+36301234567", CallLogVisibility.strictE164("06 30 123 4567"))
        assertEquals("+36301234567", CallLogVisibility.strictE164("0036301234567"))
        assertEquals("+43301234567", CallLogVisibility.strictE164("+43 30 1234567"))
        assertEquals(null, CallLogVisibility.strictE164("301234567"))    // előhívó nélkül: nem tudjuk
        assertEquals(null, CallLogVisibility.strictE164("+361234567"))   // túl kevés nemzeti számjegy
        assertEquals(null, CallLogVisibility.strictE164("-1"))
    }

    @Test
    fun strict_match_counter_examples_never_match() {
        assertTrue(CallLogVisibility.samePhoneStrict("06301234567", "+36301234567"))
        assertTrue(CallLogVisibility.samePhoneStrict("0036 30 123 4567", "+36-30-123-4567"))
        assertFalse(CallLogVisibility.samePhoneStrict("+36301234567", "01234567"))
        assertFalse(CallLogVisibility.samePhoneStrict("301234567", "+43301234567"))
        assertFalse(CallLogVisibility.samePhoneStrict("301234567", "+36301234567"))
        assertFalse(CallLogVisibility.samePhoneStrict("1234567", "+36301234567"))
        assertFalse(CallLogVisibility.samePhoneStrict("1234567", "1234567"))       // túl rövid
        assertFalse(CallLogVisibility.samePhoneStrict("+49301234567", "+36301234567"))
        assertTrue(CallLogVisibility.samePhoneStrict("12345678", "1234-5678"))     // pontos számjegyek
    }

    @Test
    fun purge_row_counter_examples_are_not_deleted() {
        val none = emptyList<String>()
        // „1234567" a feketelistán NEM töröl minden +36x01234567-et
        for (n in listOf("+36301234567", "+36201234567", "+36701234567", "06301234567")) {
            assertFalse(CallLogVisibility.isPurgeRow(n, "", listOf("1234567"), none))
        }
        assertFalse(CallLogVisibility.isPurgeRow("01234567", "", listOf("+36301234567"), none))
        assertFalse(CallLogVisibility.isPurgeRow("+43301234567", "", listOf("301234567"), none))
        assertFalse(CallLogVisibility.isPurgeRow("+43301234567", "+43301234567", listOf("+36301234567"), none))
        // a valódi egyezés törölhető
        assertTrue(CallLogVisibility.isPurgeRow("06301234567", "", listOf("+36301234567"), none))
        assertTrue(CallLogVisibility.isPurgeRow("30 123 4567", "+36301234567", listOf("06301234567"), none))
        // a fehérlista véd — lazán is
        assertFalse(
            CallLogVisibility.isPurgeRow("+36301234567", "", listOf("+36301234567"), listOf("301234567"))
        )
        assertFalse(CallLogVisibility.isPurgeRow("-1", "", listOf("+36301234567"), none))
    }

    @Test
    fun purge_target_requires_strict_number_and_time_window() {
        val black = listOf("06301234567")
        val none = emptyList<String>()
        assertTrue(CallLogVisibility.isPurgeTarget("+36301234567", "", t0 - 800, "06301234567", t0, black, none))
        assertFalse(CallLogVisibility.isPurgeTarget("+36301234568", "", t0, "06301234567", t0, black, none))
        assertFalse(
            CallLogVisibility.isPurgeTarget(
                "+36301234567", "", t0 - CallLogVisibility.PURGE_WINDOW_MS - 1, "06301234567", t0, black, none
            )
        )
        assertFalse(CallLogVisibility.isPurgeTarget("-1", "", t0, "", t0, black, none))
        // csonka / más országbeli sor az ablakon belül sem törlődik
        assertFalse(CallLogVisibility.isPurgeTarget("01234567", "", t0, "+36301234567", t0, listOf("+36301234567"), none))
        assertFalse(CallLogVisibility.isPurgeTarget("+43301234567", "", t0, "301234567", t0, listOf("301234567"), none))
        // a hívó szám egyezik, de a sor nem feketelistás (közben levették): marad
        assertFalse(CallLogVisibility.isPurgeTarget("+36301234567", "", t0, "+36301234567", t0, none, none))
    }

    // ── Értesítés szövege ────────────────────────────────────────────────

    @Test
    fun notification_text_mentions_number() {
        val black = listOf("06301234567")
        assertTrue(
            CallLogVisibility.textMentionsBlacklisted("Nem fogadott hívás: +36 30 123 4567", black, emptyList())
        )
        assertFalse(
            CallLogVisibility.textMentionsBlacklisted("Nem fogadott hívás: +36 30 123 4568", black, emptyList())
        )
        assertFalse(CallLogVisibility.textMentionsBlacklisted("Ma 12:30-kor, 2026.09.30.", black, emptyList()))
        assertFalse(
            CallLogVisibility.textMentionsBlacklisted(
                "Nem fogadott hívás: +36 30 123 4567", black, listOf("+36301234567")
            )
        )
        assertTrue(CallLogVisibility.textMentionsNumber("Hívó: 06-30-123-4567", "+36301234567"))
    }

    @Test
    fun custom_matcher_is_used_and_its_failure_is_contained() {
        val boom: (String, String) -> Boolean = { _, _ -> throw IllegalStateException("x") }
        assertFalse(CallLogVisibility.matchesAny("+36301234567", listOf("+36301234567"), boom))
        val loose: (String, String) -> Boolean = { a, b -> a.takeLast(4) == b.takeLast(4) }
        assertTrue(CallLogVisibility.isBlacklistedNumber("+36309994567", listOf("111114567"), emptyList(), loose))
    }
}
