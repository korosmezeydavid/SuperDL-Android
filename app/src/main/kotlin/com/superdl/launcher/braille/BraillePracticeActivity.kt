package com.superdl.launcher.braille

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.superdl.launcher.tts.TtsManager

/**
 * BRAILLE PRÓBAPAD — itt lehet kipróbálni az írást, tét nélkül.
 *
 * MIÉRT MARAD MEG A VALÓDI BILLENTYŰZET MELLETT IS: egy vak felhasználónak
 * kell egy hely, ahol nyugodtan elronthatja. Itt a leírt szöveg nem megy
 * sehova, a hiba nem kerül semmibe.
 *
 * AZ ÉRINTÉS-FELISMERÉS NEM ITT VAN: a `BrailleTouchRecognizer` a közös
 * motor, amit az éles billentyűzet is használ. Ez a képernyő csak a
 * visszajelzést intézi — hangot, rezgést, beszédet — és a szöveget gyűjti.
 */
class BraillePracticeActivity : Activity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(
                Intent(context, BraillePracticeActivity::class.java).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private lateinit var tts: TtsManager
    private lateinit var sounds: BrailleSounds
    private lateinit var label: TextView
    private lateinit var recognizer: BrailleTouchRecognizer
    private val handler = Handler(Looper.getMainLooper())

    private val state = BrailleInputState()
    private val text = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = BrailleLayoutPrefs.orientation(this).activityInfo()

        label = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            text = "Braille próba"
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.BLACK)
                setPadding(32, 32, 32, 32)
                addView(label)
            }
        )

        tts = TtsManager(this)
        sounds = BrailleSounds(this)
        recognizer = BrailleTouchRecognizer(this, feedback)

        // AMI ÉRVÉNYES: a felhasználó választása, és csak ha nem választott,
        // akkor a mérés ajánlása. A gépé a javaslat, az emberé a döntés.
        val mode = TouchCapability.effectiveMode(this)
        val anchors = BrailleAnchors.load(this)
        recognizer.configure(mode, anchors)

        // A SÍN NEM IGÉNYEL KALIBRÁCIÓT — a többi igen.
        if (!recognizer.isRail && anchors.size < 3) {
            tts.speakThen(
                "Előbb meg kell tanulnom a kezed. Menj a Billentyűzet, Braille " +
                    "billentyűzet, A kezem megtanítása menüpontra, tedd le az " +
                    "ujjaidat, és utána gyere vissza ide."
            ) {
                handler.postDelayed({ finish() }, 900L)
            }
            return
        }

        tts.speak(BrailleIntro.text(this, recognizer, "Braille próba."))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val h = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        return recognizer.onTouchEvent(event, w, h)
    }

    // ── Visszajelzés ────────────────────────────────────────────────────────

    /** A puffer végén álló szó (az utolsó szóköz vagy sortörés után). */
    private fun currentWord(): String {
        val s = text.toString()
        val cut = s.lastIndexOfAny(charArrayOf(' ', '\n')).let { if (it < 0) 0 else it + 1 }
        return s.substring(cut)
    }

    /** SZÓKÖZ = A SZÓ KIMONDÁSA: egyben hallod, amit betűnként írtál. */
    private fun appendSpace() {
        val word = currentWord()
        text.append(' ')
        label.text = text.toString().takeLast(40)
        tts.speak(if (word.isBlank()) "szóköz" else "szóköz. $word")
    }

    private val feedback = object : BrailleTouchRecognizer.Listener {

        override fun onCell(mask: Int) {
            val result = state.consume(mask)
            if (mask == 0) {
                sounds.space()
                appendSpace()
                return
            }
            // A KATTANÁS A BETŰ ELŐTT SZÓL: a hang azt jelzi, „megvan", a
            // beszéd azt, „mi lett" — a kettő nem takarja egymást.
            sounds.charWritten()
            result.text?.let { text.append(it) }
            label.text = text.toString().takeLast(40)
            tts.speak(result.speak)
            // A követett pontok időnként lemezre — egy váratlan kilépés se
            // dobja el, amit a kéz megtanított.
            if (!recognizer.isRail && recognizer.cellsSinceStart % 8 == 0) {
                BrailleAnchors.save(this@BraillePracticeActivity, recognizer.anchors)
            }
        }

        override fun onHalfCell() = tts.speak("Első fél kész. Jöhet a másik.")

        override fun onHalfCellExpired() {
            sounds.invalid()
            tts.speak("A fél cellát elfelejtettem. Kezdd elölről a betűt.")
        }

        override fun onSpace() {
            sounds.space()
            state.consume(0)
            appendSpace()
        }

        // TÖRLÉS = MI TŰNT EL, ÉS MI MARADT — ugyanúgy, mint az éles
        // billentyűzetben. A két hely nem viselkedhet másképp.
        override fun onBackspace() {
            if (text.isEmpty()) {
                sounds.invalid()
                tts.speak("Nincs mit törölni.")
                return
            }
            sounds.delete()
            val removed = text.last()
            text.deleteCharAt(text.length - 1)
            label.text = text.toString().takeLast(40)
            val remaining = currentWord()
            tts.speak(
                when {
                    removed == ' ' && remaining.isNotBlank() -> "szóköz törölve. Marad: $remaining"
                    removed == ' ' -> "szóköz törölve"
                    remaining.isNotBlank() -> "törölve: $removed. Marad: $remaining"
                    else -> "törölve: $removed. A szó üres."
                }
            )
        }

        override fun onDeleteWord() {
            val trimmed = text.toString().trimEnd()
            if (trimmed.isEmpty()) {
                sounds.invalid()
                tts.speak("Nincs mit törölni.")
                return
            }
            val cut = trimmed.lastIndexOf(' ').let { if (it < 0) 0 else it + 1 }
            val word = trimmed.substring(cut)
            text.setLength(cut)
            label.text = text.toString().takeLast(40)
            sounds.delete()
            tts.speak("Törölve: $word")
        }

        override fun onReadBack() {
            tts.speak(
                if (text.isBlank()) "A szöveg üres. ${state.speakMode()}."
                else "$text. ${state.speakMode()}."
            )
        }

        override fun onClose() = closePad()

        // A próbapadon az új sor egy sortörés a pufferben; a billentyűzet-
        // váltásnak itt nincs értelme, ezért csak megmondjuk.
        override fun onEnter() {
            sounds.enter()
            text.append('\n')
            label.text = text.toString().takeLast(40)
            tts.speak("új sor")
        }

        override fun onSwitchKeyboard() =
            tts.speak("Ez a próbapad. A billentyűzet-váltás az éles billentyűzetben működik.")

        override fun onFingerDown() = sounds.fingerDown()

        // A sín: rezgés, mert nyolc mozdulatnyi betűnél a beszéd lassítana.
        override fun onRailRow(row: Int) = vibratePulses(row)

        override fun onRailDot(dot: Int, added: Boolean) {
            if (added) { sounds.fingerDown(); vibrateMs(55) }
            else { sounds.delete(); vibratePulses(2, 30, 60) }
        }

        override fun onRailCleared() {
            vibrateMs(180)
            tts.speak("Cella törölve.")
        }

        override fun onRailIdle(row: Int, dots: Set<Int>) =
            tts.speak(recognizer.describeRail(row, dots))
    }

    private fun closePad() {
        handler.removeCallbacksAndMessages(null)
        BrailleInputActive.release()
        com.superdl.launcher.screenreader.ScreenReaderService.refreshTouchOwnership()
        tts.speak("Braille próba bezárva.")
        handler.postDelayed({ finish() }, 700L)
    }

    // ── Rezgés ──────────────────────────────────────────────────────────────
    // MINDEN REZGÉS BURKOLVA VAN: a rezgés kényelem, a beírt betű a funkció.

    private val vibrator: android.os.Vibrator? by lazy {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun vibrateMs(ms: Long) {
        try {
            vibrator?.vibrate(
                android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Throwable) {
        }
    }

    private fun vibratePulses(count: Int, onMs: Long = 35, gapMs: Long = 70) {
        try {
            val pattern = ArrayList<Long>()
            pattern.add(0L)
            repeat(count.coerceIn(1, 6)) { pattern.add(onMs); pattern.add(gapMs) }
            vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern.toLongArray(), -1))
        } catch (_: Throwable) {
        }
    }

    // ── Az érintések birtoklása ─────────────────────────────────────────────
    // Az elengedés az onPause-ban van: hívásnál vagy képernyőzárnál az
    // olvasónak AZONNAL vissza kell kapnia az érintéseket. Egy ottragadt
    // kapcsoló némává tenné az egész telefont.

    override fun onResume() {
        super.onResume()
        BrailleInputActive.hold()
        com.superdl.launcher.screenreader.ScreenReaderService.refreshTouchOwnership()
    }

    override fun onPause() {
        if (::recognizer.isInitialized && !recognizer.isRail && recognizer.anchors.isNotEmpty()) {
            try { BrailleAnchors.save(this, recognizer.anchors) } catch (_: Throwable) {}
        }
        BrailleInputActive.release()
        com.superdl.launcher.screenreader.ScreenReaderService.refreshTouchOwnership()
        super.onPause()
    }

    override fun onBackPressed() {
        tts.speak("Braille próba bezárva.")
        handler.postDelayed({ finish() }, 600L)
    }

    override fun onDestroy() {
        BrailleInputActive.release()
        com.superdl.launcher.screenreader.ScreenReaderService.refreshTouchOwnership()
        handler.removeCallbacksAndMessages(null)
        if (::recognizer.isInitialized) recognizer.reset()
        try { tts.shutdown() } catch (_: Exception) {}
        super.onDestroy()
    }
}
