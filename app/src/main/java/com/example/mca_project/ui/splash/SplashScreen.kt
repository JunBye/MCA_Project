package com.example.mca_project.ui.splash

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mca_project.ui.components.HeartBeat
import com.example.mca_project.ui.components.Screen
import com.example.mca_project.ui.components.Spinner
import com.example.mca_project.ui.theme.EdTheme
import com.example.mca_project.ui.theme.EdType
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onReady: () -> Unit) {
    val c = EdTheme.colors
    LaunchedEffect(Unit) {
        // TODO(model): ModelManager.loadModels() 비동기 로드 대기
        delay(2200)
        onReady()
    }
    Screen(center = true) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                // 펄스 링 3개
                val transition = rememberInfiniteTransition(label = "pulse")
                listOf(0, 1, 2).forEach { i ->
                    val p by transition.animateFloat(
                        0.55f, 1.15f,
                        infiniteRepeatable(tween(2600), RepeatMode.Restart, initialStartOffset = androidx.compose.animation.core.StartOffset(i * 700)),
                        label = "ring$i",
                    )
                    Box(
                        Modifier.size(132.dp).scale(p).alpha((1.15f - p).coerceIn(0f, 1f))
                            .clip(CircleShape).border(2.dp, c.primary, CircleShape),
                    )
                }
                Box(Modifier.size(88.dp).clip(CircleShape).background(c.primaryTint), contentAlignment = Alignment.Center) {
                    HeartBeat(bpm = 66, size = 42, color = c.primary)
                }
            }
            Spacer(Modifier.size(30.dp))
            Text("Emotion Detector", color = c.text, fontFamily = EdType.sans, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("ON-DEVICE · VOICE · FACE · PPG", color = c.textFaint, fontFamily = EdType.mono, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
            Spacer(Modifier.size(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spinner(size = 18, color = c.primary)
                Spacer(Modifier.width(10.dp))
                Text("Loading models…", color = c.textDim, fontFamily = EdType.sans, fontSize = 13.5.sp)
            }
        }
    }
}
