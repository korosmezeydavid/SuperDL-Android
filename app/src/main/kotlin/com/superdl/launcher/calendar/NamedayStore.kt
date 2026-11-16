package com.superdl.launcher.calendar

import android.content.Context
import java.util.Base64
import java.util.GregorianCalendar

/** Csak a SuperDL névnapkeresőjével létrehozott naptári események helyi jegyzéke. */
object NamedayStore {
    private const val PREFS = "superdl_namedays"
    private const val KEY = "entries"

    data class Entry(val eventId: Long, val name: String, val month: Int, val day: Int)

    fun encode(entry: Entry): String {
        val name = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(entry.name.toByteArray(Charsets.UTF_8))
        return "${entry.eventId},${entry.month},${entry.day},$name"
    }

    fun decode(line: String): Entry? {
        val fields = line.split(',', limit = 4)
        if (fields.size != 4) return null
        val id = fields[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val month = fields[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val day = fields[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        try {
            GregorianCalendar().apply {
                isLenient = false
                clear()
                set(2024, month - 1, day)
            }.timeInMillis
        } catch (_: IllegalArgumentException) { return null }
        val name = try {
            String(Base64.getUrlDecoder().decode(fields[3]), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) { return null }
        if (name.isBlank()) return null
        return Entry(id, name, month, day)
    }

    fun list(context: Context): List<Entry> = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        .lineSequence().mapNotNull(::decode).distinctBy { it.eventId }.toList()

    fun get(context: Context, eventId: Long): Entry? = list(context).firstOrNull { it.eventId == eventId }

    fun save(context: Context, entry: Entry): Boolean {
        if (decode(encode(entry)) != entry) return false
        val updated = list(context).filterNot { it.eventId == entry.eventId } + entry
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, updated.joinToString("\n", transform = ::encode)).commit()
    }

    fun remove(context: Context, eventId: Long): Boolean {
        val current = list(context)
        if (current.none { it.eventId == eventId }) return true
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, current.filterNot { it.eventId == eventId }
                .joinToString("\n", transform = ::encode)).commit()
    }
}
