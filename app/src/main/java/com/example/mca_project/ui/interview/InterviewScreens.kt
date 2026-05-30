package com.example.mca_project.ui.interview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mca_project.ui.components.AppHeader
import com.example.mca_project.ui.components.CameraView
import com.example.mca_project.ui.components.CardMeta
import com.example.mca_project.ui.components.ChecklistRow
import com.example.mca_project.ui.components.Divider
import com.example.mca_project.ui.components.EdBtn
import com.example.mca_project.ui.components.EdButton
import com.example.mca_project.ui.components.EdCard
import com.example.mca_project.ui.components.EdIcon
import com.example.mca_project.ui.components.GaugeTone
import com.example.mca_project.ui.components.HeartBeat
import com.example.mca_project.ui.components.LabeledGauge
import com.example.mca_project.ui.components.MetricRow
import com.example.mca_project.ui.components.Screen
import com.example.mca_project.ui.components.SectionLabel
import com.example.mca_project.ui.components.Spinner
import com.example.mca_project.ui.components.WarningBanner
import com.example.mca_project.ui.theme.EdTheme
import com.example.mca_project.ui.theme.EdType
import kotlinx.coroutines.delay

/** 3-1 Setup */
@Composable
fun InterviewSetupScreen(onStart: () -> Unit) {
    val c = EdTheme.colors
    var granted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(480); granted = true } // TODO(permission): 실제 권한 요청
    Screen(footer = { EdButton("Start", onStart, icon = "activity", enabled = granted) }) {
        AppHeader("Job Interview", step = "Setup · real-time")
        Text(
            "We'll read voice, facial expression and heart-rate (PPG) continuously while you talk.",
            color = c.textDim, fontFamily = EdType.sans, fontSize = 14.5.sp, modifier = Modifier.padding(horizontal = 2.dp).padding(bottom = 20.dp),
        )
        SectionLabel("Permissions required")
        EdCard {
            Column {
                ChecklistRow("camera", "Camera (rear)", "Reads facial micro-expressions", granted) {}
                Divider()
                ChecklistRow("mic", "Microphone", "Reads voice tremor & prosody", granted) {}
            }
        }
        Spacer(Modifier.size(16.dp))
        EdCard(background = c.surface2, borderColor = c.surface2) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                EdIcon("camera", size = 20, tint = c.primary)
                Spacer(Modifier.width(12.dp))
                Text("Place the phone so the rear camera sees the other person's face.", color = c.textDim, fontFamily = EdType.sans, fontSize = 13.5.sp)
            }
        }
        Spacer(Modifier.size(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
            EdIcon("shield", size = 15, tint = c.textFaint)
            Spacer(Modifier.width(8.dp))
            Text("Nothing leaves this device.", color = c.textFaint, fontFamily = EdType.mono, fontSize = 12.sp)
        }
    }
}

/** 3-2 Measuring — Gauges 변형 */
@Composable
fun InterviewMeasuringScreen(
    viewModel: InterviewViewModel = hiltViewModel(),
    onStop: () -> Unit,
) {
    val c = EdTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var warming by remember { mutableStateOf(true) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.startMeasuring() }
    LaunchedEffect(Unit) { delay(3600); warming = false }

    // 모델 미연동: state.notReadyMessage 가 있으면 항상 Not ready
    val notReady = state.notReadyMessage != null
    val showWarn = notReady && (warming && !dismissed || !warming)
    val warnText = if (warming) "Not ready! — loading models…" else "Not ready! — model not connected"

    val dec = state.fakeProbability * 100f
    val mismatch = (state.faceVoiceDiscordance ?: 0f) * 100f
    val bpm = state.bpm?.toInt() ?: 0

    Screen(pad = 18, footer = { EdButton("Stop analysis", onStop, variant = EdBtn.Danger, icon = "square") }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(c.alert))
            Spacer(Modifier.width(9.dp))
            Text("REC · LIVE", color = c.text, fontFamily = EdType.mono, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Job Interview", color = c.textFaint, fontFamily = EdType.mono, fontSize = 12.5.sp)
        }

        WarningBanner(show = showWarn, text = warnText, onDismiss = if (warming) ({ dismissed = true }) else null)

        // 카메라 프리뷰
        CameraView(ratio = 4f / 3f) {
            Box(Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(7.dp)).background(c.bg.copy(alpha = 0.6f)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EdIcon("face", size = 13, tint = c.primary)
                    Spacer(Modifier.width(7.dp))
                    Text("tracking face · ${(state.emotionConfidence * 100).toInt()}%", color = c.primary, fontFamily = EdType.mono, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.size(14.dp))
        EdCard(borderColor = if (dec > 55) c.alertEdge else c.border) {
            Box(Modifier.padding(18.dp)) {
                LabeledGauge("Deception probability", dec, tone = GaugeTone.Alert, big = true, sub = if (dec > 55) "elevated — corroborate before trusting" else "within honest range")
            }
        }

        Spacer(Modifier.size(12.dp))
        EdCard {
            Column(Modifier.padding(horizontal = 18.dp)) {
                // Surface emotion
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
                        EdIcon("face", size = 22, tint = c.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Surface emotion", color = c.textDim, fontFamily = EdType.sans, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                        Text("dominant facial read", color = c.textFaint, fontFamily = EdType.sans, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(state.currentEmotion?.label ?: "—", color = c.text, fontFamily = EdType.sans, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("${(state.emotionConfidence * 100).toInt()}% conf", color = c.textFaint, fontFamily = EdType.mono, fontSize = 11.5.sp)
                    }
                }
                Divider()
                MetricRow(
                    label = "Heart rate", sub = "via facial rPPG",
                    value = if (bpm > 0) "$bpm" else "—", unit = "BPM", big = true, accent = true,
                    leading = { HeartBeat(bpm = if (bpm > 0) bpm else 72, size = 24, color = c.alert) },
                )
                Divider()
                Box(Modifier.padding(vertical = 14.dp)) {
                    LabeledGauge("Face–Voice mismatch", mismatch, tone = if (mismatch > 45) GaugeTone.Warn else GaugeTone.Dim)
                }
            }
        }
    }
}

/** 3-3 Processing */
@Composable
fun InterviewProcessingScreen(
    viewModel: InterviewViewModel = hiltViewModel(),
    onDone: (sessionId: String) -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.stopMeasuring(onDone) }
    ProcessingBody(mode = "face · voice · PPG")
}

@Composable
fun ProcessingBody(mode: String) {
    val c = EdTheme.colors
    Screen(center = true) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                Spinner(size = 76, color = c.primary)
                EdIcon("layers", size = 30, tint = c.primary)
            }
            Spacer(Modifier.size(22.dp))
            Text("Analyzing results…", color = c.text, fontFamily = EdType.sans, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("fusing $mode signals", color = c.textDim, fontFamily = EdType.mono, fontSize = 13.5.sp, modifier = Modifier.padding(top = 7.dp))
        }
    }
}
