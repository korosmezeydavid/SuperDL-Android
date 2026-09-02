package com.superdl.launcher.braille

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.superdl.launcher.tts.TtsManager

/**
 * A BRAILLE BILLENTYŰZET — az éles bevitel.
 *
 * A próbapad testvére: UGYANAZ a motor (`BrailleTouchRecognizer`), ugyanaz a
 * tábla, ugyanazok a hangok — csak itt a betű a SZÖVEGMEZŐBE megy, nem egy
 * helyi pufferbe. Külön tétel a rendszer billentyűzet-választójában, a
 * mátrix mellett (Alph döntése, 2026-09-02).
 *
 * MIÉRT NEM A PRÓBAPAD LETT A BILLENTYŰZET: az `InputMethodService` más
 * életciklus, más ablak, más birtokosa az érintéseknek. Ha a kettőt egybe
 * gyúrnánk, a próbapad elveszítené azt, amiért van — hogy tét nélkül lehet
 * benne hibázni.
 *
 * AMIT A MÁTRIXTÓL VÁLTOZTATÁS NÉLKÜL ÁTVESZÜNK: összecsippentés = bezárás,
 * három ujjal koppintás = billentyűzet-választó, jelszómezőben a beírt
 * karaktert nem mondjuk ki. Ne tanuljon a felhasználó két rendszert.
 *
 * A NAGY KÜLÖNBSÉG A PRÓBAPADHOZ KÉPEST: a bezárásnál és a képernyő
 * elhagyásánál az ÖSSZES állapotot elengedjük — a jelzőket is. Egy ottfelejtett
 * „nagybetűs szó" a következő mezőben csendben csupa nagybetűt írna.
 */
class BrailleKeyboardService : InputMethodService() {

    private var tts: TtsManager? = null
    private var sounds: BrailleSounds? = null
    private var label: TextView? = null
    private var recognizer: BrailleTouchRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val state = BrailleInputState()
    private var closing = false

    override fun onCreateInputView(): View {
        tts = try { TtsManager(this) } catch (_: Throwable) { null }
        sounds = try { BrailleSounds(this) } catch (_: Throwable) { null }
        recognizer = BrailleTouchRecognizer(this, feedback)

        val view = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            text = "Braille"
            // A BILLENTYŰZET A TELJES KIJELZŐ. (2026-09-02 esti hiba.)
            //
            // Az első változat a kijelző alsó 60%-a volt — és az ujjak, amik
            // feljebb értek le, EL SEM JUTOTTAK a billentyűzethez, azokat az
            // alkalmazás kapta. Csak a legalsó ujj találta el: a jobb kéz
            // gyűrűsujja, a 6-os pont. A tesztelő következetesen aposztrófot
            // kapott, bármit írt.
            //
            // Vakon a mező „látszása" nem érv; a kéz helye viszont a
            // kalibrálásból jön, ami a TELJES kijelzőn történt. Ugyanaz a
            // felület kell ide is.
            minHeight = resources.displayMetrics.heightPixels
            setOnTouchListener { v, event ->
                // UGYANAZ A KOORDINÁTA-RENDSZER, MINT A KALIBRÁLÁSNÁL: a
                // kijelzőé, nem a felületé. A felület teteje alatt van a
                // státuszsáv; ha a felülethez mérnénk, minden pont annyival
                // csúszna el, és a betűk hibásak lennének.
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                event.offsetLocation(loc[0].toFloat(), loc[1].toFloat())
                recognizer?.onTouchEvent(
                    event,
                    resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f),
                    resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
                ) ?: true
            }
        }
        label = view
        return view
    }

    /**
     * FEKVŐ TELEFONON a rendszer alapból „teljes képernyős" szerkesztőt
     * tenne a billentyűzetünk HELYÉRE — egy szövegdobozt, ami elveszi az
     * érintéseket. A Braille-nek a fekvő tartás az egyik fő módja; ezt
     * ki kell kapcsolni, különben fekvőben egyáltalán nem lehetne írni.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        closing = false
        state.reset()
        BrailleInputActive.hold()
        com.superdl.launcher.screenreader.ScreenReaderService.refreshTouchOwnership()

        val r = recognizer ?: return
        val mode = TouchCapability.effectiveMode(this)

        // A KÉPERNYŐ FORGATÁSA ÉS A KALIBRÁLÁSÉ ELTÉRHET. A billentyűzet nem
        // forgathatja el az alkalmazást; ha az álló, mi is állóban kapjuk az
        // érintéseket — a felhasználó pedig fekvőben fogja a telefont, ahogy
        // kalibrált. Ezért a pontokat a MOSTANI forgatásra számoljuk át.
        referenceRotation = BrailleAnchors.referenceRotation(this)
        currentRotation = displayRotation()
        val stored = BrailleAnchors.load(this)
        val anchors = BrailleAnchors.rotate(stored, referenceRotation, currentRotation)
        r.reset()
        r.configure(mode, anchors)

        val rotationNote = if (!r.isRail && stored.isNotEmpty() && referenceRotation != currentRotation) {
            "Az alkalmazás képernyője most más állásban van, mint amiben kalibráltál; " +
                "a pontokat átszámoltam. Fogd a telefont ugyanúgy, mint a kalibrálásnál. "
        } else {
            ""
        }

        // KALIBRÁLATLAN: a billentyűzet nem tud írni, de NEM zsákutca —
        // két ujjal felfelé söpörve a választó jön, csippentésre bezár.
        // Ezt a kettőt a motor akkor is felismeri, ha nincsenek pontok.
        if (!r.isRail && anchors.size < 3) {
            tts?.speak(
                "Braille billentyűzet. Előbb meg kell tanulnom a kezed: a Super DL " +
                    "Beállítások, Billentyűzet, Braille billentyűzet, A kezem megtanítása " +
                    "menüpontban. Addig két ujjal felfelé söpörve válts másik " +
                    "billentyűzetre, vagy csippents össze a bezáráshoz."
            )
            return
        }
        // A TÁJÉKOZTATÓ KIKAPCSOLHATÓ („Mennyit magyarázzon"), a NÉV nem:
        // vakon tudni kell, melyik billentyűzet jött elő.
        if (!restarting) {
            val intro = if (com.superdl.launcher.tts.VerbosityPrefs.isKeyboardIntro(this)) {
                BrailleIntro.text(this, r, "Braille billentyűzet.")
            } else {
                "Braille billentyűzet."
            }
            tts?.speak(rotationNote + intro)
        } else if (rotationNote.isNotEmpty()) {
            tts?.speak(rotationNote)
        }
    }

    private var referenceRotation = 0
    private var currentRotation = 0

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = try {
        val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        wm.defaultDisplay.rotation
    } catch (_: Throwable) {
        0
    }

    /** A követett pontok mentése — VISSZASZÁMOLVA a kalibrálás keretébe. */
    private fun saveAnchors() {
        val r = recognizer ?: return
        if (r.isRail || r.anchors.isEmpty()) return
        try {
            BrailleAnchors.save(this, BrailleAnchors.rotate(r.anchors, currentRotation, referenceRotation))
        } catch (_: Throwable) {
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        releaseAll()
        super.onFinishInputView(finishingInput)
    }

    /**
     * MINDENT ELENGEDÜNK: a jelzőket, az érintés-birtoklást, a függő fél
     * cellát. És mentjük a követett pontokat — amit a kéz megtanított, az
     * maradjon meg a következő mezőre is.
     */
    private fun releaseAll() {
        handler.removeCallbacksAndMessages(null)
        state.reset()
        saveAnchors()
        recognizer?.reset()
        BrailleInputActive.release()
        com.superdl.launcher.screenreader.ScreenReaderService.refreshTouchOwnership()
    }

    // ── Jelszómező ──────────────────────────────────────────────────────────

    private fun isPasswordField(): Boolean = try {
        val type = currentInputEditorInfo?.inputType ?: 0
        val cls = type and android.text.InputType.TYPE_MASK_CLASS
        val variation = type and android.text.InputType.TYPE_MASK_VARIATION
        when (cls) {
            android.text.InputType.TYPE_CLASS_TEXT ->
                variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            android.text.InputType.TYPE_CLASS_NUMBER ->
                variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    } catch (_: Throwable) {
        false
    }

    private fun shouldHideChars(): Boolean =
        isPasswordField() &&
            !com.superdl.launcher.keyboard.MatrixKeyboardPrefs.isSpeakPasswordChars(this)

    // ── Beírás ──────────────────────────────────────────────────────────────

    /**
     * A KURZOR ELŐTTI SZÓ — a szóköz és a törlés visszajelzéséhez.
     * A szóközök után maradó rész nem szó; azt üresnek vesszük.
     */
    private fun wordBeforeCursor(): String {
        val before = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val cut = before.lastIndexOfAny(charArrayOf(' ', '\n')).let { if (it < 0) 0 else it + 1 }
        return before.substring(cut)
    }

    /**
     * SZÓKÖZ = A SZÓ KIMONDÁSA (Alph kérése). Betűnként hallottad a betűket;
     * a szóköznél EGYBEN hallod a szót — így derül ki, ha elírtad, még
     * mielőtt továbbmennél.
     */
    private fun commitSpace() {
        val word = wordBeforeCursor()
        currentInputConnection?.commitText(" ", 1)
        speak(
            when {
                shouldHideChars() -> "szóköz"
                word.isBlank() -> "szóköz"
                else -> "szóköz. $word"
            }
        )
    }

    private val feedback = object : BrailleTouchRecognizer.Listener {

        override fun onCell(mask: Int) {
            val result = state.consume(mask)
            if (mask == 0) {
                sounds?.space()
                commitSpace()
                label?.text = " "
                return
            }
            sounds?.charWritten()
            result.text?.let { currentInputConnection?.commitText(it, 1) }
            label?.text = result.text ?: state.speakMode()
            // JELSZÓMEZŐBEN a karaktert nem mondjuk ki — a jelzőket igen,
            // mert azok nem árulnak el semmit a jelszóból.
            if (result.text != null && shouldHideChars()) {
                tts?.speak("beírva")
            } else {
                tts?.speak(result.speak)
            }
            recognizer?.let {
                if (!it.isRail && it.cellsSinceStart % 8 == 0) saveAnchors()
            }
        }

        override fun onHalfCell() = speak("Első fél kész.")

        override fun onHalfCellExpired() {
            sounds?.invalid()
            speak("A fél cellát elfelejtettem.")
        }

        override fun onSpace() {
            sounds?.space()
            state.consume(0)
            commitSpace()
        }

        /**
         * TÖRLÉS = MI TŰNT EL, ÉS MI MARADT (Alph kérése). A törölt betű
         * önmagában kevés: azt is hallanod kell, hogy a szó ezután hogyan
         * hangzik — különben a következő betűt vakon írod hozzá.
         */
        override fun onBackspace() {
            val ic = currentInputConnection ?: return
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.isEmpty()) {
                sounds?.invalid()
                speak("Nincs mit törölni.")
                return
            }
            // Az utolsó KARAKTERT töröljük — kétjegyű betűnél is egyet, mert
            // a felhasználó azt hallja vissza, ami eltűnt, és abból tudja.
            val removed = before.last()
            ic.deleteSurroundingText(1, 0)
            sounds?.delete()
            val remaining = wordBeforeCursor()
            speak(
                when {
                    shouldHideChars() -> "törölve"
                    removed == ' ' && remaining.isNotBlank() -> "szóköz törölve. Marad: $remaining"
                    removed == ' ' -> "szóköz törölve"
                    remaining.isNotBlank() -> "törölve: $removed. Marad: $remaining"
                    else -> "törölve: $removed. A szó üres."
                }
            )
        }

        override fun onDeleteWord() {
            val ic = currentInputConnection ?: return
            val before = ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
            if (before.isBlank()) {
                sounds?.invalid()
                speak("Nincs mit törölni.")
                return
            }
            val trimmed = before.trimEnd()
            val cut = trimmed.lastIndexOf(' ').let { if (it < 0) 0 else it + 1 }
            val word = trimmed.substring(cut)
            ic.deleteSurroundingText(before.length - cut, 0)
            sounds?.delete()
            speak(if (shouldHideChars()) "szó törölve" else "Törölve: $word")
        }

        /**
         * „HOL VAGYOK?" — a kurzor előtti utolsó szavak, a mód és a réteg.
         * Ez a vak felhasználó egyetlen fogódzója egy hosszabb mezőben.
         */
        override fun onReadBack() {
            val ic = currentInputConnection
            val before = ic?.getTextBeforeCursor(120, 0)?.toString().orEmpty()
            val tail = before.trimEnd().split(' ').takeLast(6).joinToString(" ")
            speak(
                when {
                    shouldHideChars() -> "Jelszómező, ${before.length} karakter. ${state.speakMode()}."
                    before.isBlank() -> "A mező üres. ${state.speakMode()}."
                    else -> "$tail. ${state.speakMode()}."
                }
            )
        }

        override fun onClose() = close()

        /**
         * ÚJ SOR — a mátrixszal azonos mozdulat (két ujjal lefelé).
         * Egysoros mezőben az Enter gyakran „Küldés" vagy „Keresés": ezt a
         * rendszer dönti el, mi csak a billentyűt adjuk le.
         */
        override fun onEnter() {
            val ic = currentInputConnection ?: return
            sounds?.enter()
            state.reset()
            ic.sendKeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
            )
            ic.sendKeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER)
            )
            speak("új sor")
        }

        override fun onSwitchKeyboard() = showKeyboardPicker()

        override fun onFingerDown() { sounds?.fingerDown() }

        override fun onRailRow(row: Int) = vibratePulses(row)

        override fun onRailDot(dot: Int, added: Boolean) {
            if (added) { sounds?.fingerDown(); vibrateMs(55) }
            else { sounds?.delete(); vibratePulses(2, 30, 60) }
        }

        override fun onRailCleared() {
            vibrateMs(180)
            speak("Cella törölve.")
        }

        override fun onRailIdle(row: Int, dots: Set<Int>) {
            recognizer?.let { speak(it.describeRail(row, dots)) }
        }
    }

    private fun speak(text: String) { tts?.speak(text) }

    private fun showKeyboardPicker() {
        try {
            speak("Billentyűzet választása.")
            val imm = getSystemService(INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        } catch (_: Throwable) {
            speak("A választó nem nyitható meg.")
        }
    }

    /**
     * BEZÁRÁS: ELŐBB ELTŰNIK, AZTÁN BESZÉL. Ha a beszéd végére várnánk, és
     * a visszahívás valamiért elmaradna (a beszélő épp foglalt, vagy a
     * hangfelismerő tartja a hangot), a billentyűzet nyitva ragadna — és a
     * `closing` zár miatt a következő csippentés se segítene. A rejtés
     * azonnali; a bemondás attól még elhangzik.
     */
    private fun close() {
        if (closing) return
        closing = true
        releaseAll()
        try { requestHideSelf(0) } catch (_: Throwable) {}
        tts?.speak("Braille billentyűzet bezárva.")
        closing = false
    }

    // ── Rezgés ──────────────────────────────────────────────────────────────

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

    override fun onDestroy() {
        releaseAll()
        try { tts?.shutdown() } catch (_: Throwable) {}
        tts = null
        super.onDestroy()
    }
}
