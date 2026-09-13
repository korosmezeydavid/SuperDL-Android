package com.superdl.launcher.home

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.superdl.launcher.R
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager

/**
 * A VISSZASZÁMLÁLÁS KÉPERNYŐJE.
 *
 * MIÉRT KELL EGYÁLTALÁN KÉPERNYŐ: a leállításhoz olyan mozdulat kell, ami a
 * zsebből előkapott telefonon is működik, zárolt képernyőn is. Pontosan ez az
 * ébresztő riasztási képernyőjének a dolga — ugyanaz a minta, ugyanaz a
 * mozdulat. Vészhelyzetben valami újat megtanulni nem lehet.
 *
 * BALRA: leállítás. Nincs jobbra-művelet, és ez szándékos: itt egyetlen
 * dolgot lehet tenni, és annak a legkönnyebb mozdulatnak kell lennie.
 * Az ébresztőnél is a balra a leállítás.
 */
class HomeWatchAlertActivity : AppCompatActivity() {

    private lateinit var tts: TtsManager
    private lateinit var gestureListener: SwipeGestureListener
    private val handler = Handler(Looper.getMainLooper())

    private var reason = ""
    private var probe = false
    private var secondsLeft = 45
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOnLockedScreen()
        setContentView(R.layout.activity_media_player)
        applyImmersive()

        reason = intent.getStringExtra(EXTRA_REASON) ?: ""
        probe = intent.getBooleanExtra(EXTRA_PROBE, false)
        secondsLeft = intent.getIntExtra(EXTRA_SECONDS, 45)

        findViewById<TextView>(R.id.tvPlayerTitle).text = "Még nem vagy otthon"
        findViewById<TextView>(R.id.tvPlayerPosition).text =
            if (probe) "Otthon-figyelés — PRÓBA" else "Otthon-figyelés"
        findViewById<TextView>(R.id.tvPlayerStatus).text = reason
        findViewById<TextView>(R.id.tvPlayerHint).text = "⬅ leállítás  •  ⬆⬇ ismétlés"

        tts = TtsManager(this)
        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = { announce() },
            onSwipeDown = { announce() },
            onSwipeRight = { announce() },
            onSwipeLeft = { stopWatch() }
        )
        tts.runWhenReady { announce() }
        handler.postDelayed(ticker, 10_000L)
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (finished) return
            secondsLeft -= 10
            if (secondsLeft <= 0) {
                // A szolgáltatás küld, nem mi. Itt csak bezárjuk a képernyőt.
                finish()
                return
            }
            findViewById<TextView>(R.id.tvPlayerStatus).text = "$secondsLeft másodperc"
            tts.speak("$secondsLeft másodperc. Ha otthon vagy, söpörj balra.")
            handler.postDelayed(this, 10_000L)
        }
    }

    private fun announce() {
        val fej = if (probe) "Próba. " else ""
        tts.speak(
            "${fej}Az otthon-figyelés szerint még nem értél haza: $reason. " +
                "$secondsLeft másodperc múlva üzenek. Ha otthon vagy, söpörj balra."
        )
    }

    private fun stopWatch() {
        if (finished) return
        finished = true
        handler.removeCallbacks(ticker)
        HomeWatchService.requestStop()
        try {
            startService(
                Intent(this, HomeWatchService::class.java).apply {
                    action = HomeWatchService.ACTION_STOP
                }
            )
        } catch (_: Exception) {
        }
        tts.speakThen("Otthon-figyelés leállítva. Nem küldök üzenetet.") { finish() }
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
        finished = true
        handler.removeCallbacks(ticker)
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_REASON = "ok"
        const val EXTRA_PROBE = "proba"
        const val EXTRA_SECONDS = "masodperc"
    }
}
