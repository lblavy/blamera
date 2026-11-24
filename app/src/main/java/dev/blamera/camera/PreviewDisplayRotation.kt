package dev.blamera.camera

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView

/** Tracks the view's display, including 180-degree turns which need no Activity recreation. */
internal class PreviewDisplayRotation(
    private val view: PreviewView,
    private val preview: Preview,
    private val changed: (Int) -> Unit = {}
) : DisplayManager.DisplayListener,
    View.OnAttachStateChangeListener,
    AutoCloseable {
    private val manager = checkNotNull(view.context.getSystemService(DisplayManager::class.java))
    private var closed = false
    private var observed: Pair<Int, Int>? = null

    init {
        manager.registerDisplayListener(this, Handler(Looper.getMainLooper()))
        view.addOnAttachStateChangeListener(this)
        updateRotation()
    }

    override fun onDisplayChanged(displayId: Int) = updateRotation(displayId)
    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit
    override fun onViewAttachedToWindow(attached: View) = updateRotation()
    override fun onViewDetachedFromWindow(detached: View) = Unit

    override fun close() {
        if (!closed) {
            closed = true
            manager.unregisterDisplayListener(this)
            view.removeOnAttachStateChangeListener(this)
        }
    }

    private fun updateRotation(changedDisplay: Int? = null) {
        if (closed) return
        val display = view.display ?: return
        if (changedDisplay == null || changedDisplay == display.displayId) {
            val current = display.displayId to display.rotation
            if (current != observed) {
                observed = current
                // CameraX expects Surface.ROTATION_* enum values, never degrees or a view rotation.
                preview.targetRotation = display.rotation
                changed(display.rotation)
            }
        }
    }
}
