package com.superdl.launcher.patrol

import android.content.Context

/** Az egész órás időbemondás hangja; a köztes időpontokat mindig Elena mondja. */
object ClockVoiceStore {
    private const val KEY = "patrol_clock_voice"
    const val ELENA = "elena"
    const val LILLY = "lilly"
    const val MARTIN = "martin"
    private val options = listOf(ELENA, LILLY, MARTIN)

    fun selected(context: Context): String {
        val value = context.getSharedPreferences("superdl", Context.MODE_PRIVATE)
            .getString(KEY, ELENA)
        return if (value in options) value!! else ELENA
    }

    fun label(value: String): String = when (value) {
        LILLY -> "Lilly"
        MARTIN -> "Martin"
        else -> "Elena"
    }

    fun cycle(context: Context): String {
        val current = selected(context)
        val next = options[(options.indexOf(current) + 1) % options.size]
        context.getSharedPreferences("superdl", Context.MODE_PRIVATE)
            .edit().putString(KEY, next).apply()
        return next
    }
}
