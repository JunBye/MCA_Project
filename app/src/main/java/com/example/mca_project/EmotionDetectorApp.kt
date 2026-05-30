package com.example.mca_project

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt 의존성 주입 컨테이너의 진입점.
 * 이 클래스에 @HiltAndroidApp을 붙여야 앱 전역 DI 그래프가 생성된다.
 */
@HiltAndroidApp
class EmotionDetectorApp : Application()