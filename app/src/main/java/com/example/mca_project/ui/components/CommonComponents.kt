package com.example.mca_project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mca_project.ui.theme.EdTheme
import com.example.mca_project.ui.theme.EdType

private val Radius = 16.dp

// ── Card ──
@Composable
fun EdCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    background: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = EdTheme.colors
    var m = modifier
        .clip(RoundedCornerShape(Radius))
        .background(background ?: c.surface)
        .border(BorderStroke(1.dp, borderColor ?: c.border), RoundedCornerShape(Radius))
    if (onClick != null) m = m.clickable { onClick() }
    Box(m) { content() }
}

enum class EdBtn { Filled, Danger, Outlined, Text, Ghost }

@Composable
fun EdButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: EdBtn = EdBtn.Filled,
    icon: String? = null,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
) {
    val c = EdTheme.colors
    val (bg, fg, border) = when (variant) {
        EdBtn.Filled -> Triple(c.primary, c.onPrimary, null)
        EdBtn.Danger -> Triple(c.alert, Color.White, null)
        EdBtn.Outlined -> Triple(Color.Transparent, c.primary, c.primary)
        EdBtn.Text -> Triple(Color.Transparent, c.primary, null)
        EdBtn.Ghost -> Triple(c.surface2, c.text, null)
    }
    val height = if (variant == EdBtn.Text) 44.dp else 54.dp
    var m = modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .height(height)
        .clip(RoundedCornerShape(27.dp))
        .background(if (enabled) bg else bg.copy(alpha = 0.4f))
    if (border != null) m = m.border(BorderStroke(1.5.dp, border), RoundedCornerShape(27.dp))
    if (enabled) m = m.clickable { onClick() }
    Row(
        modifier = m,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (enabled) fg else fg.copy(alpha = 0.5f)
        if (icon != null) {
            EdIcon(icon, size = 20, tint = content)
            Spacer(Modifier.width(9.dp))
        }
        Text(text, color = content, fontFamily = EdType.sans, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppHeader(title: String, onBack: (() -> Unit)? = null, step: String? = null, modifier: Modifier = Modifier) {
    val c = EdTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { EdIcon("arrow-left", size = 22, tint = c.text) }
            Spacer(Modifier.width(8.dp))
        } else Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontFamily = EdType.sans, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            if (step != null) Text(step, color = c.textDim, fontFamily = EdType.mono, fontSize = 12.5.sp)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = EdTheme.colors.textFaint,
        fontFamily = EdType.sans,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = modifier.padding(horizontal = 2.dp, vertical = 4.dp),
    )
}

enum class ChipTone { Genuine, Fake, Neutral, Warn }

@Composable
fun EdChip(tone: ChipTone, text: String, icon: String? = null) {
    val c = EdTheme.colors
    val (fg, bg) = when (tone) {
        ChipTone.Genuine -> c.genuine to c.genuineDim
        ChipTone.Fake -> c.alert to c.alertDim
        ChipTone.Neutral -> c.textDim to c.surface2
        ChipTone.Warn -> c.warn to c.warnDim
    }
    Row(
        modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(bg).padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { EdIcon(icon, size = 13, tint = fg); Spacer(Modifier.width(5.dp)) }
        Text(text, color = fg, fontFamily = EdType.sans, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

enum class GaugeTone { Primary, Alert, Genuine, Warn, Dim }

@Composable
fun LabeledGauge(label: String, value: Float, tone: GaugeTone = GaugeTone.Primary, sub: String? = null, big: Boolean = false) {
    val c = EdTheme.colors
    val color = when (tone) {
        GaugeTone.Primary -> c.primary
        GaugeTone.Alert -> c.alert
        GaugeTone.Genuine -> c.genuine
        GaugeTone.Warn -> c.warn
        GaugeTone.Dim -> c.textDim
    }
    val anim by animateFloatAsState(value.coerceIn(0f, 100f), label = "gauge")
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c.textDim, fontFamily = EdType.sans, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${value.toInt()}",
                    color = if (tone == GaugeTone.Alert) color else c.text,
                    fontFamily = EdType.mono, fontWeight = FontWeight.SemiBold,
                    fontSize = if (big) 30.sp else 17.sp,
                )
                Text("%", color = (if (tone == GaugeTone.Alert) color else c.text).copy(alpha = 0.55f), fontFamily = EdType.mono, fontSize = if (big) 16.sp else 12.sp)
            }
        }
        Box(
            Modifier.fillMaxWidth().height(if (big) 12.dp else 8.dp).clip(RoundedCornerShape(100.dp)).background(c.track),
        ) {
            Box(Modifier.fillMaxWidth(anim / 100f).height(if (big) 12.dp else 8.dp).clip(RoundedCornerShape(100.dp)).background(color))
        }
        if (sub != null) Text(sub, color = c.textFaint, fontFamily = EdType.mono, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun MetricRow(label: String, value: String, unit: String? = null, sub: String? = null, accent: Boolean = false, big: Boolean = false, leading: (@Composable () -> Unit)? = null, icon: String? = null) {
    val c = EdTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(if (accent) c.alertDim else c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (leading != null) leading() else EdIcon(icon ?: "activity", size = 22, tint = if (accent) c.alert else c.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = c.textDim, fontFamily = EdType.sans, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, color = c.textFaint, fontFamily = EdType.sans, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = if (accent) c.alert else c.text, fontFamily = EdType.mono, fontWeight = FontWeight.SemiBold, fontSize = if (big) 30.sp else 22.sp)
            if (unit != null) { Spacer(Modifier.width(3.dp)); Text(unit, color = c.textFaint, fontFamily = EdType.mono, fontSize = 12.5.sp) }
        }
    }
}

@Composable
fun WarningBanner(show: Boolean, text: String, onDismiss: (() -> Unit)? = null) {
    if (!show) return
    val c = EdTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(12.dp))
            .background(c.warnDim).border(BorderStroke(1.dp, c.warnEdge), RoundedCornerShape(12.dp))
            .height(48.dp).padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdIcon("alert-triangle", size = 18, tint = c.warn)
        Spacer(Modifier.width(10.dp))
        Text(text, color = c.warn, fontFamily = EdType.sans, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (onDismiss != null) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                EdIcon("x", size = 16, tint = c.warn)
            }
        }
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(EdTheme.colors.border))
}
