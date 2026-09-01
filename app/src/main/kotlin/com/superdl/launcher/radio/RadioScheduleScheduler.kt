package com.superdl.launcher.radio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.superdl.launcher.alarm.AlarmScheduler
import java.util.Calendar

/**
 * AZ IDŐZÍTETT FELVÉTELEK BEÜTEMEZÉSE.
 *
 * MIÉRT PONTOS ÉBRESZTŐVEL: egy műsor 10 óra 5 perckor kezdődik, nem
 * "valamikor 10 körül". Egy késve induló felvétel pont az elejét vágja le —
 * azt a részt, amiért a felhasználó felvette.
 *
 * A pontos ébresztő engedélyét a Beállítás varázsló alapvetőként kéri, tehát
 * a legtöbb telefonon megvan. Ha mégsem, az AlarmScheduler pontatlanra vált,
 * és ezt a felhasználónak KI IS MONDJUK a rögzítéskor — nem hallgatjuk el.
 */
object RadioScheduleScheduler {

    /** A kérés-azonosítók elkülönítése az ébresztőktől. */
    private const val REQUEST_BASE = 70000

    fun schedule(context: Context, entry: RadioScheduleEntry): Boolean {
        if (!entry.enabled) return true
        val triggerAt = nextTriggerMillis(entry)
        return AlarmScheduler.setExactAlarm(context, triggerAt, pendingIntent(context, entry.id))
    }

    fun cancel(context: Context, id: Int) {
        try {
            val manager = context.getSystemService(android.app.AlarmManager::class.java) ?: return
            manager.cancel(pendingIntent(context, id))
        } catch (_: Exception) {
        }
    }

    /** Újraütemezés indulás után és minden változtatás után. */
    fun rescheduleAll(context: Context) {
        RadioScheduleStore.getAll(context).forEach { entry ->
            if (entry.enabled) schedule(context, entry) else cancel(context, entry.id)
        }
    }

    /**
     * A megszólalás UTÁN: ismétlődőnél a következő alkalom, egyszerinél
     * kikapcsolás. Ugyanaz a logika, mint az ébresztőnél — így nem lesz két
     * különböző viselkedés, amit a felhasználónak fejben kellene tartania.
     */
    fun scheduleNextOccurrence(context: Context, entry: RadioScheduleEntry) {
        if (entry.isOneTime()) {
            RadioScheduleStore.setEnabled(context, entry.id, false)
            return
        }
        schedule(context, entry)
    }

    fun nextTriggerMillis(entry: RadioScheduleEntry): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, entry.hour)
            set(Calendar.MINUTE, entry.minute)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        var guard = 0
        while (guard < 8 && !entry.isActiveOnDay(cal.get(Calendar.DAY_OF_WEEK))) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            guard++
        }
        return cal.timeInMillis
    }

    /** Kimondható: mennyi van hátra a következő felvételig. */
    fun speakNext(context: Context): String {
        val entries = RadioScheduleStore.getAll(context).filter { it.enabled }
        if (entries.isEmpty()) return "Nincs beütemezett felvétel."
        val next = entries.minByOrNull { nextTriggerMillis(it) } ?: return "Nincs beütemezett felvétel."
        val minutes = ((nextTriggerMillis(next) - System.currentTimeMillis()) / 60_000L).toInt()
        val when_ = when {
            minutes < 60 -> "$minutes perc múlva"
            minutes < 24 * 60 -> "${minutes / 60} óra múlva"
            else -> "${minutes / (24 * 60)} nap múlva"
        }
        return "Következő felvétel: ${next.stationName}, ${next.speakTime()}, $when_."
    }

    private fun pendingIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, RadioScheduleReceiver::class.java).apply {
            action = RadioScheduleReceiver.ACTION_START
            putExtra(RadioScheduleReceiver.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
