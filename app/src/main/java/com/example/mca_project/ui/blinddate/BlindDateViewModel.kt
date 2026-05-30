package com.example.mca_project.ui.blinddate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mca_project.data.repository.SessionRepository
import com.example.mca_project.domain.model.Emotion
import com.example.mca_project.domain.model.InferenceOutput
import com.example.mca_project.domain.model.Mode
import com.example.mca_project.domain.model.SegmentResult
import com.example.mca_project.domain.model.Session
import com.example.mca_project.ml.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlindDateUiState())
    val uiState = _uiState.asStateFlow()

    private var startTime = 0L

    /** PPG 잠금 (현재는 즉시 성공하는 stub) */
    fun lockPpg() {
        // TODO(ppg): Camera2 + 플래시로 손가락 RGB 신호 안정화 감지
        _uiState.update { it.copy(ppgLocked = true, lockedBpm = 72f) }
    }

    fun nextCalibrationQuestion() {
        _uiState.update { it.copy(calibrationIndex = it.calibrationIndex + 1) }
        // TODO(calibration): 각 질문 답변 시 음성+PPG baseline 수집
    }

    fun startMeasuring() {
        startTime = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isMeasuring = true,
                cards = emptyList(),
                notReadyMessage = if (modelManager.isReady) null else "Not ready!",
            )
        }
        // TODO(vad): VAD로 발화 분절 → 발화 종료마다 modelManager.inferVoicePpg(...) → 카드 추가
    }

    fun stopMeasuring(onFinished: (sessionId: String) -> Unit) {
        _uiState.update { it.copy(isMeasuring = false) }
        val sessionId = UUID.randomUUID().toString()
        viewModelScope.launch {
            sessionRepository.saveSession(
                Session(
                    id = sessionId,
                    mode = Mode.BLIND_DATE,
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    avgFakeProbability = 0f,
                    dominantEmotion = Emotion.NEUTRAL,
                    results = _uiState.value.cards.map {
                        SegmentResult(System.currentTimeMillis(), it.index, it.inference)
                    },
                )
            )
            onFinished(sessionId)
        }
    }
}