package com.superdl.launcher.screenreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenReaderSpeechHistoryTest {
    @Test
    fun keepsRecentEntriesWithoutConsecutiveRepeats() {
        val history = ScreenReaderSpeechHistory(2)
        listOf("első", "második", " második ", "", "harmadik").forEach(history::add)
        assertEquals(listOf("harmadik", "második"), history.newestFirst())
    }

    @Test
    fun clearingDoesNotMutateEarlierSnapshots() {
        val history = ScreenReaderSpeechHistory(2)
        history.add("friss")
        val snapshot = history.newestFirst()
        history.clear()
        assertTrue(history.newestFirst().isEmpty())
        assertEquals(listOf("friss"), snapshot)
    }
}