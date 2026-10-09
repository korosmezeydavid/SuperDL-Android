package com.superdl.launcher.sound

import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.superdl.launcher.R
import com.superdl.launcher.feedback.SoundFeedback
import com.superdl.launcher.feedback.SoundType
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager

/**
 * Újrahasznosítható gyári hang-választó vak felhasználóknak.
 * Fel/le: hangok között lépkedés (mindegyikbe azonnal belehallgat),
 * jobbra: kiválasztás, balra: mégse.
 *
 * Az eredményt Intent-ben adja vissza: EXTRA_RESULT_URI, EXTRA_RESULT_TITLE.
 */
class RingtonePickerActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tts: TtsManager
    private lateinit var sounds: SoundFeedback
    private lateinit var gestureListener: SwipeGestureListener
    private lateinit var preview: RingtonePreviewPlayer

    private var items: List<SystemRingtoneHelper.RingtoneItem> = emptyList()
    private var index = 0
    private var streamType = AudioManager.STREAM_ALARM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)
        applyImmersive()

        tvTitle = findViewById(R.id.tvPlayerTitle)
        tvPosition = findViewById(R.id.tvPlayerPosition)
        findViewById<TextView>(R.id.tvPlayerStatus).text = ""
        findViewById<TextView>(R.id.tvPlayerHint).text =
            "Fel-le: hangok. Jobbra: kiválaszt. Balra: mégse."

        tts = TtsManager(this)
        sounds = SoundFeedback(this)
        preview = RingtonePreviewPlayer(this)

        val type = intent.getIntExtra(EXTRA_TONE_TYPE, TONE_ALARM)
        val (list, stream, label) = when (type) {
            TONE_RINGTONE -> Triple(
                SystemRingtoneHelper.ringtones(this),
                AudioManager.STREAM_RING,
                "Csengőhang választás"
            )
            TONE_NOTIFICATION -> Triple(
                SystemRingtoneHelper.notificationTones(this),
                AudioManager.STREAM_NOTIFICATION,
                "Értesítési hang választás"
            )
            TONE_ALL -> Triple(
                SystemRingtoneHelper.allAlertTones(this),
                AudioManager.STREAM_ALARM,
                "Programhang választás"
            )
            else -> Triple(
                SystemRingtoneHelper.alarmTones(this),
                AudioManager.STREAM_ALARM,
                "Ébresztőhang választás"
            )
        }
        items = if (intent.hasExtra(EXTRA_CATEGORY)) {
            listOf(SystemRingtoneHelper.RingtoneItem("Beépített SuperDL-hangok", Uri.parse(BUILT_IN_TONES))) + list
        } else list
        streamType = stream
        tvTitle.text = intent.getStringExtra(EXTRA_PICKER_LABEL) ?: label

        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = {
                sounds.play(SoundType.SWIPE_UP)
                navigate(-1)
            },
            onSwipeDown = {
                sounds.play(SoundType.SWIPE_DOWN)
                navigate(+1)
            },
            onSwipeRight = {
                sounds.play(SoundType.SWIPE_RIGHT)
                choose()
            },
            onSwipeLeft = {
                sounds.play(SoundType.SWIPE_LEFT)
                cancelPick()
            }
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancelPick()
        })

        if (items.isEmpty()) {
            tts.speakThen("Nem találtam elérhető rendszerhangot. A saját fájlokhoz ellenőrizd a médiahangok engedélyét és a Notifications mappát.") {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            return
        }

        // A korábban kiválasztott hangra állunk, ha átadták.
        val preselect = intent.getStringExtra(EXTRA_CURRENT_URI)
        if (!preselect.isNullOrBlank()) {
            val i = items.indexOfFirst { it.uri.toString() == preselect }
            if (i >= 0) index = i
        }

        tts.runWhenReady {
            tts.speak("$label. ${items.size} hang. Fel-le lépkedés, minden hangba belehallgatsz. ${current().title}")
            previewCurrent()
        }
        updateDisplay()
    }

    private fun current() = items[index]

    private fun navigate(delta: Int) {
        index = (index + delta + items.size) % items.size
        updateDisplay()
        tts.speak(current().title)
        previewCurrent()
    }

    private fun previewCurrent() {
        if (current().uri.toString() == BUILT_IN_TONES) preview.stop()
        else preview.preview(current().uri, streamType)
    }

    private fun updateDisplay() {
        tvPosition.text = "${index + 1} / ${items.size}"
    }

    private fun choose() {
        preview.stop()
        val chosen = current()
        val data = Intent().apply {
            putExtra(EXTRA_RESULT_URI, chosen.uri.toString())
            putExtra(EXTRA_RESULT_TITLE, chosen.title)
            putExtra(EXTRA_CATEGORY, intent.getStringExtra(EXTRA_CATEGORY))
        }
        setResult(Activity.RESULT_OK, data)
        tts.speakThen("Kiválasztva: ${chosen.title}.") { finish() }
    }

    private fun cancelPick() {
        preview.stop()
        setResult(Activity.RESULT_CANCELED)
        tts.speakThen("Mégse.") { finish() }
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

    override fun onPause() {
        super.onPause()
        preview.stop()
    }

    override fun onDestroy() {
        preview.stop()
        tts.shutdown()
        sounds.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TONE_TYPE = "tone_type"
        const val EXTRA_CURRENT_URI = "current_uri"
        const val EXTRA_RESULT_URI = "result_uri"
        const val EXTRA_RESULT_TITLE = "result_title"
        const val EXTRA_CATEGORY = "alert_category"
        const val EXTRA_PICKER_LABEL = "picker_label"
        const val BUILT_IN_TONES = "superdl:built-in-tones"

        const val TONE_ALARM = 0
        const val TONE_RINGTONE = 1
        const val TONE_NOTIFICATION = 2
        const val TONE_ALL = 3
    }
}
