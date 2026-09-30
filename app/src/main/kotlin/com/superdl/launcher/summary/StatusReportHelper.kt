package com.superdl.launcher.summary

import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import com.superdl.launcher.alarm.AlarmStore
import com.superdl.launcher.calendar.CalendarHelper
import com.superdl.launcher.info.InfoHelper

/**
 * Gyors, offline "helyzetjelentés" – egyetlen paranccsal felolvassa a
 * legfontosabb aktuális információkat vak felhasználónak:
 * idő, akku + térerő, nem fogadott hívások, olvasatlan üzenetek,
 * következő ébresztő, következő naptár esemény.
 *
 * Ellentétben a DaySummaryHelper-rel, ez NEM hálózatfüggő (nincs időjárás),
 * ezért azonnal, internet nélkül is teljes választ ad.
 */
object StatusReportHelper {

    fun buildReport(context: Context): String {
        val parts = mutableListOf<String>()

        // Idő
        parts.add(InfoHelper.speakDateTime())

        // Akku + térerő (a beépített InfoHelper-ből)
        parts.add(InfoHelper.batteryAndSignalReport(context))

        // Nem fogadott hívások
        parts.add(missedCallsLine(context))

        // Olvasatlan üzenetek
        parts.add(unreadSmsLine(context))

        // Következő ébresztő
        parts.add(nextAlarmLine(context))

        // Következő naptár esemény
        parts.add(nextEventLine(context))

        // Mi vár rád: visszahívandók és függő üzenetek.
        //
        // MIÉRT ITT: a listáik szándékosan a saját helyükön laknak (a
        // hívásoknál, illetve az üzeneteknél), nem az ébresztők között. Így
        // viszont kell EGY hely, ahol kiderül, hogy egyáltalán van-e ilyesmi
        // — különben a jól elrejtett lista csendben feledésbe merül.
        pendingRemindersLine(context)?.let { parts.add(it) }

        // Hány hívást szűrt ki ma a program.
        //
        // MIÉRT ITT: a szűrés néma — ez a helyes, mert éjjel senkit nem
        // ébresztünk fel egy kiszűrt reklámhívással. De akkor kell EGY hely,
        // ahol kiderül, hogy volt ilyen. Ha valakinek ez kevés vagy sok,
        // a Hívásszűrő alatt átállíthatja.
        filteredCallsLine(context)?.let { parts.add(it) }

        return parts.joinToString(" ")
    }

    private fun filteredCallsLine(context: Context): String? = try {
        val mode = com.superdl.launcher.callfilter.CallFilterStore.announceMode(context)
        if (mode == com.superdl.launcher.callfilter.CallFilterStore.AnnounceMode.NEVER) {
            null
        } else {
            val count = com.superdl.launcher.callfilter.FilteredCallStore.countToday(context)
            when (count) {
                0 -> null
                1 -> "Egy hívást szűrtem ki ma. A Szűrt hívások között megnézheted."
                else -> "$count hívást szűrtem ki ma. A Szűrt hívások között megnézheted."
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun pendingRemindersLine(context: Context): String? = try {
        com.superdl.launcher.reminder.LaterReminderStore.pruneCalledBack(context)
        val calls = com.superdl.launcher.reminder.LaterReminderStore
            .count(context, com.superdl.launcher.reminder.LaterReminder.KIND_CALL)
        val messages = com.superdl.launcher.reminder.LaterReminderStore
            .count(context, com.superdl.launcher.reminder.LaterReminder.KIND_SMS)
        when {
            calls == 0 && messages == 0 -> null
            messages == 0 -> "$calls visszahívandó vár rád."
            calls == 0 -> "$messages függő üzenet vár rád."
            else -> "$calls visszahívandó és $messages függő üzenet vár rád."
        }
    } catch (_: Throwable) {
        null
    }

    private fun missedCallsLine(context: Context): String {
        return try {
            var count = 0
            // FEKETELISTÁS SZÁM NEM SZÁMÍT BELE (Alph döntése, 2026-09-30:
            // „ne is tudjunk róla"), a szűrt hívás pedig akkor nem, ha a
            // hívásnaplóban is rejtve van. Ezért soronként nézzük, nem a
            // puszta darabszámot kérjük.
            //
            // MIÉRT A TÖBBI ELUTASÍTOTT/BLOKKOLT SORT IS BEOLVASSUK: egy szűrt
            // hívás csak a hozzá LEGKÖZELEBBI sort rejtheti el. Ha a nyoma egy
            // „elutasított" sor, azt kell elrejtenie — nem a mellette álló,
            // valódi nem fogadott hívást.
            val gate = com.superdl.launcher.callfilter.CallLogGate.load(context)
            val since = System.currentTimeMillis() - 31L * 24 * 60 * 60_000L
            val rows = mutableListOf<com.superdl.launcher.callfilter.CallLogVisibility.LogRow>()
            val countable = mutableListOf<Boolean>()
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.NEW,
                    CallLog.Calls.CACHED_NORMALIZED_NUMBER
                ),
                "(${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1) OR " +
                    "(${CallLog.Calls.TYPE} IN (?, ?, ?, ?) AND ${CallLog.Calls.DATE} > ?)",
                arrayOf(
                    CallLog.Calls.MISSED_TYPE.toString(),
                    CallLog.Calls.MISSED_TYPE.toString(),
                    CallLog.Calls.REJECTED_TYPE.toString(),
                    CallLog.Calls.BLOCKED_TYPE.toString(),
                    CallLog.Calls.VOICEMAIL_TYPE.toString(),
                    since.toString()
                ),
                null
            )?.use { cursor ->
                val iNumber = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val iDate = cursor.getColumnIndex(CallLog.Calls.DATE)
                val iType = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val iNew = cursor.getColumnIndex(CallLog.Calls.NEW)
                val iNorm = cursor.getColumnIndex(CallLog.Calls.CACHED_NORMALIZED_NUMBER)
                while (cursor.moveToNext()) {
                    val number = if (iNumber >= 0) cursor.getString(iNumber).orEmpty() else ""
                    val date = if (iDate >= 0) cursor.getLong(iDate) else 0L
                    val type = if (iType >= 0) cursor.getInt(iType) else 0
                    val isNew = iNew >= 0 && cursor.getInt(iNew) == 1
                    val normalized = if (iNorm >= 0) cursor.getString(iNorm).orEmpty() else ""
                    rows.add(gate.row(number, date, type, normalized))
                    countable.add(type == CallLog.Calls.MISSED_TYPE && isNew)
                }
            }
            val hidden = gate.hiddenIndices(rows)
            count = countable.indices.count { countable[it] && it !in hidden }
            when (count) {
                0 -> "Nincs nem fogadott hívás."
                1 -> "1 nem fogadott hívás."
                else -> "$count nem fogadott hívás."
            }
        } catch (_: Exception) {
            "Nem fogadott hívások: nem elérhető."
        }
    }

    private fun unreadSmsLine(context: Context): String {
        return try {
            var count = 0
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.READ} = 0",
                null,
                null
            )?.use { cursor ->
                count = cursor.count
            }
            when (count) {
                0 -> "Nincs olvasatlan üzenet."
                1 -> "1 olvasatlan üzenet."
                else -> "$count olvasatlan üzenet."
            }
        } catch (_: Exception) {
            "Üzenetek: nem elérhető."
        }
    }

    private fun nextAlarmLine(context: Context): String {
        val next = AlarmStore.getNextAlarm(context)
            ?: return "Nincs beállított ébresztő."
        return "Következő ébresztő: ${next.speakSummary()}."
    }

    private fun nextEventLine(context: Context): String {
        return try {
            val events = CalendarHelper.getTodayEvents(context)
            if (events.isEmpty()) {
                "Ma nincs több program a naptárban."
            } else {
                "Következő program: ${CalendarHelper.speakEvent(events.first())}."
            }
        } catch (_: Exception) {
            "Naptár: nem elérhető."
        }
    }
}
