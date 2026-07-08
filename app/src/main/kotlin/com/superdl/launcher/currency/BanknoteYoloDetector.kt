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

/** Stage 1 – YOLO banknote localization (Ultralytics TFLite, typically 640×640). */
internal class BanknoteYoloDetector private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val inputWidth: Int,
    private val inputHeight: Int,
    private val outputShape: IntArray,
    private val outputBuffer: Array<FloatArray>
) : Closeable {

    private val imageProcessor: ImageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    fun detect(bitmap: Bitmap): List<BanknoteDetection> {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return emptyList()

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        val processed = imageProcessor.process(tensorImage)
        interpreter.run(processed.buffer, outputBuffer)

        return YoloOutputParser.parse(
            output = outputBuffer,
            outputShape = outputShape,
            labels = labels,
            confThreshold = CONFIDENCE_THRESHOLD,
            iouThreshold = IOU_THRESHOLD,
            maxDetections = MAX_DETECTIONS
        )
    }

    fun bestDetection(bitmap: Bitmap): BanknoteDetection? =
        detect(bitmap)
            .asSequence()
            .filter { it.areaFraction >= MIN_AREA_FRACTION }
            .maxByOrNull { it.confidence * (0.75f + it.areaFraction.coerceAtMost(0.5f)) }

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val MODEL_FILE = "huf_banknote_detector.tflite"
        private const val LABEL_FILE = "huf_banknote_detector_labels.txt"
        private const val CONFIDENCE_THRESHOLD = 0.55f
        private const val IOU_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 3
        private const val MIN_AREA_FRACTION = 0.06f
        private const val DEFAULT_INPUT = 640

        fun tryCreate(context: Context): BanknoteYoloDetector? {
            return try {
                val assetManager = context.assets
                assetManager.open(MODEL_FILE).close()
                val model = FileUtil.loadMappedFile(context, MODEL_FILE)
                val interpreter = Interpreter(
                    model,
                    Interpreter.Options().apply {
                        numThreads = 2
                        try {
                            setUseNNAPI(true)
                        } catch (_: Exception) {
                        }
                    }
                )
                val labels = FileUtil.loadLabels(context, LABEL_FILE)
                if (labels.isEmpty()) return null

                val inputTensor = interpreter.getInputTensor(0)
                val inputShape = inputTensor.shape()
                val inputHeight = if (inputShape.size >= 3) inputShape[1] else DEFAULT_INPUT
                val inputWidth = if (inputShape.size >= 3) inputShape[2] else DEFAULT_INPUT

                val outputTensor = interpreter.getOutputTensor(0)
                val outputShape = outputTensor.shape()
                val outputElementCount = outputShape.fold(1) { acc, dim -> acc * dim }
                val outputBuffer = arrayOf(FloatArray(outputElementCount))

                BanknoteYoloDetector(
                    interpreter = interpreter,
                    labels = labels,
                    inputWidth = inputWidth,
                    inputHeight = inputHeight,
                    outputShape = outputShape,
                    outputBuffer = outputBuffer
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}