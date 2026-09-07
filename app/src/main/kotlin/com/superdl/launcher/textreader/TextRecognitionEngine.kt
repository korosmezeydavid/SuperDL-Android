package com.superdl.launcher.textreader

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class TextRecognitionEngine : Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val busy = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    fun recognize(
        bitmap: Bitmap,
        onResult: (String) -> Unit,
        onError: () -> Unit
    ) {
        if (!busy.compareAndSet(false, true)) {
            onError()
            return
        }
        clearTimeout()
        val timeout = Runnable {
            if (busy.compareAndSet(true, false)) {
                onError()
            }
        }
        timeoutRunnable = timeout
        mainHandler.postDelayed(timeout, RECOGNITION_TIMEOUT_MS)

        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                clearTimeout()
                busy.set(false)
                val text = visionText.text.orEmpty()
                mainHandler.post { onResult(text) }
            }
            .addOnFailureListener {
                clearTimeout()
                busy.set(false)
                mainHandler.post { onError() }
            }
    }

    /**
     * SZÖVEGKERESÉSHEZ: nem csak a szöveget adja vissza, hanem azt is, hogy
     * MELYIK RÉSZÉN van a képnek.
     *
     * A `recognize()` a felolvasáshoz jó — ott mindegy, hol volt a szöveg.
     * A kereséshez viszont a HELY a lényeg: abból lehet megmondani, hogy
     * merre fordítsa a telefont. A dobozokat a kép méretével elosztva
     * 0 és 1 közötti arányokká alakítjuk, mert a rávezető logika
     * (SpatialDescriber) ilyenekkel dolgozik.
     */
    fun recognizeBlocks(
        bitmap: Bitmap,
        onResult: (List<TextBox>) -> Unit,
        onError: () -> Unit
    ) {
        if (!busy.compareAndSet(false, true)) {
            onError()
            return
        }
        clearTimeout()
        val timeout = Runnable {
            if (busy.compareAndSet(true, false)) {
                onError()
            }
        }
        timeoutRunnable = timeout
        mainHandler.postDelayed(timeout, RECOGNITION_TIMEOUT_MS)

        val szelesseg = bitmap.width.toFloat().coerceAtLeast(1f)
        val magassag = bitmap.height.toFloat().coerceAtLeast(1f)
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                clearTimeout()
                busy.set(false)
                val boxes = mutableListOf<TextBox>()
                for (block in visionText.textBlocks) {
                    // A SOROKAT vesszük, nem a blokkokat: egy blokk gyakran
                    // fél táblát összefog, és akkor a „hol van" válasz a
                    // blokk közepére mutatna, nem a keresett szóra.
                    for (line in block.lines) {
                        val r = line.boundingBox ?: continue
                        boxes.add(
                            TextBox(
                                text = line.text.orEmpty(),
                                box = android.graphics.RectF(
                                    r.left / szelesseg,
                                    r.top / magassag,
                                    r.right / szelesseg,
                                    r.bottom / magassag
                                )
                            )
                        )
                    }
                }
                mainHandler.post { onResult(boxes) }
            }
            .addOnFailureListener {
                clearTimeout()
                busy.set(false)
                mainHandler.post { onError() }
            }
    }

    private fun clearTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    override fun close() {
        clearTimeout()
        busy.set(false)
        recognizer.close()
    }

    companion object {
        private const val RECOGNITION_TIMEOUT_MS = 8_000L
    }
}