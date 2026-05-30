package com.example.mca_project.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

@Singleton
class AudioFeatureExtractor @Inject constructor() {

    fun buildModelMel(samples16k: FloatArray): FloatArray {
        val window = normalizeWindow(samples16k)
        val maxStart = (WINDOW_SAMPLES - FFT_SIZE).coerceAtLeast(0)
        val stftFrames = Array(MEL_FRAMES) { frameIndex ->
            val start = if (MEL_FRAMES == 1) {
                0
            } else {
                ((frameIndex.toFloat() / (MEL_FRAMES - 1)) * maxStart).toInt()
            }
            computePowerSpectrum(window, start)
        }

        val mel = FloatArray(MEL_BINS * MEL_FRAMES)
        for (frameIndex in 0 until MEL_FRAMES) {
            val spectrum = stftFrames[frameIndex]
            for (melIndex in 0 until MEL_BINS) {
                var energy = 0f
                val filter = melFilterBank[melIndex]
                for (bin in filter.indices) {
                    energy += spectrum[bin] * filter[bin]
                }
                mel[melIndex * MEL_FRAMES + frameIndex] = ln(max(energy, 1e-6f))
            }
        }
        return normalizeMel(mel)
    }

    private fun normalizeWindow(samples16k: FloatArray): FloatArray {
        val result = FloatArray(WINDOW_SAMPLES)
        if (samples16k.isEmpty()) return result

        val copyCount = minOf(samples16k.size, WINDOW_SAMPLES)
        val sourceOffset = max(samples16k.size - WINDOW_SAMPLES, 0)
        val destOffset = WINDOW_SAMPLES - copyCount
        for (index in 0 until copyCount) {
            result[destOffset + index] = samples16k[sourceOffset + index]
        }

        var peak = 1e-4f
        result.forEach { sample -> peak = max(peak, kotlin.math.abs(sample)) }
        for (index in result.indices) {
            result[index] /= peak
        }
        return result
    }

    private fun computePowerSpectrum(samples: FloatArray, start: Int): FloatArray {
        val power = FloatArray(FFT_BINS)
        for (bin in 0 until FFT_BINS) {
            var real = 0.0
            var imag = 0.0
            val omega = -2.0 * PI * bin / FFT_SIZE
            for (n in 0 until FFT_SIZE) {
                val sample = samples[start + n] * hammingWindow[n]
                val angle = omega * n
                real += sample * cos(angle)
                imag += sample * sin(angle)
            }
            power[bin] = ((real * real + imag * imag) / FFT_SIZE).toFloat()
        }
        return power
    }

    private fun normalizeMel(mel: FloatArray): FloatArray {
        val mean = mel.average().toFloat()
        var variance = 0f
        mel.forEach { value ->
            val centered = value - mean
            variance += centered * centered
        }
        val std = kotlin.math.sqrt(variance / mel.size.coerceAtLeast(1)).coerceAtLeast(1e-5f)
        return FloatArray(mel.size) { index -> ((mel[index] - mean) / std).coerceIn(-4f, 4f) }
    }

    companion object {
        const val MODEL_SAMPLE_RATE = 16_000
        private const val FFT_SIZE = 400
        private const val MEL_BINS = 128
        private const val MEL_FRAMES = 96
        private const val FFT_BINS = FFT_SIZE / 2 + 1
        const val WINDOW_SECONDS = 5
        const val WINDOW_SAMPLES = MODEL_SAMPLE_RATE * WINDOW_SECONDS

        private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)

        private fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

        private val hammingWindow = FloatArray(FFT_SIZE) { index ->
            (0.54 - 0.46 * cos(2.0 * PI * index / (FFT_SIZE - 1))).toFloat()
        }

        private val melFilterBank = run {
            val filters = Array(MEL_BINS) { FloatArray(FFT_BINS) }
            val minMel = hzToMel(0.0)
            val maxMel = hzToMel(MODEL_SAMPLE_RATE / 2.0)
            val melPoints = DoubleArray(MEL_BINS + 2) { index ->
                minMel + (maxMel - minMel) * index / (MEL_BINS + 1)
            }
            val hzPoints = melPoints.map { mel -> melToHz(mel) }
            val bins = hzPoints.map { hz ->
                ((FFT_SIZE + 1) * hz / MODEL_SAMPLE_RATE).toInt().coerceIn(0, FFT_BINS - 1)
            }

            for (melIndex in 0 until MEL_BINS) {
                val left = bins[melIndex]
                val center = bins[melIndex + 1]
                val right = bins[melIndex + 2]
                if (center <= left || right <= center) continue
                for (bin in left until center) {
                    filters[melIndex][bin] = (bin - left).toFloat() / (center - left).toFloat()
                }
                for (bin in center until right) {
                    filters[melIndex][bin] = (right - bin).toFloat() / (right - center).toFloat()
                }
            }
            filters
        }
    }
}
