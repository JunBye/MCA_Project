package com.example.mca_project.ui.blinddate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mca_project.ui.components.AppHeader
import com.example.mca_project.ui.components.CameraXPreview
import com.example.mca_project.ui.components.CardMeta
import com.example.mca_project.ui.components.ChecklistRow
import com.example.mca_project.ui.components.ChipTone
import com.example.mca_project.ui.components.Divider
import com.example.mca_project.ui.components.EdBtn
import com.example.mca_project.ui.components.EdButton
import com.example.mca_project.ui.components.EdCard
import com.example.mca_project.ui.components.EdChip
import com.example.mca_project.ui.components.EdIcon
import com.example.mca_project.ui.components.HeartBeat
import com.example.mca_project.ui.components.ResultCard
import com.example.mca_project.ui.components.Screen
import com.example.mca_project.ui.components.SectionLabel
import com.example.mca_project.ui.components.Spinner
import com.example.mca_project.ui.interview.ProcessingBody
import com.example.mca_project.ui.theme.EdTheme
import com.example.mca_project.ui.theme.EdType
import androidx.core.content.ContextCompat

/** 4-1 Setup */
@Composable
fun BlindDateSetupScreen(onNext: () -> Unit) {
    val c = EdTheme.colors
    val context = LocalContext.current
    val permissions = remember {
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }
    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var permissionMap by remember { mutableStateOf(permissions.associateWith(::isGranted)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionMap = permissions.associateWith { permission ->
            result[permission] ?: isGranted(permission)
        }
    }
    val allGranted = permissionMap.values.all { it }

    LaunchedEffect(Unit) {
        permissionMap = permissions.associateWith(::isGranted)
        if (!allGranted) launcher.launch(permissions.toTypedArray())
    }

    Screen(footer = { EdButton("Next", onNext, icon = "arrow-right", enabled = allGranted) }) {
        AppHeader("Blind Date", step = "Setup · per-utterance")
        Text(
            "We'll lock onto their pulse through the camera, then analyze each answer they give.",
            color = c.textDim, fontFamily = EdType.sans, fontSize = 14.5.sp, modifier = Modifier.padding(horizontal = 2.dp).padding(bottom = 20.dp),
        )
        SectionLabel("Permissions required")
        EdCard {
            Column {
                ChecklistRow("mic", "Microphone", "Captures each spoken answer", permissionMap[Manifest.permission.RECORD_AUDIO] == true) {
                    launcher.launch(permissions.toTypedArray())
                }
                Divider()
                ChecklistRow("camera", "Camera (rear + flash)", "Reads pulse via fingertip PPG", permissionMap[Manifest.permission.CAMERA] == true) {
                    launcher.launch(permissions.toTypedArray())
                }
            }
        }
        Spacer(Modifier.size(16.dp))
        EdCard(background = c.surface2, borderColor = c.surface2) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                EdIcon("camera", size = 20, tint = c.primary)
                Spacer(Modifier.width(12.dp))
                Text("In the next step, ask them to rest a fingertip over the rear camera and flash.", color = c.textDim, fontFamily = EdType.sans, fontSize = 13.5.sp)
            }
        }
    }
}

/** 4-2 PPG Lock */
@Composable
fun BlindDatePpgLockScreen(viewModel: BlindDateViewModel, onNext: () -> Unit) {
    val c = EdTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locked = state.ppgLocked
    LaunchedEffect(Unit) { viewModel.resetPpgLock() }
    Screen(footer = { EdButton("Next", onNext, icon = "arrow-right", enabled = locked) }) {
        AppHeader("Pulse lock", step = "Blind Date · PPG", onBack = null)
        Text(
            "Ask the other person to place a fingertip gently over the rear camera.",
            color = c.textDim, fontFamily = EdType.sans, fontSize = 14.5.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).padding(bottom = 30.dp),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CameraXPreview(
                analysisExecutor = viewModel.analysisExecutor,
                lensFacing = CameraSelector.LENS_FACING_BACK,
                torchEnabled = true,
                round = true,
                glow = true,
                ringColor = if (locked) c.genuine else c.primaryDim,
                onFrame = viewModel::onPpgFrame,
            ) {
                if (locked) HeartBeat(bpm = 72, size = 56, color = c.genuine)
                else EdIcon("fingerprint", size = 64, tint = c.textFaint)
            }
        }
        Spacer(Modifier.size(34.dp))
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            if (locked) {
                Row(
                    Modifier.clip(RoundedCornerShape(100.dp)).background(c.genuineDim).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EdIcon("check-circle", size = 20, tint = c.genuine)
                    Spacer(Modifier.width(9.dp))
                    Text("PPG locked · BPM ${state.lockedBpm?.toInt() ?: 72}", color = c.genuine, fontFamily = EdType.sans, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spinner(size = 18, color = c.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Searching for pulse…", color = c.textDim, fontFamily = EdType.mono, fontSize = 14.sp)
                    Spacer(Modifier.width(14.dp))
                    EdButton("Lock anyway", { viewModel.lockPpg() }, variant = EdBtn.Ghost, fillWidth = false)
                }
            }
        }
    }
}

/** 4-3 Calibration */
@Composable
fun BlindDateCalibrationScreen(viewModel: BlindDateViewModel, onDone: () -> Unit) {
    val c = EdTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val index = state.calibrationIndex
    if (index >= CALIBRATION_QUESTIONS.size) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val last = index == CALIBRATION_QUESTIONS.lastIndex
    Screen(footer = {
        EdButton(if (last) "Start measuring" else "Next question", { viewModel.nextCalibrationQuestion() }, icon = if (last) "activity" else "arrow-right")
    }) {
        AppHeader("Baseline calibration", step = "Measurement ${index + 1} / ${CALIBRATION_QUESTIONS.size}")
        Text(
            "Ask a neutral question to establish their honest baseline.",
            color = c.textDim, fontFamily = EdType.sans, fontSize = 14.5.sp, modifier = Modifier.padding(horizontal = 2.dp).padding(bottom = 24.dp),
        )
        // 진행 바
        Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CALIBRATION_QUESTIONS.indices.forEach { j ->
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (j <= index) c.primary else c.track))
            }
        }
        EdCard {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 30.dp)) {
                Text("“", color = c.primary.copy(alpha = 0.35f), fontFamily = EdType.sans, fontSize = 52.sp)
                Text(CALIBRATION_QUESTIONS[index], color = c.text, fontFamily = EdType.sans, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeartBeat(bpm = state.lockedBpm?.toInt() ?: 72, size = 18, color = c.genuine)
                    Spacer(Modifier.width(8.dp))
                    Text("baseline BPM ${state.lockedBpm?.toInt() ?: 72} · voice nominal", color = c.textFaint, fontFamily = EdType.mono, fontSize = 12.5.sp)
                }
            }
        }
    }
}

/** 4-4 Measuring */
@Composable
fun BlindDateMeasuringScreen(viewModel: BlindDateViewModel, onStop: () -> Unit) {
    val c = EdTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.startMeasuring() }
    val bpm = state.lockedBpm?.toInt() ?: 72

    Screen(pad = 18, footer = { EdButton("Stop analysis", onStop, variant = EdBtn.Danger, icon = "square") }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(c.alert))
            Spacer(Modifier.width(9.dp))
            Text("LISTENING", color = c.text, fontFamily = EdType.mono, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Blind Date", color = c.textFaint, fontFamily = EdType.mono, fontSize = 12.5.sp)
        }

        EdCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                HeartBeat(bpm = bpm, size = 30, color = c.genuine)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("CURRENT BPM", color = c.textFaint, fontFamily = EdType.sans, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                    Text("$bpm", color = c.text, fontFamily = EdType.mono, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                }
                EdChip(ChipTone.Genuine, "PPG locked", icon = "activity")
            }
        }

        Spacer(Modifier.size(14.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CameraXPreview(
                analysisExecutor = viewModel.analysisExecutor,
                lensFacing = CameraSelector.LENS_FACING_BACK,
                torchEnabled = true,
                round = true,
                glow = true,
                ringColor = c.genuine,
                onFrame = viewModel::onPpgFrame,
            ) {
                HeartBeat(bpm = bpm, size = 46, color = c.genuine)
            }
        }

        Spacer(Modifier.size(16.dp))
        SectionLabel("Answers analyzed · ${state.cards.size}")
        Spacer(Modifier.size(12.dp))
        if (state.cards.isEmpty()) {
            // 모델 미연동 안내
            Row(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                EdIcon("mic", size = 18, tint = c.textFaint)
                Spacer(Modifier.width(10.dp))
                Text(state.notReadyMessage ?: "waiting for their first answer…", color = c.textFaint, fontFamily = EdType.mono, fontSize = 13.5.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                state.cards.forEach { card ->
                    val fake = card.inference.fakeProbability > 0.5f
                    val top2 = card.inference.topEmotions.joinToString(" / ") {
                        "${it.label} ${(it.probability * 100).toInt()}%"
                    }
                    ResultCard(
                        index = "#${card.index}", title = "Answer #${card.index}",
                        fake = fake, confidence = ((if (fake) card.inference.fakeProbability else 1 - card.inference.fakeProbability) * 100).toInt(),
                        emotion = top2.ifBlank { card.inference.emotion },
                        bpmDelta = card.inference.bpm?.toInt(),
                    )
                }
            }
        }
    }
}

/** 4-5 Processing */
@Composable
fun BlindDateProcessingScreen(viewModel: BlindDateViewModel, onDone: (sessionId: String) -> Unit) {
    LaunchedEffect(Unit) { viewModel.stopMeasuring(onDone) }
    ProcessingBody(mode = "voice · PPG")
}
