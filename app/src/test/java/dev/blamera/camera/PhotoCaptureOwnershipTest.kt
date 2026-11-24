package dev.blamera.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dev.blamera.image.PhotoLoadResult
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoCaptureOwnershipTest {
    @Test fun captureCannotRunOutsideItsOwnedBindingAndCloseIsIdempotent() = runBlocking {
        var closed = 0
        var captured = 0
        val source = OwnedPhotoCapture(
            PreviewProvider { _, _, report ->
                report(PreviewStatus.STREAMING)
                AutoCloseable { closed++ }
            },
            {
                captured++
                PhotoLoadResult.Failed(dev.blamera.image.ImageImportError.UNREADABLE)
            }
        )
        assertTrue(source.capture() is PhotoLoadResult.Failed)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        val binding = source.bind(owner, PreviewView(RuntimeEnvironment.getApplication())) {}
        source.capture()
        assertEquals(1, captured)
        binding.close()
        binding.close()
        assertEquals(1, closed)
        source.capture()
        assertEquals(1, captured)
    }

    @Test fun cancellationStillClosesALateImageWithoutReadingIt() = runBlocking {
        var callback: ImageCapture.OnImageCapturedCallback? = null
        var closed = 0
        val job =
            async(start = CoroutineStart.UNDISPATCHED) { awaitCapturedPhoto { callback = it } }
        job.cancel()
        callback!!.onCaptureSuccess(
            proxy { name ->
                if (name ==
                    "close"
                ) {
                    closed++
                    null
                } else {
                    error("Cancelled image must not be decoded")
                }
            }
        )
        assertEquals(1, closed)
    }

    @Test fun corruptImageIsClosedAndCaptureErrorsAreRecoverable() = runBlocking {
        var closed = 0
        val info = proxy<ImageInfo> { if (it == "getRotationDegrees") 0 else null }
        val plane = proxy<ImageProxy.PlaneProxy> {
            if (it ==
                "getBuffer"
            ) {
                ByteBuffer.wrap(byteArrayOf(1))
            } else {
                null
            }
        }
        val image = proxy<ImageProxy> { name ->
            when (name) {
                "close" -> {
                    closed++
                    null
                }
                "getFormat" -> ImageFormat.JPEG
                "getImageInfo" -> info
                "getPlanes" -> arrayOf(plane)
                "getCropRect" -> Rect(0, 0, 4, 4)
                else -> null
            }
        }
        assertTrue(awaitCapturedPhoto { it.onCaptureSuccess(image) } is PhotoLoadResult.Failed)
        assertEquals(1, closed)
        assertTrue(
            awaitCapturedPhoto {
                it.onError(
                    ImageCaptureException(ImageCapture.ERROR_CAPTURE_FAILED, "fixture", null)
                )
            } is PhotoLoadResult.Failed
        )
    }

    @Test fun permissionRevocationDuringCaptureBecomesARecoverableFailure() = runBlocking {
        assertTrue(
            awaitCapturedPhoto {
                throw SecurityException("permission revoked")
            } is PhotoLoadResult.Failed
        )
    }

    private inline fun <reified T> proxy(crossinline call: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            call(method.name)
        } as T
}
