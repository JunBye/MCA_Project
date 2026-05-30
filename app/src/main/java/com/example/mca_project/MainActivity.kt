package com.example.mca_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.mca_project.ui.navigation.AppNavHost
import com.example.mca_project.ui.theme.EdThemeProvider
import com.example.mca_project.ui.theme.EdTheme
import com.example.mca_project.ui.theme.LocalThemeController
import com.example.mca_project.ui.theme.ThemeController
import dagger.hilt.android.AndroidEntryPoint

/**
 * 단일 Activity. Compose Navigation + 디자인 시스템 테마(light+indigo 기본).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeController = remember { ThemeController() }
            CompositionLocalProvider(LocalThemeController provides themeController) {
                EdThemeProvider(dark = themeController.dark, accent = themeController.accent) {
                    val c = EdTheme.colors
                    Box(
                        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        AppNavHost()
                    }
                }
            }
        }
    }
}
