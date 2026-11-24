package dev.blamera.camera

import android.app.Activity
import android.os.Looper
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraXPreviewProviderTest {
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
            currentState =
                Lifecycle.State.RESUMED
        }
    }
    private val view = PreviewView(RuntimeEnvironment.getApplication())
    private val cameraState = MutableLiveData(CameraState.create(CameraState.Type.OPEN))

    @Test
    fun streamingAndErrorsAreObservedAndOnlyOwnedPreviewIsUnbound() {
        var owned: Preview? = null
        val unbound = mutableListOf<Preview>()
        val provider =
            CameraXPreviewProvider({ actualOwner, preview ->
                assertSame(owner, actualOwner)
                owned =
                    preview
                camera()
            }, unbound::add)
        val states = mutableListOf<PreviewStatus>()
        val binding = provider.bind(owner, view, states::add)
        assertTrue(owned != null)
        assertFalse(states.contains(PreviewStatus.STREAMING))
        @Suppress("UNCHECKED_CAST")
        val stream = view.previewStreamState as MutableLiveData<PreviewView.StreamState>
        stream.value = PreviewView.StreamState.STREAMING
        assertEquals(PreviewStatus.STREAMING, states.last())
        cameraState.value =
            CameraState.create(
                CameraState.Type.PENDING_OPEN,
                CameraState.StateError.create(CameraState.ERROR_CAMERA_IN_USE)
            )
        assertEquals(PreviewStatus.FAILED, states.last())
        binding.close()
        binding.close()
        assertEquals(listOf(owned), unbound)
        assertFalse(cameraState.hasObservers())
        val count = states.size
        stream.value = PreviewView.StreamState.IDLE
        assertEquals(count, states.size)
    }

    @Test
    fun bindingFailureReleasesThePreviewItCreated() {
        var owned: Preview? = null
        val unbound = mutableListOf<Preview>()
        val provider =
            CameraXPreviewProvider({ _, preview ->
                owned = preview
                throw SecurityException("revoked")
            }, unbound::add)
        assertThrows(SecurityException::class.java) { provider.bind(owner, view) {} }
        assertEquals(listOf(owned), unbound)
        checkNotNull(owned).targetRotation = Surface.ROTATION_180
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            shadowOf(controller.get().windowManager.defaultDisplay).setRotation(Surface.ROTATION_90)
            controller.get().setContentView(view)
            controller.visible()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Surface.ROTATION_180, checkNotNull(owned).targetRotation)
        }
    }

    @Test
    fun anExistingCameraErrorCannotBeHiddenByAnIdlePreview() {
        cameraState.value =
            CameraState.create(
                CameraState.Type.PENDING_OPEN,
                CameraState.StateError.create(CameraState.ERROR_CAMERA_IN_USE)
            )
        val states = mutableListOf<PreviewStatus>()
        val binding = CameraXPreviewProvider({ _, _ ->
            camera()
        }, {}).bind(owner, view, states::add)
        assertEquals(PreviewStatus.FAILED, states.last())
        cameraState.value = CameraState.create(CameraState.Type.OPEN)
        assertEquals(PreviewStatus.CONNECTING, states.last())
        binding.close()
    }

    @Test
    fun displayRotationChangesUpdateTheSamePreviewWithoutRebinding() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val attached = PreviewView(controller.get())
            controller.get().setContentView(attached)
            controller.visible()
            val display = checkNotNull(attached.display)
            shadowOf(display).setRotation(Surface.ROTATION_90)
            shadowOf(Looper.getMainLooper()).idle()
            var binds = 0
            var preview: Preview? = null
            val binding = CameraXPreviewProvider({ _, created ->
                binds++
                preview = created
                camera()
            }, {}).bind(owner, attached) {}
            try {
                assertEquals(Surface.ROTATION_90, checkNotNull(preview).targetRotation)
                for (rotation in listOf(
                    Surface.ROTATION_270,
                    Surface.ROTATION_180,
                    Surface.ROTATION_0,
                    Surface.ROTATION_90
                )) {
                    shadowOf(display).setRotation(rotation)
                    shadowOf(Looper.getMainLooper()).idle()
                    assertEquals(rotation, checkNotNull(preview).targetRotation)
                }
                assertEquals(1, binds)
            } finally {
                binding.close()
            }
            shadowOf(display).setRotation(Surface.ROTATION_270)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Surface.ROTATION_90, checkNotNull(preview).targetRotation)
        }
    }

    private fun camera(): Camera {
        val info = Proxy.newProxyInstance(
            CameraInfo::class.java.classLoader,
            arrayOf(CameraInfo::class.java)
        ) {
                _,
                method,
                _
            ->
            when (method.name) {
                "getCameraState" -> cameraState
                else -> error("Unexpected CameraInfo method ${method.name}")
            }
        } as CameraInfo
        return Proxy.newProxyInstance(Camera::class.java.classLoader, arrayOf(Camera::class.java)) {
                _,
                method,
                _
            ->
            when (method.name) {
                "getCameraInfo" -> info
                else -> error("Unexpected Camera method ${method.name}")
            }
        } as Camera
    }
}
