package com.example.mca_project.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 디자인(icons.jsx)의 라인 아이콘 name을 Material outlined 아이콘으로 매핑.
 */
private fun iconFor(name: String): ImageVector = when (name) {
    "chevron-right" -> Icons.Outlined.ChevronRight
    "arrow-left" -> Icons.AutoMirrored.Outlined.ArrowBack
    "arrow-right" -> Icons.AutoMirrored.Outlined.ArrowForward
    "x" -> Icons.Outlined.Close
    "check" -> Icons.Outlined.Check
    "check-circle" -> Icons.Outlined.CheckCircle
    "heart" -> Icons.Filled.Favorite
    "heart-outline" -> Icons.Outlined.Favorite
    "mic" -> Icons.Outlined.Mic
    "camera" -> Icons.Outlined.CameraAlt
    "zap" -> Icons.Outlined.Bolt
    "activity" -> Icons.Outlined.MonitorHeart
    "waveform" -> Icons.Outlined.GraphicEq
    "face" -> Icons.Outlined.SentimentNeutral
    "scan" -> Icons.Outlined.CropFree
    "briefcase" -> Icons.Outlined.BusinessCenter
    "glass" -> Icons.Outlined.LocalBar
    "alert-triangle" -> Icons.Outlined.WarningAmber
    "square" -> Icons.Outlined.Stop
    "clock" -> Icons.Outlined.AccessTime
    "calendar" -> Icons.Outlined.CalendarMonth
    "list" -> Icons.AutoMirrored.Outlined.List
    "layers" -> Icons.Outlined.Layers
    "refresh" -> Icons.Outlined.Refresh
    "home" -> Icons.Outlined.Home
    "shield" -> Icons.Outlined.Shield
    "trend-up" -> Icons.AutoMirrored.Outlined.TrendingUp
    "fingerprint" -> Icons.Outlined.Fingerprint
    "auto-graph" -> Icons.Outlined.AutoGraph
    else -> Icons.Outlined.CropFree
}

@Composable
fun EdIcon(
    name: String,
    size: Int = 22,
    tint: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Icon(
        imageVector = iconFor(name),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size.dp),
    )
}
