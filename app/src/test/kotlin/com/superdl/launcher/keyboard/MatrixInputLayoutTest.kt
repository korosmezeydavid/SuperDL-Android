package com.superdl.launcher.keyboard

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixInputLayoutTest {
    @Test fun phoneFieldOffersDigitImmediatelyAndPlusOnStar() {
        val type = InputType.TYPE_CLASS_PHONE
        val mode = MatrixInputLayout.modeFor(type)
        assertEquals(MatrixInputMode.PHONE, mode)
        assertEquals(listOf('2'), MatrixInputLayout.chars(MatrixKey.KEY_2, mode, type))
        assertEquals(listOf('0'), MatrixInputLayout.chars(MatrixKey.KEY_0, mode, type))
        assertEquals(listOf('+', '*'), MatrixInputLayout.chars(MatrixKey.KEY_STAR, mode, type))
        assertEquals(listOf('#'), MatrixInputLayout.chars(MatrixKey.KEY_HASH, mode, type))
    }

    @Test fun numberFieldKeepsDecimalAndSignWhenRequested() {
        val type = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED
        val mode = MatrixInputLayout.modeFor(type)
        assertEquals(MatrixInputMode.NUMBER, mode)
        assertEquals(listOf('6'), MatrixInputLayout.chars(MatrixKey.KEY_6, mode, type))
        assertEquals(listOf('.', '-'), MatrixInputLayout.chars(MatrixKey.KEY_STAR, mode, type))
    }

    @Test fun ordinaryTextRetainsLetterCycle() {
        val type = InputType.TYPE_CLASS_TEXT
        val mode = MatrixInputLayout.modeFor(type)
        assertEquals(MatrixInputMode.LETTERS, mode)
        assertEquals(MatrixKey.KEY_2.chars, MatrixInputLayout.chars(MatrixKey.KEY_2, mode, type))
    }
}
