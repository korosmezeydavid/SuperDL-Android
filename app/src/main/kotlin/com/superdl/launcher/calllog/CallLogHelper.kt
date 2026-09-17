package com.superdl.launcher.calllog

import android.content.Context
import android.provider.CallLog

object CallLogHelper {

    /**
     * A hívások, legfrissebbtől visszafelé.
     *
     * A DARABSZÁM A BEÁLLÍTÁSBÓL JÖN. Eddig fixen húsz volt, és nem lehetett
     * visszamenni régebbre — a huszadik után a lista némán véget ért, anélkül
     * hogy jelezte volna, hogy van még. A „Hívásnapló beállítások" pontban
     * állítható.
     */
    fun getRecentCalls(
        context: Context,
        limit: Int = com.superdl.launcher.history.HistoryPrefs.callQueryLimit(context)
    ): List<CallLogEntry> {
        val entries = mutableListOf<CallLogEntry>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION
            ),
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
            val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
            while (cursor.moveToNext() && entries.size < limit) {
                val number = cursor.getString(numberIdx)?.trim().orEmpty()
                if (number.isBlank()) continue
                entries.add(
                    CallLogEntry(
                        number = number,
                        name = cursor.getString(nameIdx)?.trim().orEmpty(),
                        date = cursor.getLong(dateIdx),
                        type = cursor.getInt(typeIdx),
                        durationSeconds = cursor.getInt(durationIdx)
                    )
                )
            }
        }
        return entries
    }
}