package com.example.mca_project.ml

import android.content.Context
import com.example.mca_project.domain.model.EmotionCatalog
import com.example.mca_project.domain.model.EmotionScore
import com.example.mca_project.domain.model.InferenceOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.tensorflow.lite.Interpreter

class ModelNotReadyException(message: String = "Model load failed") : Exception(message)

data class VoicePpgInput(
    val mel: FloatArray,
    val ppgFeatures: FloatArray,
    val ppgSignal: FloatArray,
    val bpmHint: Float? = null,
)

data class FusionInput(
    val mel: FloatArray,
    val ppgFeatures: FloatArray,
    val ppgSignal: FloatArray,
    val faceImage: FloatArray,
    val bpmHint: Float? = null,
)

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var voiceEmotionInterpreter: Interpreter? = null
    private var voiceVeracityInterpreter: Interpreter? = null
    private var faceEmotionInterpreter: Interpreter? = null

    @Volatile
    var loadErrorMessage: String? = null
        private set

    val isReady: Boolean
        get() = voiceEmotionInterpreter != null &&
            voiceVeracityInterpreter != null &&
            faceEmotionInterpreter != null

    @Synchronized
    fun loadModels() {
        if (isReady) return
        runCatching {
            // Some exported models fail during XNNPack delegate preparation on Android.
            // Use the plain CPU interpreter first so we prioritize correctness over speed.
            loadModelsInternal(useXnnpack = false)
            loadErrorMessage = "Running on CPU interpreter (XNNPack disabled for compatibility)"
        }.getOrElse { throwable ->
            closeInterpreters()
            loadErrorMessage = throwable.message ?: throwable.javaClass.simpleName
            throw throwable
        }
    }

    fun inferVoicePpg(input: VoicePpgInput): InferenceOutput {
        ensureLoaded()
        validateVoicePpgInput(input)

        val emotionScores = runVoiceEmotion(input)
        val topEmotions = toEmotionScores(emotionScores, EmotionCatalog.voicePpg)
        val veracityScores = runVeracity(input)
        val fakeProbability = veracityScores.getOrElse(1) { 1f - veracityScores[0] }

        return InferenceOutput(
            emotion = topEmotions.first().label,
            emotionConfidence = topEmotions.first().probability,
            topEmotions = topEmotions.take(2),
            fakeProbability = fakeProbability.coerceIn(0f, 1f),
            bpm = input.bpmHint ?: estimateBpm(input.ppgSignal),
        )
    }

    fun inferFusion(input: FusionInput): InferenceOutput {
        ensureLoaded()
        validateVoicePpgInput(
            VoicePpgInput(
                mel = input.mel,
                ppgFeatures = input.ppgFeatures,
                ppgSignal = input.ppgSignal,
                bpmHint = input.bpmHint,
            )
        )
        require(input.faceImage.size == FACE_INPUT_SIZE) {
            "faceImage must have $FACE_INPUT_SIZE floats (96x96x3), got ${input.faceImage.size}"
        }

        val faceScores = runFaceEmotion(input)
        val faceTop = toEmotionScores(faceScores, EmotionCatalog.face)
        val voiceInput = VoicePpgInput(
            mel = input.mel,
            ppgFeatures = input.ppgFeatures,
            ppgSignal = input.ppgSignal,
            bpmHint = input.bpmHint,
        )
        val voiceScores = runVoiceEmotion(voiceInput)
        val veracityScores = runVeracity(voiceInput)
        val fakeProbability = veracityScores.getOrElse(1) { 1f - veracityScores[0] }

        return InferenceOutput(
            emotion = faceTop.first().label,
            emotionConfidence = faceTop.first().probability,
            topEmotions = faceTop.take(2),
            fakeProbability = fakeProbability.coerceIn(0f, 1f),
            bpm = input.bpmHint ?: estimateBpm(input.ppgSignal),
            faceVoiceDiscordance = cosineDistance(
                projectToHeatmap(faceScores, EmotionCatalog.face),
                projectToHeatmap(voiceScores, EmotionCatalog.voicePpg),
            ),
        )
    }

    fun inferDemoVoicePpg(seed: Int, baselineBpm: Float = 72f): InferenceOutput {
        val targetBpm = baselineBpm + ((seed % 5) - 2) * 2.4f
        val input = VoicePpgInput(
            mel = demoMel(seed),
            ppgFeatures = demoPpgFeatures(seed, targetBpm),
            ppgSignal = demoPpgSignal(seed, targetBpm),
            bpmHint = targetBpm,
        )
        return inferVoicePpg(input)
    }

    fun inferDemoVoicePpgFromPpg(
        seed: Int,
        ppgFeatures: FloatArray,
        ppgSignal: FloatArray,
        bpmHint: Float?,
    ): InferenceOutput {
        val input = VoicePpgInput(
            mel = demoMel(seed),
            ppgFeatures = ppgFeatures,
            ppgSignal = ppgSignal,
            bpmHint = bpmHint,
        )
        return inferVoicePpg(input)
    }

    fun inferDemoFusion(seed: Int, baselineBpm: Float = 74f): InferenceOutput {
        val targetBpm = baselineBpm + ((seed % 7) - 3) * 1.8f
        val input = FusionInput(
            mel = demoMel(seed + 37),
            ppgFeatures = demoPpgFeatures(seed + 19, targetBpm),
            ppgSignal = demoPpgSignal(seed + 11, targetBpm),
            faceImage = demoFaceImage(seed),
            bpmHint = targetBpm,
        )
        return inferFusion(input)
    }

    fun inferDemoFusionFromCamera(
        seed: Int,
        faceImage: FloatArray,
        ppgFeatures: FloatArray,
        ppgSignal: FloatArray,
        bpmHint: Float?,
    ): InferenceOutput {
        val input = FusionInput(
            mel = demoMel(seed + 37),
            ppgFeatures = ppgFeatures,
            ppgSignal = ppgSignal,
            faceImage = faceImage,
            bpmHint = bpmHint,
        )
        return inferFusion(input)
    }

    private fun runVoiceEmotion(input: VoicePpgInput): FloatArray {
        val outputs = HashMap<Int, Any>(1)
        val output = Array(1) { FloatArray(EmotionCatalog.voicePpg.size) }
        outputs[0] = output
        voiceEmotionInterpreter!!.runForMultipleInputsOutputs(
            buildVoiceModelInputs(voiceEmotionInterpreter!!, input),
            outputs,
        )
        return softmax(output[0])
    }

    private fun runVeracity(input: VoicePpgInput): FloatArray {
        val outputs = HashMap<Int, Any>(1)
        val output = Array(1) { FloatArray(2) }
        outputs[0] = output
        voiceVeracityInterpreter!!.runForMultipleInputsOutputs(
            buildVoiceModelInputs(voiceVeracityInterpreter!!, input),
            outputs,
        )
        return softmax(output[0])
    }

    private fun runFaceEmotion(input: FusionInput): FloatArray {
        val output = Array(1) { FloatArray(EmotionCatalog.face.size) }
        faceEmotionInterpreter!!.run(toFaceTensor(input.faceImage), output)
        return softmax(output[0])
    }

    private fun toMelTensor(mel: FloatArray): Array<Array<Array<FloatArray>>> {
        return Array(1) { batch ->
            Array(MEL_HEIGHT) { y ->
                Array(MEL_WIDTH) { x ->
                    FloatArray(1).apply {
                        this[0] = mel[(batch * MEL_SIZE) + y * MEL_WIDTH + x]
                    }
                }
            }
        }
    }

    private fun toPpgFeatureTensor(features: FloatArray): Array<FloatArray> {
        val normalized = FloatArray(PPG_FEATURE_SIZE) { index ->
            ((features[index] - PPG_MEAN[index]) / max(PPG_STD[index], 1e-6f)).coerceIn(-6f, 6f)
        }
        return arrayOf(normalized)
    }

    private fun toPpgSignalTensor(signal: FloatArray): Array<Array<FloatArray>> {
        return Array(1) {
            Array(PPG_SIGNAL_SIZE) { index ->
                FloatArray(1).apply { this[0] = signal[index] }
            }
        }
    }

    private fun toFaceTensor(faceImage: FloatArray): Array<Array<Array<FloatArray>>> {
        return Array(1) { batch ->
            Array(FACE_SIZE) { y ->
                Array(FACE_SIZE) { x ->
                    FloatArray(3).apply {
                        val base = (batch * FACE_INPUT_SIZE) + (y * FACE_SIZE + x) * 3
                        this[0] = faceImage[base]
                        this[1] = faceImage[base + 1]
                        this[2] = faceImage[base + 2]
                    }
                }
            }
        }
    }

    private fun buildVoiceModelInputs(
        interpreter: Interpreter,
        input: VoicePpgInput,
    ): Array<Any> {
        val inputs = Array<Any>(interpreter.inputTensorCount) { Unit }
        for (inputIndex in 0 until interpreter.inputTensorCount) {
            val tensorName = interpreter.getInputTensor(inputIndex).name()
            inputs[inputIndex] = when {
                tensorName.contains("mel", ignoreCase = true) -> toMelTensor(input.mel)
                tensorName.contains("ppg_features", ignoreCase = true) -> toPpgFeatureTensor(input.ppgFeatures)
                tensorName.contains("ppg_signal", ignoreCase = true) -> toPpgSignalTensor(input.ppgSignal)
                else -> error("Unknown voice model input tensor: $tensorName")
            }
        }
        return inputs
    }

    private fun toEmotionScores(probabilities: FloatArray, labels: List<String>): List<EmotionScore> {
        return probabilities
            .mapIndexed { index, probability -> EmotionScore(labels[index], probability) }
            .sortedByDescending { it.probability }
    }

    private fun projectToHeatmap(probabilities: FloatArray, labels: List<String>): FloatArray {
        val projected = FloatArray(EmotionCatalog.heatmap.size)
        labels.forEachIndexed { index, label ->
            val targetIndex = EmotionCatalog.heatmap.indexOf(label)
            if (targetIndex >= 0) projected[targetIndex] = probabilities[index]
        }
        return projected
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size)
        var sum = 0f
        logits.indices.forEach { index ->
            val value = exp((logits[index] - maxLogit).toDouble()).toFloat()
            exps[index] = value
            sum += value
        }
        if (sum <= 0f) return FloatArray(logits.size) { 1f / logits.size }
        return FloatArray(logits.size) { index -> exps[index] / sum }
    }

    private fun cosineDistance(left: FloatArray, right: FloatArray): Float {
        var dot = 0f
        var leftNorm = 0f
        var rightNorm = 0f
        left.indices.forEach { index ->
            dot += left[index] * right[index]
            leftNorm += left[index] * left[index]
            rightNorm += right[index] * right[index]
        }
        val denom = sqrt(leftNorm) * sqrt(rightNorm)
        if (denom <= 1e-6f) return 0f
        return (1f - (dot / denom)).coerceIn(0f, 1f)
    }

    private fun estimateBpm(signal: FloatArray): Float {
        if (signal.size < 3) return 72f
        val mean = signal.average().toFloat()
        var peakCount = 0
        for (index in 1 until signal.lastIndex) {
            if (signal[index] > signal[index - 1] &&
                signal[index] > signal[index + 1] &&
                signal[index] > mean
            ) {
                peakCount++
            }
        }
        val seconds = signal.size / DEMO_PPG_SAMPLE_RATE
        return if (seconds <= 0f) 72f else ((peakCount / seconds) * 60f).coerceIn(48f, 140f)
    }

    private fun demoMel(seed: Int): FloatArray {
        val random = Random(seed)
        return FloatArray(MEL_SIZE) { index ->
            val row = index / MEL_WIDTH
            val col = index % MEL_WIDTH
            val base = 0.32f + 0.18f * sin((col + seed) / 8f) + 0.12f * cos(row / 11f)
            val band = if (row in 20..54) 0.18f else if (row in 55..95) 0.09f else 0.03f
            val tremor = 0.05f * sin((row + col + seed * 0.7f) / 5.5f)
            (base + band + tremor + random.nextFloat() * 0.025f).coerceIn(0f, 1f)
        }
    }

    private fun demoPpgFeatures(seed: Int, bpm: Float): FloatArray {
        val random = Random(seed * 31 + 7)
        return FloatArray(PPG_FEATURE_SIZE) { index ->
            val swing = sin(seed * 0.23f + index * 0.61f) * 0.55f
            val jitter = (random.nextFloat() - 0.5f) * 0.18f
            val bpmShift = if (index == 0 || index == 10 || index == 14) (bpm - 72f) * 0.45f else 0f
            PPG_MEAN[index] + PPG_STD[index] * (swing + jitter) + bpmShift
        }
    }

    private fun demoPpgSignal(seed: Int, bpm: Float): FloatArray {
        val random = Random(seed * 17 + 3)
        val frequency = bpm / 60f
        return FloatArray(PPG_SIGNAL_SIZE) { index ->
            val t = index / DEMO_PPG_SAMPLE_RATE
            val carrier = sin((2f * Math.PI.toFloat()) * frequency * t)
            val harmonic = 0.35f * sin((2f * Math.PI.toFloat()) * frequency * 2f * t + 0.5f)
            val drift = 0.12f * sin((2f * Math.PI.toFloat()) * 0.08f * t + seed * 0.2f)
            val noise = (random.nextFloat() - 0.5f) * 0.05f
            (0.5f + carrier * 0.28f + harmonic * 0.12f + drift + noise).coerceIn(0f, 1f)
        }
    }

    private fun demoFaceImage(seed: Int): FloatArray {
        val smile = sin(seed * 0.45f).toFloat()
        val browTilt = cos(seed * 0.31f).toFloat()
        val pixels = FloatArray(FACE_INPUT_SIZE)
        for (y in 0 until FACE_SIZE) {
            for (x in 0 until FACE_SIZE) {
                val nx = (x / (FACE_SIZE - 1f)) * 2f - 1f
                val ny = (y / (FACE_SIZE - 1f)) * 2f - 1f
                val radius = nx * nx * 0.82f + ny * ny
                val insideFace = radius <= 0.94f
                var r = 0.06f
                var g = 0.08f
                var b = 0.1f
                if (insideFace) {
                    r = 0.72f - abs(nx) * 0.08f + (0.04f * browTilt)
                    g = 0.58f - abs(ny) * 0.06f
                    b = 0.46f - abs(nx * ny) * 0.05f
                }

                val leftEye = distanceSquared(nx + 0.33f, ny + 0.18f + browTilt * 0.04f)
                val rightEye = distanceSquared(nx - 0.33f, ny + 0.18f - browTilt * 0.04f)
                if (leftEye < 0.018f || rightEye < 0.018f) {
                    r = 0.12f
                    g = 0.1f
                    b = 0.1f
                }

                val mouthCurve = 0.28f + smile * 0.12f
                val mouthY = ny - 0.38f - (nx * nx) * mouthCurve
                if (abs(mouthY) < 0.035f && abs(nx) < 0.38f) {
                    r = 0.35f + max(smile, 0f) * 0.22f
                    g = 0.1f
                    b = 0.14f
                }

                val base = (y * FACE_SIZE + x) * 3
                pixels[base] = r.coerceIn(0f, 1f)
                pixels[base + 1] = g.coerceIn(0f, 1f)
                pixels[base + 2] = b.coerceIn(0f, 1f)
            }
        }
        return pixels
    }

    private fun distanceSquared(x: Float, y: Float): Float = x * x + y * y

    private fun validateVoicePpgInput(input: VoicePpgInput) {
        require(input.mel.size == MEL_SIZE) {
            "mel must have $MEL_SIZE floats (128x96), got ${input.mel.size}"
        }
        require(input.ppgFeatures.size == PPG_FEATURE_SIZE) {
            "ppgFeatures must have $PPG_FEATURE_SIZE floats, got ${input.ppgFeatures.size}"
        }
        require(input.ppgSignal.size == PPG_SIGNAL_SIZE) {
            "ppgSignal must have $PPG_SIGNAL_SIZE floats, got ${input.ppgSignal.size}"
        }
    }

    private fun ensureLoaded() {
        if (!isReady) {
            loadModels()
        }
        if (!isReady) throw ModelNotReadyException(loadErrorMessage ?: "Unable to load TFLite models")
    }

    private fun loadModelsInternal(useXnnpack: Boolean) {
        voiceEmotionInterpreter = Interpreter(loadModelFile(VOICE_EMOTION_MODEL), interpreterOptions(useXnnpack))
        prepareVoicePpgInterpreter(voiceEmotionInterpreter!!)

        voiceVeracityInterpreter = Interpreter(loadModelFile(VOICE_VERACITY_MODEL), interpreterOptions(useXnnpack))
        prepareVoicePpgInterpreter(voiceVeracityInterpreter!!)

        faceEmotionInterpreter = Interpreter(loadModelFile(FACE_EMOTION_MODEL), interpreterOptions(useXnnpack))
        faceEmotionInterpreter!!.resizeInput(0, intArrayOf(1, FACE_SIZE, FACE_SIZE, 3))
        faceEmotionInterpreter!!.allocateTensors()
    }

    private fun prepareVoicePpgInterpreter(interpreter: Interpreter) {
        for (inputIndex in 0 until interpreter.inputTensorCount) {
            val tensorName = interpreter.getInputTensor(inputIndex).name()
            when {
                tensorName.contains("mel", ignoreCase = true) -> {
                    interpreter.resizeInput(inputIndex, intArrayOf(1, MEL_HEIGHT, MEL_WIDTH, 1))
                }
                tensorName.contains("ppg_features", ignoreCase = true) -> {
                    interpreter.resizeInput(inputIndex, intArrayOf(1, PPG_FEATURE_SIZE))
                }
                tensorName.contains("ppg_signal", ignoreCase = true) -> {
                    interpreter.resizeInput(inputIndex, intArrayOf(1, PPG_SIGNAL_SIZE, 1))
                }
            }
        }
        interpreter.allocateTensors()
    }

    private fun closeInterpreters() {
        voiceEmotionInterpreter?.close()
        voiceEmotionInterpreter = null
        voiceVeracityInterpreter?.close()
        voiceVeracityInterpreter = null
        faceEmotionInterpreter?.close()
        faceEmotionInterpreter = null
    }

    private fun loadModelFile(assetName: String): MappedByteBuffer {
        context.assets.openFd(assetName).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).channel.use { channel ->
                return channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength,
                )
            }
        }
    }

    private fun interpreterOptions(useXnnpack: Boolean): Interpreter.Options {
        return Interpreter.Options().apply {
            setNumThreads(1)
            setUseXNNPACK(useXnnpack)
        }
    }

    companion object {
        private const val VOICE_EMOTION_MODEL = "model_1_emotion_float16.tflite"
        private const val VOICE_VERACITY_MODEL = "model_1_veracity_float16.tflite"
        private const val FACE_EMOTION_MODEL = "model_2_face_emotion_float16.tflite"

        private const val MEL_HEIGHT = 128
        private const val MEL_WIDTH = 96
        private const val MEL_SIZE = MEL_HEIGHT * MEL_WIDTH
        private const val PPG_FEATURE_SIZE = 16
        private const val PPG_SIGNAL_SIZE = 256
        private const val FACE_SIZE = 96
        private const val FACE_INPUT_SIZE = FACE_SIZE * FACE_SIZE * 3
        private const val DEMO_PPG_SAMPLE_RATE = 32f

        private val PPG_MEAN = floatArrayOf(
            69.288155f, 7.6080084f, 54.047024f, 102.77816f,
            48.73116f, 1.5750747f, 0.053060386f, 3.6082406f,
            4.1116686f, 0.82233655f, 92.50488f, 8.853871f,
            0.81539047f, 0.302888f, 86.35121f, 22.757528f,
        )

        private val PPG_STD = floatArrayOf(
            19.763117f, 11.917997f, 28.495676f, 64.45155f,
            78.85601f, 5.266306f, 0.17746153f, 6.753431f,
            1.7615207f, 0.3523063f, 50.48478f, 21.37437f,
            0.50540376f, 0.33734843f, 37.716778f, 18.643372f,
        )
    }
}
