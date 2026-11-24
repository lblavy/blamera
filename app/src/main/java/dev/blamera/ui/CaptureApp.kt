package dev.blamera.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.blamera.BuildConfig
import dev.blamera.R
import dev.blamera.benchmark.BenchmarkActivity
import dev.blamera.camera.CameraXCaptureLoader
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.chatgpt.ui.ChatGptTrialActivity
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import dev.blamera.image.AndroidPhotoLoader
import dev.blamera.image.PhotoLoader
import dev.blamera.information.AndroidSearchImagePreparer
import dev.blamera.information.ObjectInformationTools
import dev.blamera.information.SharedSearchImageStore
import dev.blamera.information.createImageShareSender
import dev.blamera.vision.VisionAnalyzer
import dev.blamera.vision.categoryLabel
import dev.blamera.vision.createLiveAnalyzer
import dev.blamera.vision.createPhotoAnalyzer
import dev.blamera.vision.createPhotoCategoryLabels

@Composable
internal fun BlameraApp(
    photoLoader: PhotoLoader? = null,
    visionAnalyzer: VisionAnalyzer? = null,
    cameraLoader: PreviewProviderLoader? = null,
    liveAnalyzer: VisionAnalyzer? = null,
    chatGpt: ChatGptTrialViewModel? = null
) {
    val context = LocalContext.current.applicationContext
    val loader = remember(photoLoader, context) {
        photoLoader ?: AndroidPhotoLoader(context.contentResolver::openInputStream)
    }
    val analyzer = remember(visionAnalyzer, context) {
        visionAnalyzer
            ?: createPhotoAnalyzer(context)
    }
    val labels = remember(visionAnalyzer, context) {
        if (visionAnalyzer != null) ::categoryLabel else createPhotoCategoryLabels(context)
    }
    val camera = remember(cameraLoader, context) { cameraLoader ?: CameraXCaptureLoader(context) }
    val live = remember(liveAnalyzer, context) {
        LiveCameraConfig(liveAnalyzer ?: createLiveAnalyzer(context))
    }
    val session = rememberCaptureSession()
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) session.import(uri, loader, analyzer)
    }
    val pick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    BackHandler(enabled = session.screen != CaptureScreen.CAMERA) {
        session.navigate(CaptureScreen.CAMERA)
    }
    MaterialTheme {
        CaptureHome(session, CaptureTools(camera, analyzer, labels, live, chatGpt = chatGpt), pick)
    }
}

@Composable
private fun rememberCaptureSession(): CaptureSession {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    return remember(activity) {
        (activity as? ViewModelStoreOwner)?.let {
            ViewModelProvider(it)[CaptureHolder::class.java].session
        } ?: CaptureSession(scope)
    }
}

@Composable
internal fun CaptureHome(session: CaptureSession, tools: CaptureTools, pick: () -> Unit) {
    ObserveCaptureLifecycle(session)
    val context = LocalContext.current.applicationContext
    val information = remember(tools.information, context) {
        tools.information ?: ObjectInformationTools(
            AndroidSearchImagePreparer(),
            SharedSearchImageStore(context),
            createImageShareSender(context)
        )
    }
    BindObjectChatGpt(session, tools.chatGpt)
    LaunchedEffect(information) { information.publisher.cleanup() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CaptureHeader(session, pick)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (session.screen) {
                    CaptureScreen.CAMERA -> CaptureCameraPane(
                        tools.camera,
                        session.loading,
                        tools.live
                    ) { source ->
                        session.capture(source::capture, tools.analyzer)
                    }
                    CaptureScreen.PHOTO -> PhotoResults(
                        session,
                        tools.labels,
                        PhotoActions(
                            retry = { session.retry(tools.analyzer) },
                            region = { session.analyzeVisible(tools.analyzer) },
                            original = session::restoreOriginal
                        ),
                        information,
                        tools.chatGpt
                    )
                }
            }
            session.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ObserveCaptureLifecycle(session: CaptureSession) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current
    DisposableEffect(session, lifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) session.onForeground()
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) {
                session.onBackground()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

internal data class CaptureTools(
    val camera: PreviewProviderLoader,
    val analyzer: VisionAnalyzer,
    val labels: (Int) -> String,
    val live: LiveCameraConfig = LiveCameraConfig.UNAVAILABLE,
    val information: ObjectInformationTools? = null,
    val chatGpt: ChatGptTrialViewModel? = null
)

@Composable
private fun BindObjectChatGpt(session: CaptureSession, model: ChatGptTrialViewModel?) {
    SideEffect {
        model?.objects?.let { objects ->
            // The retained capture session keeps this callback across rotation; no Activity is captured.
            session.onDocumentChanged = objects::select
            objects.select(session.documentKey)
        }
    }
}

@Composable
private fun CaptureHeader(session: CaptureSession, pick: () -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Blamera",
            Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (session.isRegion) {
            NavigationIcon(
                "Image entière",
                R.drawable.ic_full_image,
                session::restoreOriginal
            )
        }
        NavigationIcon("Caméra", R.drawable.ic_capture, {
            if (session.screen != CaptureScreen.CAMERA) session.navigate(CaptureScreen.CAMERA)
        })
        NavigationIcon("Galerie", R.drawable.ic_gallery, pick)
        if (BuildConfig.DEBUG && session.screen == CaptureScreen.CAMERA) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(painterResource(R.drawable.ic_more), "Diagnostics")
                }
                DropdownMenu(open, { open = false }) {
                    DropdownMenuItem(text = { Text("Comparer les modèles") }, onClick = {
                        open = false
                        context.startActivity(Intent(context, BenchmarkActivity::class.java))
                    })
                    DropdownMenuItem(text = { Text("Essai ChatGPT") }, onClick = {
                        open = false
                        context.startActivity(Intent(context, ChatGptTrialActivity::class.java))
                    })
                }
            }
        }
    }
}
