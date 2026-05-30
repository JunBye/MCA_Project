package com.example.mca_project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mca_project.ui.theme.EdTheme

/**
 * 공용 화면 스캐폴드 — 스크롤 본문 + 옵션 고정 footer.
 */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    pad: Int = 20,
    center: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = EdTheme.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        if (center) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = pad.dp), contentAlignment = Alignment.Center) {
                content()
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = pad.dp, end = pad.dp, top = 12.dp, bottom = if (footer != null) 8.dp else 22.dp),
                verticalArrangement = Arrangement.Top,
            ) { content() }
        }
        if (footer != null) {
            Column(
                Modifier.fillMaxWidth().background(c.bgElev).padding(horizontal = pad.dp, vertical = 12.dp),
            ) { footer() }
        }
    }
}
