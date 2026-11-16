package com.superdl.launcher.screenreader

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenReaderReadingTest {
    @Test
    fun paragraphNavigationKeepsWrappedLinesTogether() {
        assertEquals(
            listOf("Első sor\r\nmásodik sor", "Új bekezdés", "Utolsó bekezdés"),
            ScreenReaderFilter.segments(
                "Első sor\r\nmásodik sor\r\n  \r\nÚj bekezdés\u2029Utolsó bekezdés",
                ReadingGranularity.PARAGRAPH
            )
        )
    }

    @Test
    fun continuousReadingCanResumeAtSentenceBoundaries() {
        assertEquals(
            listOf("Első mondat.", "Második mondat!", "Utolsó mondat"),
            ScreenReaderFilter.segments("Első mondat. Második mondat! Utolsó mondat", ReadingGranularity.SENTENCE)
        )
    }
}