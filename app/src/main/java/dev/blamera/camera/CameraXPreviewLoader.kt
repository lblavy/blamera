package dev.blamera.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class CameraXPreviewLoader(context: Context) :
    PreviewProviderLoader,
    AnalysisPreviewLoader {
    private val application = context.applicationContext

    override fun withAnalysis(analysis: LiveCameraAnalysis): PreviewProviderLoader =
        CameraXLiveLoader(application, analysis)

    override suspend fun load(): PreviewProvider {
        val provider =
            awaitCameraFuture(
                ProcessCameraProvider.getInstance(application),
                ContextCompat.getMainExecutor(application)
            )
        return CameraXPreviewProvider(
            { owner, preview ->
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            },
            { preview -> provider.unbind(preview) }
        )
    }
}

internal suspend fun <T> awaitCameraFuture(future: ListenableFuture<T>, executor: Executor): T =
    suspendCancellableCoroutine { continuation ->
        // ProcessCameraProvider's global future cannot be cancelled. Never bind from this callback.
        future.addListener({
            if (continuation.isActive) {
                try {
                    continuation.resume(future.get())
                } catch (error: ExecutionException) {
                    continuation.resumeWithException(error)
                } catch (error: CancellationException) {
                    continuation.cancel(error)
                }
            }
        }, executor)
    }
