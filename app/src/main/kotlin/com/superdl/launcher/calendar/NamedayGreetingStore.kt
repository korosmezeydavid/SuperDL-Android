package com.superdl.launcher.calendar

import android.content.Context
import java.util.Base64

/** Helyi, privát SMS-beállítások; a foglalás mindig a küldési kísérlet előtt történik. */
object NamedayGreetingStore {
    private const val PREFS = "superdl_nameday_greetings"
    private const val KEY = "entries"

    private fun encoded(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decoded(value: String): String? = try {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) { null }

    fun encode(item: NamedayGreetingPolicy.Greeting): String = listOf(
        item.eventId.toString(), encoded(item.phone), encoded(item.text),
        if (item.enabled) "1" else "0", item.lastAttemptDay.toString()
    ).joinToString(",")

    fun decode(line: String): NamedayGreetingPolicy.Greeting? {
        val parts = line.split(',', limit = 5)
        if (parts.size != 5) return null
        val id = parts[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val phone = decoded(parts[1]) ?: return null
        val text = decoded(parts[2]) ?: return null
        val enabled = when (parts[3]) { "1" -> true; "0" -> false; else -> return null }
        val attempt = parts[4].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val item = NamedayGreetingPolicy.Greeting(id, phone, text, enabled, attempt)
        if (enabled && !NamedayGreetingPolicy.validConfiguration(item)) return null
        return item
    }

    private fun entries(context: Context): List<NamedayGreetingPolicy.Greeting> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty().lineSequence().mapNotNull(::decode)
            .distinctBy { it.eventId }.toList()

    fun get(context: Context, eventId: Long): NamedayGreetingPolicy.Greeting? =
        entries(context).firstOrNull { it.eventId == eventId }

    /** A korábbi kísérletnap módosításkor is megmarad. */
    @Synchronized
    fun configure(context: Context, item: NamedayGreetingPolicy.Greeting): Boolean {
        if (item.eventId <= 0 || (item.enabled && !NamedayGreetingPolicy.validConfiguration(item)))
            return false
        val current = entries(context)
        val previous = current.firstOrNull { it.eventId == item.eventId }
        val updated = item.copy(lastAttemptDay = previous?.lastAttemptDay ?: 0)
        return persist(context, current.filterNot { it.eventId == item.eventId } + updated)
    }

    internal fun reservedEntries(
        current: List<NamedayGreetingPolicy.Greeting>, event: CalendarEvent, nowMs: Long
    ): Pair<List<NamedayGreetingPolicy.Greeting>, NamedayGreetingPolicy.Greeting>? {
        val item = current.firstOrNull { it.eventId == event.eventId } ?: return null
        if (!NamedayGreetingPolicy.ready(item, event, nowMs)) return null
        val reserved = item.copy(lastAttemptDay = NamedayGreetingPolicy.dayKey(nowMs))
        return current.map { if (it.eventId == item.eventId) reserved else it } to reserved
    }

    /** Sikeres tartós foglalás után adja vissza a pontos, egyszer küldhető tartalmat. */
    @Synchronized
    fun reserve(context: Context, event: CalendarEvent, nowMs: Long): NamedayGreetingPolicy.Greeting? {
        val change = reservedEntries(entries(context), event, nowMs) ?: return null
        if (!persist(context, change.first)) return null
        return change.second
    }

    @Synchronized
    fun remove(context: Context, eventId: Long): Boolean {
        val current = entries(context)
        if (current.none { it.eventId == eventId }) return true
        return persist(context, current.filterNot { it.eventId == eventId })
    }

    private fun persist(context: Context, items: List<NamedayGreetingPolicy.Greeting>): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, items.joinToString("\n", transform = ::encode)).commit()
}