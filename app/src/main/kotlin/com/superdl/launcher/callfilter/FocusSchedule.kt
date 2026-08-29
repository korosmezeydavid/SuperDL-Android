package com.superdl.launcher.callfilter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * IDŐZÍTETT HÍVÁSSZŰRÉS — "egyéni fókusz".
 *
 * MIRE JÓ: nem kell kézzel be- és kikapcsolgatni a szűrést. Beállítod, hogy
 * este tíztől reggel hatig, vagy vasárnap egész nap teljes szűrés legyen, és
 * onnantól MAGÁTÓL működik.
 *
 * FONTOS: a FEHÉRLISTA MINDIG ERŐSEBB. Aki rajta van, az akkor is átcsörög,
 * ha teljes Ne Zavarj van érvényben. Ez nem apróság: egy vak embernél a
 * családtag vagy az orvos hívása életbevágó lehet — ezért az időzítés SOHA
 * nem írhatja felül a fehérlistát.
 *
 * ÉJFÉLEN ÁTNYÚLÓ IDŐSZAK: a "22:00 - 06:00" helyesen működik, tehát az
 * éjszakát egyben kezeli, nem kell két szabályra bontani.
 */
data class FocusSchedule(
    val id: String,
    val name: String,
    /** Melyik szűrési mód legyen érvényben ebben az időszakban. */
    val mode: CallFilterMode,
    /** Kezdés perc pontossággal, éjféltől számolva (pl. 22:00 = 1320). */
    val startMinute: Int,
    /** Vég perc pontossággal. Ha KISEBB a kezdésnél, éjfélen átnyúlik. */
    val endMinute: Int,
    /**
     * Mely napokon érvényes. A Calendar napjai: 1 = vasárnap ... 7 = szombat.
     * Üres halmaz = minden nap.
     */
    val days: Set<Int>,
    val enabled: Boolean = true
) {
    /** Érvényben van-e ez a szabály a megadott időpontban? */
    fun isActiveAt(calendar: Calendar): Boolean {
        if (!enabled) return false
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        return if (startMinute <= endMinute) {
            // Ugyanazon a napon belüli időszak (pl. 09:00 - 17:00).
            (days.isEmpty() || day in days) && minute >= startMinute && minute < endMinute
        } else {
            // ÉJFÉLEN ÁTNYÚLÓ időszak (pl. 22:00 - 06:00).
            // Este: az INDULÁS napja számít. Hajnalban: az ELŐZŐ nap.
            if (minute >= startMinute) {
                days.isEmpty() || day in days
            } else if (minute < endMinute) {
                val previousDay = if (day == 1) 7 else day - 1
                days.isEmpty() || previousDay in days
            } else {
                false
            }
        }
    }

    fun speakSummary(): String {
        val dayPart = when {
            days.isEmpty() -> "minden nap"
            days.size == 7 -> "minden nap"
            days == setOf(2, 3, 4, 5, 6) -> "hétköznap"
            days == setOf(1, 7) -> "hétvégén"
            else -> days.sorted().joinToString(", ") { dayName(it) }
        }
        val state = if (enabled) "" else " (kikapcsolva)"
        return "$name: $dayPart ${formatTime(startMinute)}-tól ${formatTime(endMinute)}-ig, " +
            "${mode.menuLabel}$state"
    }

    companion object {
        fun formatTime(minute: Int): String =
            "%02d:%02d".format(minute / 60, minute % 60)

        fun dayName(day: Int): String = when (day) {
            1 -> "vasárnap"; 2 -> "hétfő"; 3 -> "kedd"; 4 -> "szerda"
            5 -> "csütörtök"; 6 -> "péntek"; 7 -> "szombat"
            else -> "?"
        }
    }
}
