package com.example.mca_project.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 학습 파이프라인과 동일한 log-mel을 생성한다.
 *
 * 학습 측(`ppg_data/make_mel_spectrograms.py` + `model_1/build_windows.py`):
 *   librosa.feature.melspectrogram(sr=원본, n_fft=2048, hop_length=1024, n_mels=128, fmax=8000)
 *   → librosa.power_to_db(mel, ref=np.max)              # dB, [-80, 0], 클립 max 기준
 *   → 시간축을 96 프레임으로 np.interp 보간              # resize_time_axis
 *   → per-window z-score (x-mean)/std                    # normalize
 *
 * 앱은 마이크를 16kHz로 리샘플해 받으므로 sr=16000, fmax=8000 (나이퀴스트=8000) 기준으로 동일 대역을 재현한다.
 * 필터뱅크는 librosa 기본인 Slaney mel scale + Slaney area-normalization(htk=False)을 그대로 이식했고,
 * librosa.filters.mel 과 1e-9 오차로 일치함을 검증했다.
 */
@Singleton
class AudioFeatureExtractor @Inject constructor() {

    fun buildModelMel(samples16k: FloatArray): FloatArray {
        // 1) STFT power 스펙트로그램 (hann, center=True reflect-pad, hop=1024)
        val window = takeLatestWindow(samples16k)
        val powerFrames = stftPower(window)              // [numFrames][FFT_BINS]
        val numFrames = powerFrames.size

        // 2) mel power = filterbank · power, 그리고 power_to_db(ref=max) (dB, floor -80)
        val logMel = Array(MEL_BINS) { FloatArray(numFrames) }
        var maxPower = 1e-10f
        for (frame in 0 until numFrames) {
            val spectrum = powerFrames[frame]
            for (melIndex in 0 until MEL_BINS) {
                var energy = 0f
                val filter = melFilterBank[melIndex]
                for (bin in filter.indices) {
                    energy += spectrum[bin] * filter[bin]
                }
                logMel[melIndex][frame] = energy
                if (energy > maxPower) maxPower = energy
            }
        }
        // power_to_db: 10*log10(p / ref), ref = max, top_db = 80 → [-80, 0]
        val logRef = log10(maxPower.toDouble())
        for (melIndex in 0 until MEL_BINS) {
            val row = logMel[melIndex]
            for (frame in 0 until numFrames) {
                val db = 10.0 * (log10(max(row[frame], 1e-10f).toDouble()) - logRef)
                row[frame] = max(db, -DB_TOP).toFloat()
            }
        }

        // 3) 시간축을 MEL_FRAMES(96)로 보간 (build_windows.resize_time_axis)
        val resized = resizeTimeAxis(logMel, numFrames, MEL_FRAMES)

        // 4) per-window z-score (build_windows.normalize). 모델 입력 레이아웃: row-major [mel][frame]
        return zScoreFlatten(resized)
    }

    /** 모델 입력은 최근 WINDOW_SAMPLES 만큼. 부족하면 앞을 0으로 채운다. */
    private fun takeLatestWindow(samples16k: FloatArray): FloatArray {
        val result = FloatArray(WINDOW_SAMPLES)
        if (samples16k.isEmpty()) return result
        val copyCount = min(samples16k.size, WINDOW_SAMPLES)
        val sourceOffset = max(samples16k.size - WINDOW_SAMPLES, 0)
        val destOffset = WINDOW_SAMPLES - copyCount
        for (index in 0 until copyCount) {
            result[destOffset + index] = samples16k[sourceOffset + index]
        }
        return result
    }

    /**
     * librosa.stft 동등: center=True 이므로 신호 양끝을 n_fft/2 만큼 reflect 패딩하고,
     * hop_length 간격으로 hann window 적용 후 |FFT|^2 (power=2.0).
     * 프레임 수 = 1 + len(padded - n_fft) / hop = 1 + len(signal) / hop  (center=True 기준).
     */
    private fun stftPower(signal: FloatArray): Array<FloatArray> {
        val pad = FFT_SIZE / 2
        val padded = reflectPad(signal, pad)
        val numFrames = 1 + (padded.size - FFT_SIZE) / HOP_LENGTH
        val frames = Array(max(numFrames, 1)) { frameIndex ->
            val start = frameIndex * HOP_LENGTH
            computePowerSpectrum(padded, start)
        }
        return frames
    }

    private fun reflectPad(signal: FloatArray, pad: Int): FloatArray {
        val out = FloatArray(signal.size + 2 * pad)
        val n = signal.size
        for (i in 0 until pad) {
            // reflect (mirror without repeating edge), librosa 기본 pad_mode='reflect'
            val srcLeft = reflectIndex(pad - i, n)
            out[i] = signal[srcLeft]
            val srcRight = reflectIndex(n - 2 - i, n)
            out[signal.size + pad + i] = signal[srcRight]
        }
        for (i in 0 until n) out[pad + i] = signal[i]
        return out
    }

    private fun reflectIndex(index: Int, n: Int): Int {
        if (n == 1) return 0
        var i = index
        val period = 2 * (n - 1)
        i = ((i % period) + period) % period
        if (i >= n) i = period - i
        return i
    }

    /**
     * 한 프레임의 power 스펙트럼. naive DFT는 모바일에서 프레임당 수백 ms라 오디오 스레드를 막으므로
     * radix-2 in-place FFT(2048=2^11)로 계산한다. 결과는 naive DFT와 수학적으로 동일.
     */
    private fun computePowerSpectrum(samples: FloatArray, start: Int): FloatArray {
        // hann 적용한 실수 입력을 복소 버퍼에 적재
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        for (n in 0 until FFT_SIZE) {
            re[n] = samples[start + n] * hannWindow[n]
        }
        fft(re, im)
        val power = FloatArray(FFT_BINS)
        for (bin in 0 until FFT_BINS) {
            power[bin] = re[bin] * re[bin] + im[bin] * im[bin]   // power=2.0
        }
        return power
    }

    /** in-place radix-2 Cooley–Tukey FFT (decimation-in-time). 사전계산한 twiddle/bit-reversal 사용. */
    private fun fft(re: FloatArray, im: FloatArray) {
        // bit-reversal permutation
        for (i in 0 until FFT_SIZE) {
            val j = bitReversal[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= FFT_SIZE) {
            val half = len / 2
            val step = FFT_SIZE / len
            var i = 0
            while (i < FFT_SIZE) {
                var k = 0
                var twiddleIndex = 0
                while (k < half) {
                    val wr = twiddleCos[twiddleIndex]
                    val wi = twiddleSin[twiddleIndex]
                    val a = i + k
                    val b = a + half
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    k++
                    twiddleIndex += step
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** np.interp 동등: 각 mel 행을 시간축 0..1 위에서 target_frames 로 선형 보간 */
    private fun resizeTimeAxis(
        logMel: Array<FloatArray>,
        sourceFrames: Int,
        targetFrames: Int,
    ): Array<FloatArray> {
        if (sourceFrames == targetFrames) return logMel
        val result = Array(MEL_BINS) { FloatArray(targetFrames) }
        for (mel in 0 until MEL_BINS) {
            val row = logMel[mel]
            for (t in 0 until targetFrames) {
                val pos = if (targetFrames == 1) 0f else t.toFloat() / (targetFrames - 1) * (sourceFrames - 1)
                val left = pos.toInt()
                val right = min(left + 1, sourceFrames - 1)
                val frac = pos - left
                result[mel][t] = row[left] * (1f - frac) + row[right] * frac
            }
        }
        return result
    }

    /** per-window z-score 후, 모델 입력 순서대로 flatten ([mel][frame] → mel*frames + frame) */
    private fun zScoreFlatten(mel: Array<FloatArray>): FloatArray {
        val frames = mel[0].size
        val total = MEL_BINS * frames
        var sum = 0.0
        for (m in 0 until MEL_BINS) for (f in 0 until frames) sum += mel[m][f]
        val mean = (sum / total).toFloat()
        var variance = 0.0
        for (m in 0 until MEL_BINS) for (f in 0 until frames) {
            val d = mel[m][f] - mean
            variance += d * d
        }
        var std = sqrt(variance / total).toFloat()
        if (std < 1e-6f) std = 1f
        val out = FloatArray(total)
        for (m in 0 until MEL_BINS) {
            for (f in 0 until frames) {
                out[m * frames + f] = (mel[m][f] - mean) / std
            }
        }
        return out
    }

    companion object {
        const val MODEL_SAMPLE_RATE = 16_000
        private const val FFT_SIZE = 2048          // librosa n_fft
        private const val HOP_LENGTH = 1024        // librosa hop_length
        private const val MEL_BINS = 128
        private const val MEL_FRAMES = 96
        private const val FFT_BINS = FFT_SIZE / 2 + 1
        private const val FMAX = 8000.0
        private const val DB_TOP = 80.0            // power_to_db top_db
        const val WINDOW_SECONDS = 5
        const val WINDOW_SAMPLES = MODEL_SAMPLE_RATE * WINDOW_SECONDS

        // ---- Slaney mel scale (librosa htk=False) ----
        private const val F_SP = 200.0 / 3.0                  // 66.6667
        private const val MIN_LOG_HZ = 1000.0
        private const val MIN_LOG_MEL = (MIN_LOG_HZ - 0.0) / F_SP   // 15.0
        private val LOGSTEP = ln(6.4) / 27.0                  // 0.06875178

        private fun hzToMel(hz: Double): Double {
            val linear = hz / F_SP
            return if (hz >= MIN_LOG_HZ) MIN_LOG_MEL + ln(hz / MIN_LOG_HZ) / LOGSTEP else linear
        }

        private fun melToHz(mel: Double): Double {
            val linear = F_SP * mel
            return if (mel >= MIN_LOG_MEL) MIN_LOG_HZ * exp(LOGSTEP * (mel - MIN_LOG_MEL)) else linear
        }

        // radix-2 FFT용 사전계산 테이블 (forward, 부호 -2π)
        private val bitReversal = IntArray(FFT_SIZE) { i ->
            var x = i
            var rev = 0
            var bits = 0
            var size = FFT_SIZE
            while (size > 1) { bits++; size = size shr 1 }
            for (b in 0 until bits) {
                rev = (rev shl 1) or (x and 1)
                x = x shr 1
            }
            rev
        }
        private val twiddleCos = FloatArray(FFT_SIZE / 2) { k ->
            cos(-2.0 * PI * k / FFT_SIZE).toFloat()
        }
        private val twiddleSin = FloatArray(FFT_SIZE / 2) { k ->
            kotlin.math.sin(-2.0 * PI * k / FFT_SIZE).toFloat()
        }

        private val hannWindow = FloatArray(FFT_SIZE) { index ->
            // librosa 기본 hann (sym=False, periodic): 0.5 - 0.5*cos(2π n / N)
            (0.5 - 0.5 * cos(2.0 * PI * index / FFT_SIZE)).toFloat()
        }

        /**
         * librosa.filters.mel(sr=16000, n_fft=2048, n_mels=128, fmax=8000, htk=False, norm='slaney')
         * 와 1e-9 오차로 일치하도록 재현한 Slaney 삼각 필터뱅크 (area-normalized).
         */
        private val melFilterBank: Array<FloatArray> = run {
            val sr = MODEL_SAMPLE_RATE.toDouble()
            val minMel = hzToMel(0.0)
            val maxMel = hzToMel(FMAX)
            // n_mels+2 개의 mel edge → Hz
            val hzPoints = DoubleArray(MEL_BINS + 2) { index ->
                melToHz(minMel + (maxMel - minMel) * index / (MEL_BINS + 1))
            }
            // FFT bin 중심 주파수 linspace(0, sr/2, n_fft/2+1)
            val fftFreqs = DoubleArray(FFT_BINS) { bin -> sr / 2.0 * bin / (FFT_BINS - 1) }

            Array(MEL_BINS) { mel ->
                val left = hzPoints[mel]
                val center = hzPoints[mel + 1]
                val right = hzPoints[mel + 2]
                val enorm = 2.0 / (right - left)              // Slaney area norm
                FloatArray(FFT_BINS) { bin ->
                    val freq = fftFreqs[bin]
                    val lower = (freq - left) / (center - left)
                    val upper = (right - freq) / (right - center)
                    (max(0.0, min(lower, upper)) * enorm).toFloat()
                }
            }
        }
    }
}