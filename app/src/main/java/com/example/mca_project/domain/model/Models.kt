package com.example.mca_project.domain.model

/** 분석 모드 (시나리오) */
enum class Mode { INTERVIEW, BLIND_DATE }

/** 모델 출력 인덱스 순서: 0 angry, 1 contempt, 2 disgust, 3 fear, 4 happy, 5 neutral, 6 sad, 7 surprise */
object EmotionCatalog {
    val canonical = listOf("Angry", "Contempt", "Disgust", "Fear", "Happy", "Neutral", "Sad", "Surprise")
    val voicePpg = canonical
    val face = canonical
    val heatmap = canonical
}

data class EmotionScore(
    val label: String,
    val probability: Float,
)

/**
 * 한 세그먼트(5초 윈도우 또는 발화 단위)의 추론 결과.
 * `topEmotions`는 확률순 정렬이며, emotion은 첫 항목과 동일하다.
 */
data class InferenceOutput(
    val emotion: String,
    val emotionConfidence: Float,
    val topEmotions: List<EmotionScore> = emptyList(),
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
    val dominantEmotion: String,
    val results: List<SegmentResult>,
)

/** 세션 내 세그먼트별 결과 */
data class SegmentResult(
    val timestamp: Long,
    val segmentIndex: Int,
    val inference: InferenceOutput,
)
