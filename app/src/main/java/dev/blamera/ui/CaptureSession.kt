package dev.blamera.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.blamera.botany.PlantAnalysis
import dev.blamera.botany.PlantSnapshot
import dev.blamera.botany.createPlantAnalysis
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import dev.blamera.chatgpt.ui.ObjectDocumentKey
import dev.blamera.faces.FaceGallerySettings
import dev.blamera.faces.FacePhotoAnalysis
import dev.blamera.faces.createFaceGallerySettings
import dev.blamera.faces.runtime.FaceEngine
import dev.blamera.faces.runtime.createFaceEngine
import dev.blamera.image.AndroidPhotoCropper
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.ImageCrop
import dev.blamera.image.ImageImportError
import dev.blamera.image.PhotoCropper
import dev.blamera.image.PhotoLoadResult
import dev.blamera.image.PhotoLoader
import dev.blamera.information.ObjectInformationTools
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal enum class CaptureScreen { CAMERA, PHOTO }

// Android's default factory requires the Java (Application) constructor.
internal class CaptureHolder @JvmOverloads constructor(
    application: Application,
    faceFactory: (Context, CoroutineScope) -> FaceGallerySettings = ::createFaceGallerySettings,
    engineFactory: (Context) -> FaceEngine = ::createFaceEngine,
    plantFactory: (Context, CoroutineScope) -> PlantAnalysis = { context, scope ->
        createPlantAnalysis(context, scope)
    }
) : AndroidViewModel(application) {
    private val gallery = faceFactory(application.applicationContext, viewModelScope)
    private val recognition = FacePhotoAnalysis(
        viewModelScope,
        gallery,
        engineFactory(application.applicationContext)
    )
    val session = CaptureSession(
        viewModelScope,
        plantFactory(application.applicationContext, viewModelScope),
        gallery,
        recognition
    )
    init {
        addCloseable(session)
    }
}

internal class CaptureSession(
    private val scope: CoroutineScope,
    val plants: PlantAnalysis? = null,
    val faces: FaceGallerySettings? = null,
    val recognition: FacePhotoAnalysis? = null
) : AutoCloseable {
    internal val targeting = PhotoTargetState(scope, {
        PhotoTargetInput(
            photo,
            interaction,
            result as? AnalysisResult.Success,
            screen == CaptureScreen.PHOTO && !loading && !running
        )
    }) { onDocumentChanged(it) }
    val target get() = targeting.target
    val documentKey get() = targeting.key
    var onDocumentChanged: (ObjectDocumentKey?) -> Unit = { }
    var screen by mutableStateOf(CaptureScreen.CAMERA)
        private set
    var photo by mutableStateOf<DecodedPhoto?>(null)
        private set
    var result by mutableStateOf<AnalysisResult?>(null)
        private set
    var running by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val selected get() = interaction?.selected
    val hits get() = interaction?.hits.orEmpty()
    var interaction by mutableStateOf<PhotoInteraction?>(null)
        private set
    private var original by mutableStateOf<PhotoSnapshot?>(null)
    val isRegion get() = original?.let { photo !== it.photo } == true
    private var job: Job? = null
    private var generation = 0

    fun accept(image: DecodedPhoto, analyzer: VisionAnalyzer, keepOriginal: Boolean = false) {
        invalidate()
        publish(image, keepOriginal)
        val token = generation
        job = scope.launch { analyze(image, analyzer, token) }
    }

    fun capture(
        load: suspend () -> PhotoLoadResult,
        analyzer: VisionAnalyzer,
        keepOriginal: Boolean = false
    ) {
        invalidate()
        loading = true
        error = null
        val token = generation
        job = scope.launch {
            val loaded = load()
            currentCoroutineContext().ensureActive()
            loading = false
            when (loaded) {
                is PhotoLoadResult.Loaded -> {
                    publish(loaded.photo, keepOriginal)
                    analyze(loaded.photo, analyzer, token)
                }
                is PhotoLoadResult.Failed -> {
                    error = importError(loaded.reason)
                    onDocumentChanged(documentKey)
                }
            }
        }
    }

    private fun publish(image: DecodedPhoto, keepOriginal: Boolean) {
        if (!keepOriginal) original = null
        photo = image
        interaction = targeting.interaction(image)
        result = null
        error = null
        screen = CaptureScreen.PHOTO
        recognition?.show(image.bitmap)
    }

    private suspend fun analyze(image: DecodedPhoto, analyzer: VisionAnalyzer, token: Int) {
        running = true
        try {
            plants?.start(image)
            val analyzed = analyzer.analyze(image.bitmap)
            currentCoroutineContext().ensureActive()
            if (token == generation) result = analyzed
        } finally {
            if (token == generation) running = false
        }
    }

    fun navigate(destination: CaptureScreen) {
        invalidate()
        screen = destination
        result = null
        interaction = photo?.let(targeting::interaction)
        original = null
        error = null
        if (destination == CaptureScreen.PHOTO) photo?.let { recognition?.show(it.bitmap) }
    }

    private fun invalidate() {
        generation++
        plants?.cancel(clear = true)
        recognition?.clear()
        targeting.invalidate()
        job?.cancel()
        running = false
        loading = false
    }

    fun onBackground() {
        // Stop the action that is already pending, not a later picker result on restart.
        generation++
        job?.cancel()
        job = null
        running = false
        loading = false
        plants?.cancel()
        recognition?.background()
    }

    fun analyzeVisible(analyzer: VisionAnalyzer, cropper: PhotoCropper = AndroidPhotoCropper()) {
        val snapshot = snapshotTarget() ?: return
        val image = snapshot.photo
        val crop = snapshot.crop
        if (crop == ImageCrop(0, 0, image.bitmap.width, image.bitmap.height)) {
            retry(analyzer)
            return
        }
        if (original == null) {
            original = PhotoSnapshot(
                image,
                result,
                plants?.snapshot?.takeUnless { it.running }
            )
        }
        capture({ cropper.crop(image, crop) }, analyzer, keepOriginal = true)
    }

    fun restoreOriginal() {
        val saved = original ?: return
        invalidate()
        publish(saved.photo, keepOriginal = false)
        result = saved.result
        plants?.restore(saved.plant)
    }

    override fun close() {
        job?.cancel()
        plants?.close()
        recognition?.close()
        targeting.close()
    }
}

internal fun CaptureSession.snapshotTarget() = targeting.snapshot()?.let {
    it.copy(plantHint = plants?.hint(it.photo, it.crop))
}
internal fun CaptureSession.onForeground() {
    recognition?.foreground()
}
internal fun CaptureSession.retry(analyzer: VisionAnalyzer) {
    photo?.let { accept(it, analyzer, keepOriginal = true) }
}
internal fun CaptureSession.information(
    tools: ObjectInformationTools,
    chatGpt: ChatGptTrialViewModel?
) = targeting.information(tools, chatGpt)
internal fun CaptureSession.import(uri: Uri, loader: PhotoLoader, analyzer: VisionAnalyzer) =
    capture({ loader.load(uri) }, analyzer)

private data class PhotoSnapshot(
    val photo: DecodedPhoto,
    val result: AnalysisResult?,
    val plant: PlantSnapshot?
)

internal fun importError(reason: ImageImportError): String = when (reason) {
    ImageImportError.TOO_LARGE -> "Image trop grande : 30 Mio ou 100 mégapixels maximum."
    ImageImportError.UNSUPPORTED -> "Format non pris en charge. Choisissez JPEG, PNG, WebP ou HEIF."
    ImageImportError.UNREADABLE ->
        "Image illisible ou accès indisponible. Choisissez une autre image."
    ImageImportError.MEMORY -> "Mémoire insuffisante. Choisissez une image plus petite."
}
