package com.example.mca_project.camera

import androidx.camera.core.ImageProxy
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class InterviewCameraReading(
    val faceImage: FloatArray,
    val ppgFeatures: FloatArray,
    val ppgSignal: FloatArray,
    val bpm: Float?,
    val trackingConfidence: Float,
)

data class FingerPpgReading(
    val locked: Boolean,
    val bpm: Float?,
    val ppgFeatures: FloatArray,
    val ppgSignal: FloatArray,
)

class InterviewCameraProcessor @Inject constructor() {
    private val redHistory = ArrayDeque<Float>()

    fun reset() {
        redHistory.clear()
    }

    fun analyze(image: ImageProxy): InterviewCameraReading {
        val faceImage = sampleFaceImage(image)
        val cheek = averageRgb(image, 0.34f, 0.66f, 0.40f, 0.70f)
        push(redHistory, cheek.red, HISTORY_LIMIT)
        val signal = buildSignal(redHistory)
        val bpm = estimateBpm(signal)
        return InterviewCameraReading(
            faceImage = faceImage,
            ppgFeatures = buildPpgFeatures(signal, bpm),
            ppgSignal = signal,
            bpm = bpm,
            trackingConfidence = estimateTrackingConfidence(faceImage),
        )
    }
}

class FingerPpgProcessor @Inject constructor() {
    private val redHistory = ArrayDeque<Float>()

    fun reset() {
        redHistory.clear()
    }

    fun analyze(image: ImageProxy): FingerPpgReading {
        val center = averageRgb(image, 0.22f, 0.78f, 0.22f, 0.78f)
        push(redHistory, center.red, HISTORY_LIMIT)
        val signal = buildSignal(redHistory)
        val bpm = estimateBpm(signal)
        val locked = center.red > center.green * 1.18f &&
            center.red > center.blue * 1.24f &&
            center.red > 0.30f &&
            redHistory.size >= 48
        return FingerPpgReading(
            locked = locked,
            bpm = if (locked) bpm else null,
            ppgFeatures = buildPpgFeatures(signal, bpm),
            ppgSignal = signal,
        )
    }
}

private data class RgbStats(
    val red: Float,
    val green: Float,
    val blue: Float,
)

private fun sampleFaceImage(image: ImageProxy): FloatArray {
    val result = FloatArray(FACE_SIZE * FACE_SIZE * 3)
    val cropWidth = image.width * 0.72f
    val cropHeight = image.height * 0.72f
    val left = ((image.width - cropWidth) / 2f)
    val top = ((image.height - cropHeight) / 2f)
    for (y in 0 until FACE_SIZE) {
        val sy = top + (y / (FACE_SIZE - 1f)) * cropHeight
        for (x in 0 until FACE_SIZE) {
            val sx = left + (x / (FACE_SIZE - 1f)) * cropWidth
            val rgb = sampleRgb(image, sx.toInt(), sy.toInt())
            val base = (y * FACE_SIZE + x) * 3
            result[base] = rgb[0]
            result[base + 1] = rgb[1]
            result[base + 2] = rgb[2]
        }
    }
    return result
}

private fun averageRgb(
    image: ImageProxy,
    xStartFraction: Float,
    xEndFraction: Float,
    yStartFraction: Float,
    yEndFraction: Float,
): RgbStats {
    val startX = (image.width * xStartFraction).toInt()
    val endX = (image.width * xEndFraction).toInt()
    val startY = (image.height * yStartFraction).toInt()
    val endY = (image.height * yEndFraction).toInt()

    var red = 0f
    var green = 0f
    var blue = 0f
    var count = 0
    for (y in startY until endY step SAMPLE_STEP) {
        for (x in startX until endX step SAMPLE_STEP) {
            val rgb = sampleRgb(image, x, y)
            red += rgb[0]
            green += rgb[1]
            blue += rgb[2]
            count++
        }
    }
    if (count == 0) return RgbStats(0f, 0f, 0f)
    return RgbStats(red / count, green / count, blue / count)
}

private fun sampleRgb(image: ImageProxy, x: Int, y: Int): FloatArray {
    val safeX = x.coerceIn(0, image.width - 1)
    val safeY = y.coerceIn(0, image.height - 1)
    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yIndex = safeY * yPlane.rowStride + safeX * yPlane.pixelStride
    val uvX = safeX / 2
    val uvY = safeY / 2
    val uIndex = uvY * uPlane.rowStride + uvX * uPlane.pixelStride
    val vIndex = uvY * vPlane.rowStride + uvX * vPlane.pixelStride

    val yValue = yPlane.buffer.get(yIndex).toInt() and 0xFF
    val uValue = (uPlane.buffer.get(uIndex).toInt() and 0xFF) - 128
    val vValue = (vPlane.buffer.get(vIndex).toInt() and 0xFF) - 128

    val r = (yValue + 1.370705f * vValue) / 255f
    val g = (yValue - 0.337633f * uValue - 0.698001f * vValue) / 255f
    val b = (yValue + 1.732446f * uValue) / 255f
    return floatArrayOf(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
}

private fun buildSignal(history: ArrayDeque<Float>): FloatArray {
    if (history.isEmpty()) return FloatArray(SIGNAL_SIZE)
    val values = history.toFloatArray()
    val mean = values.average().toFloat()
    val centered = FloatArray(values.size) { index -> values[index] - mean }
    var maxAbs = 1e-4f
    centered.forEach { sample -> maxAbs = max(maxAbs, abs(sample)) }
    val normalized = FloatArray(centered.size) { index -> centered[index] / maxAbs }
    return resample(normalized, SIGNAL_SIZE)
}

private fun buildPpgFeatures(signal: FloatArray, bpm: Float?): FloatArray {
    val minValue = signal.minOrNull() ?: 0f
    val maxValue = signal.maxOrNull() ?: 0f
    val meanValue = signal.average().toFloat()
    val variance = signal.fold(0f) { acc, value -> acc + (value - meanValue).pow(2) } / signal.size.coerceAtLeast(1)
    val diff = if (signal.size > 1) {
        FloatArray(signal.size - 1) { index -> signal[index + 1] - signal[index] }
    } else {
        floatArrayOf(0f)
    }
    val diffMean = diff.average().toFloat()
    val diffStd = sqrt(diff.fold(0f) { acc, value -> acc + (value - diffMean).pow(2) } / diff.size.coerceAtLeast(1))
    val energy = signal.fold(0f) { acc, value -> acc + value * value } / signal.size.coerceAtLeast(1)
    var zeroCrossings = 0f
    var peaks = 0f
    for (index in 0 until signal.lastIndex) {
        if ((signal[index] >= 0f) != (signal[index + 1] >= 0f)) {
            zeroCrossings += 1f
        }
        if (signal[index + 1] > signal[index]) {
            peaks += 1f
        }
    }
    val absMean = signal.fold(0f) { acc, value -> acc + abs(value) } / signal.size.coerceAtLeast(1)
    val rms = sqrt(energy)
    val percentile90 = signal.sorted()[((signal.lastIndex) * 0.9f).toInt().coerceIn(0, signal.lastIndex)]
    val percentile10 = signal.sorted()[((signal.lastIndex) * 0.1f).toInt().coerceIn(0, signal.lastIndex)]

    return floatArrayOf(
        bpm ?: 72f,
        sqrt(variance),
        minValue,
        maxValue,
        maxValue - minValue,
        diffMean,
        diffStd,
        energy,
        zeroCrossings,
        absMean,
        rms,
        percentile90,
        percentile10,
        percentile90 - percentile10,
        peaks,
        if (signal.isNotEmpty()) signal[signal.lastIndex] else 0f,
    )
}

private fun estimateBpm(signal: FloatArray): Float? {
    if (signal.size < 32) return null
    val peaks = mutableListOf<Int>()
    for (index in 1 until signal.lastIndex) {
        if (signal[index] > signal[index - 1] &&
            signal[index] > signal[index + 1] &&
            signal[index] > 0.12f
        ) {
            peaks += index
        }
    }
    if (peaks.size < 2) return null
    val intervals = peaks.zipWithNext { a, b -> b - a }
        .filter { it in 6..80 }
    if (intervals.isEmpty()) return null
    val averageInterval = intervals.average().toFloat()
    val bpm = 60f * SAMPLE_RATE / averageInterval
    return bpm.coerceIn(45f, 180f)
}

private fun estimateTrackingConfidence(faceImage: FloatArray): Float {
    val luminance = FloatArray(faceImage.size / 3) { index ->
        val base = index * 3
        (faceImage[base] + faceImage[base + 1] + faceImage[base + 2]) / 3f
    }
    val mean = luminance.average().toFloat()
    val variance = luminance.fold(0f) { acc, value -> acc + (value - mean).pow(2) } / luminance.size.coerceAtLeast(1)
    return (sqrt(variance) * 3.2f).coerceIn(0.1f, 1f)
}

private fun push(history: ArrayDeque<Float>, value: Float, maxSize: Int) {
    if (history.size >= maxSize) history.removeFirst()
    history.addLast(value)
}

private fun ArrayDeque<Float>.toFloatArray(): FloatArray {
    val result = FloatArray(size)
    forEachIndexed { index, value -> result[index] = value }
    return result
}

private fun resample(source: FloatArray, targetSize: Int): FloatArray {
    if (source.isEmpty()) return FloatArray(targetSize)
    if (source.size == targetSize) return source.copyOf()
    val result = FloatArray(targetSize)
    for (index in 0 until targetSize) {
        val position = index * (source.lastIndex.toFloat() / max(targetSize - 1, 1))
        val left = position.toInt()
        val right = min(left + 1, source.lastIndex)
        val fraction = position - left
        result[index] = source[left] * (1f - fraction) + source[right] * fraction
    }
    return result
}

private fun <T> Iterable<T>.forEachIndexed(action: (Int, T) -> Unit) {
    var index = 0
    for (item in this) {
        action(index++, item)
    }
}

private const val FACE_SIZE = 96
private const val SIGNAL_SIZE = 256
private const val HISTORY_LIMIT = 320
private const val SAMPLE_RATE = 32f
private const val SAMPLE_STEP = 8
