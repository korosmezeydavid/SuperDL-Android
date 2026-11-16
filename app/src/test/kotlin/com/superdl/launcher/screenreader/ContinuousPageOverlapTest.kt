package com.superdl.launcher.screenreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinuousPageOverlapTest {
    @Test
    fun skipsRepeatedTailAtBeginningOfNextPage() {
        assertEquals(2, ContinuousPageOverlap.firstUnread(listOf("A", "B", "C"), listOf("B", "C", "D")))
    }

    @Test
    fun continuesAtStartOfDisjointPage() {
        assertEquals(0, ContinuousPageOverlap.firstUnread(listOf("A"), listOf("B", "C")))
    }

    @Test
    fun stopsWhenScrollDoesNotExposeUnreadElements() {
        assertNull(ContinuousPageOverlap.firstUnread(listOf("A", "B"), listOf("A", "B")))
        assertNull(ContinuousPageOverlap.firstUnread(listOf("A", "B"), listOf("B")))
        assertNull(ContinuousPageOverlap.firstUnread(listOf("A", "B"), emptyList()))
    }
}