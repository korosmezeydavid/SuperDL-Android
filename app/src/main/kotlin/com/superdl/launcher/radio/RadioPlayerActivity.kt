package com.superdl.launcher.radio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.MotionEvent
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.superdl.launcher.R
import com.superdl.launcher.feedback.SoundFeedback
import com.superdl.launcher.feedback.SoundType
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager

/**
 * Internetes rádió-lejátszó, a zenelejátszó / podcast-lejátszó fa-menüs elvén:
 * fel/le a vezérlők között, jobbra aktivál, balra kilép (és leáll).
 *
 * Rádió-specifikum: ÉLŐ adás — nincs pozíció, tekerés, sebesség. Csak
 * play/szünet, állomásváltás (előző/következő), kedvencbe mentés, felvétel.
 */
class RadioPlayerActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tvHint: TextView
    private lateinit var tts: TtsManager
    private lateinit var sounds: SoundFeedback
    private lateinit var gestureListener: SwipeGestureListener

    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false
    private var paused = false

    /** A wifi ébren tartása, amíg élő adás szól. Lásd: halozatiZarFel(). */
    private var wifiZar: android.net.wifi.WifiManager.WifiLock? = null

    /** Ha más alkalmazás megszólal, a rádió elhallgat — nem beszélnek egymásra. */
    private val focusGuard by lazy {
        com.superdl.launcher.media.AudioFocusGuard(
            context = this,
            onPause = {
                runOnUiThread {
                    try {
                        if (mediaPlayer?.isPlaying == true) { mediaPlayer?.pause(); paused = true }
                    } catch (_: Exception) {}
                }
            },
            onResume = {
                runOnUiThread {
                    try { mediaPlayer?.start(); paused = false } catch (_: Exception) {}
                }
            }
        )
    }

    /** Fülhallgató kihúzásakor a rádió elhallgat, nem üvölt a hangszóróból. */
    private val headphoneGuard by lazy {
        com.superdl.launcher.media.HeadphoneUnplugGuard(this) {
            runOnUiThread {
                try {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.pause()
                        paused = true
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * HÍVÁS ALATT a rádió elhallgat, utána magától folytatja.
     * Enélkül a rádió beleszólt a telefonbeszélgetésbe.
     */
    private val callGuard by lazy {
        com.superdl.launcher.call.CallPauseGuard(
            context = this,
            isPlaying = { mediaPlayer?.isPlaying == true },
            onPause = { try { mediaPlayer?.pause(); paused = true } catch (_: Exception) {} },
            onResume = { try { mediaPlayer?.start(); paused = false } catch (_: Exception) {} }
        )
    }

    private var stations: List<RadioStation> = emptyList()
    private var currentIndex = 0

    private var recorder: RadioRecorder? = null

    private fun currentStation(): RadioStation? = stations.getOrNull(currentIndex)

    private enum class ControlItem(val label: String) {
        PLAY_PAUSE("Szünet vagy folytatás"),
        NEXT("Következő állomás"),
        PREVIOUS("Előző állomás"),
        SAVE("Mentés a kedvencekhez"),
        RECORD("Felvétel indítása vagy leállítása"),
        STOP("Rádió leállítása")
    }

    private val menuItems = ControlItem.entries
    private var menuIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)
        applyImmersive()

        tvTitle = findViewById(R.id.tvPlayerTitle)
        tvStatus = findViewById(R.id.tvPlayerStatus)
        tvPosition = findViewById(R.id.tvPlayerPosition)
        tvHint = findViewById(R.id.tvPlayerHint)
        tvHint.text = "Fel-le: vezérlők. Jobbra: kiválaszt. Balra: kilépés."

        tts = TtsManager(this)
        sounds = SoundFeedback(this)

        stations = RadioPlaylistHolder.stations
        currentIndex = RadioPlaylistHolder.startIndex.coerceIn(0, (stations.size - 1).coerceAtLeast(0))

        if (stations.isEmpty()) {
            tts.speakThen("Nincs lejátszható állomás.") { finish() }
            return
        }

        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = { sounds.play(SoundType.SWIPE_UP); navigateMenu(-1) },
            onSwipeDown = { sounds.play(SoundType.SWIPE_DOWN); navigateMenu(+1) },
            onSwipeRight = { sounds.play(SoundType.SWIPE_RIGHT); activateMenuItem() },
            onSwipeLeft = { sounds.play(SoundType.SWIPE_LEFT); stopAndFinish("Rádió leállítva.") }
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stopAndFinish("Rádió leállítva.")
        })
        // Hívás alatt a rádió elhallgat, utána magától folytatja.
        callGuard.register()
        headphoneGuard.register()
        focusGuard.request()

        playCurrent()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureListener.detector.onTouchEvent(event) || super.onTouchEvent(event)

    /**
     * UGYANANNAK AZ ADÓNAK A TARTALÉK CÍMEI.
     *
     * Lásd RadioBrowserClient.alternativeStreams(): a közösségi adatbázisban
     * ugyanaz az adó több címmel is szerepel, és nem mindegyik él egyszerre.
     * Ha az első nem szól, végigpróbáljuk a többit, mielőtt feladnánk.
     */
    private var alternatives: List<String> = emptyList()
    private var alternativesFetched = false
    private var altIndex = 0

    private fun playCurrent() {
        val station = currentStation() ?: return
        releasePlayer()
        prepared = false
        paused = false
        // Új adó: a tartaléklista is újraindul.
        alternatives = emptyList()
        alternativesFetched = false
        altIndex = 0
        tvTitle.text = station.name
        tvPosition.text = "Rádió"
        tvStatus.text = getString(R.string.player_loading)
        tts.speak("${station.name}. Betöltés.")
        // Az URL feloldása HÁTTÉRSZÁLON: ha .pls/.m3u lista, kibányásszuk belőle a
        // valódi stream-címet (a MediaPlayer a listákat nem tudja lejátszani).
        Thread {
            val resolved = RadioPlaylistResolver.resolve(station.streamUrl)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (resolved == null) {
                    tryAlternative(station)
                    return@runOnUiThread
                }
                startStream(station, resolved)
            }
        }.start()
    }

    /**
     * A KÖVETKEZŐ CÍM MEGPRÓBÁLÁSA — a feladás előtt.
     *
     * Vakon a „nem elérhető" zsákutca: a felhasználó nem tudja, hogy más
     * címen ugyanaz az adó szólna. Ezért mi próbáljuk végig helyette, és csak
     * akkor mondjuk azt, hogy nem megy, ha tényleg egyik sem válaszolt.
     *
     * A keresés hálózatot használ, ezért háttérszálon fut. Közben szólunk,
     * hogy dolgozunk — a néma várakozás elbizonytalanít.
     */
    private fun tryAlternative(station: RadioStation) {
        if (isFinishing || isDestroyed) return

        if (!alternativesFetched) {
            alternativesFetched = true
            tts.speak("Ez a cím nem válaszol. Keresek másikat.")
            Thread {
                val list = RadioBrowserClient.alternativeStreams(station.name, station.streamUrl)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    alternatives = list
                    altIndex = 0
                    tryAlternative(station)
                }
            }.start()
            return
        }

        if (altIndex >= alternatives.size) {
            tvStatus.text = getString(R.string.player_loading)
            tts.speak(
                if (alternatives.isEmpty()) {
                    "Ez az állomás most nem elérhető. Más címet sem találtam hozzá."
                } else {
                    "Ez az állomás most nem elérhető. Mind a ${alternatives.size} címét megpróbáltam."
                }
            )
            return
        }

        val next = alternatives[altIndex]
        altIndex++
        Thread {
            val resolved = RadioPlaylistResolver.resolve(next)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (resolved == null) {
                    tryAlternative(station)
                } else {
                    startStream(station, resolved)
                }
            }
        }.start()
    }

    /** A feloldott stream-URL tényleges lejátszása. */
    private fun startStream(station: RadioStation, streamUrl: String) {
        try {
            halozatiZarFel()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                // AZ ELALVÁS ELLEN — EZ A LÉNYEG.
                //
                // A HIBA (Mezei Géza, 2026-09-09): „a rádióban baj van,
                // bealszik lezárt képernyőn egy idő után."
                //
                // Igaza volt, és az ok pontosan az, amit a neve mond: a
                // telefon ELALSZIK. Lezárt képernyőnél az Android egy idő
                // után felfüggeszti a processzort, és ha a lejátszó nem szól,
                // hogy neki futnia kell, a hang egyszerűen elhallgat. Nem
                // hiba, nem szakadás — a rendszer takarékoskodik.
                //
                // A setWakeMode pontosan erre való: a MediaPlayer maga tartja
                // ébren a processzort, amíg szól, és elengedi, amikor
                // leállítjuk. Ez az egyetlen olyan ébrentartás, amit nem kell
                // kézzel elengedni — épp ezért nem is lehet elfelejteni.
                setWakeMode(applicationContext, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(streamUrl)
                setOnPreparedListener {
                    prepared = true
                    tvStatus.text = getString(R.string.player_playing)
                    start()
                    tts.speak("Szól a(z) ${station.name}.")
                }
                setOnErrorListener { _, _, _ ->
                    // NEM ITT ADJUK FEL: lehet, hogy csak ez a cím néma.
                    // A hibát is a tartaléklánc kapja meg — de csak akkor, ha
                    // még el sem indult a hang. Ha már szólt és menet közben
                    // szakadt meg, azt nem címhibaként kezeljük.
                    if (!prepared) {
                        releasePlayer()
                        tryAlternative(station)
                    } else {
                        tts.speak("A műsor megszakadt.")
                    }
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            releasePlayer()
            tryAlternative(station)
        }
    }

    private fun navigateMenu(delta: Int) {
        menuIndex = (menuIndex + delta + menuItems.size) % menuItems.size
        val item = menuItems[menuIndex]
        tvHint.text = "${item.label}  •  jobbra: kiválaszt  •  balra: kilépés"
        tts.speak(item.label)
    }

    private fun activateMenuItem() {
        when (menuItems[menuIndex]) {
            ControlItem.PLAY_PAUSE -> togglePause()
            ControlItem.NEXT -> switchStation(+1)
            ControlItem.PREVIOUS -> switchStation(-1)
            ControlItem.SAVE -> saveCurrentToFavorites()
            ControlItem.RECORD -> toggleRecording()
            ControlItem.STOP -> stopAndFinish("Rádió leállítva.")
        }
    }

    private fun togglePause() {
        val player = mediaPlayer ?: return
        if (!prepared) {
            tts.speak("Az állomás még töltődik.")
            return
        }
        if (paused) {
            player.start()
            paused = false
            tvStatus.text = getString(R.string.player_playing)
            tts.speak("Folytatás.")
        } else {
            player.pause()
            paused = true
            tvStatus.text = getString(R.string.player_paused)
            tts.speak("Szünet.")
        }
    }

    private fun switchStation(delta: Int) {
        if (stations.size <= 1) {
            tts.speak("Nincs másik állomás.")
            return
        }
        currentIndex = (currentIndex + delta + stations.size) % stations.size
        playCurrent()
    }

    private fun saveCurrentToFavorites() {
        val station = currentStation() ?: return
        if (RadioStore.isSaved(this, station)) {
            tts.speak("Ez az állomás már a kedvenceid között van.")
            return
        }
        RadioStore.addStation(this, station)
        tts.speak("${station.name} elmentve a kedvencekhez.")
    }

    private fun toggleRecording() {
        val station = currentStation() ?: return
        val rec = recorder
        if (rec != null && rec.isRecording) {
            val file = rec.stop()
            recorder = null
            tts.speak(
                if (file != null) "Felvétel leállítva. Elmentve: ${file.name}."
                else "Felvétel leállítva."
            )
        } else {
            val newRec = RadioRecorder(this)
            val started = newRec.start(station)
            if (started) {
                recorder = newRec
                tts.speak("Felvétel elindult: ${station.name}.")
            } else {
                tts.speak("A felvételt most nem sikerült elindítani.")
            }
        }
    }

    private fun stopAndFinish(message: String) {
        // Ha épp felvétel megy, azt is lezárjuk kilépéskor.
        recorder?.let { if (it.isRecording) it.stop() }
        recorder = null
        releasePlayer()
        // A hurok-bug elkerülése: a finish() NEM várja meg a TTS végét.
        tts.speak(message)
        finish()
    }

    private fun releasePlayer() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        prepared = false
        halozatiZarLe()
    }

    /**
     * A WIFI IS ELALSZIK — NEM CSAK A PROCESSZOR.
     *
     * A setWakeMode ébren tartja a processzort, de a wifi-rádiót nem. Lezárt
     * képernyőnél a telefon a wifit is takarékos állapotba teszi, és egy élő
     * adás ettől akadozni kezd, majd elhallgat. Aki a konyhában hallgatja a
     * rádiót és nem nyúl a telefonhoz, pontosan ezt tapasztalja.
     *
     * Mobilneten ez a zár nem csinál semmit — és nem is baj: ott a rendszer
     * magától ébren tartja a kapcsolatot, amíg a hang szól.
     *
     * MIÉRT NEM SZÁMLÁLT (`setReferenceCounted(false)`): állomásváltásnál a
     * felszabadítás és az újrafoglalás sorrendje nem mindig ugyanaz. Számlált
     * zárnál egy elmaradt elengedés örökre bent ragadna, és a telefon az
     * akkumulátorával fizetne érte.
     */
    private fun halozatiZarFel() {
        if (wifiZar == null) {
            wifiZar = try {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE)
                    as? android.net.wifi.WifiManager
                @Suppress("DEPRECATION")
                wm?.createWifiLock(
                    android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "SuperDL:Radio"
                )?.apply { setReferenceCounted(false) }
            } catch (_: Exception) {
                null
            }
        }
        try {
            wifiZar?.let { if (!it.isHeld) it.acquire() }
        } catch (_: Exception) {
        }
    }

    private fun halozatiZarLe() {
        try {
            wifiZar?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        callGuard.unregister()
        headphoneGuard.unregister()
        focusGuard.release()
        recorder?.let { if (it.isRecording) it.stop() }
        recorder = null
        releasePlayer()
        tts.shutdown()
        sounds.release()
        super.onDestroy()
    }

    private fun applyImmersive() {
        window.decorView.systemUiVisibility =
            (android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN)
    }
}
