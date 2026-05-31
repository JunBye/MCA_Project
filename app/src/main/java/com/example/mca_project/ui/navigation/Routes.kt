package com.example.mca_project.ui.navigation

/**
 * 앱 내 네비게이션 경로.
 * 설계문서의 Activity/Fragment 구조를 Compose 단일 NavHost의 route로 매핑한다.
 *
 *  Splash → Home ┬→ Interview(Setup→Measuring→Processing) ┐
 *                └→ BlindDate(Setup→PpgLock→Measuring      ├→ Result → (재시작/홈)
 *                              →Processing)                 ┘
 *                └→ History → Result
 */
object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"

    // 시나리오 1: Job Interview (Voice + Face)
    const val INTERVIEW_SETUP = "interview/setup"
    const val INTERVIEW_MEASURING = "interview/measuring"
    const val INTERVIEW_PROCESSING = "interview/processing"

    // 시나리오 2: Blind Date (Voice + PPG)
    const val BLIND_DATE_SETUP = "blinddate/setup"
    const val BLIND_DATE_PPG_LOCK = "blinddate/ppglock"
    const val BLIND_DATE_MEASURING = "blinddate/measuring"
    const val BLIND_DATE_PROCESSING = "blinddate/processing"

    // 공통
    const val RESULT = "result/{sessionId}"
    const val HISTORY = "history"

    fun result(sessionId: String) = "result/$sessionId"
}
