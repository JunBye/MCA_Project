package com.example.mca_project.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 앱 전역 테마 상태. 사용자 요청에 따라 기본은 Light + Indigo.
 * dark/accent 토글은 Home 화면에서 노출한다.
 */
class ThemeController {
    var dark by mutableStateOf(false)        // 기본 light
    var accent by mutableStateOf(Accent.Indigo) // 기본 indigo

    fun toggleDark() { dark = !dark }
    fun chooseAccent(a: Accent) { accent = a }
}

val LocalThemeController = staticCompositionLocalOf { ThemeController() }
