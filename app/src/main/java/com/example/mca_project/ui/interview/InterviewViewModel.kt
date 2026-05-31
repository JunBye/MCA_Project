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
import com.example.mca_project.ml.InterviewInput
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
    val voiceEmotion: String? = null,
    val voiceEmotionConfidence: Float = 0f,
    val fakeProbability: Float = 0f,
    val faceVoiceDiscordance: Float? = null,
    val trackingConfidence: Float = 0f,
    val faceBox: com.example.mca_project.camera.FaceBox? = null,
    /** 모델 미연동 안내 메시지 ("Not ready!") */
    val notReadyMessage: String? = null,
    val segmentCount: Int = 0,
)

/**
 * 시나리오 1(Job Interview, Voice-only + Face) ViewModel.
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
    private var tickerJob: Job? = null
    @Volatile private var latestFaceImage: FloatArray? = null

    // ML Kit 검출은 비동기 → analyze 가 image.close()까지 책임진다(CameraXPreview는 닫지 않음).
    // isFrontCamera: 전면이면 회전 역변환·미러를 보정한다.
    fun onCameraFrame(image: ImageProxy, isFrontCamera: Boolean = false) {
        interviewCameraProcessor.analyze(image, isFrontCamera) { reading ->
            latestFaceImage = reading.faceImage
            _uiState.update {
                it.copy(
                    trackingConfidence = reading.trackingConfidence,
                    faceBox = reading.faceBox,
                )
            }
        }
    }

    fun startMeasuring() {
        if (_uiState.value.isMeasuring) return
        segments.clear()
        interviewCameraProcessor.reset()
        latestFaceImage = null
        realtimeAudioEngine.start()
        startTime = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isMeasuring = true,
                elapsedSeconds = 0,
                currentEmotion = null,
                emotionConfidence = 0f,
                fakeProbability = 0f,
                faceVoiceDiscordance = null,
                trackingConfidence = 0f,
                segmentCount = 0,
                notReadyMessage = modelManager.loadErrorMessage ?: "Warming up 5-second mic window and camera stream…",
            )
        }
        measuringJob?.cancel()
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isMeasuring) {
                val now = System.currentTimeMillis()
                _uiState.update { state ->
                    state.copy(elapsedSeconds = ((now - startTime) / 1_000L).toInt())
                }
                delay(1_000)
            }
        }
        measuringJob = viewModelScope.launch {
            while (isActive && _uiState.value.isMeasuring) {
                val faceImage = latestFaceImage
                val audioSnapshot = realtimeAudioEngine.latestSnapshot()
                if (faceImage == null || audioSnapshot == null) {
                    _uiState.update {
                        it.copy(
                            notReadyMessage = when {
                                faceImage == null -> "Waiting for live camera frames…"
                                else -> "Warming up 5-second mic window…"
                            }
                        )
                    }
                    delay(500)
                    continue
                }
                val nextIndex = segments.size + 1
                runCatching {
                    modelManager.inferInterview(
                        InterviewInput(
                            mel = audioSnapshot.mel,
                            faceImage = faceImage,
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
                            voiceEmotion = inference.voiceEmotion,
                            voiceEmotionConfidence = inference.voiceEmotionConfidence ?: 0f,
                            fakeProbability = inference.fakeProbability,
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
        tickerJob?.cancel()
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
        tickerJob?.cancel()
        realtimeAudioEngine.stop()
        super.onCleared()
    }
}
