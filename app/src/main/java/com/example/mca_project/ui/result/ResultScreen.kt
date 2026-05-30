package com.example.mca_project.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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

        // 히트맵
        Spacer(Modifier.size(14.dp))
        Heatmap(segmentCount = s.results.size.coerceAtLeast(1))

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

/** 감정 타임라인 히트맵 placeholder (모델 미연동: 빈 그리드) */
@Composable
private fun Heatmap(segmentCount: Int) {
    val c = EdTheme.colors
    EdCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EdIcon("layers", size = 14, tint = c.primary)
                Spacer(Modifier.width(8.dp))
                Text("EMOTION TIMELINE HEATMAP", color = c.textDim, fontFamily = EdType.mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.size(14.dp))
            Row(Modifier.fillMaxWidth().height(150.dp)) {
                Column(Modifier.width(60.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    HEATMAP_EMOTIONS.forEach { e ->
                        Text(e, color = c.textFaint, fontFamily = EdType.mono, fontSize = 10.5.sp)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    HEATMAP_EMOTIONS.forEach { _ ->
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(segmentCount) {
                                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(c.track))
                            }
                        }
                    }
                }
            }
            // 범례
            Spacer(Modifier.size(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Legend(Color(0xA62BC79A), "Genuine")
                Legend(Color(0xC7FF5D5D), "Deceptive")
            }
        }
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
