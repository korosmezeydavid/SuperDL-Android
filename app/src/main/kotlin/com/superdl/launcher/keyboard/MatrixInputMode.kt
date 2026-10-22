package com.superdl.launcher.keyboard

import android.text.InputType

/** A célmező beviteli típusa határozza meg, kell-e betűket pörgetni. */
internal enum class MatrixInputMode { LETTERS, NUMBER, PHONE }

internal object MatrixInputLayout {
    fun modeFor(inputType: Int): MatrixInputMode = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_PHONE -> MatrixInputMode.PHONE
        InputType.TYPE_CLASS_NUMBER -> MatrixInputMode.NUMBER
        else -> MatrixInputMode.LETTERS
    }

    fun chars(key: MatrixKey, mode: MatrixInputMode, inputType: Int): List<Char> {
        if (mode == MatrixInputMode.LETTERS) return key.chars
        return when (key) {
            MatrixKey.KEY_0 -> listOf('0')
            MatrixKey.KEY_1 -> listOf('1')
            MatrixKey.KEY_2 -> listOf('2')
            MatrixKey.KEY_3 -> listOf('3')
            MatrixKey.KEY_4 -> listOf('4')
            MatrixKey.KEY_5 -> listOf('5')
            MatrixKey.KEY_6 -> listOf('6')
            MatrixKey.KEY_7 -> listOf('7')
            MatrixKey.KEY_8 -> listOf('8')
            MatrixKey.KEY_9 -> listOf('9')
            MatrixKey.KEY_HASH -> if (mode == MatrixInputMode.PHONE) listOf('#') else emptyList()
            MatrixKey.KEY_STAR -> when (mode) {
                MatrixInputMode.PHONE -> listOf('+', '*')
                MatrixInputMode.NUMBER -> buildList {
                    if (inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0) add('.')
                    if (inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0) add('-')
                }
                MatrixInputMode.LETTERS -> key.chars
            }
        }
    }
}
