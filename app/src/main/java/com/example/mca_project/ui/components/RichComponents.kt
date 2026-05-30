package com.example.mca_project.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mca_project.ui.theme.EdTheme
import com.example.mca_project.ui.theme.EdType

private val Radius = 16.dp

data class CardMeta(val icon: String, val label: String)

@Composable
fun ScenarioCard(icon: String, title: String, subtitle: String, meta: List<CardMeta>, onClick: () -> Unit) {
    val c = EdTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius)).background(c.surface)
            .border(BorderStroke(1.dp, c.border), RoundedCornerShape(Radius))
            .clickable { onClick() }.padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.primaryTint), contentAlignment = Alignment.Center) {
            EdIcon(icon, size = 28, tint = c.primary)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontFamily = EdType.sans, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = c.textDim, fontFamily = EdType.sans, fontSize = 13.5.sp, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                meta.forEach { m ->
                    Row(
                        Modifier.clip(RoundedCornerShape(7.dp)).background(c.surface2).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EdIcon(m.icon, size = 11, tint = c.textDim)
                        Spacer(Modifier.width(4.dp))
                        Text(m.label, color = c.textDim, fontFamily = EdType.mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        EdIcon("chevron-right", size = 22, tint = c.textFaint)
    }
}

@Composable
fun ResultCard(index: String, title: String, fake: Boolean, confidence: Int, emotion: String? = null, time: String? = null, bpmDelta: Int? = null, note: List<CardMeta> = emptyList()) {
    val c = EdTheme.colors
    EdCard(borderColor = if (fake) c.alertEdge else c.border) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
                    Text(index, color = c.textDim, fontFamily = EdType.mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = c.text, fontFamily = EdType.sans, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    if (emotion != null || time != null) {
                        Text(listOfNotNull(emotion, time).joinToString(" · "), color = c.textFaint, fontFamily = EdType.mono, fontSize = 12.sp)
                    }
                }
                EdChip(if (fake) ChipTone.Fake else ChipTone.Genuine, "${if (fake) "Fake" else "Genuine"} $confidence%", icon = if (fake) "alert-triangle" else "check")
            }
            if (bpmDelta != null || note.isNotEmpty()) {
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (bpmDelta != null) MiniStat("heart", "BPM ${if (bpmDelta > 0) "+" else ""}$bpmDelta")
                    note.forEach { MiniStat(it.icon, it.label) }
                }
            }
        }
    }
}

@Composable
fun MiniStat(icon: String, label: String) {
    val c = EdTheme.colors
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(c.surface2).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdIcon(icon, size = 12, tint = c.textDim)
        Spacer(Modifier.width(5.dp))
        Text(label, color = c.textDim, fontFamily = EdType.mono, fontSize = 11.5.sp)
    }
}

@Composable
fun ChecklistRow(icon: String, label: String, sub: String?, granted: Boolean, onToggle: () -> Unit) {
    val c = EdTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(if (granted) c.primaryTint else c.surface2), contentAlignment = Alignment.Center) {
            EdIcon(icon, size = 22, tint = if (granted) c.primary else c.textFaint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = c.text, fontFamily = EdType.sans, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, color = c.textFaint, fontFamily = EdType.sans, fontSize = 12.5.sp)
        }
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (granted) c.primary else Color.Transparent)
                .then(if (!granted) Modifier.border(2.dp, c.borderStrong, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) EdIcon("check", size = 15, tint = c.onPrimary)
        }
    }
}

/** 카메라 프리뷰 placeholder (대각선 줄무늬 + 스캔 + 옵션 링/플래시) */
@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    round: Boolean = false,
    ratio: Float = 4f / 3f,
    glow: Boolean = false,
    ringColor: Color? = null,
    flash: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val c = EdTheme.colors
    val ring = ringColor ?: c.primaryDim
    val shape = if (round) CircleShape else RoundedCornerShape(Radius)
    var m = modifier
        .then(if (round) Modifier.size(220.dp) else Modifier.fillMaxWidth().aspectRatio(ratio))
        .clip(shape)
        .background(Color(0xFF0B1010))
    m = if (glow) m.border(BorderStroke(3.dp, ring), shape) else m.border(BorderStroke(1.dp, c.border), shape)
    Box(m, contentAlignment = Alignment.Center) {
        // 대각선 줄무늬
        Canvas(Modifier.fillMaxSize()) {
            val stripe = 22.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = Color(0xFF0D1414),
                    start = androidx.compose.ui.geometry.Offset(x, size.height),
                    end = androidx.compose.ui.geometry.Offset(x + size.height, 0f),
                    strokeWidth = stripe / 2,
                )
                x += stripe
            }
        }
        if (flash) {
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(8.dp).clip(CircleShape).background(c.warn))
        }
        content()
    }
}

typealias BoxScope = androidx.compose.foundation.layout.BoxScope

/** BPM에 맞춰 박동하는 하트 */
@Composable
fun HeartBeat(bpm: Int, size: Int = 26, color: Color = EdTheme.colors.alert) {
    val transition = rememberInfiniteTransition(label = "heart")
    val periodMs = (60000 / bpm.coerceAtLeast(40)).coerceIn(400, 1500)
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = periodMs
                1f at 0
                1.28f at (periodMs * 0.12).toInt()
                1f at (periodMs * 0.4).toInt()
                1f at periodMs
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "heartScale",
    )
    Box(Modifier.scale(scale)) { EdIcon("heart", size = size, tint = color) }
}

/** 회전 스피너 */
@Composable
fun Spinner(size: Int = 26, color: Color = EdTheme.colors.primary) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(850, easing = androidx.compose.animation.core.LinearEasing)),
        label = "spinAngle",
    )
    val c = EdTheme.colors
    Canvas(Modifier.size(size.dp)) {
        val sw = 3.dp.toPx()
        drawArc(color = c.track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        drawArc(color = color, startAngle = angle, sweepAngle = 90f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round))
    }
}

/** ECG 악센트 라인 (정적 path) */
@Composable
fun EcgLine(modifier: Modifier = Modifier, color: Color = EdTheme.colors.primary, height: Int = 20) {
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val h = size.height
        val mid = h / 2
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, mid)
            lineTo(size.width * 0.33f, mid)
            lineTo(size.width * 0.36f, mid - h * 0.35f)
            lineTo(size.width * 0.40f, mid + h * 0.45f)
            lineTo(size.width * 0.44f, mid - h * 0.15f)
            lineTo(size.width * 0.48f, mid)
            lineTo(size.width * 0.60f, mid)
            lineTo(size.width * 0.63f, mid - h * 0.45f)
            lineTo(size.width * 0.67f, mid + h * 0.5f)
            lineTo(size.width * 0.71f, mid)
            lineTo(size.width, mid)
        }
        drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}
