package com.example.mca_project.camera

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** 정규화된 얼굴 박스 (0~1, 프리뷰 좌표계). 오버레이로 카메라 뷰 위에 그린다. */
data class FaceBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class InterviewCameraReading(
    val faceImage: FloatArray,
    val trackingConfidence: Float,
    val faceBox: FaceBox? = null,
)

data class FingerPpgReading(
    val locked: Boolean,
    val bpm: Float?,
    val ppgFeatures: FloatArray,
    val ppgSignal: FloatArray,
)

/**
 * ML Kit 얼굴 검출 기반 Interview 카메라 프로세서.
 * 검출된 얼굴 박스 영역만 96×96으로 크롭해 모델 입력으로 쓴다(중앙 무조건 크롭 대체).
 * ML Kit은 비동기라 결과를 콜백(onReading)으로 전달한다.
 */
class InterviewCameraProcessor @Inject constructor() {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
    )

    fun reset() = Unit

    /**
     * 프레임을 분석한다. 검출이 비동기로 끝나면 onReading 으로 결과를 넘긴다.
     * 호출 측은 image.close()를 하지 말고 이 함수에 위임한다(검출 완료 후 닫음).
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun analyze(image: ImageProxy, isFront: Boolean = false, onReading: (InterviewCameraReading) -> Unit) {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                val reading = if (face != null) {
                    buildReadingFromFace(image, face, rotation, isFront)
                } else {
                    // 얼굴 미검출: 직전 입력 유지를 위해 중앙 크롭 폴백 + confidence 0
                    InterviewCameraReading(sampleFaceImage(image), trackingConfidence = 0f, faceBox = null)
                }
                onReading(reading)
            }
            .addOnFailureListener {
                onReading(InterviewCameraReading(sampleFaceImage(image), trackingConfidence = 0f, faceBox = null))
            }
            .addOnCompleteListener { image.close() }
    }
}

class FingerPpgProcessor @Inject constructor() {
    private val redHistory = ArrayDeque<Float>()

    // BPM 계산용 상태 (PPGbetterWithVoice 방식 이식: 실제 timestamp 기반 peak 검출 + 10초 median)
    private var emaRed: Float? = null
    private val recentRed = ArrayDeque<Float>()       // 3-point local max 검출용
    private val peakTimestamps = ArrayList<Long>()    // peak가 잡힌 실제 시각(ms)

    fun reset() {
        redHistory.clear()
        emaRed = null
        recentRed.clear()
        peakTimestamps.clear()
    }

    fun analyze(image: ImageProxy): FingerPpgReading {
        return analyze(image, System.currentTimeMillis())
    }

    fun analyze(image: ImageProxy, timestampMs: Long): FingerPpgReading {
        val center = averageRgb(image, 0.22f, 0.78f, 0.22f, 0.78f)
        push(redHistory, center.red, HISTORY_LIMIT)
        val signal = buildSignal(redHistory)
        val bpm = updateBpm(center.red, timestampMs)
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

    /**
     * PPGbetterWithVoice 의 analyzeImage + computeHeartRate 로직 이식.
     * EMA 평활화 → 3-point local-max peak 검출(최소 600ms 간격) → 최근 10초 interval median → BPM.
     * 프레임레이트 가정 없이 실제 ms 간격을 쓰므로 기기와 무관하게 안정적.
     */
    private fun updateBpm(rawRed: Float, now: Long): Float? {
        val filtered = emaRed?.let { EMA_ALPHA * rawRed + (1f - EMA_ALPHA) * it } ?: rawRed
        emaRed = filtered

        if (recentRed.size == 3) recentRed.removeFirst()
        recentRed.addLast(filtered)
        if (recentRed.size == 3) {
            val prev = recentRed.elementAt(0)
            val curr = recentRed.elementAt(1)
            val next = recentRed.elementAt(2)
            val isPeak = curr > prev && curr > next
            val farEnough = peakTimestamps.isEmpty() ||
                now - peakTimestamps.last() > MIN_PEAK_INTERVAL_MS
            if (isPeak && farEnough) peakTimestamps.add(now)
        }

        // 10초보다 오래된 peak 제거
        while (peakTimestamps.size > 1 && now - peakTimestamps.first() > BPM_WINDOW_MS) {
            peakTimestamps.removeAt(0)
        }
        if (peakTimestamps.size < 2) return null

        val intervals = peakTimestamps.zipWithNext { a, b -> b - a }.sorted()
        val n = intervals.size
        val medianInterval = if (n % 2 == 0) {
            (intervals[n / 2 - 1] + intervals[n / 2]) / 2f
        } else {
            intervals[n / 2].toFloat()
        }
        if (medianInterval <= 0f) return null
        val bpm = 60_000f / medianInterval
        return if (bpm in 45f..180f) bpm else null
    }
}

private data class RgbStats(
    val red: Float,
    val green: Float,
    val blue: Float,
)

/**
 * 검출된 얼굴 박스 영역만 96×96으로 크롭한다.
 * ML Kit boundingBox 는 rotation 적용된 "정립 이미지" 좌표계 기준이므로,
 * 정립 크기(uprightW/H)로 정규화한 뒤 원본 ImageProxy 픽셀로 역회전 샘플링한다.
 */
private fun buildReadingFromFace(image: ImageProxy, face: Face, rotation: Int, isFront: Boolean): InterviewCameraReading {
    val uprightW: Int
    val uprightH: Int
    if (rotation == 90 || rotation == 270) {
        uprightW = image.height; uprightH = image.width
    } else {
        uprightW = image.width; uprightH = image.height
    }
    val box = face.boundingBox
    // 약간의 여백을 줘서 턱/이마가 잘리지 않게 (margin 18%)
    val margin = 0.18f
    val bw = box.width(); val bh = box.height()
    var l = (box.left - bw * margin)
    var t = (box.top - bh * margin)
    var r = (box.right + bw * margin)
    var b = (box.bottom + bh * margin)
    l = l.coerceIn(0f, uprightW.toFloat())
    t = t.coerceIn(0f, uprightH.toFloat())
    r = r.coerceIn(0f, uprightW.toFloat())
    b = b.coerceIn(0f, uprightH.toFloat())

    val faceImage = FloatArray(FACE_SIZE * FACE_SIZE * 3)
    for (y in 0 until FACE_SIZE) {
        val uy = t + (y / (FACE_SIZE - 1f)) * (b - t)
        for (x in 0 until FACE_SIZE) {
            val ux = l + (x / (FACE_SIZE - 1f)) * (r - l)
            // 정립 좌표(ux,uy) → 원본 ImageProxy 좌표 역변환
            val (ox, oy) = uprightToOriginal(ux.toInt(), uy.toInt(), rotation, image.width, image.height, isFront)
            val rgb = sampleRgb(image, ox, oy)
            val base = (y * FACE_SIZE + x) * 3
            // 모델이 내부 Rescaling(1/255)을 가지므로 0~255 float32로 입력해야 한다(sampleRgb는 0~1 반환).
            faceImage[base] = rgb[0] * 255f
            faceImage[base + 1] = rgb[1] * 255f
            faceImage[base + 2] = rgb[2] * 255f
        }
    }
    var nl = (l / uprightW).coerceIn(0f, 1f)
    var nr = (r / uprightW).coerceIn(0f, 1f)
    // 전면 프리뷰는 거울상 → 오버레이 박스 x좌표를 좌우 반전해 화면과 맞춤
    if (isFront) {
        val ml = 1f - nr
        val mr = 1f - nl
        nl = ml; nr = mr
    }
    val faceBox = FaceBox(
        left = nl,
        top = (t / uprightH).coerceIn(0f, 1f),
        right = nr,
        bottom = (b / uprightH).coerceIn(0f, 1f),
    )
    return InterviewCameraReading(faceImage, trackingConfidence = 1f, faceBox = faceBox)
}

/**
 * 정립 이미지 좌표 → 원본 센서 이미지 좌표 역변환 (rotation 만큼 역회전).
 * 전면 카메라는 센서가 좌우 반전 캡처되므로 정립 x축을 한 번 더 뒤집어 실제 얼굴 픽셀을 샘플링한다.
 */
private fun uprightToOriginal(ux: Int, uy0: Int, rotation: Int, ow: Int, oh: Int, isFront: Boolean): Pair<Int, Int> {
    // 정립 폭(rotation 90/270이면 ow/oh가 정립 기준으로 뒤바뀐 상태) 기준 x 미러
    val uprightW = if (rotation == 90 || rotation == 270) oh else ow
    val x = if (isFront) (uprightW - 1 - ux).coerceIn(0, uprightW - 1) else ux
    val uy = uy0
    return when (rotation) {
        90 -> Pair(uy, (ow - 1 - x).coerceIn(0, ow - 1))
        180 -> Pair((ow - 1 - x).coerceIn(0, ow - 1), (oh - 1 - uy).coerceIn(0, oh - 1))
        270 -> Pair((oh - 1 - uy).coerceIn(0, oh - 1), x)
        else -> Pair(x, uy)
    }
}

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
            // 0~255 float32 (모델 내부 Rescaling 1/255)
            result[base] = rgb[0] * 255f
            result[base + 1] = rgb[1] * 255f
            result[base + 2] = rgb[2] * 255f
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
private const val SAMPLE_STEP = 8

// BPM 계산 파라미터 (PPGbetterWithVoice 이식)
private const val EMA_ALPHA = 0.2f               // PPG raw 평활화 계수
private const val MIN_PEAK_INTERVAL_MS = 600L     // 최소 peak 간격 → 최대 ~100 BPM 제한
private const val BPM_WINDOW_MS = 10_000L         // interval median 산출 윈도우
