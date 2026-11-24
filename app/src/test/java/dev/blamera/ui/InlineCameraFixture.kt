package dev.blamera.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.OutputTransform
import androidx.lifecycle.LifecycleOwner
import dev.blamera.camera.AnalysisPreviewLoader
import dev.blamera.camera.LiveCameraAnalysis
import dev.blamera.camera.LiveFrame
import dev.blamera.camera.PhotoCaptureProvider
import dev.blamera.camera.PreviewProvider
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.camera.PreviewStatus
import java.util.concurrent.atomic.AtomicInteger

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
internal class InlineCameraFixture :
    PreviewProviderLoader,
    AnalysisPreviewLoader {
    var processor: LiveCameraAnalysis? = null
        private set
    var activeBindings = 0
        private set
    var captures = 0
        private set
    val views = mutableSetOf<PreviewView>()
    private var current: Any? = null

    override suspend fun load(): PreviewProvider = source(null)
    override fun withAnalysis(analysis: LiveCameraAnalysis) =
        PreviewProviderLoader { source(analysis) }

    private fun source(live: LiveCameraAnalysis?) = object : PhotoCaptureProvider {
        override suspend fun capture() = captureLoaded().also { captures++ }
        override fun bind(
            owner: LifecycleOwner,
            view: PreviewView,
            report: (PreviewStatus) -> Unit
        ): AutoCloseable {
            check(activeBindings == 0) { "Two camera bindings would overlap" }
            activeBindings++
            processor = live
            views.add(view)
            current = this
            report(PreviewStatus.STREAMING)
            var closed = false
            return AutoCloseable {
                if (!closed) {
                    closed = true
                    activeBindings--
                    if (current === this) processor = null
                }
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
internal class InlineFrameFixture : LiveFrame {
    val image = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
    val closes = AtomicInteger()
    val copies = AtomicInteger()
    override fun bitmap(): Bitmap = image.also { copies.incrementAndGet() }
    override fun transform() = OutputTransform(Matrix(), Size(640, 480))
    override fun close() {
        closes.incrementAndGet()
    }
}
