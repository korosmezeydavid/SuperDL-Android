package com.superdl.launcher.callfilter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.superdl.launcher.gps.GpsLocationHelper
import com.superdl.launcher.patrol.PatrolAnnouncer

/**
 * A HELY ALAPÚ FÓKUSZOK FIGYELÉSE.
 *
 * MIÉRT NEGYEDÓRÁNKÉNT, ÉS NEM FOLYAMATOSAN: a folyamatos GPS-figyelés
 * napok alatt lemeríti az akkumulátort, és egy vak felhasználónál a lemerült
 * telefon nem kényelmetlenség, hanem baj. A munkahelyre érkezés öt perccel
 * későbbi észrevétele senkinek nem fáj.
 *
 * MIÉRT AZ UTOLSÓ ISMERT HELYZET: nem indítunk külön GPS-mérést. A telefon
 * az útvonaltervezőtől, az órától, más alkalmazásoktól amúgy is kap
 * helyzetet; ha épp nincs friss, kihagyjuk ezt a kört. Egy fókusz nem éri
 * meg a külön mérés árát.
 */
object PlaceFocusWatcher {

    private const val TAG = "SDL_HELYFOKUSZ"
    private const val REQUEST = 91_000

    const val ACTION_CHECK = "com.superdl.launcher.callfilter.HELY_FOKUSZ_ELLENORZES"

    /** Ennyinél régebbi helyzettel nem döntünk. */
    private const val MAX_AGE_MS = 20 * 60_000L

    private const val PERIOD_MS = 15 * 60_000L

    // ── Ütemezés ─────────────────────────────────────────────────────────

    /**
     * Beállítja vagy leállítja a figyelést aszerint, hogy van-e egyáltalán
     * bekapcsolt hely alapú fókusz. Egy figyelés, aminek nincs mit figyelnie,
     * csak akkumulátort eszik.
     */
    fun sync(context: Context) {
        val app = context.applicationContext
        val kell = PlaceFocusStore.all(app).any { it.enabled }
        if (kell) start(app) else stop(app)
    }

    private fun start(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            manager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60_000L,
                PERIOD_MS,
                pendingIntent(context)
            )
        } catch (e: Exception) {
            Log.w(TAG, "utemezes hiba: ${e.message}")
        }
    }

    private fun stop(context: Context) {
        try {
            context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        } catch (_: Exception) {
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PlaceFocusReceiver::class.java).apply {
            action = ACTION_CHECK
        }
        return PendingIntent.getBroadcast(
            context, REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── Az ellenőrzés ────────────────────────────────────────────────────

    /**
     * @param manual igaz, ha a felhasználó kérte — ilyenkor MINDIG mondunk
     *        valamit, akkor is, ha nem változott semmi
     * @return amit ki lehet mondani, vagy null, ha nincs mit
     */
    fun check(context: Context, manual: Boolean = false): String? {
        val app = context.applicationContext
        val items = PlaceFocusStore.all(app).filter { it.enabled }
        if (items.isEmpty()) {
            return if (manual) "Nincs bekapcsolt hely alapú fókuszod." else null
        }

        if (!GpsLocationHelper.hasPermission(app)) {
            val text = "A helyzet engedélye hiányzik, ezért a hely alapú fókusz nem működik."
            PlaceFocusStore.noteOutcome(app, text)
            return if (manual) text else null
        }

        val location = GpsLocationHelper.getLastLocation(app)
        if (location == null || System.currentTimeMillis() - location.time > MAX_AGE_MS) {
            val text = "Nem volt friss helyzet, ezért most nem döntöttem."
            PlaceFocusStore.noteOutcome(app, text)
            return if (manual) text else null
        }

        val hit = items.firstOrNull { it.contains(location) }
        val previous = PlaceFocusStore.activeId(app)
        val probe = PlaceFocusStore.isProbe(app)

        // NEM VÁLTOZOTT SEMMI: csendben maradunk. Egy negyedóránként
        // megszólaló program elviselhetetlen.
        if (hit?.id == previous) {
            val text = if (hit == null) {
                "Egyik helynél sem vagy."
            } else {
                "Továbbra is itt vagy: ${hit.name}."
            }
            PlaceFocusStore.noteOutcome(app, text)
            return if (manual) text else null
        }

        PlaceFocusStore.noteActive(app, hit?.id)

        val text = when {
            hit != null && probe ->
                "Próba: megérkeztél ide: ${hit.name}. Élesben most bekapcsolnám ezt: " +
                    "${hit.mode.menuLabel}. Próba módban nem szűrök semmit."
            hit != null ->
                "Megérkeztél ide: ${hit.name}. Bekapcsoltam: ${hit.mode.menuLabel}."
            probe ->
                "Próba: elhagytad ezt a helyet. Élesben most visszaállna a szokásos szűrés."
            else ->
                "Elhagytad a helyet. Visszaállt a szokásos szűrés."
        }
        PlaceFocusStore.noteOutcome(app, text)

        // A VÁLTOZÁST KI KELL MONDANI. Ha a telefon magától elkezd hívásokat
        // elutasítani, azt a felhasználónak tudnia kell — különben azt hiszi,
        // hogy senki nem keresi.
        if (!manual) {
            PatrolAnnouncer.announce(app, text, critical = false)
        }
        return text
    }
}

/** A negyedórás ébresztő fogadója. */
class PlaceFocusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlaceFocusWatcher.ACTION_CHECK) return
        val pending = goAsync()
        Thread {
            try {
                PlaceFocusWatcher.check(context.applicationContext)
            } catch (t: Throwable) {
                Log.w("SDL_HELYFOKUSZ", "ellenorzes hiba: ${t.message}")
            } finally {
                try { pending.finish() } catch (_: Throwable) {}
            }
        }.start()
    }
}
