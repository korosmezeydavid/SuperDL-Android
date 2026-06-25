package com.superdl.launcher.currency

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BanknoteClassifierEngine(context: Context) : Closeable {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val imageProcessor: ImageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()
    private val outputBuffer: ByteBuffer

    init {
        val model = FileUtil.loadMappedFile(context, MODEL_FILE)
        interpreter = Interpreter(model, Interpreter.Options().apply { numThreads = 2 })
        labels = FileUtil.loadLabels(context, LABEL_FILE)
        if (labels.isEmpty()) {
            throw IllegalStateException("Bankjegy címke fájl üres: $LABEL_FILE")
        }
        outputBuffer = ByteBuffer.allocateDirect(NUM_CLASSES * FLOAT_BYTES).order(ByteOrder.nativeOrder())
    }

    fun classify(bitmap: Bitmap): BanknoteClassificationResult? =
        classifyInternal(bitmap, applyColorCheck = true)

    fun classifyForManualCheck(bitmap: Bitmap): BanknoteClassificationResult? =
        classifyInternal(bitmap, applyColorCheck = true)

    private fun classifyInternal(bitmap: Bitmap, applyColorCheck: Boolean): BanknoteClassificationResult? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null

        val scores = runInference(bitmap) ?: return null
        val noneIndex = labels.indexOf(LABEL_NONE)
        val noneConfidence = if (noneIndex >= 0) scores[noneIndex] else 0f

        var bestBanknoteIndex = -1
        var bestBanknoteScore = 0f
        var secondBanknoteScore = 0f

        for (index in labels.indices) {
            val label = labels[index]
            if (label == LABEL_NONE) continue
            val score = scores[index]
            if (score > bestBanknoteScore) {
                secondBanknoteScore = bestBanknoteScore
                bestBanknoteScore = score
                bestBanknoteIndex = index
            } else if (score > secondBanknoteScore) {
                secondBanknoteScore = score
            }
        }

        if (noneConfidence > bestBanknoteScore + 0.08f) return null
        if (bestBanknoteIndex < 0) return null

        val label = labels[bestBanknoteIndex]
        val denomination = BanknoteDenomination.fromLabel(label) ?: return null
        val colorVerdict = if (applyColorCheck) {
            BanknoteColorVerifier.verify(bitmap, denomination)
        } else {
            BanknoteColorVerifier.Verdict.NEUTRAL
        }

        return BanknoteClassificationResult(
            denomination = denomination,
            confidence = bestBanknoteScore,
            secondBestConfidence = secondBanknoteScore,
            noneConfidence = noneConfidence,
            colorVerdict = colorVerdict
        )
    }

    private fun runInference(bitmap: Bitmap): FloatArray? {
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        val processed = imageProcessor.process(tensorImage)
        outputBuffer.rewind()
        interpreter.run(processed.buffer, outputBuffer)
        outputBuffer.rewind()

        val classCount = NUM_CLASSES.coerceAtMost(labels.size)
        return FloatArray(classCount) { outputBuffer.float }
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val MODEL_FILE = "huf_banknote_classifier.tflite"
        private const val LABEL_FILE = "huf_banknote_labels.txt"
        private const val LABEL_NONE = "none"
        private const val INPUT_SIZE = 224
        private const val NUM_CLASSES = 7
        private const val FLOAT_BYTES = 4
    }
}