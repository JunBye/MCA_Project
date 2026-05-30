package com.example.mca_project.domain.model

/** 분석 모드 (시나리오) */
enum class Mode { INTERVIEW, BLIND_DATE }

/** 모델별 라벨 공간이 달라 문자열 라벨로 관리한다. */
object EmotionCatalog {
    val voicePpg = listOf("중립", "차분함", "행복", "슬픔", "분노", "공포", "혐오", "놀람")
    val face = listOf("중립", "행복", "슬픔", "놀람", "공포", "혐오", "분노", "경멸")
    val heatmap = listOf("중립", "차분함", "행복", "슬픔", "분노", "공포", "혐오", "놀람", "경멸")
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
