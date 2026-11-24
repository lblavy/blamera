package dev.blamera.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

internal interface AnalysisPreviewLoader {
    fun withAnalysis(analysis: LiveCameraAnalysis): PreviewProviderLoader
}

internal class CameraXLiveLoader(
    private val context: Context,
    private val processor: LiveCameraAnalysis
) : PreviewProviderLoader {
    override suspend fun load(): PreviewProvider {
        val provider = awaitCameraFuture(
            ProcessCameraProvider.getInstance(context),
            ContextCompat.getMainExecutor(context)
        )
        return CameraXLiveProvider(
            { owner, group ->
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
            },
            { owned -> provider.unbind(owned) },
            processor
        )
    }
}

internal class CameraXLiveProvider(
    private val bindGroup: (LifecycleOwner, UseCaseGroup) -> Camera,
    private val unbind: (androidx.camera.core.UseCase) -> Unit,
    private val processor: LiveCameraAnalysis,
    private val capture: ImageCapture? = null
) : PreviewProvider {
    override fun bind(
        owner: LifecycleOwner,
        view: PreviewView,
        report: (PreviewStatus) -> Unit
    ): AutoCloseable {
        val live = LiveUseCase(processor)
        var closed = false
        val cleanup = {
            if (!closed) {
                closed = true
                release(live)
            }
        }
        var linked = false
        try {
            val preview = CameraXPreviewProvider({ lifecycle, owned ->
                val group = UseCaseGroup.Builder()
                    .setViewPort(checkNotNull(view.viewPort) { "Preview layout unavailable" })
                    .addUseCase(owned).addUseCase(live.analysis)
                capture?.let(group::addUseCase)
                bindGroup(lifecycle, group.build())
                    .also { live.camera = it }
            }, { owned -> unbind(owned) }, { rotation ->
                live.rotate(rotation)
                capture?.targetRotation = rotation
            }).bind(owner, view, report)
            linked = true
            return AutoCloseable {
                try {
                    preview.close()
                } finally {
                    cleanup()
                }
            }
        } finally {
            if (!linked) cleanup()
        }
    }

    private fun release(live: LiveUseCase) {
        try {
            capture?.let(unbind)
        } finally {
            try {
                unbind(live.analysis)
            } finally {
                live.close()
            }
        }
    }
}
