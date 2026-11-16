package com.superdl.launcher.calendar

import android.content.Context
import java.util.Base64
import java.util.GregorianCalendar

/** A SuperDL-ben felvett születésnapok helyi jegyzéke; a naptári esemény az elsődleges adat. */
object BirthdayStore {
    private const val PREFS = "superdl_birthdays"
    private const val KEY = "entries"

    data class Entry(val eventId: Long, val name: String, val birth: BirthdayHelper.BirthDate)

    fun encode(entry: Entry): String {
        val name = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(entry.name.toByteArray(Charsets.UTF_8))
        return "${entry.eventId},${entry.birth.year},${entry.birth.month},${entry.birth.day},$name"
    }

    fun decode(line: String): Entry? {
        val fields = line.split(',', limit = 5)
        if (fields.size != 5) return null
        val id = fields[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val year = fields[1].toIntOrNull()?.takeIf { it in 1870..9999 } ?: return null
        val month = fields[2].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val day = fields[3].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        val validDate = try {
            GregorianCalendar().apply {
                isLenient = false
                clear()
                set(year, month - 1, day)
            }.timeInMillis
            true
        } catch (_: IllegalArgumentException) { false }
        if (!validDate) return null
        val name = try {
            String(Base64.getUrlDecoder().decode(fields[4]), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) { return null }
        if (name.isBlank()) return null
        return Entry(id, name, BirthdayHelper.BirthDate(year, month, day))
    }

    fun list(context: Context): List<Entry> = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "")!!
        .lineSequence().mapNotNull(::decode).distinctBy { it.eventId }.toList()

    /** Tartós mentés: a naptárbejegyzés után hívandó. */
    fun save(context: Context, entry: Entry): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = list(context).filterNot { it.eventId == entry.eventId } + entry
        return prefs.edit().putString(KEY, updated.joinToString("\n", transform = ::encode)).commit()
    }

    /** Csak sikeres naptári törlés után távolítjuk el a helyi jegyzéket. */
    fun remove(context: Context, eventId: Long): Boolean {
        val current = list(context)
        if (current.none { it.eventId == eventId }) return true
        val remaining = current.filterNot { it.eventId == eventId }
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, remaining.joinToString("\n", transform = ::encode)).commit()
    }
}
