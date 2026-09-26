package com.superdl.launcher.sos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.superdl.launcher.call.ActiveCallRegistry
import com.superdl.launcher.call.CallHelper
import com.superdl.launcher.call.DialerRoleHelper
import com.superdl.launcher.gps.GpsLocationHelper
import com.superdl.launcher.sms.SmsHelper
import com.superdl.launcher.tts.TtsManager
import kotlinx.coroutines.*

/**
 * AZ S.O.S. LÁNC.
 *
 * A FELHASZNÁLÓ KÉRÉSE, SZÓ SZERINT:
 *   - hívja végig a négy számot;
 *   - az SMS a helyzettel MINDENKÉPP menjen ki, akkor is, ha felvették;
 *   - a hangposta NE számítson elérésnek;
 *   - két teljes kör után a 112, előzetes kérdés nélkül.
 *
 * AMIT EBBŐL MÁSKÉNT OLDOTTUNK MEG, ÉS MIÉRT:
 *
 * 1. A NÉGY SMS AZONNAL, A LÁNC ELEJÉN MEGY KI — nem számonként.
 *    Így mind a négy ismerős másodperceken belül tudja, hol vagy, AKKOR IS,
 *    ha a hívás-lánc bármi miatt elakad (nincs térerő a híváshoz, lemerül a
 *    telefon, a hívás-engedély hiányzik). Az SMS a legszívósabb csatorna:
 *    kimegy olyan hálózaton is, ahol a hívás már nem.
 *
 * 2. A HANGPOSTÁT NEM HEURISZTIKÁVAL SZŰRJÜK.
 *    Nincs megbízható jel arra, hogy ember vette-e fel vagy gép. Egy rossz
 *    tipp itt azt jelentené, hogy rábontunk a legjobb barátodra, aki két
 *    másodperc alatt felvette. Ezért: a lánc a hívás VÉGE UTÁN MINDIG
 *    FOLYTATÓDIK. Ha hangposta volt, magától megy tovább — pontosan ahogy
 *    kérted. Ha ember volt és megbeszéltétek, TE állítod le.
 *
 * 3. A LEÁLLÍTÁS: indítsd el újra az S.O.S.-t.
 *    Nem új gesztus, nem új képernyő — ugyanaz a menüpont, amit már ismersz,
 *    és a hívás-képernyőről is elérhető. Vészhelyzetben megtanulni valami
 *    újat nem lehet. A program ezt minden hívás után kimondja.
 *
 * A LÁNC ALAPSZABÁLYA: MINDIG TOVÁBBLÉP. Minden várakozásnak felső határa
 * van, minden lépés köré védőháló került. Egy beragadt vészjelzés némább,
 * mint a semmi — mert azt hiszed, megy a segítség.
 */
class SosService : Service() {

    companion object {
        const val CHANNEL_ID = "SOS_CHANNEL"
        const val EXTRA_NUMBERS = "sos_numbers"
        const val ACTION_STOP = "com.superdl.launcher.sos.STOP"

        /** Meddig csengessünk, mielőtt továbblépünk. */
        private const val RING_TIMEOUT_MS = 25_000L

        /** Egy felvett hívás maximális ideje, ameddig a lánc vár rá. */
        private const val MAX_CALL_MS = 10 * 60_000L

        /** Két kör után jön a 112. */
        private const val ROUNDS_BEFORE_EMERGENCY = 2

        /** A friss GPS-mérésre ennyit várunk a háttérben. */
        private const val GPS_WINDOW_MS = 45_000L

        private const val POLL_MS = 250L

        /** Szerepkör nélkül: ennyit várunk, hogy a hívás elinduljon (OFFHOOK). */
        private const val CALL_START_TIMEOUT_MS = 15_000L

        /** Szerepkör ÉS hívásállapot nélkül: vak várakozás, bontás nélkül. */
        private const val BLIND_WAIT_MS = 90_000L
        private const val EMERGENCY_NUMBER = "112"

        /** Fut-e épp S.O.S. Innen tudja a menü, hogy leállítást kérnek. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tts: TtsManager? = null
    private var gpsListener: LocationListener? = null

    @Volatile
    private var freshLocation: Location? = null

    @Volatile
    private var stopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundSafely(buildNotification("Vészjelzés indul…"))
        tts = try {
            TtsManager(this)
        } catch (_: Exception) {
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestStop()
            return START_NOT_STICKY
        }
        val numbers = intent?.getStringArrayListExtra(EXTRA_NUMBERS)
            ?.filter { it.isNotBlank() }
            ?: return START_NOT_STICKY
        if (numbers.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (isRunning) return START_NOT_STICKY

        isRunning = true
        scope.launch {
            try {
                runChain(numbers)
            } catch (t: Throwable) {
                // VÉDŐHÁLÓ: bármi történjék, a felhasználó megtudja, hogy a
                // lánc megállt. A néma leállás a legrosszabb kimenetel.
                Log.w("SOS", "A lánc hibára futott", t)
                say("Az S.O.S. lánc hibába ütközött és leállt. Hívd a 112-t.")
            } finally {
                isRunning = false
                stopGps()
                SosCallWatcher.disarm()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun requestStop() {
        stopRequested = true
        say("S.O.S. leállítva.")
    }

    // ── A LÁNC ───────────────────────────────────────────────────────────

    private suspend fun runChain(numbers: List<String>) {
        // 1. A HELYZET. Az utolsó ismert pozíciót AZONNAL használjuk, és
        //    közben indítunk friss mérést. Pánikban a GPS-re várni nem
        //    lehet: percekbe telhet, és addig senki nem tud rólad.
        val firstFix = safe { GpsLocationHelper.getLastLocation(this) }
        startGps()

        // 2. A NÉGY SMS. Ez az első valódi tett — a hívás csak ezután jön.
        val message = SosMessage.build(firstFix)
        val sent = broadcastSms(numbers, message)
        notify("Értesítés elküldve $sent címzettnek")
        say(
            if (sent > 0) {
                "$sent üzenet elküldve a helyzeteddel. Most hívom a számokat. " +
                    "Az S.O.S. leállításához indítsd el újra az S.O.S.-t."
            } else {
                "Az üzeneteket nem sikerült elküldeni. Hívom a számokat."
            }
        )

        // 3. A pontosítás a háttérben — csak ha tényleg érdemes.
        scope.launch { sendUpdateIfWorthIt(numbers, firstFix) }

        // 4. A HÍVÁS-KÖRÖK.
        for (round in 1..ROUNDS_BEFORE_EMERGENCY) {
            for ((index, number) in numbers.withIndex()) {
                if (stopRequested) return
                say("${round}. kör, ${index + 1}. szám.")
                val answered = callAndWait(number)
                if (stopRequested) return
                if (answered) {
                    // A hívásnak vége. NEM tudjuk, ember volt-e vagy hangposta,
                    // és nem is tippelünk. Megyünk tovább; te állíthatod le.
                    say(
                        "A hívás véget ért. Folytatom a láncot. Ha sikerült " +
                            "beszélned valakivel, indítsd el újra az S.O.S.-t a leállításhoz."
                    )
                    delay(4_000L)
                }
                if (stopRequested) return
            }
        }

        // 5. A 112. Előzetes kérdés nélkül — csak a visszaszámlálás, ha kérted.
        if (stopRequested) return
        callEmergency()
    }

    /**
     * Egy szám felhívása, és várakozás a sorsára.
     * @return igaz, ha a hívás létrejött (bárki vagy bármi vette fel).
     */
    private suspend fun callAndWait(number: String): Boolean {
        // MIÉRT: a SosCallWatcher-t csak a SuperInCallService frissíti, ami
        // csak alapértelmezett telefon-appként fut. Nélküle a fázis örökre
        // RINGING, és a lenti csengetési határ egy FELVETT hívást bontana.
        val ownDialer = safe { DialerRoleHelper.isDefaultDialer(this) } == true
        SosCallWatcher.arm()
        val placed = safe { CallHelper.placeCall(this, number) } ?: false
        if (placed != true) {
            SosCallWatcher.disarm()
            say("Ezt a számot nem sikerült hívni.")
            return false
        }
        if (!ownDialer) return waitWithoutDialerRole()

        // 5a. Csengetés — legfeljebb RING_TIMEOUT_MS.
        val ringDeadline = System.currentTimeMillis() + RING_TIMEOUT_MS
        while (System.currentTimeMillis() < ringDeadline) {
            if (stopRequested) { hangUp(); return false }
            when (SosCallWatcher.phase) {
                SosCallWatcher.Phase.ANSWERED -> break
                SosCallWatcher.Phase.ENDED -> {
                    SosCallWatcher.disarm()
                    return false
                }
                else -> delay(POLL_MS)
            }
        }

        if (SosCallWatcher.phase != SosCallWatcher.Phase.ANSWERED) {
            // Nem vették fel. Bontunk, hogy ne csengjen tovább a következő
            // tárcsázás alatt — két párhuzamos hívás a lánc halála.
            hangUp()
            SosCallWatcher.disarm()
            return false
        }

        // 5b. Beszélgetés — megvárjuk a végét, de nem örökké.
        val talkDeadline = System.currentTimeMillis() + MAX_CALL_MS
        while (System.currentTimeMillis() < talkDeadline) {
            if (stopRequested) return true
            if (SosCallWatcher.phase == SosCallWatcher.Phase.ENDED) break
            if (!ActiveCallRegistry.hasManagedCall) break
            delay(POLL_MS)
        }
        SosCallWatcher.disarm()
        return true
    }

    /**
     * Várakozás alapértelmezett telefon-szerep NÉLKÜL.
     *
     * MIÉRT: a rendszer hívásállapotából (TelephonyManager) nem látszik, hogy
     * felvették-e — kimenő hívásnál a tárcsázás és a beszélgetés egyaránt
     * OFFHOOK. Ezért SOHA nem bontunk: megvárjuk, hogy a hívás véget érjen
     * (IDLE), vagy a felső határt. A kicsengő, fel nem vett hívást a hálózat
     * maga zárja le (vagy hangposta veszi fel) — a lánc ezután megy tovább.
     */
    private suspend fun waitWithoutDialerRole(): Boolean {
        SosCallWatcher.disarm()

        // 1. Megvárjuk, hogy a hívás elinduljon (OFFHOOK).
        val startDeadline = System.currentTimeMillis() + CALL_START_TIMEOUT_MS
        var started = false
        var stateKnown = false
        while (System.currentTimeMillis() < startDeadline) {
            if (stopRequested) return true
            val state = readCallState()
            if (state == null) break
            stateKnown = true
            if (state != TelephonyManager.CALL_STATE_IDLE) {
                started = true
                break
            }
            delay(POLL_MS)
        }

        if (!stateKnown) {
            // Nem olvasható a hívásállapot (hiányzó engedély): vakon várunk,
            // de NEM bontunk — egy felvett vészhívást elvágni a legrosszabb.
            val blindDeadline = System.currentTimeMillis() + BLIND_WAIT_MS
            while (System.currentTimeMillis() < blindDeadline) {
                if (stopRequested) return true
                delay(POLL_MS)
            }
            return true
        }
        if (!started) return false

        // 2. Megvárjuk a hívás végét — de nem örökké.
        val talkDeadline = System.currentTimeMillis() + MAX_CALL_MS
        while (System.currentTimeMillis() < talkDeadline) {
            if (stopRequested) return true
            val state = readCallState() ?: break
            if (state == TelephonyManager.CALL_STATE_IDLE) break
            delay(POLL_MS)
        }
        return true
    }

    /** A rendszer hívásállapota; null, ha nem olvasható (pl. nincs engedély). */
    private fun readCallState(): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return safe {
            @Suppress("DEPRECATION")
            getSystemService(TelephonyManager::class.java)?.callState
        }
    }

    private suspend fun callEmergency() {
        if (SosPreferences.isCountdownEnabled(this)) {
            say(
                "Egyik szám sem válaszolt. Tíz másodperc múlva hívom a 112-t. " +
                    "Ha nem akarod, indítsd el újra az S.O.S.-t."
            )
            for (second in 10 downTo 1) {
                if (stopRequested) return
                say(second.toString())
                delay(1_000L)
            }
        } else {
            say("Egyik szám sem válaszolt. Hívom a 112-t.")
        }
        if (stopRequested) return
        notify("112 hívása")
        safe { CallHelper.placeCall(this, EMERGENCY_NUMBER) }
    }

    // ── SMS ──────────────────────────────────────────────────────────────

    private fun broadcastSms(numbers: List<String>, message: String): Int {
        var ok = 0
        for (number in numbers) {
            // Számonként külön védőháló: ha az egyik szám hibás, a másik
            // háromnak akkor is ki kell mennie.
            val result = safe { SmsHelper.send(this, number, message) }
            if (result == true) ok++
        }
        Log.i("SOS", "Vesz-SMS: $ok sikeres a(z) ${numbers.size} cimzettbol")
        return ok
    }

    private suspend fun sendUpdateIfWorthIt(numbers: List<String>, firstFix: Location?) {
        val deadline = System.currentTimeMillis() + GPS_WINDOW_MS
        while (System.currentTimeMillis() < deadline) {
            if (stopRequested) return
            val fresh = freshLocation
            if (fresh != null && SosMessage.worthUpdating(firstFix, fresh)) {
                broadcastSms(numbers, SosMessage.buildUpdate(fresh))
                stopGps()
                return
            }
            delay(2_000L)
        }
        stopGps()
    }

    // ── GPS ──────────────────────────────────────────────────────────────

    private fun startGps() {
        gpsListener = safe {
            GpsLocationHelper.requestUpdates(this, 2_000L) { location ->
                val best = freshLocation
                if (best == null || !best.hasAccuracy() ||
                    (location.hasAccuracy() && location.accuracy < best.accuracy)
                ) {
                    freshLocation = location
                }
            }
        }
    }

    private fun stopGps() {
        val listener = gpsListener ?: return
        gpsListener = null
        safe { GpsLocationHelper.removeUpdates(this, listener) }
    }

    // ── Segédek ──────────────────────────────────────────────────────────

    private fun hangUp() {
        safe { CallHelper.endCallAggressive(this) }
    }

    private fun say(text: String) {
        safe { tts?.speak(text) }
    }

    /**
     * MINDENT ELKAPUNK, A HIBÁKAT IS. Vészhelyzetben egyetlen váratlan
     * kivétel sem állíthatja meg a láncot. A `Throwable` szándékos: az
     * OutOfMemoryError nem `Exception`, és pont egy ilyen omlasztotta össze
     * korábban a podcast-importot.
     */
    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        Log.w("SOS", "Lepes hibaja: ${t.javaClass.simpleName}")
        null
    }

    /**
     * MIÉRT: a kétparaméteres startForeground a manifest ÖSSZES típusát
     * (phoneCall|location) kéri; Android 14-en helymeghatározási engedély
     * nélkül ez SecurityException — és az S.O.S. el sem indul. A helyzet
     * típust csak megadott engedély mellett kérjük, hiba esetén csak a
     * hívás-típussal (a MANAGE_OWN_CALLS a manifestben van) próbáljuk újra.
     */
    private fun startForegroundSafely(notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            safe { startForeground(1, notification) }
            return
        }
        val hasLocation =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (hasLocation) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        try {
            startForeground(1, notification, types)
        } catch (t: Throwable) {
            Log.w("SOS", "Eloter-inditas hiba: ${t.javaClass.simpleName}")
            safe {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            }
        }
    }

    private fun notify(text: String) {
        safe {
            getSystemService(NotificationManager::class.java)
                .notify(1, buildNotification(text))
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Super DL S.O.S.")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "S.O.S. Vészjelzés",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "S.O.S. vészhívás értesítések"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        isRunning = false
        stopGps()
        SosCallWatcher.disarm()
        safe { tts?.shutdown() }
        scope.cancel()
        super.onDestroy()
    }
}
