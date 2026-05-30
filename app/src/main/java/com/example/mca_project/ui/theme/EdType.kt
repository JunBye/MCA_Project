package com.example.mca_project.ui.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * 디자인은 IBM Plex Sans(본문) + IBM Plex Mono(라이브 메트릭)를 쓴다.
 * 폰트 파일 번들 전까지는 시스템 Sans/Mono로 폴백 — 메트릭의 고정폭 느낌은 유지된다.
 * TODO(font): res/font/ 에 IBM Plex Sans/Mono ttf 추가 후 FontFamily 교체.
 */
object EdType {
    val sans: FontFamily = FontFamily.SansSerif
    val mono: FontFamily = FontFamily.Monospace
}
