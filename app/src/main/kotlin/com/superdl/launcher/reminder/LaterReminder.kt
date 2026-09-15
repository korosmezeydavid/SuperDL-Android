package com.superdl.launcher.reminder

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * EGY DOLOG, AMIVEL MOST NEM TUDSZ FOGLALKOZNI.
 *
 * MIÉRT NEM AZ ÉBRESZTŐK KÖZÖTT LAKIK: Alph kérése, és igaza van — a
 * visszahívás nem ébresztő. Ha egy listába kerülnek, mind a kettő rosszabb
 * lesz: az ébresztők közé bekeveredik tíz telefonszám, a visszahívandók meg
 * elvesznek a hétköznap reggeli ébresztők között. Aki visszahívandót keres,
 * a hívásoknál fogja keresni — ezért ott van a helye.
 */
data class LaterReminder(
    val id: Int,
    /** "hivas" vagy "sms" — ebből tudjuk, melyik listában lakik. */
    val kind: String,
    val number: String,
    val name: String,
    val dueAt: Long,
    /** SMS-nél az üzenet eleje, hogy tudd, miről volt szó. */
    val note: String,
    val createdAt: Long
) {

    val isCall: Boolean get() = kind == KIND_CALL

    /** Akit vissza kell hívni: a név, ha ismerjük, különben a szám. */
    fun who(): String = if (name.isNotBlank()) name else number

    /** A listában ez hangzik el egy tételen. */
    fun speakPreview(): String = buildString {
        append(who())
        append(", ")
        append(speakDue())
        if (!isCall && note.isNotBlank()) {
            append(". Az üzenet: ")
            append(note.take(60))
        }
        append('.')
    }

    /** Az emlékeztető megszólalásakor ez hangzik el. */
    fun speakAlert(): String = buildString {
        if (isCall) {
            append("Vissza kellett volna hívnod: ")
            append(who())
            append(".")
        } else {
            append("Üzenet, amivel foglalkoznod kell: ")
            append(who())
            append(".")
            if (note.isNotBlank()) {
                append(" Az üzenet: ")
                append(note.take(120))
                append(".")
            }
        }
    }

    /**
     * Mikorra kérted. Ha ma van, csak az órát mondjuk; ha holnap, azt is.
     * A távolabbi napoknál a dátum jön — „szerdán" önmagában kétértelmű.
     */
    fun speakDue(now: Long = System.currentTimeMillis()): String {
        val time = try {
            SimpleDateFormat("HH:mm", Locale("hu")).format(Date(dueAt))
        } catch (_: Exception) {
            ""
        }
        return when (dayOffset(now)) {
            0 -> "ma $time"
            1 -> "holnap $time"
            else -> {
                val date = try {
                    SimpleDateFormat("MMMM d.", Locale("hu")).format(Date(dueAt))
                } catch (_: Exception) {
                    ""
                }
                "$date $time"
            }
        }
    }

    /** Lejárt-e már. A listában a lejártakat külön jelezzük. */
    fun isOverdue(now: Long = System.currentTimeMillis()): Boolean = dueAt <= now

    private fun dayOffset(now: Long): Int {
        val a = Calendar.getInstance().apply { timeInMillis = now }
        val b = Calendar.getInstance().apply { timeInMillis = dueAt }
        a.set(Calendar.HOUR_OF_DAY, 0); a.set(Calendar.MINUTE, 0)
        a.set(Calendar.SECOND, 0); a.set(Calendar.MILLISECOND, 0)
        b.set(Calendar.HOUR_OF_DAY, 0); b.set(Calendar.MINUTE, 0)
        b.set(Calendar.SECOND, 0); b.set(Calendar.MILLISECOND, 0)
        val diff = b.timeInMillis - a.timeInMillis
        return (diff / (24 * 60 * 60_000L)).toInt()
    }

    companion object {
        const val KIND_CALL = "hivas"
        const val KIND_SMS = "sms"
    }
}

/**
 * A FELAJÁNLOTT IDŐPONTOK.
 *
 * MIÉRT VAN KÖZTÜK A „HOLNAP REGGEL": a valóságban a visszahívás
 * leggyakrabban másnapra csúszik. Aki este nyolckor nem tud telefonálni, az
 * nem egy óra múlva fog, hanem reggel.
 */
enum class ReminderDelay(val label: String) {
    TEN_MINUTES("10 perc"),
    ONE_HOUR("1 óra"),
    TOMORROW_MORNING("Holnap reggel"),
    CUSTOM("Egyéni időpont");

    /** A választott időpont ezredmásodpercben. Az egyéninél null — azt bemondja. */
    fun dueAt(now: Long = System.currentTimeMillis(), morningHour: Int = 9): Long? = when (this) {
        TEN_MINUTES -> now + 10 * 60_000L
        ONE_HOUR -> now + 60 * 60_000L
        TOMORROW_MORNING -> Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, morningHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        CUSTOM -> null
    }
}
