package com.example.mca_project.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mca_project.ui.components.CardMeta
import com.example.mca_project.ui.components.EcgLine
import com.example.mca_project.ui.components.EdIcon
import com.example.mca_project.ui.components.ScenarioCard
import com.example.mca_project.ui.components.Screen
import com.example.mca_project.ui.theme.Accent
import com.example.mca_project.ui.theme.EdTheme
import com.example.mca_project.ui.theme.EdType
import com.example.mca_project.ui.theme.LocalThemeController

@Composable
fun HomeScreen(
    onInterview: () -> Unit,
    onBlindDate: () -> Unit,
    onHistory: () -> Unit,
) {
    val c = EdTheme.colors
    val theme = LocalThemeController.current
    Screen(
        footer = {
            Row(
                Modifier.fillMaxWidth().clickable { onHistory() }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EdIcon("clock", size = 17, tint = c.textDim)
                Spacer(Modifier.width(7.dp))
                Text("View past sessions", color = c.textDim, fontFamily = EdType.sans, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            }
        },
    ) {
        // 타이틀 + ECG 악센트
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(c.primaryTint), contentAlignment = Alignment.Center) {
                EdIcon("scan", size = 18, tint = c.primary)
            }
            Spacer(Modifier.width(11.dp))
            Text("Emotion Detector", color = c.textDim, fontFamily = EdType.mono, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(11.dp))
            EcgLine(Modifier.weight(1f), color = c.primary, height = 20)
        }

        Text("Choose a situation", color = c.text, fontFamily = EdType.sans, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
        Text(
            "Run a private, on-device authenticity analysis from the sensors you allow.",
            color = c.textDim, fontFamily = EdType.sans, fontSize = 14.5.sp, modifier = Modifier.padding(top = 8.dp),
        )

        Column(Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScenarioCard(
                icon = "briefcase", title = "Job Interview",
                subtitle = "Real-time analysis of the person across from you.",
                meta = listOf(CardMeta("mic", "VOICE"), CardMeta("face", "FACE")),
                onClick = onInterview,
            )
            ScenarioCard(
                icon = "glass", title = "Blind Date",
                subtitle = "Per-utterance read on each answer they give.",
                meta = listOf(CardMeta("mic", "VOICE"), CardMeta("heart", "PPG")),
                onClick = onBlindDate,
            )
        }

        // ── 테마 설정 (사용자 요청: Home에서 dark/light + primary color 토글) ──
        Spacer(Modifier.size(28.dp))
        Text("APPEARANCE", color = c.textFaint, fontFamily = EdType.sans, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Dark mode 토글
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Dark mode", color = c.text, fontFamily = EdType.sans, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                SmallToggle(on = theme.dark) { theme.toggleDark() }
            }
            Box(Modifier.fillMaxWidth().size(1.dp).background(c.border))
            // Primary color
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Primary color", color = c.text, fontFamily = EdType.sans, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                SegRadio("Indigo", theme.accent == Accent.Indigo) { theme.chooseAccent(Accent.Indigo) }
                Spacer(Modifier.width(8.dp))
                SegRadio("Teal", theme.accent == Accent.Teal) { theme.chooseAccent(Accent.Teal) }
            }
        }
    }
}

@Composable
private fun SmallToggle(on: Boolean, onToggle: () -> Unit) {
    val c = EdTheme.colors
    Box(
        Modifier.size(width = 46.dp, height = 26.dp).clip(RoundedCornerShape(13.dp))
            .background(if (on) c.primary else c.track).clickable { onToggle() }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(if (on) c.onPrimary else c.textFaint))
    }
}

@Composable
private fun SegRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = EdTheme.colors
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.primaryTint else c.surface2)
            .border(1.dp, if (selected) c.primary else c.border, RoundedCornerShape(10.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (selected) c.primary else c.textDim, fontFamily = EdType.sans, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
