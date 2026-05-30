package com.example.mca_project.ui.interview

import androidx.camera.core.ImageProxy
import com.example.mca_project.audio.RealtimeAudioEngine
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mca_project.camera.InterviewCameraProcessor
import com.example.mca_project.data.repository.SessionRepository
import com.example.mca_project.domain.model.Mode
import com.example.mca_project.domain.model.SegmentResult
import com.example.mca_project.domain.model.Session
import com.example.mca_project.di.CameraExecutor
import com.example.mca_project.ml.FusionInput
import com.example.mca_project.ml.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ExecutorService
import javax.inject.Inject

data class InterviewUiState(
    val isMeasuring: Boolean = false,
    val elapsedSeconds: Int = 0,
    val currentEmotion: String? = null,
    val emotionConfidence: Float = 0f,
    val fakeProbability: Float = 0f,
    val bpm: Float? = null,
    val faceVoiceDiscordance: Float? = null,
    val trackingConfidence: Float = 0f,
    /** 모델 미연동 안내 메시지 ("Not ready!") */
    val notReadyMessage: String? = null,
    val segmentCount: Int = 0,
)

/**
 * 시나리오 1(대면 면접, 융합 모델) ViewModel.
 * 카메라/오디오 캡처와 추론은 모델 연동 전까지 stub.
 */
@HiltViewModel
class InterviewViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val sessionRepository: SessionRepository,
    private val realtimeAudioEngine: RealtimeAudioEngine,
    private val interviewCameraProcessor: InterviewCameraProcessor,
    @CameraExecutor val analysisExecutor: ExecutorService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InterviewUiState())
    val uiState = _uiState.asStateFlow()

    private val segments = mutableListOf<SegmentResult>()
    private var startTime = 0L
    private var measuringJob: Job? = null
    @Volatile private var latestFaceImage: FloatArray? = null
    @Volatile private var latestPpgFeatures: FloatArray? = null
    @Volatile private var latestPpgSignal: FloatArray? = null
    @Volatile private var latestBpm: Float? = null

    fun onCameraFrame(image: ImageProxy) {
        val reading = interviewCameraProcessor.analyze(image)
        latestFaceImage = reading.faceImage
        latestPpgFeatures = reading.ppgFeatures
        latestPpgSignal = reading.ppgSignal
        latestBpm = reading.bpm ?: latestBpm
        _uiState.update {
            it.copy(
                bpm = reading.bpm ?: it.bpm,
                trackingConfidence = reading.trackingConfidence,
            )
        }
    }

    fun startMeasuring() {
        if (_uiState.value.isMeasuring) return
        segments.clear()
        interviewCameraProcessor.reset()
        latestFaceImage = null
        latestPpgFeatures = null
        latestPpgSignal = null
        latestBpm = null
        realtimeAudioEngine.start()
        startTime = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isMeasuring = true,
                elapsedSeconds = 0,
                currentEmotion = null,
                emotionConfidence = 0f,
                fakeProbability = 0f,
                bpm = null,
                faceVoiceDiscordance = null,
                trackingConfidence = 0f,
                segmentCount = 0,
                notReadyMessage = modelManager.loadErrorMessage ?: "Warming up 5-second mic window and camera stream…",
            )
        }
        measuringJob?.cancel()
        measuringJob = viewModelScope.launch {
            while (isActive && _uiState.value.isMeasuring) {
                val faceImage = latestFaceImage
                val ppgFeatures = latestPpgFeatures
                val ppgSignal = latestPpgSignal
                val bpm = latestBpm
                val audioSnapshot = realtimeAudioEngine.latestSnapshot()
                if (faceImage == null || ppgFeatures == null || ppgSignal == null || audioSnapshot == null) {
                    _uiState.update {
                        it.copy(
                            notReadyMessage = when {
                                faceImage == null || ppgFeatures == null || ppgSignal == null -> "Waiting for live camera frames…"
                                else -> "Warming up 5-second mic window…"
                            }
                        )
                    }
                    delay(500)
                    continue
                }
                val nextIndex = segments.size + 1
                runCatching {
                    modelManager.inferFusion(
                        FusionInput(
                            mel = audioSnapshot.mel,
                            faceImage = faceImage,
                            ppgFeatures = ppgFeatures,
                            ppgSignal = ppgSignal,
                            bpmHint = bpm,
                        )
                    )
                }.onSuccess { inference ->
                    val now = System.currentTimeMillis()
                    segments += SegmentResult(now, nextIndex, inference)
                    _uiState.update {
                        it.copy(
                            elapsedSeconds = ((now - startTime) / 1_000L).toInt(),
                            currentEmotion = inference.emotion,
                            emotionConfidence = inference.emotionConfidence,
                            fakeProbability = inference.fakeProbability,
                            bpm = inference.bpm,
                            faceVoiceDiscordance = inference.faceVoiceDiscordance,
                            notReadyMessage = null,
                            segmentCount = segments.size,
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(notReadyMessage = throwable.message ?: "Model inference failed")
                    }
                    break
                }
                delay(2_500)
            }
        }
    }

    fun stopMeasuring(onFinished: (sessionId: String) -> Unit) {
        measuringJob?.cancel()
        realtimeAudioEngine.stop()
        _uiState.update { it.copy(isMeasuring = false) }
        val sessionId = UUID.randomUUID().toString()
        val avgFakeProbability = if (segments.isEmpty()) 0f else segments.map { it.inference.fakeProbability }.average().toFloat()
        val dominantEmotion = segments
            .groupingBy { it.inference.emotion }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "중립"
        viewModelScope.launch {
            sessionRepository.saveSession(
                Session(
                    id = sessionId,
                    mode = Mode.INTERVIEW,
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    avgFakeProbability = avgFakeProbability,
                    dominantEmotion = dominantEmotion,
                    results = segments.toList(),
                )
            )
            onFinished(sessionId)
        }
    }

    override fun onCleared() {
        realtimeAudioEngine.stop()
        super.onCleared()
    }
}
