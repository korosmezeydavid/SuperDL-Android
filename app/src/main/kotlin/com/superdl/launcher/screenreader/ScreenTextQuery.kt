package com.superdl.launcher.screenreader

import java.text.Normalizer
import java.util.Locale

internal class ScreenTextQuery private constructor(private val terms: List<String>) {
    fun matches(text: String): Boolean {
        val normalized = fold(text)
        return terms.all { it in normalized }
    }

    companion object {
        private val marks = Regex("\\p{M}+")
        private val whitespace = Regex("[\\s\\p{Z}]+")

        fun parse(query: String): ScreenTextQuery? {
            val terms = whitespace.split(fold(query)).filter { it.isNotEmpty() }.distinct()
            return terms.takeIf { it.isNotEmpty() }?.let(::ScreenTextQuery)
        }

        private fun fold(text: String): String =
            marks.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
    }
}