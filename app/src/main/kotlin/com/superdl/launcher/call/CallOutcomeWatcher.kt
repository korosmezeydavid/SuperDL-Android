package com.superdl.launcher.call

import android.content.Context
import android.provider.CallLog
import com.superdl.launcher.contacts.ContactHelper
import com.superdl.launcher.reminder.LaterReminder
import com.superdl.launcher.reminder.LaterReminderStore

/**
 * MI LETT A HÍVÁSSAL — ÉS MIT KEZDJÜNK VELE.
 *
 * ALPH KÉT KÉRÉSE (2026-09-21):
 *
 * 1. „ha valakit hívsz, érzékelje hogy a kimenő hívás sikertelen volt, és
 *    ajánlja fel az emlékeztető beállítást"
 * 2. „ha időközben az adott kontakt visszahív, rákérdezzen hogy törölje-e a
 *    beállított visszahívási emlékeztetőt"
 *
 * MIÉRT A HÍVÁSNAPLÓBÓL, ÉS NEM A HÍVÁS KÖZBEN: a hívás közbeni figyelés
 * csak akkor működik, ha a SuperDL az alapértelmezett telefon alkalmazás.
 * A hívásnapló MINDIG ott van, akárhonnan indult a hívás — a gyári
 * tárcsázóból is. Egy funkció, ami a felhasználók felénél nem működik,
 * rosszabb, mint ha nem lenne: ott ül a menüben, és hazudik.
 *
 * MIT JELENT A „SIKERTELEN": a hívásnapló szerint kimenő hívás volt, és a
 * hossza NULLA másodperc. Nem vette fel, foglalt volt, nem volt elérhető —
 * a végeredmény mindháromnál ugyanaz: nem beszéltetek.
 *
 * MIÉRT VAN HATÁRIDŐ: egy tegnapi nem sikerült hívásról ma reggel
 * rákérdezni bosszantó. Csak a friss hívásokról kérdezünk.
 */
object CallOutcomeWatcher {

    private const val PREFS = "superdl"
    private const val KEY_LAST_SEEN = "hivas_kimenetel_utolso"
    private const val KEY_ENABLED = "hivas_kimenetel_kerdez"

    /** Ennél régebbi hívásról már nem kérdezünk. */
    private const val FRESH_MS = 15 * 60_000L

    /** Egyszerre ennyi napló-tételt nézünk át. Több nem kell. */
    private const val SCAN_LIMIT = 25

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Ki-be ────────────────────────────────────────────────────────────

    /**
     * ALAPBÓL BE VAN KAPCSOLVA, de kikapcsolható. Aki naponta húszszor
     * telefonál és a fele nem veszi fel, annak ez húsz kérdés naponta.
     */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    // ── Amit találtunk ───────────────────────────────────────────────────

    /** Egy kimenő hívás, ami nem jött össze. */
    data class Failed(val number: String, val name: String, val at: Long) {
        fun who(): String = if (name.isNotBlank()) name else number
    }

    /** Valaki visszahívott, akire emlékeztető van beállítva. */
    data class CalledBack(
        val reminderId: Int,
        val number: String,
        val name: String,
        val at: Long
    ) {
        fun who(): String = if (name.isNotBlank()) name else number
    }

    data class Outcome(val failed: Failed?, val calledBack: CalledBack?)

    // ── A vizsgálat ──────────────────────────────────────────────────────

    /**
     * Átnézi a legutóbbi vizsgálat óta keletkezett hívásnapló-tételeket.
     *
     * FONTOS: a vizsgálat MEGJEGYZI, meddig jutott — ugyanarról a hívásról
     * csak egyszer kérdezünk. Aki egyszer nemet mondott, ne kapja meg
     * ugyanazt a kérdést minden képernyőnyitáskor.
     */
    fun scan(context: Context): Outcome {
        if (!isEnabled(context)) return Outcome(null, null)
        val lastSeen = prefs(context).getLong(KEY_LAST_SEEN, 0L)
        val now = System.currentTimeMillis()

        // ELSŐ INDULÁS: nem nézünk vissza a múltba. Aki most telepítette a
        // frissítést, ne kapjon kérdést egy három nappal ezelőtti hívásról.
        if (lastSeen == 0L) {
            noteSeen(context, now)
            return Outcome(null, null)
        }

        val entries = readEntries(context, lastSeen)
        if (entries.isEmpty()) return Outcome(null, null)

        val newest = entries.maxOfOrNull { it.date } ?: lastSeen
        noteSeen(context, maxOf(newest, lastSeen))

        val pending = LaterReminderStore.calls(context)

        // 1. VISSZAHÍVOTT-E VALAKI, AKIRE EMLÉKEZTETŐ VAN.
        //    Ez megy elöl: ez a jó hír, és ez szabadít fel egy tételt.
        val calledBack = entries
            .filter { it.incoming && now - it.date <= FRESH_MS }
            .sortedByDescending { it.date }
            .firstNotNullOfOrNull { entry ->
                pending.firstOrNull { sameNumber(it.number, entry.number) }
                    ?.let { hit ->
                        CalledBack(
                            reminderId = hit.id,
                            number = hit.number,
                            name = hit.name.ifBlank { entry.name },
                            at = entry.date
                        )
                    }
            }

        // 2. SIKERTELEN KIMENŐ HÍVÁS.
        //    Akire MÁR VAN emlékeztető, arról nem kérdezünk újra.
        val failed = entries
            .filter { !it.incoming && it.duration == 0L && now - it.date <= FRESH_MS }
            .filter { entry -> pending.none { sameNumber(it.number, entry.number) } }
            .maxByOrNull { it.date }
            ?.let { entry ->
                val name = entry.name.ifBlank {
                    ContactHelper.findNameByPhone(context, entry.number).orEmpty()
                }
                Failed(entry.number, name, entry.date)
            }

        return Outcome(failed, calledBack)
    }

    /** A vizsgálat kiinduló pontjának felvétele — az első indulásnál kell. */
    fun noteSeen(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_LAST_SEEN, at).apply()
    }

    /**
     * Ha a felhasználó nemet mond, a tételt NE kérdezzük meg újra. A
     * megjegyzés már a vizsgálatkor megtörtént, így itt nincs teendő —
     * ez a függvény csak azért van, hogy a hívó oldalon látszódjon a
     * szándék, és ha egyszer finomítjuk, legyen hova nyúlni.
     */
    fun dismiss(context: Context) {
        noteSeen(context, System.currentTimeMillis())
    }

    // ── A hívásnapló olvasása ────────────────────────────────────────────

    private data class Entry(
        val number: String,
        val name: String,
        val date: Long,
        val duration: Long,
        val incoming: Boolean
    )

    private fun readEntries(context: Context, since: Long): List<Entry> {
        val out = mutableListOf<Entry>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                ),
                "${CallLog.Calls.DATE} > ?",
                arrayOf(since.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val iNumber = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val iName = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val iDate = cursor.getColumnIndex(CallLog.Calls.DATE)
                val iDur = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val iType = cursor.getColumnIndex(CallLog.Calls.TYPE)
                var count = 0
                while (cursor.moveToNext() && count < SCAN_LIMIT) {
                    count++
                    val number = if (iNumber >= 0) cursor.getString(iNumber).orEmpty() else ""
                    if (number.isBlank()) continue
                    val type = if (iType >= 0) cursor.getInt(iType) else 0
                    val incoming = type == CallLog.Calls.INCOMING_TYPE ||
                        type == CallLog.Calls.MISSED_TYPE ||
                        type == CallLog.Calls.REJECTED_TYPE
                    val outgoing = type == CallLog.Calls.OUTGOING_TYPE
                    if (!incoming && !outgoing) continue
                    out.add(
                        Entry(
                            number = number,
                            name = if (iName >= 0) cursor.getString(iName).orEmpty() else "",
                            date = if (iDate >= 0) cursor.getLong(iDate) else 0L,
                            duration = if (iDur >= 0) cursor.getLong(iDur) else 0L,
                            incoming = incoming
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            // A hívásnapló olvasása engedélyhez kötött. Ha nincs meg, ez a
            // funkció egyszerűen nem szól — nem hiba, nem is bemondás.
        }
        return out
    }

    /**
     * Két telefonszám akkor azonos, ha az utolsó nyolc számjegyük egyezik.
     * A körzetszám és a nemzetközi előhívó hol ott van, hol nincs.
     */
    private fun sameNumber(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }
        val y = b.filter { it.isDigit() }
        if (x.isBlank() || y.isBlank()) return false
        val n = minOf(8, x.length, y.length)
        return x.takeLast(n) == y.takeLast(n)
    }

    /** A visszahívandó tételek kedvéért — a hívó oldalon jól olvasható. */
    fun kindCall(): String = LaterReminder.KIND_CALL
}
