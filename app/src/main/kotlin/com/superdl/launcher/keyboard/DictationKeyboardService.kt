package com.superdl.launcher.keyboard

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.superdl.launcher.tts.TtsManager
import com.superdl.launcher.voice.SpeechPunctuation
import com.superdl.launcher.voice.VoiceInput

/**
 * SUPERDL DIKTÁLÁS — a legegyszerűbb bevitel a világon.
 *
 * ALPH KÉRÉSE (2026-09-02):
 *
 * > „ha valakinek az kell, belép a szövegmezőbe és csak diktálhasson anélkül,
 * >  hogy a diktáláshoz szükséges gesztust aktiválnia kéne, csak az
 * >  összecsippentést kell a végén, hogy a billentyűzet, ez esetben a
 * >  diktáló mód is kikapcsoljon."
 *
 * Vagyis: NINCS mozdulat. A szövegmező megnyílik, a billentyűzet HALLGAT.
 * Mondod, beírja, újra hallgat. Ha kész vagy, összecsippentesz. Ennyi.
 *
 * MIÉRT KÜLÖN BILLENTYŰZET, ÉS NEM A MÁTRIX EGY MÓDJA: mert aki ezt akarja,
 * annak a mátrix minden mozdulata teher. Egy külön tétel a rendszer
 * billentyűzet-választójában, aminek EGYETLEN mozdulata van, az nem
 * egyszerűsítés, hanem egy másik termék — annak, aki se mátrixot, se
 * Braille-t nem akar.
 *
 * A HÁROM DOLOG, AMI NÉLKÜL EZ VESZÉLYES LENNE:
 *
 * 1. **A mikrofon nem maradhat nyitva a végtelenségig.** Ha háromszor
 *    egymás után nem ért semmit, MEGÁLL, és megmondja, hogyan folytathatod.
 *    Egy csendben nyitva maradt mikrofont vakon nem lehet észrevenni.
 * 2. **A bezárás hallható.** „Diktálás bezárva." — és csak UTÁNA tűnik el.
 * 3. **Jelszómezőben nem diktál.** Egy hangosan bemondott jelszó a buszon
 *    nem kényelmetlenség, hanem kár.
 *
 * MOZDULATOK (szándékosan kevés):
 *  - összecsippentés: bezárás (ugyanaz, mint a mátrixnál és a Braille-nél),
 *  - egy koppintás: hallgatás újraindítása, ha megállt,
 *  - két ujjal balra söprés: az utolsó szó törlése,
 *  - három ujjal koppintás: billentyűzet-választó.
 */
class DictationKeyboardService : InputMethodService() {

    companion object {
        /** Ennyi egymás utáni üres kör után megállunk. */
        private const val MAX_SILENT_ROUNDS = 3

        /** Szünet két kör között, hogy a „Beírva" ne kerüljön a felvételbe. */
        private const val RELISTEN_DELAY_MS = 350L
    }

    private var tts: TtsManager? = null
    private var sounds: com.superdl.launcher.screenreader.ScreenReaderSounds? = null
    private var voiceInput: VoiceInput? = null
    private var label: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Fut-e a folyamatos hallgatás. */
    private var running = false

    /** Minden indításnál nő; a régi körök eredménye így nem ír be semmit. */
    private var generation = 0

    private var silentRounds = 0

    // Csippentés és koppintás felismeréséhez.
    private var startSpread = 0f
    private var maxPointers = 0
    private var downX = 0f
    private var downY = 0f
    private var moved = 0f
    private var closing = false

    override fun onCreateInputView(): View {
        tts = try { TtsManager(this) } catch (_: Throwable) { null }
        sounds = try {
            com.superdl.launcher.screenreader.ScreenReaderSounds(this)
        } catch (_: Throwable) {
            null
        }
        voiceInput = try { VoiceInput(this) } catch (_: Throwable) { null }

        val view = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            text = "Diktálás"
            // A FELÜLET A TELJES KIJELZŐ. Az első változat a kijelző alsó
            // harmada volt, és a csippentés „hatástalan" lett: a felhasználó
            // a képernyő közepén csípett, ami már az alkalmazásé volt, nem a
            // billentyűzeté. Vakon nincs „a billentyűzet területe" — az egész
            // képernyő az. (Ugyanez a hiba volt a Braille-nél is.)
            minHeight = resources.displayMetrics.heightPixels
            setOnTouchListener { _, event -> onTouch(event) }
        }
        label = view
        return view
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        closing = false
        if (restarting && running) return

        if (isPasswordField()) {
            tts?.speak(
                "Jelszómező. Ide nem diktálunk, mert a jelszót mások is hallanák. " +
                    "Három ujjal koppintva válts másik billentyűzetre."
            )
            return
        }
        // A TÁJÉKOZTATÓ KIKAPCSOLHATÓ, a név nem.
        startLoop(
            if (com.superdl.launcher.tts.VerbosityPrefs.isKeyboardIntro(this)) {
                "Diktálás. Mondd a szöveget. Ha kész vagy, csippents össze két ujjal."
            } else {
                "Diktálás."
            }
        )
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopLoop()
        super.onFinishInputView(finishingInput)
    }

    // ── A folyamatos hallgatás ──────────────────────────────────────────────

    private fun startLoop(intro: String) {
        val vi = voiceInput
        if (vi == null || !vi.isAvailable()) {
            tts?.speak("A hangfelismerés nem érhető el ezen a készüléken.")
            return
        }
        running = true
        silentRounds = 0
        val gen = ++generation
        label?.text = "Hallgatok…"
        // ELŐBB A BEMONDÁS VÉGE, CSAK UTÁNA A MIKROFON. Különben a saját
        // hangunkat vennénk fel, és azt írnánk be.
        tts?.speakThen(intro) { listenOnce(gen) }
    }

    private fun stopLoop() {
        running = false
        generation++
        handler.removeCallbacksAndMessages(null)
        try { voiceInput?.cancel() } catch (_: Throwable) {}
        label?.text = "Diktálás"
    }

    private fun listenOnce(gen: Int) {
        if (!running || gen != generation) return
        val vi = voiceInput ?: return
        sounds?.play(com.superdl.launcher.screenreader.ScreenReaderSounds.Sound.FIELD)
        vi.listenPrompt(
            prompt = "",
            onResult = { raw ->
                if (!running || gen != generation) return@listenPrompt
                val text = SpeechPunctuation.apply(raw)
                if (text.isBlank()) {
                    onSilentRound(gen)
                } else {
                    silentRounds = 0
                    commitDictated(text)
                    // A „Beírva" bemondása után jön a következő kör.
                    handler.postDelayed({ listenOnce(gen) }, RELISTEN_DELAY_MS)
                }
            },
            onError = {
                if (!running || gen != generation) return@listenPrompt
                onSilentRound(gen)
            }
        )
    }

    /**
     * NEM ÉRTETT SEMMIT. Nem pörgünk a végtelenségig: három üres kör után
     * megállunk, és MEGMONDJUK, hogyan lehet folytatni. Enélkül a mikrofon
     * nyitva maradna, és ezt vakon nem lehet észrevenni.
     */
    private fun onSilentRound(gen: Int) {
        silentRounds++
        if (silentRounds >= MAX_SILENT_ROUNDS) {
            running = false
            label?.text = "Megálltam"
            sounds?.play(com.superdl.launcher.screenreader.ScreenReaderSounds.Sound.EDGE)
            tts?.speak(
                "Nem hallok semmit, ezért megálltam. Koppints egyet, ha folytatnád, " +
                    "vagy csippents össze a bezáráshoz."
            )
            return
        }
        sounds?.play(com.superdl.launcher.screenreader.ScreenReaderSounds.Sound.PIP)
        handler.postDelayed({ listenOnce(gen) }, RELISTEN_DELAY_MS)
    }

    /**
     * A diktált szöveg beírása, a KÖRNYEZETHEZ IGAZÍTVA — ugyanaz a szabály,
     * mint a mátrixnál: ha a mező nem szóközzel végződik, teszünk elé egyet.
     */
    private fun commitDictated(text: String) {
        val ic = currentInputConnection
        val before = ic?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val needsSpace = before.isNotEmpty() &&
            !before.last().isWhitespace() &&
            text.firstOrNull()?.isLetterOrDigit() == true
        ic?.commitText(if (needsSpace) " $text" else text, 1)
        sounds?.play(com.superdl.launcher.screenreader.ScreenReaderSounds.Sound.ACTIVATE)
        label?.text = text.takeLast(40)
        tts?.speak("Beírva: $text")
    }

    private fun deleteLastWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
        if (before.isBlank()) {
            tts?.speak("Nincs mit törölni.")
            return
        }
        // A záró szóközöket és az utolsó szót együtt töröljük.
        val trimmed = before.trimEnd()
        val cut = trimmed.lastIndexOf(' ').let { if (it < 0) 0 else it + 1 }
        val word = trimmed.substring(cut)
        ic.deleteSurroundingText(before.length - cut, 0)
        sounds?.play(com.superdl.launcher.screenreader.ScreenReaderSounds.Sound.BACK)
        tts?.speak("Törölve: $word")
    }

    /**
     * BEZÁRÁS: ELŐBB A MIKROFON ÁLL LE, AZTÁN ELTŰNIK, AZTÁN BESZÉL.
     *
     * Az első változat a bemondás VÉGÉRE várt a rejtéssel. Csakhogy a
     * hangfelismerő és a beszélő ugyanazt a hangrendszert használja, és a
     * visszahívás elmaradhatott — akkor a billentyűzet nyitva ragadt, a
     * `closing` zár pedig minden további csippentést elnyelt. Ez pont az a
     * hiba, ami miatt aggódtam: vakon nem látszik, hogy nyitva maradt.
     */
    private fun close() {
        if (closing) return
        closing = true
        stopLoop()
        sounds?.play(com.superdl.launcher.screenreader.ScreenReaderSounds.Sound.OFF)
        try { requestHideSelf(0) } catch (_: Throwable) {}
        tts?.speak("Diktálás bezárva.")
        closing = false
    }

    private fun showKeyboardPicker() {
        try {
            stopLoop()
            sounds?.play(com.superdl.launcher.screenreader.ScreenReaderSounds.Sound.RECENTS)
            tts?.speak("Billentyűzet választása.")
            val imm = getSystemService(INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        } catch (_: Throwable) {
            tts?.speak("A választó nem nyitható meg.")
        }
    }

    // ── Az érintések ────────────────────────────────────────────────────────

    private fun spreadOf(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.hypot(dx, dy)
    }

    private fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                maxPointers = 1
                startSpread = 0f
                downX = event.getX(0)
                downY = event.getY(0)
                moved = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = kotlin.math.max(maxPointers, event.pointerCount)
                if (event.pointerCount == 2) startSpread = spreadOf(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    moved = kotlin.math.max(
                        moved,
                        kotlin.math.hypot(event.getX(0) - downX, event.getY(0) - downY)
                    )
                }
                if (startSpread > 0f && event.pointerCount >= 2) {
                    val shrink = resources.displayMetrics.widthPixels / 6f
                    if (spreadOf(event) < startSpread - shrink) {
                        startSpread = 0f
                        close()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (closing) return true
                val dx = event.getX(0) - downX
                val threshold = resources.displayMetrics.widthPixels * 0.18f
                when {
                    maxPointers >= 3 -> showKeyboardPicker()
                    maxPointers == 2 && dx < -threshold -> deleteLastWord()
                    maxPointers == 1 && moved < threshold / 2 -> {
                        // KOPPINTÁS: ha megálltunk, újraindítjuk a hallgatást.
                        if (!running) startLoop("Folytatom. Mondd a szöveget.")
                    }
                }
                startSpread = 0f
                maxPointers = 0
            }
        }
        return true
    }

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

    override fun onDestroy() {
        stopLoop()
        try { voiceInput?.destroy() } catch (_: Throwable) {}
        voiceInput = null
        try { sounds?.release() } catch (_: Throwable) {}
        sounds = null
        try { tts?.shutdown() } catch (_: Throwable) {}
        tts = null
        super.onDestroy()
    }
}
