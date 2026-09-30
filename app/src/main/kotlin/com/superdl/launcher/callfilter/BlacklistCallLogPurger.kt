package com.superdl.launcher.callfilter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * FEKETELISTÁS HÍVÁSOK TÖRLÉSE A RENDSZER HÍVÁSNAPLÓJÁBÓL.
 *
 * ALPH DÖNTÉSE (2026-09-30): „akit odaraktunk, azt pontosan azért raktuk
 * oda, hogy ne is tudjunk róla! ne is tudjon még gondolati szinten se
 * zaklatni, irritálni." A teljes törlést kifejezetten megengedte.
 *
 * CSAK FEKETELISTÁS SZÁM SORÁT TÖRÖLJÜK — soha semmi mást. Minden törlés
 * előtt újra megnézzük, hogy a szám MOST is feketelistás-e és nem
 * fehérlistás: ha közben levetted a listáról, a sor marad.
 *
 * SZIGORÚ EGYEZÉS: a törlés visszafordíthatatlan, ezért itt NINCS „utolsó hét
 * számjegy" és nincs rendszer-összevetés (PhoneNumberUtils) — a sor számának
 * (vagy a rendszer normalizált számának) E.164 alakja pontosan egyezzen a
 * feketelistás száméval ([CallLogVisibility.samePhoneStrict]).
 *
 * MIÉRT KÉSLELTETVE, TÖBBSZÖR: a rendszer a hívásnapló sorát a hívás VÉGE
 * UTÁN, a saját tempójában írja be. Az elutasítás pillanatában még nincs mit
 * törölni. Ezért három, egyre későbbi próbát teszünk (3, 10 és 25 másodperc).
 *
 * MIÉRT KÜLÖN SZÁLON: a hívásnapló lekérdezése és törlése lemezművelet. A fő
 * szálon a felület akadna — egy vak felhasználónál ez azt jelenti, hogy a
 * beszéd elakad.
 *
 * SOHA NEM DOB: a hívásszűrő szolgáltatásból hívjuk; egy elszálló takarítás
 * nem viheti magával a szűrést. Minden eredmény a naplóba kerül (SDL_FEKETE).
 */
object BlacklistCallLogPurger {

    private const val TAG = "SDL_FEKETE"

    /** A próbák ideje az elutasítás után. */
    private val RETRY_DELAYS_MS = longArrayOf(3_000L, 10_000L, 25_000L)

    /** Egy törlési kérésben legfeljebb ennyi azonosító (az SQL-korlát alatt). */
    private const val DELETE_BATCH = 100

    @Volatile
    private var worker: Handler? = null

    /** Egyetlen háttérszál a takarításhoz — lustán indul, aztán életben marad. */
    private fun worker(): Handler {
        worker?.let { return it }
        synchronized(this) {
            worker?.let { return it }
            val thread = HandlerThread("sdl-feketelista-torles").apply { start() }
            return Handler(thread.looper).also { worker = it }
        }
    }

    /** Van-e jogunk olvasni ÉS írni a hívásnaplót. */
    fun hasPermission(context: Context): Boolean = try {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    // ── Egy elutasított hívás után ───────────────────────────────────────

    /**
     * A szűrő épp elutasított egy feketelistás hívást: töröljük a sorát, amint
     * a rendszer beírta. A hívó oldalon nem kell semmit ellenőrizni — itt
     * nézzük meg, be van-e kapcsolva, feketelistás-e, van-e engedély.
     */
    fun scheduleAfterBlock(context: Context, number: String?, blockedAt: Long) {
        try {
            val app = context.applicationContext ?: context
            val szam = number.orEmpty().trim()
            if (CallLogVisibility.isHiddenNumber(szam)) return
            // MINDEN ELLENŐRZÉS A HÁTTÉRSZÁLON: ezt a hívásszűrő a fő szálon
            // hívja, a döntés után — ott egy pillanatot se vegyünk el.
            val found = java.util.concurrent.atomic.AtomicInteger(0)
            RETRY_DELAYS_MS.forEachIndexed { i, delay ->
                val first = i == 0
                val last = i == RETRY_DELAYS_MS.lastIndex
                worker().postDelayed({
                    val n = purgeOne(app, szam, blockedAt, logSkip = first)
                    if (n > 0) {
                        found.addAndGet(n)
                        Log.i(TAG, "feketelistas hivas sora torolve (${i + 1}. proba): $n db")
                    } else if (last && found.get() == 0 && n == 0) {
                        Log.i(TAG, "a feketelistas hivas sora nem jelent meg a hivasnaploban")
                    }
                }, delay)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "utemezes hiba: ${t.message}")
        }
    }

    /**
     * Egy konkrét hívás sorainak törlése.
     * @return hány sor törlődött; -1, ha most nem is próbáltuk (kikapcsolva,
     *         nincs engedély, vagy a szám már nem feketelistás)
     */
    private fun purgeOne(context: Context, number: String, blockedAt: Long, logSkip: Boolean): Int = try {
        // Minden próbánál újra: közben kikapcsolhattad, vagy levehetted a listáról.
        val gate = CallLogGate.load(context)
        when {
            !CallFilterStore.isBlacklistPurgeEnabled(context) -> -1
            !gate.isPurgeRow(number, "") -> -1
            !hasPermission(context) -> {
                if (logSkip) {
                    Log.i(TAG, "nincs hivasnaplo-irasi engedely, a sor marad (a SuperDL-ben rejtve)")
                }
                -1
            }
            else -> {
                val from = blockedAt - CallLogVisibility.PURGE_WINDOW_MS
                val to = blockedAt + CallLogVisibility.PURGE_WINDOW_MS
                val ids = mutableListOf<Long>()
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls._ID,
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.CACHED_NORMALIZED_NUMBER,
                        CallLog.Calls.DATE
                    ),
                    "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} <= ?",
                    arrayOf(from.toString(), to.toString()),
                    null
                )?.use { c ->
                    val iId = c.getColumnIndex(CallLog.Calls._ID)
                    val iNum = c.getColumnIndex(CallLog.Calls.NUMBER)
                    val iNorm = c.getColumnIndex(CallLog.Calls.CACHED_NORMALIZED_NUMBER)
                    val iDate = c.getColumnIndex(CallLog.Calls.DATE)
                    if (iId >= 0 && iNum >= 0 && iDate >= 0) {
                        while (c.moveToNext()) {
                            val rowNumber = c.getString(iNum).orEmpty()
                            val rowNorm = if (iNorm >= 0) c.getString(iNorm).orEmpty() else ""
                            if (gate.isPurgeTarget(rowNumber, rowNorm, c.getLong(iDate), number, blockedAt)) {
                                ids.add(c.getLong(iId))
                            }
                        }
                    }
                }
                deleteIds(context, ids)
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "torles hiba: ${t.message}")
        -1
    }

    // ── Az egész hívásnapló takarítása, kérésre ──────────────────────────

    sealed class Result {
        /** Nincs hívásnapló-írási (vagy olvasási) engedély. */
        object NoPermission : Result()

        /** A számolás eredménye: ezek a sorok törlődnének. */
        data class Found(val ids: List<Long>) : Result()

        /** Ennyi sort töröltünk (lehet nulla is). */
        data class Deleted(val count: Int) : Result()

        /** Valami elszállt; a szöveg a naplóba megy, nem a felhasználónak. */
        data class Failed(val message: String) : Result()
    }

    /**
     * A „Feketelistás hívások törlése most" ELSŐ LÉPÉSE: MEGSZÁMOLJA, mit
     * törölne — minden olyan sort, amelynek a száma SZIGORÚAN feketelistás
     * (és nem fehérlistás), iránytól függetlenül. NEM TÖRÖL SEMMIT.
     *
     * MIÉRT KÉT LÉPÉS: a törlés visszavonhatatlan, és vakon egy elsöpört
     * mozdulat is elindíthatná. A hívó a darabszámmal rákérdez, és csak a
     * jóváhagyás után hívja a [deleteCounted]-et.
     *
     * LASSÚ LEHET (az egész naplót átnézi), ezért a hívó HÁTTÉRSZÁLON futtassa.
     * @return [Result.Found], [Result.NoPermission] vagy [Result.Failed]
     */
    fun countAll(context: Context): Result {
        val app = context.applicationContext ?: context
        if (!hasPermission(app)) return Result.NoPermission
        return try {
            val gate = CallLogGate.load(app)
            if (!gate.hasBlacklist) return Result.Found(emptyList())
            val ids = mutableListOf<Long>()
            app.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NORMALIZED_NUMBER),
                null,
                null,
                null
            )?.use { c ->
                val iId = c.getColumnIndex(CallLog.Calls._ID)
                val iNum = c.getColumnIndex(CallLog.Calls.NUMBER)
                val iNorm = c.getColumnIndex(CallLog.Calls.CACHED_NORMALIZED_NUMBER)
                if (iId >= 0 && iNum >= 0) {
                    while (c.moveToNext()) {
                        val rowNumber = c.getString(iNum).orEmpty()
                        val rowNorm = if (iNorm >= 0) c.getString(iNorm).orEmpty() else ""
                        if (gate.isPurgeRow(rowNumber, rowNorm)) ids.add(c.getLong(iId))
                    }
                }
            }
            Log.i(TAG, "kezi takaritas: ${ids.size} talalat")
            Result.Found(ids)
        } catch (t: SecurityException) {
            Log.w(TAG, "kezi takaritas: engedely hiba: ${t.message}")
            Result.NoPermission
        } catch (t: Throwable) {
            Log.w(TAG, "kezi takaritas szamolas hiba: ${t.message}")
            Result.Failed(t.message.orEmpty())
        }
    }

    /**
     * A MÁSODIK LÉPÉS, a jóváhagyás után: CSAK a megszámolt sorokat törli, és
     * azokat is csak akkor, ha törléskor is szigorúan feketelistásak. Ami
     * azóta került a naplóba, az nem törlődik — arról nem kérdeztünk.
     *
     * HÁTTÉRSZÁLON futtassa a hívó.
     * @return [Result.Deleted], [Result.NoPermission] vagy [Result.Failed]
     */
    fun deleteCounted(context: Context, ids: List<Long>): Result {
        val app = context.applicationContext ?: context
        if (!hasPermission(app)) return Result.NoPermission
        if (ids.isEmpty()) return Result.Deleted(0)
        return try {
            val gate = CallLogGate.load(app)
            val confirmed = mutableListOf<Long>()
            ids.chunked(DELETE_BATCH).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                app.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NORMALIZED_NUMBER),
                    "${CallLog.Calls._ID} IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray(),
                    null
                )?.use { c ->
                    val iId = c.getColumnIndex(CallLog.Calls._ID)
                    val iNum = c.getColumnIndex(CallLog.Calls.NUMBER)
                    val iNorm = c.getColumnIndex(CallLog.Calls.CACHED_NORMALIZED_NUMBER)
                    if (iId >= 0 && iNum >= 0) {
                        while (c.moveToNext()) {
                            val rowNumber = c.getString(iNum).orEmpty()
                            val rowNorm = if (iNorm >= 0) c.getString(iNorm).orEmpty() else ""
                            if (gate.isPurgeRow(rowNumber, rowNorm)) confirmed.add(c.getLong(iId))
                        }
                    }
                }
            }
            val deleted = deleteIds(app, confirmed)
            Log.i(TAG, "kezi takaritas: ${ids.size} jovahagyva, ${confirmed.size} ellenorizve, $deleted torolve")
            Result.Deleted(deleted)
        } catch (t: SecurityException) {
            Log.w(TAG, "kezi takaritas: engedely hiba: ${t.message}")
            Result.NoPermission
        } catch (t: Throwable) {
            Log.w(TAG, "kezi takaritas hiba: ${t.message}")
            Result.Failed(t.message.orEmpty())
        }
    }

    /** Törlés azonosító szerint, adagokban. @return a ténylegesen törölt sorok */
    private fun deleteIds(context: Context, ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        var deleted = 0
        ids.chunked(DELETE_BATCH).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            deleted += context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} IN ($placeholders)",
                chunk.map { it.toString() }.toTypedArray()
            )
        }
        return deleted
    }
}
