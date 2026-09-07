package com.superdl.launcher.textreader

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.superdl.launcher.R
import com.superdl.launcher.camera.AutoTorchController
import com.superdl.launcher.camera.CameraStabilityHelper
import com.superdl.launcher.environment.GuidanceTone
import com.superdl.launcher.environment.SpatialDescriber
import com.superdl.launcher.feedback.SoundFeedback
import com.superdl.launcher.feedback.SoundType
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager
import com.superdl.launcher.util.postWhenAlive
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * SZÖVEG KERESÉSE ÉS RÁVEZETÉS — utcatábla, ajtószám, felirat, peron.
 *
 * MIÉRT KÜLÖN A SZÖVEGOLVASÓTÓL: a szövegolvasó MINDENT felolvas, ami a
 * kamera elé kerül. Egy utcán ez használhatatlan — húsz felirat közül
 * tizenkilenc érdektelen, és mire a huszadikhoz ér, a keresett tábla már
 * elmozdult. Itt fordítva működik: megmondod, MIT keresel, és a telefon
 * csendben marad, amíg meg nem találja.
 *
 * A rávezetés ugyanaz a `GuidanceTone`, amit a tárgykereséshez írtunk: a
 * sípolás sűrűsödik, ahogy a felirat a kép közepe felé kerül, és elhallgat,
 * amikor ott van. Egy vak ember számára az utcatábla megtalálása nem
 * információ kérdése, hanem irányé.
 *
 * ŐSZINTE KORLÁT, amit ki is mondunk a felhasználónak: a kamera csak azt
 * látja, ami elé kerül. Egy magasan lévő utcatáblát fölfelé kell tartani,
 * és sötétben a lámpa sem tesz csodát öt méterre. Ezt jobb előre tudni,
 * mint ötpercnyi hiábavaló forgatás után.
 */
@ExperimentalGetImage
class TextSearchActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var sounds: SoundFeedback
    private lateinit var tts: TtsManager
    private lateinit var gestureListener: SwipeGestureListener
    private lateinit var cameraExecutor: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())

    private var engine: TextRecognitionEngine? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val autoTorch = AutoTorchController()
    private val guidance = GuidanceTone()

    private val scanning = AtomicBoolean(false)
    private val lastFrameAt = AtomicLong(0L)

    private var query: String = ""
    private var found = false
    private var stopped = false
    private var lastSeenAt = 0L
    private var centeredSince = 0L
    private var lastSpokenAt = 0L
    private var lastBackPressAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_reader)
        query = intent.getStringExtra(EXTRA_QUERY).orEmpty().trim()
        title = "Szöveg keresése"
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tvStatus = findViewById(R.id.tvTextReaderStatus)
        CameraStabilityHelper.configurePreviewView(findViewById(R.id.textReaderPreview))
        sounds = SoundFeedback(this)
        tts = TtsManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = {
                sounds.play(SoundType.SWIPE_UP)
                speakState()
            },
            onSwipeDown = {
                sounds.play(SoundType.SWIPE_DOWN)
                speakHelp()
            },
            onSwipeRight = {
                sounds.play(SoundType.SWIPE_RIGHT)
                restartSearch()
            },
            onSwipeLeft = { finishSearch() }
        )

        findViewById<View>(R.id.textReaderRoot).setOnTouchListener { view, event ->
            gestureListener.detector.onTouchEvent(event)
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                view.performClick()
            }
            true
        }

        findViewById<Button>(R.id.btnTextReaderExit).setOnClickListener { finishSearch() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackPressAt < 2000L) {
                    finishSearch()
                } else {
                    lastBackPressAt = now
                    tts.speak("Kilépéshez nyomd meg újra a vissza gombot, vagy balra söpörj.")
                }
            }
        })

        if (query.isBlank()) {
            tts.runWhenReady { tts.speak("Nem tudom, mit keressek. Kilépek.") }
            finish()
            return
        }

        if (hasCameraPermission()) {
            startSearch()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    private fun startSearch() {
        engine = TextRecognitionEngine()
        found = false
        stopped = false
        lastSeenAt = 0L
        centeredSince = 0L
        lastSpokenAt = 0L
        setStatus("Keresem: $query")
        tts.runWhenReady {
            tts.speak(
                "Keresem ezt a feliratot: $query. Pásztázz lassan a telefonnal. " +
                    "A sípolás annál sűrűbb, minél közelebb van a felirat a kép közepéhez. " +
                    "Utcatáblánál tartsd kissé felfelé. Le söprés: súgó. Balra: kilépés."
            )
        }
        startCamera()
        scanning.set(true)
        guidance.start()
        mainHandler.postDelayed({ stopByTimeout() }, TIMEOUT_MS)
    }

    private fun restartSearch() {
        mainHandler.removeCallbacksAndMessages(null)
        guidance.stop()
        found = false
        stopped = false
        lastSeenAt = 0L
        centeredSince = 0L
        lastSpokenAt = 0L
        scanning.set(true)
        setStatus("Keresem: $query")
        tts.speak("Újrakezdem: $query.")
        guidance.start()
        mainHandler.postDelayed({ stopByTimeout() }, TIMEOUT_MS)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = CameraStabilityHelper.buildLightPreview(
                    findViewById<PreviewView>(R.id.textReaderPreview).surfaceProvider
                )
                imageAnalysis = CameraStabilityHelper.buildLightImageAnalysis()
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, FrameAnalyzer()) }
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                autoTorch.attach(camera)
            } catch (_: Exception) {
                sounds.play(SoundType.ACTION_ERROR)
                setStatus(getString(R.string.text_reader_camera_error))
                tts.runWhenReady { tts.speak(getString(R.string.text_reader_camera_error)) }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onBoxes(boxes: List<TextBox>) {
        if (stopped) return
        val now = System.currentTimeMillis()
        val talalat = TextMatcher.find(boxes, query)
            .minByOrNull { SpatialDescriber.centerDistance(it.box) }

        if (talalat == null) {
            if (now - lastSeenAt > LOST_MS) {
                guidance.update(null)
                centeredSince = 0L
                if (found) {
                    found = false
                    guidance.start()
                    postWhenAlive {
                        setStatus("Keresem: $query")
                        tts.speak("Kiment a képből. Keresem tovább.")
                    }
                }
            }
            return
        }

        lastSeenAt = now
        val tavolsag = SpatialDescriber.centerDistance(talalat.box)
        if (!found) guidance.update(tavolsag)
        val leiras = SpatialDescriber.describe(talalat.box)
        postWhenAlive { setStatus("${talalat.text} — $leiras") }

        if (SpatialDescriber.isCentered(talalat.box)) {
            if (centeredSince == 0L) centeredSince = now
            if (!found && now - centeredSince >= CONFIRM_MS) {
                found = true
                guidance.success()
                postWhenAlive {
                    setStatus("Megvan: ${talalat.text}")
                    // A TELJES felismert sort mondjuk ki, nem a keresett
                    // szót: az utcatáblán ott a házszám is, és pont az a
                    // többlet, amiért érdemes volt megkeresni.
                    tts.speak("Megvan. ${talalat.text}. $leiras.")
                }
            }
            return
        }

        centeredSince = 0L
        if (!found && now - lastSpokenAt >= SPEAK_INTERVAL_MS) {
            lastSpokenAt = now
            postWhenAlive { tts.speak(leiras) }
        }
    }

    private fun speakState() {
        val now = System.currentTimeMillis()
        tts.speak(
            when {
                stopped -> "A keresés leállt. Jobbra söprés: újrakezdés."
                found -> "$query megvan, a kép közepén."
                now - lastSeenAt <= LOST_MS -> "$query látszik, vezetlek rá."
                else -> "Keresem ezt: $query. Még nincs meg."
            }
        )
    }

    private fun speakHelp() {
        tts.speak(
            "Ezt keresem: $query. Pásztázz lassan, körülbelül karnyújtásnyi távolságból. " +
                "Utcatábla magasan van, tartsd a telefont felfelé döntve. " +
                "A kamera csak azt látja, ami elé kerül, és sötétben messzire " +
                "a lámpa sem világít. Fel söprés: hol tartunk. Jobbra: újrakezdés. " +
                "Balra: kilépés."
        )
    }

    private fun stopByTimeout() {
        if (stopped || found) return
        stopped = true
        scanning.set(false)
        guidance.stop()
        setStatus("A keresés leállt")
        tts.speak(
            "A keresést leállítom, mert három perce tart, és fogyasztja az " +
                "akkumulátort. Jobbra söprés: újrakezdés. Balra: kilépés."
        )
    }

    private fun setStatus(text: String) {
        postWhenAlive { tvStatus.text = text }
    }

    private fun finishSearch() {
        scanning.set(false)
        guidance.stop()
        sounds.play(SoundType.SWIPE_LEFT)
        tts.speakThen("Keresés bezárva.") { finish() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event?.repeatCount == 0) speakState()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSearch()
            } else {
                sounds.play(SoundType.ACTION_ERROR)
                tts.runWhenReady {
                    tts.speak("Kamera engedély nélkül nem tudok szöveget keresni.")
                }
                finish()
            }
        }
    }

    override fun onDestroy() {
        scanning.set(false)
        mainHandler.removeCallbacksAndMessages(null)
        try {
            guidance.release()
        } catch (_: Exception) {
        }
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        try {
            autoTorch.release()
        } catch (_: Exception) {
        }
        CameraStabilityHelper.shutdownExecutor(cameraExecutor)
        engine?.close()
        engine = null
        tts.shutdown()
        sounds.release()
        super.onDestroy()
    }

    @ExperimentalGetImage
    private inner class FrameAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            if (!scanning.get()) {
                imageProxy.close()
                return
            }
            val now = System.currentTimeMillis()
            if (now - lastFrameAt.get() < FRAME_INTERVAL_MS) {
                imageProxy.close()
                return
            }
            lastFrameAt.set(now)

            try {
                val fenyero = AutoTorchController.meanLuminance(imageProxy)
                postWhenAlive { autoTorch.update(fenyero) }
            } catch (_: Exception) {
            }

            val motor = engine
            if (motor == null) {
                imageProxy.close()
                return
            }

            var masolat: Bitmap? = null
            try {
                masolat = imageProxy.toBitmap().copy(Bitmap.Config.ARGB_8888, false)
            } catch (_: Exception) {
            } finally {
                imageProxy.close()
            }
            val kep = masolat ?: return

            // A felismerés aszinkron: a bitmapet CSAK a válasz után szabad
            // eldobni, különben a felismerő félkész képen dolgozna.
            postWhenAlive {
                motor.recognizeBlocks(
                    kep,
                    onResult = { boxes ->
                        onBoxes(boxes)
                        kep.recycle()
                    },
                    onError = {
                        // Foglalt vagy hibázott a felismerő: némán ejtjük a
                        // képkockát. Minden ilyet kimondani elviselhetetlen
                        // fecsegés lenne — másodpercenként többször jönne.
                        kep.recycle()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_QUERY = "text_query"
        private const val REQ_CAMERA = 7104

        /** Ritkábban, mint a tárgyfelismerőnél: az OCR jóval drágább. */
        private const val FRAME_INTERVAL_MS = 350L
        private const val CONFIRM_MS = 400L
        private const val LOST_MS = 1500L
        private const val SPEAK_INTERVAL_MS = 3500L
        private const val TIMEOUT_MS = 180_000L
    }
}
