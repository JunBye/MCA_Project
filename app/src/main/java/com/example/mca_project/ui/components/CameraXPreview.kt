package com.example.mca_project.ui.components

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mca_project.ui.theme.EdTheme
import java.util.concurrent.ExecutorService
import androidx.camera.core.ImageProxy

@Composable
fun CameraXPreview(
    modifier: Modifier = Modifier,
    analysisExecutor: ExecutorService,
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    torchEnabled: Boolean = false,
    round: Boolean = false,
    glow: Boolean = false,
    ringColor: Color? = null,
    onFrame: (ImageProxy) -> Unit,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    val c = EdTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val frameHandler by rememberUpdatedState(onFrame)
    val shape = if (round) CircleShape else RoundedCornerShape(16.dp)
    val borderColor = ringColor ?: c.primaryDim
    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(lifecycleOwner, lensFacing, torchEnabled, previewView) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(analysisExecutor) { image ->
                        try {
                            frameHandler(image)
                        } finally {
                            image.close()
                        }
                    }
                }

            runCatching {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    preview,
                    analysis,
                )
                camera.cameraControl.enableTorch(torchEnabled)
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    Box(
        modifier
            .then(if (round) Modifier.size(220.dp) else Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            .clip(shape)
            .background(Color(0xFF0B1010))
            .border(if (glow) 3.dp else 1.dp, if (glow) borderColor else c.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        overlay()
    }
}
