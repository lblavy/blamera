package dev.blamera.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.blamera.R
import dev.blamera.camera.AnalysisPreviewLoader
import dev.blamera.camera.PhotoCaptureProvider
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.camera.PreviewStatus

@Composable
internal fun CaptureCameraPane(
    loader: PreviewProviderLoader,
    loading: Boolean,
    live: LiveCameraConfig = LiveCameraConfig.UNAVAILABLE,
    onCapture: (PhotoCaptureProvider) -> Unit
) {
    val permission = rememberCameraPermission(activateIfGranted = true)
    val access = permission.access
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (access.activated) {
                CaptureSurface(
                    loader,
                    permission.resumed,
                    loading,
                    onCapture,
                    live
                )
            } else {
                Column {
                    Text("Cadrez, puis capturez une image pour l’analyser.")
                    PhotoActionButton(
                        if (access.granted) "Activer la caméra" else "Autoriser la caméra",
                        R.drawable.ic_capture,
                        permission.activate,
                        enabled = !access.pending
                    )
                    CameraPermissionMessage(access.denial)
                    PhotoActionButton("Capturer", R.drawable.ic_capture, {}, enabled = false)
                }
            }
        }
    }
}

@Composable
private fun CaptureSurface(
    loader: PreviewProviderLoader,
    resumed: Boolean,
    loading: Boolean,
    onCapture: (PhotoCaptureProvider) -> Unit,
    config: LiveCameraConfig
) {
    val context = LocalContext.current
    val view = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            contentDescription = "Aperçu caméra arrière"
        }
    }
    val live = rememberLiveCamera(view, config.analyzer, resumed) { config.target(view) }
    val attachLive = live.analyzing || (loading && live.captureFromLive)
    val source = remember(loader, live.processor, attachLive) {
        if (attachLive) {
            (loader as? AnalysisPreviewLoader)?.withAnalysis(live.processor) ?: loader
        } else {
            loader
        }
    }
    val binding = rememberCaptureBinding(source, view, resumed)
    LaunchedEffect(binding.status) {
        if (binding.status == PreviewStatus.FAILED) live.stop()
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
            CameraContours(live.result)
        }
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            CameraAnalysisMessage(live.result, live.analyzing, compact = true)
        }
        Text(binding.message(resumed, loading))
        CaptureButtons(
            binding,
            live,
            onCapture,
            binding.canCapture(resumed, loading),
            !loading && live.canToggle(resumed, binding.status)
        )
    }
}

@Composable
private fun CaptureButtons(
    binding: CapturePreviewBinding,
    live: LiveCameraUi,
    onCapture: (PhotoCaptureProvider) -> Unit,
    canCapture: Boolean,
    canToggle: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoActionButton(
            "Capturer",
            R.drawable.ic_capture,
            {
                live.prepareCapture()
                binding.source?.let(onCapture)
            },
            enabled = canCapture
        )
    }
    PhotoActionButton(
        if (live.analyzing) "Arrêter l’analyse" else "Analyse en direct",
        R.drawable.ic_live,
        live::toggle,
        enabled = canToggle
    )
    if (binding.status == PreviewStatus.FAILED) {
        PhotoActionButton("Réessayer la caméra", R.drawable.ic_retry, { binding.attempt++ })
    }
}
