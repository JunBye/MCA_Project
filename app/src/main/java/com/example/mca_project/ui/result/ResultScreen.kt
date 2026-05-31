package com.example.mca_project.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mca_project.domain.model.EmotionCatalog
import com.example.mca_project.domain.model.Mode
import com.example.mca_project.ui.components.AppHeader
import com.example.mca_project.ui.components.EdBtn
import com.example.mca_project.ui.components.EdButton
import com.example.mca_project.ui.components.EdCard
import com.example.mca_project.ui.components.EdIcon
import com.example.mca_project.ui.components.ResultCard
import com.example.mca_project.ui.components.Screen
import com.example.mca_project.ui.components.SectionLabel
import com.example.mca_project.ui.theme.EdTheme
import com.example.mca_project.ui.theme.EdType

@Composable
fun ResultScreen(
    sessionId: String,
    viewModel: ResultViewModel = hiltViewModel(),
    onRestart: () -> Unit,
    onHome: () -> Unit,
) {
    val c = EdTheme.colors
    val session by viewModel.session.collectAsStateWithLifecycle()
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    Screen(pad = 18, footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EdButton("Restart", onRestart, variant = EdBtn.Outlined, icon = "refresh", modifier = Modifier.weight(1f), fillWidth = false)
            EdButton("Home", onHome, variant = EdBtn.Filled, icon = "home", modifier = Modifier.weight(1f), fillWidth = false)
        }
    }) {
        AppHeader("Session summary")
        val s = session
        if (s == null) {
            Text("Loading…", color = c.textDim, fontFamily = EdType.sans)
            return@Screen
        }

        val fakeCount = s.results.count { it.inference.fakeProbability > 0.5f }
        val avgDec = (s.avgFakeProbability * 100).toInt()
        val emotionCounts = s.results
            .groupingBy { it.inference.emotion }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
        val topEmotionStats = emotionCounts.take(3)

        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 0.dp).padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(100.dp)).background(c.primaryTint).padding(horizontal = 11.dp, vertical = 5.dp)) {
                Text(if (s.mode == Mode.INTERVIEW) "JOB INTERVIEW" else "BLIND DATE", color = c.primary, fontFamily = EdType.mono, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Text("${s.results.size} segments", color = c.textFaint, fontFamily = EdType.mono, fontSize = 12.sp)
        }

        // 핵심 메트릭 카드 (그라데이션)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(c.alertDim, c.primaryTint)))
                .border(1.dp, c.alertEdge, RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Avg deception score", color = c.textDim, fontFamily = EdType.sans, fontSize = 13.sp)
                Text("$avgDec%", color = c.alert, fontFamily = EdType.mono, fontSize = 48.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 7.dp))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Fake segments", color = c.textFaint, fontFamily = EdType.sans, fontSize = 11.sp)
                    Text("$fakeCount/${s.results.size}", color = c.alert, fontFamily = EdType.mono, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        val totalSegments = s.results.size.coerceAtLeast(1)
        val dominantCount = emotionCounts.firstOrNull { it.first == s.dominantEmotion }?.second
            ?: emotionCounts.firstOrNull()?.second ?: 0
        Spacer(Modifier.size(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            // Dominant emotion: 큰 감정명 + "in N / M segments" 부제
            EdCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text("DOMINANT EMOTION", color = c.textFaint, fontFamily = EdType.mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                    Text(s.dominantEmotion, color = c.text, fontFamily = EdType.sans, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 7.dp))
                    Text("in $dominantCount / $totalSegments segments", color = c.textFaint, fontFamily = EdType.mono, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
            // Emotion spread: 상위 3개 막대그래프 (라벨 · 바 · count)
            EdCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text("EMOTION SPREAD", color = c.textFaint, fontFamily = EdType.mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 13.dp))
                    if (topEmotionStats.isEmpty()) {
                        Text("No data", color = c.textFaint, fontFamily = EdType.mono, fontSize = 12.5.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            topEmotionStats.forEach { (label, count) ->
                                EmotionSpreadRow(label, count, totalSegments)
                            }
                        }
                    }
                }
            }
        }

        // 히트맵
        Spacer(Modifier.size(14.dp))
        Heatmap(results = s.results)

        // 세그먼트 리스트
        SectionLabel("Per-segment breakdown", modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
        if (s.results.isEmpty()) {
            Text("No segments recorded — model not connected yet.", color = c.textFaint, fontFamily = EdType.sans, fontSize = 13.5.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                s.results.forEach { r ->
                    val fake = r.inference.fakeProbability > 0.5f
                    val top2 = r.inference.topEmotions.joinToString(" / ") {
                        "${it.label} ${(it.probability * 100).toInt()}%"
                    }
                    ResultCard(
                        index = "${r.segmentIndex}", title = "Segment ${r.segmentIndex}",
                        fake = fake, confidence = ((if (fake) r.inference.fakeProbability else 1 - r.inference.fakeProbability) * 100).toInt(),
                        emotion = top2.ifBlank { r.inference.emotion },
                    )
                }
            }
        }
    }
}

private val HEATMAP_EMOTIONS = EmotionCatalog.heatmap
private val HEATMAP_CELL_WIDTH = 20.dp
private val HEATMAP_GRID_HEIGHT = 150.dp

@Composable
private fun Heatmap(results: List<com.example.mca_project.domain.model.SegmentResult>) {
    val c = EdTheme.colors
    val columns = results.size.coerceAtLeast(1)
    EdCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EdIcon("layers", size = 14, tint = c.primary)
                Spacer(Modifier.width(8.dp))
                Text("EMOTION TIMELINE HEATMAP", color = c.textDim, fontFamily = EdType.mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.size(14.dp))
            // 컬럼(세그먼트)별 감정 강도 순위 → 1등=Strong, 2등=Medium, 나머지=회색.
            // 각 col에서 어느 감정 행이 1·2등인지 rank로 표시한다(절대 확률이 아니라 상대 우세).
            val columnRank: List<Map<String, Int>> = (0 until columns).map { col ->
                val ordered = HEATMAP_EMOTIONS
                    .map { e -> e to (results.getOrNull(col)?.let { emotionWeight(it, e) } ?: 0f) }
                    .sortedByDescending { it.second }
                buildMap {
                    ordered.getOrNull(0)?.takeIf { it.second > 1e-4f }?.let { put(it.first, 1) }  // Strong
                    ordered.getOrNull(1)?.takeIf { it.second > 1e-4f }?.let { put(it.first, 2) }  // Medium
                }
            }
            // 라벨 열은 고정, 그리드는 가로 스크롤(세그먼트가 많아도 셀이 찌그러지지 않게).
            val scrollState = rememberScrollState()
            // 새 세그먼트가 쌓이면 맨 오른쪽(최신)으로 자동 스크롤
            LaunchedEffect(columns) { scrollState.scrollTo(scrollState.maxValue) }
            Row(Modifier.fillMaxWidth().height(HEATMAP_GRID_HEIGHT)) {
                // 라벨 열: 그리드 행과 동일하게 weight(1f)+spacedBy로 1:1 정렬, 각 라벨은 행 세로 중앙
                Column(Modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    HEATMAP_EMOTIONS.forEach { e ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            Text(e, color = c.textFaint, fontFamily = EdType.mono, fontSize = 10.5.sp, maxLines = 1)
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).horizontalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    HEATMAP_EMOTIONS.forEach { emotion ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(columns) { columnIndex ->
                                val rank = columnRank[columnIndex][emotion]
                                Box(
                                    Modifier.width(HEATMAP_CELL_WIDTH)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(heatmapRankColor(rank, c.track))
                                )
                            }
                        }
                    }
                }
            }
            // 범례: 회색(약함) / Medium(2등) / Strong(1등 지배적)
            Spacer(Modifier.size(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Legend(heatmapRankColor(null, c.track), "Low")
                Legend(heatmapRankColor(2, c.track), "2nd")
                Legend(heatmapRankColor(1, c.track), "Dominant")
            }
        }
    }
}

/**
 * 한 세그먼트에서 특정 감정의 heatmap 강도.
 * 우선 8클래스 전체 분포(emotionDistribution)에서 라벨 일치 확률을 쓰고,
 * 분포가 없으면(구버전 데이터) topEmotions → dominant 순으로 폴백한다.
 */
private fun emotionWeight(
    segment: com.example.mca_project.domain.model.SegmentResult,
    emotion: String,
): Float {
    val dist = segment.inference.emotionDistribution.firstOrNull { it.label == emotion }?.probability
    if (dist != null) return dist.coerceIn(0f, 1f)
    val matchedTop = segment.inference.topEmotions.firstOrNull { it.label == emotion }?.probability
    if (matchedTop != null) return matchedTop.coerceIn(0f, 1f)
    if (segment.inference.emotion == emotion) return segment.inference.emotionConfidence.coerceIn(0f, 1f)
    return 0f
}

/**
 * 셀 색 = 그 세그먼트(컬럼)에서 해당 감정의 우세 순위.
 * 1등(Dominant) = 진한 teal, 2등 = 중간 teal, 그 외 = 회색.
 * 거짓/진실과는 무관하며, "어떤 감정이 언제 우세했나"만 나타낸다.
 */
private fun heatmapRankColor(rank: Int?, fallback: Color): Color {
    return when (rank) {
        1 -> Color(0xFF2BC79A).copy(alpha = 0.95f)   // Dominant
        2 -> Color(0xFF2BC79A).copy(alpha = 0.45f)   // 2nd
        else -> fallback                              // Low (track 회색)
    }
}

/** Emotion spread 한 줄: 라벨 · 비율 막대 · 개수 (디자인 원본 반영) */
@Composable
private fun EmotionSpreadRow(label: String, count: Int, total: Int) {
    val c = EdTheme.colors
    val fraction = (count.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, color = c.textDim, fontFamily = EdType.sans, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(50.dp))
        Box(
            Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(c.track),
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(c.primary))
        }
        Text(
            "$count",
            color = c.textDim,
            fontFamily = EdType.mono,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 20.dp),
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    val c = EdTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(12.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, color = c.textDim, fontFamily = EdType.sans, fontSize = 11.5.sp)
    }
}
