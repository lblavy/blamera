package dev.blamera.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.core.content.ContextCompat
import dev.blamera.image.ImageImportError
import dev.blamera.image.PhotoLoadResult
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine

interface PhotoCaptureProvider : PreviewProvider {
    suspend fun capture(): PhotoLoadResult
}

internal class CameraXCaptureLoader(
    context: Context,
    private val analysis: LiveCameraAnalysis? = null
) : PreviewProviderLoader,
    AnalysisPreviewLoader {
    private val application = context.applicationContext

    override fun withAnalysis(analysis: LiveCameraAnalysis): PreviewProviderLoader =
        CameraXCaptureLoader(application, analysis)

    override suspend fun load(): PreviewProvider {
        val provider = awaitCameraFuture(
            androidx.camera.lifecycle.ProcessCameraProvider.getInstance(application),
            ContextCompat.getMainExecutor(application)
        )
        val capture = ImageCapture.Builder().setCaptureMode(
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        )
            .setResolutionSelector(captureResolution()).build()
        if (analysis != null) {
            val live = CameraXLiveProvider(
                { owner, group ->
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                },
                { owned -> provider.unbind(owned) },
                analysis,
                capture
            )
            return OwnedPhotoCapture(live, { captureInMemory(capture) })
        }
        var viewport: androidx.camera.core.ViewPort? = null
        val preview = CameraXPreviewProvider(
            { owner, useCase ->
                val group = UseCaseGroup.Builder().setViewPort(checkNotNull(viewport))
                    .addUseCase(useCase).addUseCase(capture).build()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
            },
            { useCase -> provider.unbind(useCase, capture) },
            { rotation -> capture.targetRotation = rotation }
        )
        return OwnedPhotoCapture(preview, { captureInMemory(capture) }) {
            viewport = checkNotNull(it.viewPort)
        }
    }
}

private fun captureResolution() = ResolutionSelector.Builder()
    .setResolutionStrategy(
        ResolutionStrategy(
            android.util.Size(CAPTURE_TARGET_WIDTH, CAPTURE_TARGET_HEIGHT),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
        )
    ).build()

internal class OwnedPhotoCapture(
    private val preview: PreviewProvider,
    private val take: suspend () -> PhotoLoadResult,
    private val beforeBind: (androidx.camera.view.PreviewView) -> Unit = {}
) : PhotoCaptureProvider {
    private var bound = false

    override fun bind(
        owner: androidx.lifecycle.LifecycleOwner,
        view: androidx.camera.view.PreviewView,
        report: (PreviewStatus) -> Unit
    ): AutoCloseable {
        beforeBind(view)
        val handle = preview.bind(owner, view, report)
        bound = true
        return AutoCloseable {
            if (bound) {
                bound = false
                handle.close()
            }
        }
    }

    override suspend fun capture(): PhotoLoadResult =
        if (bound) take() else PhotoLoadResult.Failed(ImageImportError.UNREADABLE)
}

private const val CAPTURE_TARGET_WIDTH = 1920
private const val CAPTURE_TARGET_HEIGHT = 1080

private suspend fun captureInMemory(capture: ImageCapture): PhotoLoadResult =
    awaitCapturedPhoto { callback -> capture.takePicture(Dispatchers.IO.asExecutor(), callback) }

internal suspend fun awaitCapturedPhoto(
    request: (ImageCapture.OnImageCapturedCallback) -> Unit
): PhotoLoadResult = try {
    suspendCancellableCoroutine { continuation ->
        request(object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    if (continuation.isActive) {
                        val decoded = decodeCapturedJpeg(image)
                        if (continuation.isActive) continuation.resume(decoded)
                    }
                } finally {
                    image.close()
                }
            }
            override fun onError(exception: ImageCaptureException) {
                if (continuation.isActive) {
                    continuation.resume(PhotoLoadResult.Failed(ImageImportError.UNREADABLE))
                }
            }
        })
    }
} catch (_: SecurityException) {
    PhotoLoadResult.Failed(ImageImportError.UNREADABLE)
}
