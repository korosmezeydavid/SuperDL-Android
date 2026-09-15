package com.superdl.launcher.home

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.superdl.launcher.gps.GpsLocationHelper
import com.superdl.launcher.patrol.PatrolAnnouncer
import com.superdl.launcher.sms.SmsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AZ OTTHON-ELLENŐRZÉS.
 *
 * Pár másodpercre felébred, körülnéz, és eldönti, otthon van-e a felhasználó.
 * Utána azonnal leáll. Nem fut folyamatosan, nem figyel, nem követ.
 *
 * A LÁNC:
 *   1. wifi és mobilcella leolvasása — ez azonnali és ingyen van;
 *   2. ha ebből eldől, kész (ez a leggyakoribb eset);
 *   3. ha nem dől el, GPS-re várunk, de legfeljebb 40 másodpercet;
 *   4. döntés;
 *   5. ha nincs otthon: visszaszámlálás (ha kérted), majd üzenet.
 *
 * OTTHON ESETÉN NÉMA MARAD. Ez a lényeg: aki hazaért, ne kapjon bemondást
 * este tízkor arról, hogy hazaért. Csak a naplóba kerül be.
 */
class HomeWatchService : Service() {

    companion object {
        const val CHANNEL_ID = "HOME_WATCH_CHANNEL"
        const val ACTION_STOP = "com.superdl.launcher.home.STOP"
        const val EXTRA_MANUAL = "kezi_proba"
        const val EXTRA_FOLLOWUP = "frissites"

        /** Ennyit várunk a GPS-re, ha a wifi és a cella nem döntött. */
        private const val GPS_WINDOW_MS = 40_000L

        /**
         * Ennyit várunk a GPS-re CSAK AZ ÜZENET KEDVÉÉRT, ha a döntés már
         * megvan, de helyzetünk nincs. Rövidebb, mint a döntéshez szabott
         * ablak: a riasztást nem késleltetheti sokáig.
         */
        private const val MESSAGE_GPS_WINDOW_MS = 20_000L

        /** A visszaszámlálás hossza. Elég ahhoz, hogy elő lehessen venni a telefont. */
        private const val COUNTDOWN_SECONDS = 45

        /** Fut-e épp ellenőrzés. Innen tudja a menü, hogy ne indítson másikat. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** A visszaszámlálás megállítását a riasztási képernyő kéri. */
        @Volatile
        var stopRequested: Boolean = false
            private set

        fun requestStop() {
            stopRequested = true
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var gpsListener: LocationListener? = null

    @Volatile
    private var bestLocation: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundSafely()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestStop()
            return START_NOT_STICKY
        }
        if (isRunning) return START_NOT_STICKY
        isRunning = true
        stopRequested = false
        val manual = intent?.getBooleanExtra(EXTRA_MANUAL, false) ?: false
        val followUp = intent?.getBooleanExtra(EXTRA_FOLLOWUP, false) ?: false

        scope.launch {
            try {
                run(manual, followUp)
            } catch (t: Throwable) {
                // VÉDŐHÁLÓ: egy csendben elbukott ellenőrzés a legrosszabb
                // kimenetel, mert a felhasználó azt hiszi, van védőhálója.
                Log.w("SDL_OTTHON", "ellenorzes hiba", t)
                note("Az ellenőrzés hibába ütközött és leállt.")
                announce("Az otthon-ellenőrzés hibába ütközött. Nem küldtem üzenetet.")
            } finally {
                isRunning = false
                stopGps()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // ── A LÁNC ───────────────────────────────────────────────────────────

    private suspend fun run(manual: Boolean, followUp: Boolean) {
        notify(if (followUp) "Frissítés…" else "Körülnézek…")

        // Egy ÚJ esti ellenőrzés tiszta lappal indul: a tegnapi frissítések
        // számlálója nullázódik, és egy régen ottfelejtett frissítés sem
        // szólalhat meg a semmiből.
        if (!followUp) {
            HomeWatchSettings.resetFollowUps(this)
            HomeWatchFollowUp.cancel(this)
        }

        // 1. Az olcsó jelek: kapcsolódó wifi és látott mobilcellák.
        var sample = HomeEnvironment.sample(this, GpsLocationHelper.getLastLocation(this))
        var check = HomeDetector.check(this, sample)

        // 2. Ha nem dőlt el, megvárjuk a GPS-t — de nem örökké.
        if (check.verdict == HomeVerdict.UNKNOWN) {
            startGps()
            val deadline = System.currentTimeMillis() + GPS_WINDOW_MS
            while (System.currentTimeMillis() < deadline) {
                delay(2_000L)
                val loc = bestLocation ?: continue
                sample = HomeEnvironment.sample(this, loc)
                check = HomeDetector.check(this, sample)
                if (check.verdict != HomeVerdict.UNKNOWN) break
            }
            stopGps()
        }

        when (check.verdict) {
            HomeVerdict.HOME -> {
                // SZÁNDÉKOSAN NÉMA. Aki hazaért, ne kapjon bemondást arról,
                // hogy hazaért. Ez a leggyakoribb eset, és a legjobb kimenetel.
                //
                // FRISSÍTÉSKOR EZ KÜLÖN FONTOS: közben hazaérhetett. Ilyenkor
                // a függő frissítéseket le kell mondani, különben negyedóra
                // múlva riasztás menne valakiről, aki már otthon alszik.
                HomeWatchFollowUp.cancel(this)
                note(
                    if (followUp) "Frissítéskor már otthon voltál. Nem küldtem többet."
                    else "Otthon voltál (${check.reason}). Nem küldtem semmit."
                )
                if (manual) {
                    announce("Próba: otthon vagy. ${check.reason}. Ilyenkor nem küldenék semmit.")
                }
            }

            HomeVerdict.UNKNOWN -> {
                // NEM RIASZTUNK VAKTÁBAN, DE HALLGATNI SEM HALLGATUNK.
                // Egy hamis riasztás után a felhasználó kikapcsolja az
                // egészet, és onnantól nincs védőháló. Ezért itt nem megy ki
                // üzenet — viszont KIMONDJUK, hogy nem sikerült eldönteni,
                // hogy ne éljen abban a hitben, hogy minden rendben volt.
                note("Nem lehetett eldönteni (${check.reason}). Nem küldtem üzenetet.")
                announce(
                    "Otthon-ellenőrzés: nem tudtam megállapítani, otthon vagy-e, mert " +
                        "${check.reason}. Ezért nem küldtem üzenetet."
                )
            }

            HomeVerdict.AWAY -> alarm(check, sample.location, manual, followUp)
        }
    }

    // ── A RIASZTÁS ───────────────────────────────────────────────────────

    private suspend fun alarm(
        check: HomeCheck,
        location: Location?,
        manual: Boolean,
        followUp: Boolean
    ) {
        val probe = manual || HomeWatchSettings.isProbe(this)
        val numbers = HomeWatchSettings.numbers(this)
        val deadline = HomeWatchSettings.speakTime(this)

        if (numbers.isEmpty() && !probe) {
            note("Nem voltál otthon, de nincs kitöltve egyetlen S.O.S. szám sem.")
            announce(
                "Otthon-ellenőrzés: nem vagy otthon, de nincs kitöltve egyetlen " +
                    "S.O.S. szám sem, ezért nem tudtam üzenni senkinek."
            )
            return
        }

        // 1. A HELYZET BEGYŰJTÉSE, MÁR MOST. A döntés sokszor a wifiből vagy
        //    a cellából született, és olyankor a GPS-t el sem indítottuk —
        //    a segítőnek viszont helyzet kell. A GPS itt indul, és a
        //    visszaszámlálás alatt végig gyűjt: az a negyvenöt másodperc
        //    amúgy is eltelik, legalább dolgozzon.
        startGps()

        // 2. VISSZASZÁMLÁLÁS. Ez az, ami a hamis riasztást megfogja: ha a
        //    felhasználó ott van a telefonnál, egy balra söpréssel leállítja.
        //    Frissítéskor NINCS visszaszámlálás: az első riasztást már nem
        //    állította le, a másodikat éjfélkor pláne nem fogja.
        if (!followUp && HomeWatchSettings.isCountdownEnabled(this)) {
            notify("Visszaszámlálás…")
            openAlert(check.reason, probe)
            for (second in COUNTDOWN_SECONDS downTo 1) {
                if (stopRequested) {
                    stopGps()
                    HomeWatchFollowUp.cancel(this)
                    note("Nem voltál otthon, de te állítottad le a riasztást.")
                    announce("Otthon-figyelés leállítva. Nem küldtem üzenetet.")
                    return
                }
                delay(1_000L)
            }
        } else {
            // Visszaszámlálás nélkül is várunk egy keveset a helyzetre, ha
            // egyáltalán nincs mit kiküldeni. Néhány másodperc késés árán a
            // segítő térképet kap a „nem tudom, hol vagyok" helyett.
            waitForLocationIfEmpty(location)
        }
        stopGps()
        if (stopRequested) {
            HomeWatchFollowUp.cancel(this)
            note("Nem voltál otthon, de te állítottad le a riasztást.")
            return
        }

        // 3. A LEGJOBB HELYZET ÉS AZ UTCANÉV.
        val fix = HomeLocationResolver.bestWithLastKnown(this, bestLocation, location)
        // Az utcanév hálózatot igényel, ezért HÁTTÉRSZÁLON kérjük le. A fő
        // szálat semmilyen körülmények között nem foghatja le: ha nem jön meg,
        // egyszerűen elmarad, és megy az üzenet nélküle.
        val address = if (fix == null) null else withContext(Dispatchers.IO) {
            HomeLocationResolver.addressOrNull(fix)
        }

        // 4. AZ ÜZENET.
        val message = if (followUp) {
            HomeWatchMessage.buildUpdate(fix, address)
        } else {
            HomeWatchMessage.build(null, deadline, fix, address)
        }

        if (probe) {
            // PRÓBA MÓD: mindent végigcsinálunk, de NEM küldünk. A frissítést
            // is beütemezzük, mert a próba akkor ér valamit, ha tényleg
            // mindent végigjátszik — csak éppen néma marad kifelé.
            if (followUp) HomeWatchSettings.noteFollowUpSent(this)
            HomeWatchFollowUp.schedule(this)
            note("PRÓBA: nem voltál otthon (${check.reason}). Élesben ment volna üzenet.")
            announce(
                "Próba mód. Nem vagy otthon: ${check.reason}. Élesben most " +
                    "${cimzettSzoveg(numbers.size)} ment volna üzenet. " +
                    helyzetSzoveg(fix) + " Nem küldtem el semmit."
            )
            return
        }

        var sent = 0
        for (number in numbers) {
            val ok = try {
                SmsHelper.send(this, number, message)
            } catch (t: Throwable) {
                Log.w("SDL_OTTHON", "kuldes hiba: ${t.message}")
                false
            }
            if (ok) sent++
        }

        if (sent > 0) {
            if (followUp) HomeWatchSettings.noteFollowUpSent(this)
            // A KÖVETKEZŐ FRISSÍTÉS. Csak akkor ütemezünk, ha tényleg ment ki
            // üzenet: egy elbukott küldés után a frissítés sem fog átmenni,
            // és nem érdemes vele altatni a felhasználót.
            HomeWatchFollowUp.schedule(this)
            note(
                if (followUp) "Frissítés: még mindig nem voltál otthon. Elment $sent címzettnek."
                else "Nem voltál otthon (${check.reason}). Üzenet elment $sent címzettnek."
            )
            announce(
                if (followUp) "Otthon-figyelés: friss helyzetet küldtem $sent címzettnek."
                else "Otthon-figyelés: nem vagy otthon, ezért üzentem $sent címzettnek."
            )
        } else {
            // A NÉMA KUDARC A LEGROSSZABB. Ha nem ment ki, azt ki kell mondani.
            note("Nem voltál otthon, de az üzenetet NEM sikerült elküldeni.")
            announce(
                "Figyelem! Otthon-figyelés: nem vagy otthon, de az üzenetet nem " +
                    "sikerült elküldeni. Senki nem tud rólad."
            )
        }
    }

    private fun cimzettSzoveg(n: Int): String =
        if (n == 1) "egy címzettnek" else "$n címzettnek"

    /** Próba módban ezt hallja a felhasználó a helyzetről. */
    private fun helyzetSzoveg(fix: Location?): String {
        if (fix == null) return "A helyzetemet nem sikerült megállapítani."
        val age = HomeLocationResolver.ageMinutes(fix)
        return if (age == null) {
            "A helyzet friss."
        } else {
            "A helyzet $age perces lett volna."
        }
    }

    /**
     * Ha egyáltalán nincs helyzetünk, várunk rá egy keveset — de csak akkor.
     * Ha már van valamink, nem késleltetjük a riasztást a tökéletesért.
     */
    private suspend fun waitForLocationIfEmpty(existing: Location?) {
        if (existing != null || bestLocation != null) return
        val deadline = System.currentTimeMillis() + MESSAGE_GPS_WINDOW_MS
        while (System.currentTimeMillis() < deadline) {
            if (stopRequested) return
            delay(1_000L)
            if (bestLocation != null) return
        }
    }

    private fun openAlert(reason: String, probe: Boolean) {
        try {
            val intent = Intent(this, HomeWatchAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(HomeWatchAlertActivity.EXTRA_REASON, reason)
                putExtra(HomeWatchAlertActivity.EXTRA_PROBE, probe)
                putExtra(HomeWatchAlertActivity.EXTRA_SECONDS, COUNTDOWN_SECONDS)
            }
            startActivity(intent)
        } catch (t: Throwable) {
            Log.w("SDL_OTTHON", "riasztasi kepernyo hiba: ${t.message}")
        }
    }

    // ── Segédek ──────────────────────────────────────────────────────────

    private fun note(text: String) {
        try {
            HomeWatchSettings.noteOutcome(this, text)
        } catch (_: Throwable) {
        }
    }

    /**
     * A bemondás a `PatrolAnnouncer`-en megy, mert a felhasználó ilyenkor
     * jellemzően NEM a Super DL-t nézi — lehet, hogy a telefon a zsebében van.
     */
    private fun announce(text: String) {
        try {
            PatrolAnnouncer.announce(applicationContext, text, critical = true)
        } catch (t: Throwable) {
            Log.w("SDL_OTTHON", "bemondas hiba: ${t.message}")
        }
    }

    private fun startGps() {
        gpsListener = try {
            GpsLocationHelper.requestUpdates(this, 2_000L) { location ->
                val best = bestLocation
                if (best == null || !best.hasAccuracy() ||
                    (location.hasAccuracy() && location.accuracy < best.accuracy)
                ) {
                    bestLocation = location
                }
            }
        } catch (t: Throwable) {
            Log.w("SDL_OTTHON", "gps hiba: ${t.message}")
            null
        }
    }

    private fun stopGps() {
        val listener = gpsListener ?: return
        gpsListener = null
        try {
            GpsLocationHelper.removeUpdates(this, listener)
        } catch (_: Throwable) {
        }
    }

    private fun startForegroundSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    7301,
                    buildNotification("Otthon-ellenőrzés"),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(7301, buildNotification("Otthon-ellenőrzés"))
            }
        } catch (t: Throwable) {
            Log.w("SDL_OTTHON", "elotér-inditas hiba: ${t.message}")
            try {
                startForeground(7301, buildNotification("Otthon-ellenőrzés"))
            } catch (_: Throwable) {
            }
        }
    }

    private fun notify(text: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(7301, buildNotification(text))
        } catch (_: Throwable) {
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Super DL otthon-figyelés")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        try {
            val channel = NotificationChannel(
                CHANNEL_ID, "Otthon-figyelés", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "A napi otthon-ellenőrzés futása" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        isRunning = false
        stopGps()
        scope.cancel()
        super.onDestroy()
    }
}
