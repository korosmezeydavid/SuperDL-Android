package com.superdl.launcher.screenreader

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager

/**
 * ELNEVEZÉS BILLENTYŰZETTEL — a hangos elnevezés párja.
 *
 * MIÉRT KELL:
 * Az elnevezés eddig KIZÁRÓLAG beszédfelismeréssel ment. Ha az nem érhető el —
 * nincs internet, zajos a hely, hiányzik a magyar nyelvi csomag, vagy egyszerűen
 * nem akarsz beszélni a villamoson —, akkor a névtelen gombot SEHOGY nem
 * lehetett elnevezni. Pedig a mátrix-billentyűzet megvan, és működik.
 *
 * MŰKÖDÉS: megnyílik egy szövegmező a billentyűzettel. Beírod a nevet, és
 * JOBBRA söpörsz: mentés. BALRA: elvetés. LEFELÉ: felolvassa, mi van beírva.
 * Ez ugyanaz a négy irány, amit a program mindenhol használ.
 *
 * MIÉRT KULCCSAL DOLGOZIK: mire ez az ablak megnyílik, az elnevezendő elem már
 * nincs a képernyőn — másik alkalmazás van elöl. A képernyőolvasó ezért ELŐRE
 * kiszámolja az elem kulcsát, és azt adja át. Így az elnevezés akkor is
 * elmenthető, ha az eredeti elem közben eltűnt.
 */
class LabelInputActivity : Activity() {

    companion object {
        /** Az elnevezendő elem kulcsa (ScreenReaderLabels.keyOf). */
        const val EXTRA_KEY = "label_key"

        /** Mit mondjunk, MELYIK elemről van szó — csak felolvasáshoz. */
        const val EXTRA_WHAT = "label_what"
    }

    private lateinit var tts: TtsManager
    private lateinit var gestureListener: SwipeGestureListener
    private var input: EditText? = null
    private var labelKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        labelKey = intent?.getStringExtra(EXTRA_KEY)?.takeIf { it.isNotBlank() }
        val what = intent?.getStringExtra(EXTRA_WHAT).orEmpty()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            text = "Elnevezés"
        }
        root.addView(title)

        val existing = labelKey?.let { ScreenReaderLabels.labelForKey(this, it) }

        val field = EditText(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "A gomb neve"
            setSingleLine(true)
            if (existing != null) {
                setText(existing)
                setSelection(existing.length)
            }
        }
        input = field
        root.addView(
            field,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(root)

        tts = TtsManager(this)
        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = { speakHelp() },
            onSwipeDown = { speakContent() },
            onSwipeRight = { save() },
            onSwipeLeft = { cancel() }
        )

        if (labelKey == null) {
            // Ez nem fordulhat elő rendes úton, de ha mégis: ne hallgassunk.
            tts.speak("Ezt az elemet nem tudom megjegyezni.")
            field.postDelayed({ finish() }, 2500L)
            return
        }

        field.requestFocus()
        field.postDelayed({
            try {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            } catch (_: Exception) {
            }
        }, 300L)

        val opening = if (existing != null) {
            "Elnevezés. Jelenlegi neve: $existing. Írd be az újat."
        } else if (what.isNotBlank()) {
            "Elnevezés. Az elem most ennyi: $what. Írd be, minek nevezzem."
        } else {
            "Elnevezés. Írd be, minek nevezzem ezt az elemet."
        }
        tts.speak(opening)
        tts.speakAdd("Jobbra mentés, balra elvetés, lefelé felolvasás.")
    }

    private fun speakHelp() {
        tts.speak(
            "Írd be a nevet a billentyűzettel. Jobbra söprés menti, " +
                "balra söprés elveti, lefelé felolvassa, mi van beírva."
        )
    }

    private fun speakContent() {
        val text = input?.text?.toString().orEmpty()
        if (text.isBlank()) tts.speak("A mező üres.") else tts.speak("Beírva: $text")
    }

    /**
     * MENTÉS.
     *
     * Az ÜRES mező itt nem hiba, hanem TÖRLÉS: ha kitörlöd a nevet és mentesz,
     * azzal veszed le a címkét az elemről. Ez ma az egyetlen módja annak, hogy
     * egy elrontott elnevezéstől megszabadulj — ezért mondjuk is ki, mi történt.
     */
    private fun save() {
        val key = labelKey ?: return
        val text = input?.text?.toString().orEmpty().trim()
        ScreenReaderLabels.setLabelByKey(this, key, text)
        if (text.isBlank()) {
            tts.speak("A név törölve.")
        } else {
            tts.speak("Elmentve: $text. Mostantól így fogom nevezni.")
        }
        input?.postDelayed({ finish() }, 1800L)
    }

    private fun cancel() {
        tts.speak("Elvetve.")
        input?.postDelayed({ finish() }, 900L)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureListener.detector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun onDestroy() {
        try {
            tts.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
