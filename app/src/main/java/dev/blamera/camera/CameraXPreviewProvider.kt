package dev.blamera.camera

import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer

class CameraXPreviewProvider(
    private val bindPreview: (LifecycleOwner, Preview) -> Camera,
    private val unbindPreview: (Preview) -> Unit,
    private val rotationChanged: (Int) -> Unit = {}
) : PreviewProvider {
    override fun bind(
        owner: LifecycleOwner,
        view: PreviewView,
        report: (PreviewStatus) -> Unit
    ): AutoCloseable {
        val preview = Preview.Builder().build()
        val rotation = PreviewDisplayRotation(view, preview, rotationChanged)
        var linked = false
        try {
            preview.setSurfaceProvider(view.surfaceProvider)
            val camera = bindPreview(owner, preview)
            val binding = observeOwned(camera, owner, view, preview, report)
            linked = true
            return AutoCloseable {
                try {
                    binding.close()
                } finally {
                    rotation.close()
                }
            }
        } finally {
            if (!linked) {
                rotation.close()
                preview.setSurfaceProvider(null)
                unbindPreview(preview)
            }
        }
    }

    private fun observeOwned(
        camera: Camera,
        owner: LifecycleOwner,
        view: PreviewView,
        preview: Preview,
        report: (PreviewStatus) -> Unit
    ): AutoCloseable {
        val cameraState = camera.cameraInfo.cameraState
        fun currentStatus() = when {
            cameraState.value?.error != null -> PreviewStatus.FAILED
            view.previewStreamState.value == PreviewView.StreamState.STREAMING ->
                PreviewStatus.STREAMING
            else -> PreviewStatus.CONNECTING
        }
        val errorObserver = Observer<CameraState> { report(currentStatus()) }
        val streamObserver = Observer<PreviewView.StreamState> { report(currentStatus()) }
        cameraState.observe(owner, errorObserver)
        view.previewStreamState.observe(owner, streamObserver)
        var closed = false
        return AutoCloseable {
            if (!closed) {
                closed = true
                cameraState.removeObserver(errorObserver)
                view.previewStreamState.removeObserver(streamObserver)
                preview.setSurfaceProvider(null)
                unbindPreview(preview)
            }
        }
    }
}
