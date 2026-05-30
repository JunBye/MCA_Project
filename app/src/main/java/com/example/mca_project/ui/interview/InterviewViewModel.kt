package com.example.mca_project.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mca_project.data.repository.SessionRepository
import com.example.mca_project.domain.model.Emotion
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

data class InterviewUiState(
    val isMeasuring: Boolean = false,
    val elapsedSeconds: Int = 0,
    val currentEmotion: Emotion? = null,
    val emotionConfidence: Float = 0f,
    val fakeProbability: Float = 0f,
    val bpm: Float? = null,
    val faceVoiceDiscordance: Float? = null,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(InterviewUiState())
    val uiState = _uiState.asStateFlow()

    private val segments = mutableListOf<SegmentResult>()
    private var startTime = 0L

    fun startMeasuring() {
        segments.clear()
        startTime = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isMeasuring = true,
                segmentCount = 0,
                notReadyMessage = if (modelManager.isReady) null else "Not ready!",
            )
        }
        // TODO(camera): CameraX로 후면 카메라 프레임 수집 → FaceMesh + 뺨 PPG
        // TODO(audio): 마이크 음성 수집
        // TODO(infer): 5초 슬라이딩 윈도우마다 modelManager.inferFusion(...) 호출 후 UI 갱신
    }

    fun stopMeasuring(onFinished: (sessionId: String) -> Unit) {
        _uiState.update { it.copy(isMeasuring = false) }
        val sessionId = UUID.randomUUID().toString()
        viewModelScope.launch {
            // 모델 미연동 상태이므로 빈 결과 세션을 저장 (집계 로직은 추후)
            sessionRepository.saveSession(
                Session(
                    id = sessionId,
                    mode = Mode.INTERVIEW,
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    avgFakeProbability = 0f,
                    dominantEmotion = Emotion.NEUTRAL,
                    results = segments.toList(),
                )
            )
            onFinished(sessionId)
        }
    }
}