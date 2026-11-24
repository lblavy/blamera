package dev.blamera.camera

import android.app.Activity
import android.os.Looper
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class CameraXLiveProviderTest {
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
            currentState =
                Lifecycle.State.RESUMED
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val processor =
        LiveCameraAnalysis(
            VisionAnalyzer {
                AnalysisResult.Success(emptyList())
            },
            scope,
            { null },
            {}
        )

    @Test fun bindsSameViewportOwnsBothUseCasesAndClosesIdempotently() {
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            val view = PreviewView(activity.get())
            activity.get().setContentView(view)
            activity.visible()
            view.layout(0, 0, 600, 320)
            var group: UseCaseGroup? = null
            val released = mutableListOf<UseCase>()
            val provider = CameraXLiveProvider({ actualOwner, actualGroup ->
                assertSame(owner, actualOwner)
                group = actualGroup
                camera()
            }, { released.add(it) }, processor)
            val binding = provider.bind(owner, view) {}
            val cases = checkNotNull(group).useCases
            assertNotNull(checkNotNull(group).viewPort)
            assertEquals(2, cases.size)
            val analysis = cases.filterIsInstance<ImageAnalysis>().single()
            assertEquals(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST, analysis.backpressureStrategy)
            assertEquals(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888, analysis.outputImageFormat)
            val preview = cases.filterIsInstance<Preview>().single()
            shadowOf(activity.get().windowManager.defaultDisplay).setRotation(Surface.ROTATION_270)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(preview.targetRotation, analysis.targetRotation)
            binding.close()
            binding.close()
            assertEquals(cases.toSet(), released.toSet())
            assertEquals(2, released.size)
        }
        scope.cancel()
    }

    @Test fun bindFailureAndMissingLayoutReleaseAnalysisAndPreview() {
        val view = PreviewView(RuntimeEnvironment.getApplication())
        val released = mutableListOf<UseCase>()
        val provider =
            CameraXLiveProvider({ _, _ ->
                error("must have layout")
            }, { released.add(it) }, processor)
        assertThrows(IllegalStateException::class.java) { provider.bind(owner, view) {} }
        assertEquals(1, released.filterIsInstance<ImageAnalysis>().size)
        assertEquals(1, released.filterIsInstance<Preview>().size)
        scope.cancel()
    }

    @Test fun photoCaptureAndAnalysisShareThePreviewViewportAndAllRotations() {
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            val view = PreviewView(activity.get())
            activity.get().setContentView(view)
            activity.visible()
            view.layout(0, 0, 600, 320)
            val capture = ImageCapture.Builder().build()
            var group: UseCaseGroup? = null
            val released = mutableListOf<UseCase>()
            val provider = CameraXLiveProvider(
                { _, actual ->
                    group = actual
                    camera()
                },
                { released.add(it) },
                processor,
                capture
            )
            val binding = provider.bind(owner, view) {}
            val actual = checkNotNull(group)
            val analysis = actual.useCases.filterIsInstance<ImageAnalysis>().single()
            val preview = actual.useCases.filterIsInstance<Preview>().single()
            assertEquals(3, actual.useCases.size)
            assertSame(capture, actual.useCases.filterIsInstance<ImageCapture>().single())
            assertNotNull(actual.viewPort)
            listOf(
                Surface.ROTATION_90,
                Surface.ROTATION_180,
                Surface.ROTATION_270
            ).forEach { rotation ->
                shadowOf(activity.get().windowManager.defaultDisplay).setRotation(rotation)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(rotation, preview.targetRotation)
                assertEquals(rotation, capture.targetRotation)
                assertEquals(rotation, analysis.targetRotation)
            }
            binding.close()
            binding.close()
            assertEquals(actual.useCases.toSet(), released.toSet())
            assertEquals(3, released.size)
        }
        scope.cancel()
    }

    @Test fun missingLayoutAlsoReleasesTheOwnedPhotoCapture() {
        val capture = ImageCapture.Builder().build()
        val released = mutableListOf<UseCase>()
        val provider = CameraXLiveProvider(
            { _, _ -> error("must have layout") },
            { released.add(it) },
            processor,
            capture
        )
        assertThrows(IllegalStateException::class.java) {
            provider.bind(owner, PreviewView(RuntimeEnvironment.getApplication())) {}
        }
        assertTrue(capture in released)
        assertEquals(3, released.size)
        scope.cancel()
    }

    @Test fun rejectedOrClosedFramesAreClosedExactlyOnceWithoutPixelAccess() {
        processor.setEnabled(false)
        val live = LiveUseCase(processor)
        var closes = 0
        val info = proxy<ImageInfo> { if (it == "getRotationDegrees") 0 else null }
        val frame = proxy<ImageProxy> {
            when (it) {
                "getImageInfo" -> info
                "close" -> {
                    closes++
                    null
                }
                else -> error("no pixels should be read")
            }
        }
        live.analyze(frame)
        assertEquals(1, closes)
        live.camera = camera()
        live.analyze(frame)
        assertEquals(2, closes)
        live.rotate(Surface.ROTATION_180)
        assertEquals(Surface.ROTATION_180, live.analysis.targetRotation)
        live.close()
        live.close()
        live.analyze(frame)
        assertEquals(3, closes)
        scope.cancel()
    }

    private fun camera(): Camera {
        val state = MutableLiveData(CameraState.create(CameraState.Type.OPEN))
        val info = proxy<CameraInfo> {
            when (it) {
                "getCameraState" -> state
                "getSensorRotationDegrees" -> 0
                else -> null
            }
        }
        return proxy { if (it == "getCameraInfo") info else null }
    }
    private inline fun <reified T> proxy(crossinline value: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            value(method.name)
        } as T
}
