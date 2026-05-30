package com.example.mca_project.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.mca_project.di.AudioExecutor
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

data class AudioSnapshot(
    val mel: FloatArray,
    val rms: Float,
)

@Singleton
class RealtimeAudioEngine @Inject constructor(
    private val featureExtractor: AudioFeatureExtractor,
    @AudioExecutor private val audioExecutor: ExecutorService,
) {

    private val isRunning = AtomicBoolean(false)
    private val ringBuffer = FloatArray(RING_BUFFER_SAMPLES)
    private var ringWriteIndex = 0
    private var ringSize = 0
    private val bufferLock = Any()

    @Volatile
    private var latestSnapshot: AudioSnapshot? = null
    @Volatile
    private var latestUtterance: AudioSnapshot? = null
    @Volatile
    private var lastError: String? = null

    private var audioRecord: AudioRecord? = null
    private var captureRate = AudioFeatureExtractor.MODEL_SAMPLE_RATE

    private var speechActive = false
    private var silenceMillis = 0
    private var utteranceSamples = ArrayList<Float>(AudioFeatureExtractor.WINDOW_SAMPLES)

    fun start() {
        if (isRunning.getAndSet(true)) return
        resetState()
        audioExecutor.execute {
            runCatching { captureLoop() }
                .onFailure { throwable ->
                    lastError = throwable.message ?: "Microphone capture failed"
                }
                .also {
                    stopInternal()
                }
        }
    }

    fun stop() {
        isRunning.set(false)
        stopInternal()
    }

    fun latestSnapshot(): AudioSnapshot? = latestSnapshot

    fun pollCompletedUtterance(): AudioSnapshot? {
        val utterance = latestUtterance
        latestUtterance = null
        return utterance
    }

    fun errorMessage(): String? = lastError

    private fun captureLoop() {
        val (record, sampleRate) = createAudioRecord()
        audioRecord = record
        captureRate = sampleRate
        val chunk = ShortArray(CHUNK_SIZE)

        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            throw IllegalStateException("AudioRecord failed to start")
        }

        while (isRunning.get()) {
            val read = record.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
            if (read <= 0) continue
            val normalized = FloatArray(read) { index -> chunk[index] / 32768f }
            val modelRateChunk = if (sampleRate == AudioFeatureExtractor.MODEL_SAMPLE_RATE) {
                normalized
            } else {
                linearResample(normalized, sampleRate, AudioFeatureExtractor.MODEL_SAMPLE_RATE)
            }
            pushSamples(modelRateChunk)
            updateLatestSnapshot(modelRateChunk)
            updateVad(modelRateChunk)
        }
    }

    private fun updateLatestSnapshot(chunk: FloatArray) {
        val window = synchronized(bufferLock) { latestWindowLocked() } ?: return
        if (window.size < AudioFeatureExtractor.WINDOW_SAMPLES) return
        latestSnapshot = AudioSnapshot(
            mel = featureExtractor.buildModelMel(window),
            rms = rms(chunk),
        )
    }

    private fun updateVad(chunk: FloatArray) {
        val energy = rms(chunk)
        val chunkMillis = (chunk.size * 1000f / AudioFeatureExtractor.MODEL_SAMPLE_RATE).toInt()
        val voiced = energy > VAD_THRESHOLD

        if (voiced) {
            speechActive = true
            silenceMillis = 0
            appendUtterance(chunk)
            return
        }

        if (speechActive) {
            appendUtterance(chunk)
            silenceMillis += chunkMillis
            if (silenceMillis >= SILENCE_FINISH_MS) {
                publishUtterance()
            }
        }
    }

    private fun appendUtterance(chunk: FloatArray) {
        chunk.forEach { sample ->
            utteranceSamples.add(sample)
        }
        val maxUtterance = AudioFeatureExtractor.WINDOW_SAMPLES * 3
        if (utteranceSamples.size > maxUtterance) {
            val drop = utteranceSamples.size - maxUtterance
            utteranceSamples = ArrayList(utteranceSamples.drop(drop))
        }
    }

    private fun publishUtterance() {
        speechActive = false
        silenceMillis = 0
        if (utteranceSamples.size < MIN_UTTERANCE_SAMPLES) {
            utteranceSamples.clear()
            return
        }
        val samples = utteranceSamples.toFloatArray()
        latestUtterance = AudioSnapshot(
            mel = featureExtractor.buildModelMel(samples),
            rms = rms(samples),
        )
        utteranceSamples.clear()
    }

    private fun pushSamples(chunk: FloatArray) {
        synchronized(bufferLock) {
            chunk.forEach { sample ->
                ringBuffer[ringWriteIndex] = sample
                ringWriteIndex = (ringWriteIndex + 1) % ringBuffer.size
                ringSize = minOf(ringSize + 1, ringBuffer.size)
            }
        }
    }

    private fun latestWindowLocked(): FloatArray? {
        if (ringSize == 0) return null
        val result = FloatArray(minOf(ringSize, AudioFeatureExtractor.WINDOW_SAMPLES))
        val available = result.size
        val start = (ringWriteIndex - available + ringBuffer.size) % ringBuffer.size
        for (index in 0 until available) {
            result[index] = ringBuffer[(start + index) % ringBuffer.size]
        }
        return result
    }

    private fun createAudioRecord(): Pair<AudioRecord, Int> {
        val candidates = intArrayOf(16_000, 44_100, 22_050)
        candidates.forEach { sampleRate ->
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) return@forEach
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuffer * 2, CHUNK_SIZE * 2),
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                return record to sampleRate
            }
            record.release()
        }
        throw IllegalStateException("Unable to initialize AudioRecord for the microphone")
    }

    private fun linearResample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (input.isEmpty() || fromRate == toRate) return input
        val targetSize = max((input.size.toLong() * toRate / fromRate).toInt(), 1)
        val result = FloatArray(targetSize)
        val scale = (input.size - 1).toFloat() / max(targetSize - 1, 1)
        for (index in 0 until targetSize) {
            val position = index * scale
            val left = position.toInt()
            val right = minOf(left + 1, input.lastIndex)
            val fraction = position - left
            result[index] = input[left] * (1f - fraction) + input[right] * fraction
        }
        return result
    }

    private fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var energy = 0f
        samples.forEach { sample -> energy += sample * sample }
        return kotlin.math.sqrt(energy / samples.size)
    }

    private fun resetState() {
        synchronized(bufferLock) {
            ringWriteIndex = 0
            ringSize = 0
            ringBuffer.fill(0f)
        }
        latestSnapshot = null
        latestUtterance = null
        lastError = null
        speechActive = false
        silenceMillis = 0
        utteranceSamples.clear()
    }

    private fun stopInternal() {
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    companion object {
        private const val CHUNK_SIZE = 2048
        private const val RING_BUFFER_SAMPLES = AudioFeatureExtractor.MODEL_SAMPLE_RATE * 10
        private const val VAD_THRESHOLD = 0.018f
        private const val SILENCE_FINISH_MS = 420
        private const val MIN_UTTERANCE_SAMPLES = AudioFeatureExtractor.MODEL_SAMPLE_RATE / 2
    }
}
