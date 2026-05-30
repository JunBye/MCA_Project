package com.example.mca_project.ui.blinddate

import androidx.camera.core.ImageProxy
import com.example.mca_project.audio.RealtimeAudioEngine
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mca_project.camera.FingerPpgProcessor
import com.example.mca_project.data.repository.SessionRepository
import com.example.mca_project.domain.model.InferenceOutput
import com.example.mca_project.domain.model.Mode
import com.example.mca_project.domain.model.SegmentResult
import com.example.mca_project.domain.model.Session
import com.example.mca_project.di.CameraExecutor
import com.example.mca_project.ml.VoicePpgInput
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

/** 캘리브레이션 단계에서 사용할 가벼운 질문들 */
val CALIBRATION_QUESTIONS = listOf(
    "오늘 점심 뭐 드셨어요?",
    "최근 본 영화 있어요?",
    "주말에 뭐 하셨어요?",
)

data class UtteranceCard(
    val index: Int,
    val inference: InferenceOutput,
)

data class BlindDateUiState(
    val ppgLocked: Boolean = false,
    val lockedBpm: Float? = null,
    val calibrationIndex: Int = 0,
    val isMeasuring: Boolean = false,
    val cards: List<UtteranceCard> = emptyList(),
    val notReadyMessage: String? = null,
)

/**
 * 시나리오 2(블라인드 데이트, Voice+PPG) ViewModel.
 * 손가락 PPG 잠금 → 캘리브레이션 → 발화 단위 측정.
 * PPG/VAD/추론은 모델 연동 전까지 stub.
 */
@HiltViewModel
class BlindDateViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val sessionRepository: SessionRepository,
    private val realtimeAudioEngine: RealtimeAudioEngine,
    private val fingerPpgProcessor: FingerPpgProcessor,
    @CameraExecutor val analysisExecutor: ExecutorService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlindDateUiState())
    val uiState = _uiState.asStateFlow()

    private var startTime = 0L
    private var measuringJob: Job? = null
    @Volatile private var latestPpgFeatures: FloatArray? = null
    @Volatile private var latestPpgSignal: FloatArray? = null
    @Volatile private var latestBpm: Float? = null

    fun resetPpgLock() {
        fingerPpgProcessor.reset()
        latestPpgFeatures = null
        latestPpgSignal = null
        latestBpm = null
        _uiState.update { it.copy(ppgLocked = false, lockedBpm = null) }
    }

    fun onPpgFrame(image: ImageProxy) {
        val reading = fingerPpgProcessor.analyze(image)
        latestPpgFeatures = reading.ppgFeatures
        latestPpgSignal = reading.ppgSignal
        latestBpm = reading.bpm ?: latestBpm
        _uiState.update {
            it.copy(
                ppgLocked = reading.locked,
                lockedBpm = reading.bpm ?: it.lockedBpm,
            )
        }
    }

    /** 자동 잠금이 안 잡힐 때를 위한 수동 fallback */
    fun lockPpg() {
        val bpm = latestBpm ?: (_uiState.value.lockedBpm ?: 72f)
        _uiState.update { it.copy(ppgLocked = true, lockedBpm = bpm) }
    }

    fun nextCalibrationQuestion() {
        _uiState.update { it.copy(calibrationIndex = it.calibrationIndex + 1) }
        // TODO(calibration): 각 질문 답변 시 음성+PPG baseline 수집
    }

    fun startMeasuring() {
        if (_uiState.value.isMeasuring) return
        fingerPpgProcessor.reset()
        latestPpgFeatures = null
        latestPpgSignal = null
        latestBpm = _uiState.value.lockedBpm
        realtimeAudioEngine.start()
        startTime = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isMeasuring = true,
                cards = emptyList(),
                notReadyMessage = modelManager.loadErrorMessage ?: "Warming up 5-second mic window…",
            )
        }
        measuringJob?.cancel()
        measuringJob = viewModelScope.launch {
            delay(900)
            while (isActive && _uiState.value.isMeasuring) {
                val ppgFeatures = latestPpgFeatures
                val ppgSignal = latestPpgSignal
                val bpm = latestBpm
                val audioSnapshot = realtimeAudioEngine.latestSnapshot()
                if (ppgFeatures == null || ppgSignal == null || audioSnapshot == null) {
                    _uiState.update {
                        it.copy(
                            notReadyMessage = when {
                                ppgFeatures == null || ppgSignal == null -> "Waiting for live PPG… keep a finger on the rear camera."
                                else -> "Warming up 5-second mic window…"
                            }
                        )
                    }
                    delay(500)
                    continue
                }
                val index = _uiState.value.cards.size + 1
                runCatching {
                    modelManager.inferVoicePpg(
                        VoicePpgInput(
                            mel = audioSnapshot.mel,
                            ppgFeatures = ppgFeatures,
                            ppgSignal = ppgSignal,
                            bpmHint = bpm,
                        )
                    )
                }.onSuccess { inference ->
                    _uiState.update { state ->
                        state.copy(
                            cards = state.cards + UtteranceCard(index = index, inference = inference),
                            notReadyMessage = null,
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
        val cards = _uiState.value.cards
        val sessionId = UUID.randomUUID().toString()
        viewModelScope.launch {
            sessionRepository.saveSession(
                Session(
                    id = sessionId,
                    mode = Mode.BLIND_DATE,
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    avgFakeProbability = averageFakeProbability(cards),
                    dominantEmotion = dominantEmotion(cards),
                    results = cards.map {
                        SegmentResult(System.currentTimeMillis(), it.index, it.inference)
                    },
                )
            )
            onFinished(sessionId)
        }
    }

    override fun onCleared() {
        realtimeAudioEngine.stop()
        super.onCleared()
    }

    private fun averageFakeProbability(cards: List<UtteranceCard>): Float {
        return if (cards.isEmpty()) 0f else cards.map { it.inference.fakeProbability }.average().toFloat()
    }

    private fun dominantEmotion(cards: List<UtteranceCard>): String {
        return cards
            .groupingBy { it.inference.emotion }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "중립"
    }
}
