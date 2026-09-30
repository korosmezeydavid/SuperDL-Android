package com.superdl.launcher.callfilter

import android.content.Context
import android.provider.CallLog
import android.util.Log

/**
 * A HÍVÁSOK KAPUJA — ezen megy át minden hely, ahol a SuperDL hívást mutat
 * vagy mond be (hívásnapló, nem fogadott hívások, visszahívási kérdések,
 * helyzetjelentés, értesítések).
 *
 * MIÉRT PILLANATKÉP: egy hívásnapló-listánál akár ötszáz sort nézünk át. A
 * feketelistát, a fehérlistát és a szűrt hívásokat egyszer olvassuk be, nem
 * soronként újra.
 *
 * A döntés maga a tiszta [CallLogVisibility]-ben van (JVM-en tesztelve); ez
 * csak az Android-oldali adatokat adja hozzá.
 *
 * HA BÁRMI ELSZÁLL, NEM REJTÜNK: egy összeomló beállítás miatt ne tűnjön el
 * egy fontos hívás. (A feketelista beolvasása egyszerű prefs-olvasás, ez a
 * gyakorlatban nem hibázik.)
 */
class CallLogGate private constructor(
    private val context: Context,
    private val blacklist: List<String>,
    private val whitelist: List<String>,
    private val hideFiltered: Boolean,
    private val filtered: List<CallLogVisibility.FilteredMark>
) {

    private val same: (String, String) -> Boolean = { a, b ->
        CallFilterStore.samePhoneLoose(context, a, b)
    }

    /**
     * Egy hívásnapló-sor a rejtés számára. A típus a CallLog.Calls.TYPE, a
     * `normalized` a CallLog.Calls.NORMALIZED_NUMBER (ha van).
     */
    fun row(number: String, date: Long, type: Int, normalized: String = ""): CallLogVisibility.LogRow =
        CallLogVisibility.LogRow(number, date, isFilterableType(type), normalized)

    /**
     * Mely sorokat kell elrejteni. EGYSZERRE az egész listát kell átadni:
     * egy szűrt hívás csak a hozzá időben legközelebbi sort rejti el, ehhez
     * látni kell a szomszédait is.
     */
    fun hiddenIndices(rows: List<CallLogVisibility.LogRow>): Set<Int> = try {
        CallLogVisibility.hiddenRowIndices(
            rows = rows,
            blacklist = blacklist,
            whitelist = whitelist,
            hideFiltered = hideFiltered,
            marks = filtered,
            same = same
        )
    } catch (t: Throwable) {
        Log.w(TAG, "rejtes dontes hiba: ${t.message}")
        emptySet()
    }

    /**
     * TÖRÖLHETŐ-E a sor (feketelistás szám sora) — SZIGORÚ egyezéssel.
     * A rendszer-összevetést (PhoneNumberUtils) itt SZÁNDÉKOSAN nem
     * használjuk a feketelistára: az az utolsó hét számjegyre is ráhúz, és a
     * törlés visszafordíthatatlan. A fehérlista védő oldalon laza marad.
     */
    fun isPurgeRow(number: String, normalized: String): Boolean = try {
        CallLogVisibility.isPurgeRow(number, normalized, blacklist, whitelist, same)
    } catch (_: Throwable) {
        false
    }

    /** Az épp elutasított hívás sora-e — szigorú egyezéssel, időablakkal. */
    fun isPurgeTarget(
        number: String,
        normalized: String,
        date: Long,
        blockedNumber: String,
        blockedAt: Long
    ): Boolean = try {
        CallLogVisibility.isPurgeTarget(
            number, normalized, date, blockedNumber, blockedAt, blacklist, whitelist,
            protectiveSame = same
        )
    } catch (_: Throwable) {
        false
    }

    /** Feketelistás-e (és nem fehérlistás) — A REJTÉSHEZ. Törléshez: [isPurgeRow]. */
    fun isBlacklisted(number: String): Boolean = try {
        CallLogVisibility.isBlacklistedNumber(number, blacklist, whitelist, same)
    } catch (_: Throwable) {
        false
    }

    /** Egy értesítés szövegében szerepel-e feketelistás szám. */
    fun textMentionsBlacklisted(text: String): Boolean = try {
        CallLogVisibility.textMentionsBlacklisted(text, blacklist, whitelist, same)
    } catch (_: Throwable) {
        false
    }

    fun visibleFilteredCalls(items: List<FilteredCall>): List<FilteredCall> = try {
        CallLogVisibility.visibleFilteredCalls(items, blacklist, whitelist, same)
    } catch (_: Throwable) {
        items
    }

    val hasBlacklist: Boolean get() = blacklist.isNotEmpty()

    companion object {
        private const val TAG = "SDL_HIVASKAPU"

        /**
         * Pillanatkép a mostani beállításokról. Soha nem dob.
         *
         * @param preloaded ha a hívó már beolvasta a szűrt hívásokat, ne
         *   olvassuk be (és ne értelmezzük a JSON-t) még egyszer
         */
        fun load(context: Context, preloaded: List<FilteredCall>? = null): CallLogGate {
            val app = context.applicationContext ?: context
            return try {
                val hideFiltered = CallFilterStore.isFilteredHiddenInLog(app)
                CallLogGate(
                    context = app,
                    blacklist = CallFilterStore.getBlacklist(app),
                    whitelist = CallFilterStore.getWhitelist(app),
                    hideFiltered = hideFiltered,
                    // A szűrt hívásokat csak akkor olvassuk be, ha kellenek.
                    filtered = if (hideFiltered) {
                        (preloaded ?: FilteredCallStore.all(app)).map {
                            CallLogVisibility.FilteredMark(it.number, it.at, it.reason)
                        }
                    } else {
                        emptyList()
                    }
                )
            } catch (t: Throwable) {
                Log.w(TAG, "kapu betoltes hiba: ${t.message}")
                CallLogGate(app, emptyList(), emptyList(), false, emptyList())
            }
        }

        /**
         * Lehet-e a sor egy SZŰRT hívás nyoma. Csak az elutasított, blokkolt,
         * nem fogadott és hangpostára ment sor — a fogadott, a máshol felvett
         * és a kimenő hívás SOSEM: az ténylegesen létrejött, azt nem rejthetjük
         * el „szűrtként".
         */
        fun isFilterableType(type: Int): Boolean = when (type) {
            CallLog.Calls.MISSED_TYPE,
            CallLog.Calls.VOICEMAIL_TYPE,
            CallLog.Calls.REJECTED_TYPE,
            CallLog.Calls.BLOCKED_TYPE -> true
            else -> false
        }
    }
}
