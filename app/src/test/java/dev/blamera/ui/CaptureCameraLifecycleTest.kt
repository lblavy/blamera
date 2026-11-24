package dev.blamera.ui

import android.Manifest
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.blamera.camera.PhotoCaptureProvider
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.camera.PreviewStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureCameraLifecycleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unavailableCameraCanRetryAndPauseReleasesTheOwnedPreview() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        val loading = mutableStateOf(false)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        var attempts = 0
        var opened = 0
        var closed = 0
        val provider = object : PhotoCaptureProvider {
            override suspend fun capture() = captureLoaded()
            override fun bind(
                owner: LifecycleOwner,
                view: androidx.camera.view.PreviewView,
                report: (PreviewStatus) -> Unit
            ): AutoCloseable {
                opened++
                report(PreviewStatus.STREAMING)
                return AutoCloseable { closed++ }
            }
        }
        val loader = PreviewProviderLoader {
            if (attempts++ == 0) throw IllegalStateException("Camera occupied")
            provider
        }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    CaptureCameraPane(loader, loading.value) { loading.value = true }
                }
            }
        }
        compose.onNodeWithText("Caméra indisponible ou occupée. Réessayez ou utilisez Image.")
            .assertIsDisplayed()
        compose.onNodeWithText("Capturer").assertIsNotEnabled()
        compose.onNodeWithText("Réessayer la caméra").performClick()
        compose.onNodeWithText("Capturer").assertIsEnabled().performClick()
        compose.onNodeWithText("Capture en cours…").assertIsDisplayed()
        compose.onNodeWithText("Capturer").assertIsNotEnabled()
        compose.runOnIdle {
            loading.value = false
            owner.lifecycle.currentState = Lifecycle.State.STARTED
        }
        compose.onNodeWithText("Caméra en pause.").assertIsDisplayed()
        compose.onNodeWithText("Capturer").assertIsNotEnabled()
        assertEquals(opened, closed)
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("Capturer").assertIsEnabled()
        assertEquals(opened - 1, closed)
    }
}
