package com.superdl.launcher.screenreader

import com.superdl.launcher.screenreader.ScreenReaderTable.CellPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenReaderTableTest {
    @Test
    fun movementFindsCellsAlignedWithSpanningHeader() {
        val header = CellPosition(0, 0, 1, 3)
        val cell = CellPosition(1, 2, 1, 1)
        assertEquals(1, ScreenReaderTable.distanceInDirection(header, cell, byRow = true, forward = true))
        assertEquals(1, ScreenReaderTable.distanceInDirection(cell, header, byRow = true, forward = false))
    }

    @Test
    fun horizontalMovementSkipsOccupiedColumns() {
        val wide = CellPosition(1, 0, 1, 2)
        val overlapping = CellPosition(1, 1, 1, 1)
        val next = CellPosition(1, 2, 1, 1)
        assertNull(ScreenReaderTable.distanceInDirection(wide, overlapping, byRow = false, forward = true))
        assertEquals(1, ScreenReaderTable.distanceInDirection(wide, next, byRow = false, forward = true))
    }

    @Test
    fun adjacentRowMustIntersectCurrentColumn() {
        val cell = CellPosition(0, 0, 1, 1)
        val diagonal = CellPosition(1, 1, 1, 1)
        assertNull(ScreenReaderTable.distanceInDirection(cell, diagonal, byRow = true, forward = true))
    }
}