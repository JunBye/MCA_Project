package com.example.mca_project.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mca_project.domain.model.Mode
import com.example.mca_project.ui.components.AppHeader
import com.example.mca_project.ui.components.EdBtn
import com.example.mca_project.ui.components.EdButton
import com.example.mca_project.ui.components.EdCard
import com.example.mca_project.ui.components.EdIcon
import com.example.mca_project.ui.components.Screen
import com.example.mca_project.ui.theme.EdTheme
import com.example.mca_project.ui.theme.EdType

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onOpenSession: (sessionId: String) -> Unit,
    onHome: () -> Unit = {},
) {
    val c = EdTheme.colors
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    Screen {
        AppHeader("Past sessions")
        if (sessions.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
                    EdIcon("clock", size = 34, tint = c.textFaint)
                }
                Text("No sessions yet", color = c.text, fontFamily = EdType.sans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Completed analyses appear here, kept only on this device.", color = c.textDim, fontFamily = EdType.sans, fontSize = 13.5.sp)
                EdButton("Start a session", onHome, variant = EdBtn.Ghost, icon = "home", fillWidth = false)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                sessions.forEach { s ->
                    EdCard(onClick = { onOpenSession(s.id) }) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(c.primaryTint), contentAlignment = Alignment.Center) {
                                EdIcon(if (s.mode == Mode.INTERVIEW) "briefcase" else "glass", size = 22, tint = c.primary)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (s.mode == Mode.INTERVIEW) "Job Interview" else "Blind Date", color = c.text, fontFamily = EdType.sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("${s.results.size} segments · ${s.dominantEmotion}", color = c.textFaint, fontFamily = EdType.mono, fontSize = 12.5.sp)
                            }
                            val avg = (s.avgFakeProbability * 100).toInt()
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$avg%", color = if (avg >= 50) c.alert else c.text, fontFamily = EdType.mono, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                Text("AVG DECEPTION", color = c.textFaint, fontFamily = EdType.sans, fontSize = 10.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
