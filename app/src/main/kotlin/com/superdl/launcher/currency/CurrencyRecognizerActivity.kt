package com.superdl.launcher.currency

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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.superdl.launcher.R
import com.superdl.launcher.camera.CameraAnalysisConfig
import com.superdl.launcher.camera.CameraStabilityHelper
import com.superdl.launcher.feedback.SoundFeedback
import com.superdl.launcher.feedback.SoundType
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager
import com.superdl.launcher.util.postWhenAlive
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@ExperimentalGetImage
class CurrencyRecognizerActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var sounds: SoundFeedback
    private lateinit var tts: TtsManager
    private lateinit var gestureListener: SwipeGestureListener
    private lateinit var cameraExecutor: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())

    private var classifierEngine: BanknoteClassifierEngine? = null
    private var scanBeepPlayer: ScanBeepPlayer? = null
    private val debouncer = BanknoteScanDebouncer()
    private val consensusFilter = BanknoteConsensusFilter()
    private val torchController = BanknoteTorchController()
    private val scanning = AtomicBoolean(false)
    private val memoryFailureHandled = AtomicBoolean(false)
    private val lastFrameProcessedAt = AtomicLong(0L)
    private val latestBitmap = AtomicReference<Bitmap?>(null)
    private var imageAnalysis: ImageAnalysis? = null
    private var boundCamera: Camera? = null
    private var lastBackPressAt = 0L
    private var lastTorchSpeechAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_currency_recognizer)
        title = getString(R.string.currency_title)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tvStatus = findViewById(R.id.tvCurrencyStatus)
        sounds = SoundFeedback(this)
        tts = TtsManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        scanBeepPlayer = ScanBeepPlayer()

        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = {
                sounds.play(SoundType.SWIPE_UP)
                triggerManualVerification()
            },
            onSwipeDown = {
                sounds.play(SoundType.SWIPE_DOWN)
                tts.speak(getString(R.string.currency_help))
            },
            onSwipeRight = {
                sounds.play(SoundType.SWIPE_RIGHT)
                triggerManualVerification()
            },
            onSwipeLeft = { finishRecognizer() }
        )

        findViewById<View>(R.id.currencyRecognizerRoot).setOnTouchListener { view, event ->
            gestureListener.detector.onTouchEvent(event)
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                view.performClick()
            }
            true
        }

        findViewById<Button>(R.id.btnCurrencyExit).setOnClickListener { finishRecognizer() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackPressAt < 2000L) {
                    finishRecognizer()
                } else {
                    lastBackPressAt = now
                    tts.speak("Kilépéshez nyomd meg újra a vissza gombot, vagy balra swipe-olj.")
                }
            }
        })

        if (hasCameraPermission()) {
            initializeRecognizer()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    private fun initializeRecognizer() {
        try {
            classifierEngine = BanknoteClassifierEngine(this)
        } catch (_: Exception) {
            sounds.play(SoundType.ACTION_ERROR)
            setStatusText(getString(R.string.currency_model_error))
            tts.runWhenReady { tts.speak(getString(R.string.currency_model_error)) }
            return
        }

        setStatusText(getString(R.string.currency_status_active))
        tts.runWhenReady { tts.speak(getString(R.string.currency_intro)) }
        scanning.set(true)
        startCamera()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val previewView = findViewById<PreviewView>(R.id.currencyPreview)
                CameraStabilityHelper.configurePreviewView(previewView)
                val preview = CameraStabilityHelper.buildLightPreview(previewView.surfaceProvider)
                imageAnalysis = CameraAnalysisConfig.imageAnalysisBuilder()
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor, FrameAnalyzer())
                    }
                provider.unbindAll()
                boundCamera = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                boundCamera?.let { torchController.attach(it) }
            } catch (_: Exception) {
                sounds.play(SoundType.ACTION_ERROR)
                setStatusText(getString(R.string.currency_camera_error))
                tts.runWhenReady { tts.speak(getString(R.string.currency_camera_error)) }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setStatusText(text: String) {
        postWhenAlive { tvStatus.text = text }
    }

    private fun onFrame(bitmap: Bitmap) {
        val frameDecision = BanknoteFrameGate.evaluate(bitmap)
        val torchJustOn = torchController.update(frameDecision.metrics)
        if (torchJustOn) {
            maybeSpeakTorchOn()
        }

        if (frameDecision.isEmptySlot) {
            when (debouncer.onAbsentFrame()) {
                BanknoteScanDebouncer.BillPresenceEvent.REMOVED ->
                    setStatusText(getString(R.string.currency_status_scanning))
                else -> Unit
            }
            consensusFilter.reset()
            return
        }

        val rawResult = classifierEngine?.classify(bitmap)
        val stableResult = consensusFilter.submit(frameDecision, rawResult)
        if (stableResult == null) {
            when (debouncer.onAbsentFrame()) {
                BanknoteScanDebouncer.BillPresenceEvent.REMOVED ->
                    setStatusText(getString(R.string.currency_status_scanning))
                else -> Unit
            }
            return
        }

        when (val decision = debouncer.onDetected(stableResult)) {
            is BanknoteScanDebouncer.ScanDecision.Announce -> {
                if (decision.playEntryBeep) {
                    scanBeepPlayer?.playScanStart()
                    setStatusText(getString(R.string.currency_status_detected))
                }
                announceDenomination(decision.result, flush = true)
                debouncer.markAnnounced(decision.result.denomination)
            }
            BanknoteScanDebouncer.ScanDecision.BillRemoved ->
                setStatusText(getString(R.string.currency_status_scanning))
            BanknoteScanDebouncer.ScanDecision.Ignored -> Unit
        }
    }

    private fun maybeSpeakTorchOn() {
        val now = System.currentTimeMillis()
        if (now - lastTorchSpeechAt < 8000L) return
        lastTorchSpeechAt = now
        tts.speakAdd(getString(R.string.currency_torch_on))
    }

    private fun announceDenomination(result: BanknoteClassificationResult, flush: Boolean) {
        val speech = result.denomination.speechHu
        postWhenAlive {
            setStatusText(speech)
            if (flush) tts.speak(speech) else tts.speakAdd(speech)
        }
    }

    private fun triggerManualVerification() {
        val engine = classifierEngine ?: return
        val bitmap = latestBitmap.get()
        if (bitmap == null) {
            tts.speak(getString(R.string.currency_no_frame))
            return
        }

        try {
            val frameDecision = BanknoteFrameGate.evaluate(bitmap)
            torchController.update(frameDecision.metrics)

            if (frameDecision.isEmptySlot) {
                scanBeepPlayer?.playScanStart()
                tts.speak(getString(R.string.currency_no_banknote))
                setStatusText(getString(R.string.currency_status_scanning))
                return
            }

            if (frameDecision.needsMoreLight) {
                torchController.forceOn()
                maybeSpeakTorchOn()
            }

            val result = engine.classifyForManualCheck(bitmap)
            if (result == null || !result.isReliableForManualCheck()) {
                scanBeepPlayer?.playScanStart()
                tts.speak(getString(R.string.currency_not_recognized))
                setStatusText(getString(R.string.currency_status_scanning))
                return
            }
            announceDenomination(result, flush = true)
            debouncer.markAnnounced(result.denomination)
        } catch (_: Exception) {
            sounds.play(SoundType.ACTION_ERROR)
            tts.speak(getString(R.string.currency_verify_error))
        }
    }

    private fun finishRecognizer() {
        scanning.set(false)
        debouncer.reset()
        consensusFilter.reset()
        torchController.release()
        sounds.play(SoundType.SWIPE_LEFT)
        tts.speakThen(getString(R.string.currency_exit)) { finish() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event?.repeatCount == 0) triggerManualVerification()
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
                initializeRecognizer()
            } else {
                sounds.play(SoundType.ACTION_ERROR)
                setStatusText(getString(R.string.currency_permission_denied))
                tts.runWhenReady { tts.speak(getString(R.string.currency_permission_denied)) }
                finish()
            }
        }
    }

    override fun onDestroy() {
        scanning.set(false)
        mainHandler.removeCallbacksAndMessages(null)
        torchController.release()
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        boundCamera = null
        CameraStabilityHelper.shutdownExecutor(cameraExecutor)
        classifierEngine?.close()
        classifierEngine = null
        scanBeepPlayer?.close()
        scanBeepPlayer = null
        latestBitmap.getAndSet(null)?.recycle()
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
            if (now - lastFrameProcessedAt.get() < FRAME_INTERVAL_MS) {
                imageProxy.close()
                return
            }
            lastFrameProcessedAt.set(now)

            if (classifierEngine == null) {
                imageProxy.close()
                return
            }

            try {
                val bitmap = imageProxy.toBitmap()
                latestBitmap.getAndSet(bitmap)?.recycle()
                if (scanning.get()) {
                    onFrame(bitmap)
                }
            } catch (oom: OutOfMemoryError) {
                latestBitmap.getAndSet(null)?.recycle()
                System.gc()
                postWhenAlive { handleFrameMemoryFailure() }
            } catch (_: Exception) {
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun handleFrameMemoryFailure() {
        if (!memoryFailureHandled.compareAndSet(false, true)) return
        scanning.set(false)
        imageAnalysis?.clearAnalyzer()
        sounds.play(SoundType.ACTION_ERROR)
        setStatusText(getString(R.string.currency_memory_error))
        tts.speak(getString(R.string.currency_memory_error))
    }

    companion object {
        private const val REQ_CAMERA = 7104
        private const val FRAME_INTERVAL_MS = 360L
    }
}