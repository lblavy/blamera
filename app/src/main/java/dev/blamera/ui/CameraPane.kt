package dev.blamera.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.blamera.camera.AnalysisPreviewLoader
import dev.blamera.camera.LiveCameraAnalysis
import dev.blamera.camera.LiveResult
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.camera.PreviewStatus
import dev.blamera.camera.runPreviewSession
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer

@Composable
fun CameraPane(
    loader: PreviewProviderLoader,
    analyzer: VisionAnalyzer = VisionAnalyzer { AnalysisResult.Failed }
) {
    val owner = LocalLifecycleOwner.current
    val permission = rememberCameraPermission()
    val access = permission.access
    Column {
        Text("Analyse locale sur demande. Les catégories peuvent être inexactes.")
        CameraPermissionMessage(access.denial)
        if (!access.activated) {
            Button(onClick = permission.activate, enabled = !access.pending) {
                Text(if (access.granted) "Activer la caméra" else "Autoriser la caméra")
            }
        } else {
            CameraSurface(loader, owner, permission.resumed, analyzer, permission.refresh)
        }
    }
}

@Composable
internal fun rememberCameraLifecycle(
    owner: LifecycleOwner,
    access: CameraAccess,
    granted: () -> Boolean
): Boolean {
    var resumed by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner, access) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access.refresh(granted())
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

@Composable
private fun CameraSurface(
    loader: PreviewProviderLoader,
    owner: LifecycleOwner,
    resumed: Boolean,
    analyzer: VisionAnalyzer,
    refreshPermission: () -> Unit
) {
    val context = LocalContext.current
    val view = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            contentDescription = "Aperçu caméra arrière"
        }
    }
    var attempt by remember { mutableIntStateOf(0) }
    val liveState = rememberLiveCamera(view, analyzer, resumed)
    val live = liveState.processor
    val source = remember(loader, live) {
        (loader as? AnalysisPreviewLoader)?.withAnalysis(live) ?: loader
    }
    val status = rememberPreviewStatus(source, owner, view, resumed && liveState.laidOut, attempt)
    LaunchedEffect(status) {
        if (status == PreviewStatus.FAILED) {
            liveState.analyzing = false
            live.setEnabled(false)
        }
    }
    Box(Modifier.fillMaxWidth().height(320.dp)) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        CameraContours(liveState.result)
    }
    Button(onClick = {
        liveState.analyzing = !liveState.analyzing
        live.setEnabled(liveState.analyzing)
    }, enabled = liveState.canToggle(resumed, status)) {
        Text(if (liveState.analyzing) "Arrêter l’analyse" else "Analyser en direct")
    }
    CameraAnalysisMessage(liveState.result, liveState.analyzing)
    Text(if (!resumed) "Caméra en pause." else previewMessage(status))
    if (status == PreviewStatus.FAILED) {
        Button(onClick = {
            liveState.analyzing = false
            live.setEnabled(false)
            refreshPermission()
            attempt++
        }) { Text("Réessayer la caméra") }
    }
}

internal class LiveCameraUi {
    lateinit var processor: LiveCameraAnalysis
    var analyzing by mutableStateOf(false)
    var result by mutableStateOf<LiveResult?>(null)
    var laidOut by mutableStateOf(false)
    var captureFromLive by mutableStateOf(false)

    fun canToggle(resumed: Boolean, status: PreviewStatus): Boolean =
        analyzing || (resumed && status == PreviewStatus.STREAMING)

    fun stop() {
        captureFromLive = false
        analyzing = false
        processor.setEnabled(false)
    }

    fun toggle() {
        analyzing = !analyzing
        processor.setEnabled(analyzing)
    }

    fun prepareCapture() {
        val enabled = analyzing
        stop()
        captureFromLive = enabled
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
@Composable
internal fun rememberLiveCamera(
    view: PreviewView,
    analyzer: VisionAnalyzer,
    resumed: Boolean,
    target: () -> androidx.camera.view.transform.OutputTransform? = { view.outputTransform }
): LiveCameraUi {
    val scope = rememberCoroutineScope()
    val currentTarget by rememberUpdatedState(target)
    val state = remember(view, analyzer) {
        LiveCameraUi().apply {
            processor =
                LiveCameraAnalysis(analyzer, scope, { currentTarget() }, { result = it })
                    .also { it.setEnabled(false) }
        }
    }
    DisposableEffect(view, state) {
        val listener = android.view.View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            state.laidOut = r > l && b > t
            if (r - l != or - ol || b - t != ob - ot) state.processor.invalidate()
        }
        view.addOnLayoutChangeListener(listener)
        onDispose {
            view.removeOnLayoutChangeListener(listener)
            state.processor.close()
        }
    }
    LaunchedEffect(resumed) {
        if (!resumed) {
            state.stop()
        }
    }
    return state
}

@Composable
private fun rememberPreviewStatus(
    loader: PreviewProviderLoader,
    owner: LifecycleOwner,
    view: PreviewView,
    resumed: Boolean,
    attempt: Int
): PreviewStatus {
    var status by remember { mutableStateOf(PreviewStatus.CONNECTING) }
    LaunchedEffect(loader, owner, resumed, attempt) {
        status = PreviewStatus.CONNECTING
        if (!resumed) return@LaunchedEffect
        if (!previewAttempt {
                runPreviewSession(loader, owner, view) { status = it }
            }
        ) {
            status = PreviewStatus.FAILED
        }
    }
    return status
}

internal fun previewMessage(status: PreviewStatus) = when (status) {
    PreviewStatus.CONNECTING -> "Ouverture de la caméra…"
    PreviewStatus.STREAMING -> "Aperçu caméra actif."
    PreviewStatus.FAILED -> "Caméra indisponible ou occupée. Réessayez ou utilisez Image."
}

@Composable
internal fun CameraPermissionMessage(denial: CameraDenial) {
    when (denial) {
        CameraDenial.NONE -> Unit
        CameraDenial.DENIED -> Text(
            "Autorisation caméra refusée. Vous pouvez réessayer ou utiliser Image."
        )
        CameraDenial.BLOCKED -> Text(
            "Autorisation caméra bloquée. Utilisez Image ou réactivez-la manuellement dans les réglages Android."
        )
        CameraDenial.REVOKED -> Text(
            "Autorisation caméra retirée. L’image importée reste disponible."
        )
    }
}
