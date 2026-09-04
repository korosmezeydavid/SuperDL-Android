package com.superdl.launcher.share

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager

/**
 * A FOGADÓ KÓD BEÍRÁSA BILLENTYŰZETTEL.
 *
 * MIÉRT KELL EZ KÜLÖN: a magic-wormhole kódja egy szám és két ANGOL szó,
 * például „7-crossover-clockwork". A magyar beszédfelismerő ezt nem érti
 * meg, tehát a diktálás itt nem járható út. Két út marad, és mindkettő
 * megvan: a vágólap (ha a küldő üzenetben átküldte), és a billentyűzet
 * (ha telefonban mondta be).
 *
 * A mintát a képernyőolvasó elnevező ablakából vesszük, hogy a mozdulatok
 * ugyanazok legyenek: jobbra kész, balra mégse, lefelé felolvasás.
 */
class WormholeCodeInputActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODE = "wh_typed_code"
    }

    private lateinit var tts: TtsManager
    private lateinit var gestureListener: SwipeGestureListener
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(32, 32, 32, 32)
        }
        root.addView(
            TextView(this).apply {
                textSize = 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                text = "Fogadó kód"
            }
        )
        input = EditText(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "például 7-crossover-clockwork"
            setSingleLine(true)
        }
        root.addView(
            input,
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
            onSwipeRight = { done() },
            onSwipeLeft = { cancel() }
        )

        input.requestFocus()
        input.postDelayed({
            try {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            } catch (_: Exception) {
            }
        }, 300L)

        tts.speak(
            "Írd be a kódot, amit a küldőtől kaptál. Egy szám és két szó, " +
                "kötőjelekkel elválasztva."
        )
        tts.speakAdd("Jobbra kész, balra mégse, lefelé felolvasás.")
    }

    private fun speakHelp() {
        tts.speak(
            "Írd be a kódot a billentyűzettel. Jobbra söprés kész, balra söprés " +
                "mégse, lefelé söprés felolvassa, amit eddig beírtál."
        )
    }

    private fun speakContent() {
        val text = input.text?.toString().orEmpty()
        if (text.isBlank()) {
            tts.speak("Még nincs beírva semmi.")
        } else {
            // Betűzve, mert egy elrontott betű az egész kódot használhatatlanná teszi.
            tts.speak("Eddig ez van beírva: " + text.map { it }.joinToString(", "))
        }
    }

    private fun done() {
        val code = input.text?.toString()?.trim().orEmpty()
        if (code.isBlank()) {
            tts.speak("Üres a kód, ezért nem tudom elindítani a fogadást.")
            return
        }
        setResult(Activity.RESULT_OK, intent.putExtra(EXTRA_CODE, code))
        finish()
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        tts.speak("Mégse.")
        finish()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureListener.detector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
