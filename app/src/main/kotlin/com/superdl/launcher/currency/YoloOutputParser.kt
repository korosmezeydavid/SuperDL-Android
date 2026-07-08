package com.superdl.launcher.currency

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Parses Ultralytics YOLO TFLite exports (raw head and end-to-end NMS layouts).
 * Tensor shapes are discovered at runtime from the interpreter.
 */
internal object YoloOutputParser {

    fun parse(
        output: Array<FloatArray>,
        outputShape: IntArray,
        labels: List<String>,
        confThreshold: Float,
        iouThreshold: Float,
        maxDetections: Int
    ): List<BanknoteDetection> {
        if (output.isEmpty() || labels.isEmpty()) return emptyList()

        return when (outputShape.size) {
            3 -> parseThreeDimensional(output[0], outputShape, labels, confThreshold, iouThreshold, maxDetections)
            2 -> parseTwoDimensional(output, outputShape, labels, confThreshold, iouThreshold, maxDetections)
            else -> emptyList()
        }
    }

    private fun parseThreeDimensional(
        values: FloatArray,
        shape: IntArray,
        labels: List<String>,
        confThreshold: Float,
        iouThreshold: Float,
        maxDetections: Int
    ): List<BanknoteDetection> {
        val dimA = shape[1]
        val dimB = shape[2]

        // End-to-end: [1, max_det, 6] => xyxy + conf + class
        if (dimB == 6 || dimB == 7) {
            return parseEndToEnd(values, dimA, dimB, labels, confThreshold, maxDetections)
        }

        val numClasses = labels.size
        val channels = 4 + numClasses

        return when {
            dimA == channels -> parseRawChannelsFirst(values, dimA, dimB, labels, confThreshold, iouThreshold, maxDetections)
            dimB == channels -> parseRawAnchorsFirst(values, dimA, dimB, labels, confThreshold, iouThreshold, maxDetections)
            dimB == 6 || dimB == 7 -> parseEndToEnd(values, dimA, dimB, labels, confThreshold, maxDetections)
            else -> emptyList()
        }
    }

    private fun parseTwoDimensional(
        values: Array<FloatArray>,
        shape: IntArray,
        labels: List<String>,
        confThreshold: Float,
        iouThreshold: Float,
        maxDetections: Int
    ): List<BanknoteDetection> {
        val rows = shape[0]
        val cols = shape[1]
        if (cols == 6 || cols == 7) {
            val flat = FloatArray(rows * cols)
            var offset = 0
            for (row in values) {
                row.copyInto(flat, offset, 0, min(cols, row.size))
                offset += cols
            }
            return parseEndToEnd(flat, rows, cols, labels, confThreshold, maxDetections)
        }
        return emptyList()
    }

    private fun parseEndToEnd(
        values: FloatArray,
        numRows: Int,
        rowWidth: Int,
        labels: List<String>,
        confThreshold: Float,
        maxDetections: Int
    ): List<BanknoteDetection> {
        val detections = mutableListOf<BanknoteDetection>()
        for (row in 0 until numRows) {
            val base = row * rowWidth
            if (base + 5 >= values.size) break

            val x1 = values[base]
            val y1 = values[base + 1]
            val x2 = values[base + 2]
            val y2 = values[base + 3]
            val score = values[base + 4]
            val classIndex = values[base + 5].toInt().coerceIn(0, labels.lastIndex)

            if (score < confThreshold) continue
            if (x2 <= x1 || y2 <= y1) continue

            detections.add(
                BanknoteDetection(
                    label = labels[classIndex],
                    classIndex = classIndex,
                    confidence = score,
                    boundingBox = normalizeBox(x1, y1, x2, y2)
                )
            )
            if (detections.size >= maxDetections) break
        }
        return detections.sortedByDescending { it.confidence }
    }

    private fun parseRawChannelsFirst(
        values: FloatArray,
        channels: Int,
        anchors: Int,
        labels: List<String>,
        confThreshold: Float,
        iouThreshold: Float,
        maxDetections: Int
    ): List<BanknoteDetection> {
        val numClasses = labels.size
        val candidates = mutableListOf<BanknoteDetection>()

        for (anchor in 0 until anchors) {
            val cx = values[0 * anchors + anchor]
            val cy = values[1 * anchors + anchor]
            val w = values[2 * anchors + anchor]
            val h = values[3 * anchors + anchor]

            var bestClass = -1
            var bestScore = 0f
            for (classOffset in 0 until numClasses) {
                val score = values[(4 + classOffset) * anchors + anchor]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = classOffset
                }
            }
            if (bestClass < 0 || bestScore < confThreshold) continue

            val x1 = cx - w / 2f
            val y1 = cy - h / 2f
            val x2 = cx + w / 2f
            val y2 = cy + h / 2f
            if (x2 <= x1 || y2 <= y1) continue

            candidates.add(
                BanknoteDetection(
                    label = labels[bestClass],
                    classIndex = bestClass,
                    confidence = bestScore,
                    boundingBox = normalizeBox(x1, y1, x2, y2)
                )
            )
        }
        return nonMaxSuppression(candidates, iouThreshold, maxDetections)
    }

    private fun parseRawAnchorsFirst(
        values: FloatArray,
        anchors: Int,
        channels: Int,
        labels: List<String>,
        confThreshold: Float,
        iouThreshold: Float,
        maxDetections: Int
    ): List<BanknoteDetection> {
        val numClasses = labels.size
        val candidates = mutableListOf<BanknoteDetection>()

        for (anchor in 0 until anchors) {
            val base = anchor * channels
            if (base + 3 >= values.size) break

            val cx = values[base]
            val cy = values[base + 1]
            val w = values[base + 2]
            val h = values[base + 3]

            var bestClass = -1
            var bestScore = 0f
            for (classOffset in 0 until numClasses) {
                val score = values[base + 4 + classOffset]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = classOffset
                }
            }
            if (bestClass < 0 || bestScore < confThreshold) continue

            val x1 = cx - w / 2f
            val y1 = cy - h / 2f
            val x2 = cx + w / 2f
            val y2 = cy + h / 2f
            if (x2 <= x1 || y2 <= y1) continue

            candidates.add(
                BanknoteDetection(
                    label = labels[bestClass],
                    classIndex = bestClass,
                    confidence = bestScore,
                    boundingBox = normalizeBox(x1, y1, x2, y2)
                )
            )
        }
        return nonMaxSuppression(candidates, iouThreshold, maxDetections)
    }

    private fun normalizeBox(x1: Float, y1: Float, x2: Float, y2: Float): RectF {
        val left: Float
        val top: Float
        val right: Float
        val bottom: Float

        if (x2 <= 1.5f && y2 <= 1.5f && x1 >= 0f && y1 >= 0f) {
            left = x1.coerceIn(0f, 1f)
            top = y1.coerceIn(0f, 1f)
            right = x2.coerceIn(0f, 1f)
            bottom = y2.coerceIn(0f, 1f)
        } else {
            // xywh pixel space – caller should pass model input size; treat as normalized fallback
            left = x1.coerceAtLeast(0f)
            top = y1.coerceAtLeast(0f)
            right = x2.coerceAtLeast(left)
            bottom = y2.coerceAtLeast(top)
        }
        return RectF(left, top, right, bottom)
    }

    private fun nonMaxSuppression(
        detections: List<BanknoteDetection>,
        iouThreshold: Float,
        maxDetections: Int
    ): List<BanknoteDetection> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept = mutableListOf<BanknoteDetection>()

        while (sorted.isNotEmpty() && kept.size < maxDetections) {
            val best = sorted.removeAt(0)
            kept.add(best)
            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (iou(best.boundingBox, candidate.boundingBox) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val union = a.width() * a.height() + b.width() * b.height() - interArea
        if (union <= 0f) return 0f
        return interArea / union
    }
}