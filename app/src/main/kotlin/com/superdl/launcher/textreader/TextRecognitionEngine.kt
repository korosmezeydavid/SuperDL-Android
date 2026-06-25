package com.superdl.launcher.textreader

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class TextRecognitionEngine : Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val busy = AtomicBoolean(false)

    fun recognize(
        bitmap: Bitmap,
        onResult: (String) -> Unit,
        onError: () -> Unit
    ) {
        if (!busy.compareAndSet(false, true)) return
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                busy.set(false)
                onResult(visionText.text.orEmpty())
            }
            .addOnFailureListener {
                busy.set(false)
                onError()
            }
    }

    override fun close() {
        recognizer.close()
    }
}