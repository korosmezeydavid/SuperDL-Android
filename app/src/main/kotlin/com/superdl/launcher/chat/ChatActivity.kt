package com.superdl.launcher.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.superdl.launcher.R
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager

/**
 * Csevejcenter a telefonon – a SuperDL launcher SAJÁT paradigmájában:
 * TELJES KÉPERNYŐS GESZTUS-FELÜLET + ÖN-FELOLVASÁS (TtsManager). NEM támaszkodik
 * a TalkBackre! Swipe fel/le: opciók léptetése (mindet felolvassa), swipe jobbra:
 * kiválasztás, swipe balra: vissza. Szövegbevitel DIKTÁLÁSSAL (RecognizerIntent),
 * pont mint az SMS/e-mail írásnál.
 *
 * Ugyanazokat az Ably-szobákat használja, mint a PC (csevej: előtag) → PC és
 * telefon EGY szobában. M1: szöveges chat + jelenlét. M2: élő térbeli HANG.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var tts: TtsManager
    private lateinit var kijelzo: TextView

    private var soundPool: SoundPool? = null
    private var sndIn = 0
    private var sndOut = 0

    private var room: ChatRoom? = null
    private var roomCode = ""
    private var name = ""
    private val transcript = ArrayList<String>()

    // --- gesztus-menü állapot ---
    private data class Opcio(val cimke: String, val muvelet: () -> Unit)
    private var opciok: List<Opcio> = emptyList()
    private var idx = 0
    private enum class Kepernyo { LOBBI, SZOBA }
    private var kepernyo = Kepernyo.LOBBI

    // --- diktálás ---
    private enum class Diktalas { NEV, KOD, UZENET }
    private var pendingDiktalas: Diktalas? = null
    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            onSpeechResult(r)
        }

    // --- élő hang (M2) ---
    private var hang: HangHalozat? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reannounce: Runnable? = null
    private var hostCand: List<Pair<String, Int>> = emptyList()
    private val recordPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoice()
            else mond("A hanghoz mikrofon-engedély kell. Add meg, és próbáld újra.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Csevejcenter"
        tts = TtsManager(this)
        name = savedName()

        kijelzo = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(pad(), pad(), pad(), pad())
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            addView(kijelzo, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val gl = SwipeGestureListener(this,
            onSwipeUp = { elozo() },
            onSwipeDown = { kovetkezo() },
            onSwipeRight = { kivalaszt() },
            onSwipeLeft = { vissza() },
            onDoubleTap = { mondOpcio() })
        root.setOnTouchListener { _, e -> gl.detector.onTouchEvent(e); true }
        setContentView(root)

        soundPool = SoundPool.Builder().setMaxStreams(4)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        try {
            sndIn = soundPool?.load(this, R.raw.snd_chat_incoming, 1) ?: 0
            sndOut = soundPool?.load(this, R.raw.snd_chat_outgoing, 1) ?: 0
        } catch (_: Exception) { }

        showLobby()
    }

    // ================================================================
    //  Gesztus-navigáció
    // ================================================================
    private fun elozo() {
        if (opciok.isEmpty()) return
        idx = (idx - 1 + opciok.size) % opciok.size
        mondOpcio()
    }

    private fun kovetkezo() {
        if (opciok.isEmpty()) return
        idx = (idx + 1) % opciok.size
        mondOpcio()
    }

    private fun kivalaszt() {
        opciok.getOrNull(idx)?.muvelet?.invoke()
    }

    private fun vissza() {
        when (kepernyo) {
            Kepernyo.SZOBA -> leaveRoom()
            Kepernyo.LOBBI -> { mond("Kilépés a Csevejcenterből."); finish() }
        }
    }

    private fun mondOpcio() {
        val o = opciok.getOrNull(idx) ?: return
        kijelzo.text = o.cimke
        mond(o.cimke)
    }

    private fun ujOpciok(lista: List<Opcio>, megtartIdx: Boolean = false) {
        opciok = lista
        if (!megtartIdx || idx >= opciok.size) idx = 0
    }

    // ================================================================
    //  Lobbi
    // ================================================================
    private fun showLobby() {
        kepernyo = Kepernyo.LOBBI
        epitLobbi()
        idx = 0
        mond("Csevejcenter. Söpörj fel-le a lehetőségek között, jobbra a "
                + "kiválasztáshoz, balra a kilépéshez. "
                + (opciok.getOrNull(0)?.cimke ?: ""))
    }

    private fun epitLobbi() {
        val n = if (name.isBlank()) "nincs megadva" else name
        ujOpciok(listOf(
            Opcio("Új szoba nyitása. Jobbra: új szoba.") { ujSzoba() },
            Opcio("Csatlakozás egy kódhoz. Jobbra: mondd be a kódot.") { diktal(Diktalas.KOD) },
            Opcio("A neved: $n. Jobbra: név bemondása.") { diktal(Diktalas.NEV) },
            Opcio("Kilépés a Csevejcenterből. Jobbra: kilépés.") { finish() }
        ), megtartIdx = true)
    }

    private fun ujSzoba() {
        if (name.isBlank()) { diktal(Diktalas.NEV); return }
        startRoom(name, NetRoom.roomCode())
    }

    // ================================================================
    //  Szoba
    // ================================================================
    private fun startRoom(nev: String, kod: String) {
        val r = ChatRoom(this, kod, nev)
        if (!r.available()) {
            mond("A csevegéshez internet kell. Ellenőrizd a kapcsolatot, és próbáld újra.")
            return
        }
        transcript.clear()
        roomCode = kod
        room = r
        r.onMessage = { ki, szoveg, sajat, history -> onMessage(ki, szoveg, sajat, history) }
        r.onJoined = { nv -> add("* $nv belépett a szobába *"); mond("$nv belépett a szobába.") }
        r.onLeft = { nv -> add("* $nv kilépett *"); mond("$nv kilépett.") }
        r.onMembers = { _ -> hang?.let { it.setResztvevok(room?.members().orEmpty()) }; epitSzoba() }
        kepernyo = Kepernyo.SZOBA
        epitSzoba()
        idx = 0
        mond("Beléptél a(z) ${betuzve(kod)} szobába. Söpörj fel-le a "
                + "lehetőségek közt, jobbra a kiválasztáshoz. Fejhallgatóban a "
                + "legjobb! " + (opciok.getOrNull(0)?.cimke ?: ""))
        r.enter()
    }

    private fun epitSzoba() {
        val hangAllapot = if (hang != null) "bekapcsolva" else "kikapcsolva"
        val tagok = room?.members()?.size ?: 1
        ujOpciok(listOf(
            Opcio("Üzenet írása és küldése. Jobbra: mondd be az üzenetet.") { diktal(Diktalas.UZENET) },
            Opcio("Beszélgetés felolvasása. Jobbra: az utolsó üzenetek.") { readTranscript() },
            Opcio("Résztvevők: $tagok fő. Jobbra: kik vannak itt.") { announceMembers() },
            Opcio("Élő hang most $hangAllapot. Jobbra: be- vagy kikapcsolás.") { beszedValt() },
            Opcio("Szobakód: ${betuzve(roomCode)}. Jobbra: vágólapra másolás.") { copyCode() },
            Opcio("Kilépés a szobából. Jobbra vagy balra: kilépés.") { leaveRoom() }
        ), megtartIdx = true)
        if (kepernyo == Kepernyo.SZOBA) kijelzo.text = opciok.getOrNull(idx)?.cimke ?: ""
    }

    private fun onMessage(ki: String, szoveg: String, sajat: Boolean, history: Boolean) {
        add(if (sajat) "Én: $szoveg" else "$ki: $szoveg")
        if (history) return
        play(if (sajat) sndOut else sndIn)
        if (!sajat) mond("$ki: $szoveg")
    }

    private fun add(sor: String) {
        transcript.add(sor)
        if (transcript.size > 200) transcript.removeAt(0)
    }

    private fun readTranscript() {
        if (transcript.isEmpty()) { mond("Még nincs üzenet a szobában."); return }
        val utolsok = transcript.takeLast(8)
        mond("Az utolsó üzenetek. " + utolsok.joinToString(". "))
    }

    private fun announceMembers() {
        val nevek = room?.members().orEmpty()
        if (nevek.size <= 1) mond("Rajtad kívül még senki nincs a szobában.")
        else mond("A szobában: " + nevek.joinToString(", "))
    }

    private fun copyCode() {
        if (roomCode.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Szobakód", roomCode))
        mond("A szobakód a vágólapon: ${betuzve(roomCode)}")
    }

    private fun leaveRoom() {
        stopVoice()
        room?.leave()
        room = null
        mond("Kiléptél a szobából.")
        showLobby()
    }

    // ================================================================
    //  Diktálás (beszéd → szöveg)
    // ================================================================
    private fun diktal(mit: Diktalas) {
        pendingDiktalas = mit
        val prompt = when (mit) {
            Diktalas.NEV -> "Mondd be a neved"
            Diktalas.KOD -> "Mondd be a szoba kódját, betűnként"
            Diktalas.UZENET -> "Mondd be az üzenetet"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            mond("A diktálás nem elérhető ezen a telefonon.")
        }
    }

    private fun onSpeechResult(r: ActivityResult) {
        val mit = pendingDiktalas
        pendingDiktalas = null
        val res = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val text = res?.firstOrNull()?.trim().orEmpty()
        if (text.isBlank()) { mond("Nem értettem. Próbáld újra."); return }
        when (mit) {
            Diktalas.NEV -> {
                name = text
                saveName(name)
                if (kepernyo == Kepernyo.LOBBI) epitLobbi()
                mond("A neved mostantól: $name.")
            }
            Diktalas.KOD -> {
                val kod = text.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
                if (kod.isBlank()) { mond("Nem értettem a kódot. Próbáld újra."); return }
                mond("A kód, amit értettem: ${betuzve(kod)}. Csatlakozom.")
                startRoom(if (name.isBlank()) "Vendég" else name, kod)
            }
            Diktalas.UZENET -> {
                if (room?.sendMessage(text) == true) {
                    // az elküldött üzenet visszhangja megjeleníti + lejátssza az outgoing hangot
                }
            }
            else -> { }
        }
    }

    // ================================================================
    //  Élő hang (M2)
    // ================================================================
    private fun beszedValt() {
        if (room == null) return
        if (hang != null) { stopVoice(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) startVoice()
        else recordPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoice() {
        val r = room ?: return
        mond("Élő hang indítása…")
        Thread {
            val h = HangHalozat(r.nev)
            try {
                val hh = r.hangHost()
                val tagok = r.members()
                val asClient = hh != null && hh.second in tagok && hh.second != r.nev
                if (asClient) {
                    val cand = h.kliensIndit(hh!!.first)
                    r.hirdetTag(cand)
                } else {
                    val cand = h.hostIndit()
                    r.hirdetHost(cand)
                    for ((ki, cc) in r.hangTagokAll()) if (ki != r.nev) h.punchHozzaad(cc)
                    runOnUiThread { startHostReannounce(cand) }
                }
                hang = h
                r.onHangTag = { ki, cc -> hang?.let { if (ki != r.nev) it.punchHozzaad(cc) } }
                h.setResztvevok(tagok)
                runOnUiThread {
                    epitSzoba()
                    mond(if (asClient)
                        "Élő hang: csatlakozás a házigazdához (${hh!!.second}). Fejhallgatóban a legjobb!"
                    else
                        "Élő hang elindult – te vagy a házigazda. A többiek a Beszéd "
                                + "lehetőséggel csatlakoznak. Fejhallgatóban a legjobb!")
                }
            } catch (e: Exception) {
                try { h.leallit() } catch (_: Exception) { }
                runOnUiThread { mond("Az élő hang nem indult. Próbáld újra.") }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun stopVoice() {
        stopHostReannounce()
        val h = hang
        hang = null
        room?.onHangTag = null
        if (h != null) {
            Thread { try { h.leallit() } catch (_: Exception) { } }.apply { isDaemon = true }.start()
            epitSzoba()
            mond("Élő hang kikapcsolva.")
        }
    }

    private fun startHostReannounce(cand: List<Pair<String, Int>>) {
        hostCand = cand
        val task = object : Runnable {
            override fun run() {
                val r = room ?: return
                if (hang != null) { r.hirdetHost(hostCand); mainHandler.postDelayed(this, 5000) }
            }
        }
        reannounce = task
        mainHandler.postDelayed(task, 5000)
    }

    private fun stopHostReannounce() {
        reannounce?.let { mainHandler.removeCallbacks(it) }
        reannounce = null
    }

    // ================================================================
    //  Segédek
    // ================================================================
    private fun mond(text: String) { try { tts.speak(text) } catch (_: Exception) { } }

    private fun play(id: Int) {
        if (id != 0) try { soundPool?.play(id, 1f, 1f, 1, 0, 1f) } catch (_: Exception) { }
    }

    private fun betuzve(kod: String): String = kod.toCharArray().joinToString(" ")

    private fun pad(): Int = (24 * resources.displayMetrics.density).toInt()

    private fun savedName(): String =
        getSharedPreferences("csevej", Context.MODE_PRIVATE).getString("nev", "") ?: ""

    private fun saveName(nev: String) {
        getSharedPreferences("csevej", Context.MODE_PRIVATE).edit().putString("nev", nev).apply()
    }

    override fun onDestroy() {
        try { stopHostReannounce() } catch (_: Exception) { }
        try { hang?.leallit() } catch (_: Exception) { }
        try { room?.leave() } catch (_: Exception) { }
        try { tts.stop(); tts.shutdown() } catch (_: Exception) { }
        try { soundPool?.release() } catch (_: Exception) { }
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ChatActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
