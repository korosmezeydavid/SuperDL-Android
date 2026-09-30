package com.superdl.launcher.calllog

import android.content.Context
import android.provider.CallLog

object CallLogHelper {

    /** Védőkorlát: ennél több sort akkor sem olvasunk be, ha a napló óriási. */
    private const val MAX_SCAN = 5_000

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
        // FEKETELISTÁS SZÁM SOHA NEM LÁTSZIK, a szűrt hívás pedig a beállítás
        // szerint („Szűrt hívások a hívásnaplóban"). A rejtett sorok NEM
        // számítanak bele a darabszámba — különben a lista rövidebb lenne,
        // mint amit beállítottál.
        //
        // MIÉRT OLVASSUK BE ELŐBB AZ EGÉSZET: egy szűrt hívás csak a hozzá
        // időben LEGKÖZELEBBI sort rejtheti el, ehhez a szomszédos sorokat is
        // látni kell. A rendszer hívásnaplója néhány száz sor, ez olcsó.
        val gate = com.superdl.launcher.callfilter.CallLogGate.load(context)
        val raw = mutableListOf<CallLogEntry>()
        val rows = mutableListOf<com.superdl.launcher.callfilter.CallLogVisibility.LogRow>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NORMALIZED_NUMBER
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
            val normIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NORMALIZED_NUMBER)
            while (cursor.moveToNext() && raw.size < MAX_SCAN) {
                val number = cursor.getString(numberIdx)?.trim().orEmpty()
                val date = cursor.getLong(dateIdx)
                val type = cursor.getInt(typeIdx)
                val normalized = if (normIdx >= 0) cursor.getString(normIdx)?.trim().orEmpty() else ""
                rows.add(gate.row(number, date, type, normalized))
                raw.add(
                    CallLogEntry(
                        number = number,
                        name = cursor.getString(nameIdx)?.trim().orEmpty(),
                        date = date,
                        type = type,
                        durationSeconds = cursor.getInt(durationIdx)
                    )
                )
            }
        }
        val hidden = gate.hiddenIndices(rows)
        val entries = mutableListOf<CallLogEntry>()
        for (i in raw.indices) {
            if (entries.size >= limit) break
            if (i in hidden) continue
            // Rejtett számú sort a lista eddig sem mutatott (nincs mit hívni).
            if (raw[i].number.isBlank()) continue
            entries.add(raw[i])
        }
        return entries
    }
}
