package dev.blamera.camera

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Looper
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreviewDisplayRotationTest {
    @Test
    fun initiallyDetachedViewUpdatesWhenAttachedAndReattached() {
        val view = PreviewView(RuntimeEnvironment.getApplication())
        assertNull(view.display)
        val preview = Preview.Builder().setTargetRotation(Surface.ROTATION_180).build()
        val tracking = PreviewDisplayRotation(view, preview)
        try {
            Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
                val display = controller.get().windowManager.defaultDisplay
                shadowOf(display).setRotation(Surface.ROTATION_90)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(Surface.ROTATION_180, preview.targetRotation)
                controller.get().setContentView(view)
                controller.visible()
                assertEquals(Surface.ROTATION_90, preview.targetRotation)
                (view.parent as ViewGroup).removeView(view)
                assertNull(view.display)
                shadowOf(display).setRotation(Surface.ROTATION_270)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(Surface.ROTATION_90, preview.targetRotation)
                controller.get().setContentView(view)
                assertEquals(Surface.ROTATION_270, preview.targetRotation)
            }
        } finally {
            tracking.close()
        }
    }

    @Test
    fun unrelatedDisplaysAndLateCallbacksCannotChangeOwnedPreview() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val view = PreviewView(controller.get())
            controller.get().setContentView(view)
            controller.visible()
            val display = checkNotNull(view.display)
            val preview = Preview.Builder().build()
            val tracking = PreviewDisplayRotation(view, preview)
            val other = ShadowDisplayManager.addDisplay("w800dp-h600dp")
            preview.targetRotation = Surface.ROTATION_180
            tracking.onDisplayAdded(other)
            tracking.onDisplayRemoved(other)
            tracking.onDisplayChanged(other)
            assertEquals(Surface.ROTATION_180, preview.targetRotation)
            tracking.close()
            tracking.close()
            shadowOf(display).setRotation(Surface.ROTATION_90)
            shadowOf(Looper.getMainLooper()).idle()
            tracking.onDisplayChanged(display.displayId)
            tracking.onViewAttachedToWindow(view)
            assertEquals(Surface.ROTATION_180, preview.targetRotation)
            ShadowDisplayManager.removeDisplay(other)
        }
    }

    @Test
    fun unchangedDisplayNotificationsDoNotInvalidateAndFourRotationsStillNotify() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val view = PreviewView(controller.get())
            controller.get().setContentView(view)
            controller.visible()
            val display = checkNotNull(view.display)
            shadowOf(display).setRotation(Surface.ROTATION_0)
            shadowOf(Looper.getMainLooper()).idle()
            val changes = mutableListOf<Int>()
            val preview = Preview.Builder().build()
            val tracker = PreviewDisplayRotation(view, preview, changes::add)
            try {
                assertEquals(listOf(Surface.ROTATION_0), changes)
                tracker.onDisplayChanged(display.displayId)
                tracker.onDisplayChanged(display.displayId)
                assertEquals(listOf(Surface.ROTATION_0), changes)
                val rotations = listOf(
                    Surface.ROTATION_90,
                    Surface.ROTATION_180,
                    Surface.ROTATION_270,
                    Surface.ROTATION_0
                )
                rotations.forEach { rotation ->
                    shadowOf(display).setRotation(rotation)
                    shadowOf(Looper.getMainLooper()).idle()
                    tracker.onDisplayChanged(display.displayId)
                    assertEquals(rotation, preview.targetRotation)
                }
                assertEquals(listOf(Surface.ROTATION_0) + rotations, changes)
            } finally {
                tracker.close()
            }
        }
    }

    @Test
    fun closedTrackerDoesNotUpdateOnAFirstLateAttachment() {
        val view = PreviewView(RuntimeEnvironment.getApplication())
        val preview = Preview.Builder().setTargetRotation(Surface.ROTATION_180).build()
        val tracking = PreviewDisplayRotation(view, preview)
        tracking.close()
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            shadowOf(controller.get().windowManager.defaultDisplay).setRotation(Surface.ROTATION_90)
            controller.get().setContentView(view)
            controller.visible()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Surface.ROTATION_180, preview.targetRotation)
        }
    }
}
