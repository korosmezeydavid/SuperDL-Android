package com.superdl.launcher.callfilter

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * EGY HÍVÁS, AMIT A PROGRAM KISZŰRT.
 *
 * MIÉRT VEZETÜNK RÓLA SAJÁT NAPLÓT: a rendszer hívásnaplójában legfeljebb
 * annyi látszik, hogy elutasított hívás volt. Az OKOT — hogy feketelistás
 * volt, vagy rejtett szám, vagy épp Ne Zavarj volt — csak mi tudjuk. Ok
 * nélkül a lista fele annyit ér: nem derül ki belőle, érdemes-e
 * visszahívni, vagy változtatni kellene a beállításon.
 */
data class FilteredCall(
    val id: Int,
    val number: String,
    val name: String,
    val at: Long,
    val reason: String
) {

    fun who(): String = when {
        name.isNotBlank() -> name
        number.isNotBlank() -> number
        else -> "rejtett szám"
    }

    fun reasonText(): String = when (reason) {
        "feketelista" -> "feketelistás"
        "rejtett" -> "rejtett szám"
        "nezavarj" -> "Teljes Ne Zavarj"
        "reszleges" -> "részleges szűrés"
        "ismeretlen" -> "ismeretlen szám"
        "alkalmazas" -> "alkalmazás szerinti fókusz"
        else -> "szűrve"
    }

    fun speakPreview(now: Long = System.currentTimeMillis()): String =
        "${who()}, ${speakWhen(now)}, ${reasonText()}."

    private fun speakWhen(now: Long): String {
        val time = try {
            SimpleDateFormat("HH:mm", Locale("hu")).format(Date(at))
        } catch (_: Exception) {
            ""
        }
        return when (dayOffset(now)) {
            0 -> "ma $time"
            -1 -> "tegnap $time"
            else -> {
                val date = try {
                    SimpleDateFormat("MMMM d.", Locale("hu")).format(Date(at))
                } catch (_: Exception) {
                    ""
                }
                "$date $time"
            }
        }
    }

    private fun dayOffset(now: Long): Int {
        val a = Calendar.getInstance().apply { timeInMillis = now }
        val b = Calendar.getInstance().apply { timeInMillis = at }
        listOf(a, b).forEach {
            it.set(Calendar.HOUR_OF_DAY, 0); it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0); it.set(Calendar.MILLISECOND, 0)
        }
        // MIÉRT kerekítés: nyári időszámítás váltásakor egy nap 23 vagy 25 óra.
        return Math.round((b.timeInMillis - a.timeInMillis) / 86_400_000.0).toInt()
    }
}

/** Mit lehet kezdeni egy kiszűrt hívással. */
enum class FilteredCallAction(val label: String) {
    CALL_BACK("Visszahívás"),
    TO_WHITELIST("Fehérlistára — mindig engedd át"),
    FROM_BLACKLIST("Levétel a feketelistáról"),
    TO_BLACKLIST("Feketelistára — soha ne csörögjön"),
    DELETE("Törlés a listából"),
    DELETE_ALL("Összes szűrt hívás törlése"),
    BACK("Vissza");

    companion object {
        fun forEntry(blacklisted: Boolean, hasNumber: Boolean): List<FilteredCallAction> {
            val out = mutableListOf<FilteredCallAction>()
            if (hasNumber) {
                out.add(CALL_BACK)
                out.add(TO_WHITELIST)
                if (blacklisted) out.add(FROM_BLACKLIST) else out.add(TO_BLACKLIST)
            }
            out.add(DELETE)
            out.add(DELETE_ALL)
            out.add(BACK)
            return out
        }
    }
}
