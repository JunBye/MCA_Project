package com.example.mca_project.domain.model

/** 분석 모드 (시나리오) */
enum class Mode { INTERVIEW, BLIND_DATE }

/** 감정 7클래스 (RAVDESS/CREMA-D 기준) */
enum class Emotion(val label: String) {
    HAPPY("행복"),
    SAD("슬픔"),
    ANGRY("분노"),
    FEAR("공포"),
    DISGUST("혐오"),
    SURPRISE("놀람"),
    NEUTRAL("중립"),
}

/**
 * 한 세그먼트(5초 윈도우 또는 발화 단위)의 추론 결과.
 * 모델 연동 전까지는 ModelManager가 stub 값으로 채운다.
 */
data class InferenceOutput(
    val emotion: Emotion,
    val emotionConfidence: Float,
    val fakeProbability: Float,
    val bpm: Float? = null,
    val faceVoiceDiscordance: Float? = null, // 시나리오 1(융합)만
)

/** 측정 세션 1건 */
data class Session(
    val id: String,
    val mode: Mode,
    val startTime: Long,
    val endTime: Long,
    val avgFakeProbability: Float,
    val dominantEmotion: Emotion,
    val results: List<SegmentResult>,
)

/** 세션 내 세그먼트별 결과 */
data class SegmentResult(
    val timestamp: Long,
    val segmentIndex: Int,
    val inference: InferenceOutput,
)