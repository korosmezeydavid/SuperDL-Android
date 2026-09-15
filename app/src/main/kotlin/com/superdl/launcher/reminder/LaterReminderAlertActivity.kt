package com.superdl.launcher.reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.superdl.launcher.R
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager

/**
 * AZ EMLÉKEZTETŐ KÉPERNYŐJE.
 *
 * MIÉRT LEHET INNEN AZONNAL HÍVNI: enélkül a szolgáltatás félkész. Megszólal,
 * hogy „vissza kellett volna hívnod Pétert", és utána a felhasználó ott áll,
 * hogy most akkor keresse meg a számot a hívásnaplóban. Vakon, menüről
 * menüre. Amire emlékeztet, azt el is kell tudni intézni egy mozdulattal.
 *
 * JOBBRA: hívás most. BALRA: még tíz perc. FEL-LE: ismétlés.
 * Ez ugyanaz a mozdulat-osztás, mint máshol a programban — vészhelyzetben
 * vagy félálomban újat megtanulni nem lehet.
 */
class LaterReminderAlertActivity : AppCompatActivity() {

    private lateinit var tts: TtsManager
    private lateinit var gestureListener: SwipeGestureListener

    private var entryId = -1
    private var number = ""
    private var who = ""
    private var text = ""
    private var isCall = true
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOnLockedScreen()
        setContentView(R.layout.activity_media_player)
        applyImmersive()

        entryId = intent.getIntExtra(EXTRA_ID, -1)
        number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        who = intent.getStringExtra(EXTRA_WHO).orEmpty()
        text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        isCall = intent.getBooleanExtra(EXTRA_IS_CALL, true)

        findViewById<TextView>(R.id.tvPlayerTitle).text =
            if (isCall) "Vissza kellett volna hívnod" else "Üzenet, amivel dolgod van"
        findViewById<TextView>(R.id.tvPlayerPosition).text = who
        findViewById<TextView>(R.id.tvPlayerStatus).text = number
        findViewById<TextView>(R.id.tvPlayerHint).text =
            if (isCall) "➡ hívás most  •  ⬅ még 10 perc" else "➡ üzenet  •  ⬅ még 10 perc"

        tts = TtsManager(this)
        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = { announce() },
            onSwipeDown = { announce() },
            onSwipeRight = { actNow() },
            onSwipeLeft = { snooze() }
        )
        tts.runWhenReady { announce() }
    }

    private fun announce() {
        val what = if (isCall) "Söpörj jobbra, és hívom." else "Söpörj jobbra az üzenethez."
        tts.speak("$text $what Balra: még tíz perc.")
    }

    /**
     * A HÍVÁS INDÍTÁSA. `ACTION_CALL` helyett `ACTION_DIAL` NEM jó ide: az
     * csak beírja a számot a tárcsázóba, és a vak felhasználónak még meg
     * kellene keresnie a zöld gombot. Ha nincs hívás-engedély, esünk vissza
     * a tárcsázóra — az még mindig jobb a semminél.
     */
    private fun actNow() {
        if (handled) return
        handled = true
        done()
        if (number.isBlank()) {
            tts.speakThen("Nincs telefonszám ehhez az emlékeztetőhöz.") { finish() }
            return
        }
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val action = if (isCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val spoken = if (isCall) "Hívom: $who." else "Nyitom: $who."
        tts.speakThen(spoken) {
            try {
                startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) {
                try {
                    startActivity(
                        Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Throwable) {
                }
            }
            finish()
        }
    }

    /** Még tíz perc. Az emlékeztető marad, csak később szólal meg újra. */
    private fun snooze() {
        if (handled) return
        handled = true
        val newDue = System.currentTimeMillis() + SNOOZE_MS
        try {
            val updated = LaterReminderStore.reschedule(applicationContext, entryId, newDue)
            if (updated != null) LaterReminderScheduler.schedule(applicationContext, updated)
        } catch (_: Throwable) {
        }
        tts.speakThen("Rendben, tíz perc múlva újra szólok.") { finish() }
    }

    /** Elintézettnek jelöli: lekerül a listáról, és nem szólal meg újra. */
    private fun done() {
        try {
            LaterReminderScheduler.cancel(applicationContext, entryId)
            LaterReminderStore.remove(applicationContext, entryId)
        } catch (_: Throwable) {
        }
    }

    private fun showOnLockedScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun applyImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureListener.detector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val SNOOZE_MS = 10 * 60_000L

        const val EXTRA_ID = "emlekezteto_id"
        const val EXTRA_NUMBER = "szam"
        const val EXTRA_WHO = "kicsoda"
        const val EXTRA_TEXT = "szoveg"
        const val EXTRA_IS_CALL = "hivas_e"

        fun launch(context: Context, entry: LaterReminder) {
            val intent = Intent(context, LaterReminderAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_ID, entry.id)
                putExtra(EXTRA_NUMBER, entry.number)
                putExtra(EXTRA_WHO, entry.who())
                putExtra(EXTRA_TEXT, entry.speakAlert())
                putExtra(EXTRA_IS_CALL, entry.isCall)
            }
            context.startActivity(intent)
        }
    }
}
