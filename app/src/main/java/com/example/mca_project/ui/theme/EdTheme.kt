package com.example.mca_project.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Emotion Detector 디자인 시스템 색 토큰 (Claude Design 핸드오프 index.html 기반).
 * dark/light 테마 × teal/indigo accent 조합. 빨강(alert)은 deception/Stop 전용, genuine=green.
 */
@Immutable
data class EdColors(
    val backdrop: Color,
    val bg: Color,
    val bgElev: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val borderStrong: Color,
    val track: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    // accent (teal/indigo에 따라 달라짐)
    val primary: Color,
    val primaryDim: Color,
    val primaryTint: Color,
    val onPrimary: Color,
    // semantic (테마 고정)
    val alert: Color,
    val alertDim: Color,
    val alertEdge: Color,
    val genuine: Color,
    val genuineDim: Color,
    val warn: Color,
    val warnDim: Color,
    val warnEdge: Color,
    val isDark: Boolean,
)

enum class Accent { Teal, Indigo }

// ── accent 값 (app.jsx ACCENTS) ──
private data class AccentTokens(val p: Color, val dim: Color, val tint: Color, val on: Color)

private val tealDark = AccentTokens(Color(0xFF1FBCAB), Color(0xFF13897B), Color(0x261FBCAB), Color(0xFF042B27))
private val tealLight = AccentTokens(Color(0xFF0C8074), Color(0xFF0A6E63), Color(0x1F0C8074), Color(0xFFFFFFFF))
private val indigoDark = AccentTokens(Color(0xFF7B8CFF), Color(0xFF5563D8), Color(0x297B8CFF), Color(0xFF0A1033))
private val indigoLight = AccentTokens(Color(0xFF4A57D6), Color(0xFF3A46B8), Color(0x1F4A57D6), Color(0xFFFFFFFF))

private fun accentTokens(accent: Accent, dark: Boolean): AccentTokens = when (accent) {
    Accent.Teal -> if (dark) tealDark else tealLight
    Accent.Indigo -> if (dark) indigoDark else indigoLight
}

fun edColors(dark: Boolean, accent: Accent): EdColors {
    val a = accentTokens(accent, dark)
    return if (dark) {
        EdColors(
            backdrop = Color(0xFF080D0D),
            bg = Color(0xFF0C1413), bgElev = Color(0xFF0F1A18),
            surface = Color(0xFF15211F), surface2 = Color(0xFF1B2926),
            border = Color(0x12FFFFFF), borderStrong = Color(0x29FFFFFF),
            track = Color(0x14FFFFFF),
            text = Color(0xFFE8EFEC), textDim = Color(0xFF93A39E), textFaint = Color(0xFF62736E),
            primary = a.p, primaryDim = a.dim, primaryTint = a.tint, onPrimary = a.on,
            alert = Color(0xFFFF5D5D), alertDim = Color(0x24FF5D5D), alertEdge = Color(0x66FF5D5D),
            genuine = Color(0xFF2BC79A), genuineDim = Color(0x262BC79A),
            warn = Color(0xFFECB44A), warnDim = Color(0x24ECB44A), warnEdge = Color(0x59ECB44A),
            isDark = true,
        )
    } else {
        EdColors(
            backdrop = Color(0xFFD3DDDA),
            bg = Color(0xFFEEF4F2), bgElev = Color(0xFFF6FAF9),
            surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF3F1),
            border = Color(0x170A2824), borderStrong = Color(0x380A2824),
            track = Color(0x1A0A2824),
            text = Color(0xFF0D1C1A), textDim = Color(0xFF4D615C), textFaint = Color(0xFF889993),
            primary = a.p, primaryDim = a.dim, primaryTint = a.tint, onPrimary = a.on,
            alert = Color(0xFFE23B3B), alertDim = Color(0x1AE23B3B), alertEdge = Color(0x52E23B3B),
            genuine = Color(0xFF0F9C73), genuineDim = Color(0x1F0F9C73),
            warn = Color(0xFFC2891B), warnDim = Color(0x1FC2891B), warnEdge = Color(0x4DC2891B),
            isDark = false,
        )
    }
}

val LocalEdColors = staticCompositionLocalOf { edColors(dark = false, accent = Accent.Indigo) }

/** 어디서든 EdTheme.colors 로 토큰 접근 */
object EdTheme {
    val colors: EdColors
        @Composable get() = LocalEdColors.current
}

@Composable
fun EdThemeProvider(dark: Boolean, accent: Accent, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEdColors provides edColors(dark, accent), content = content)
}
