package dev.blamera.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.blamera.camera.PhotoCaptureProvider
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.camera.PreviewStatus
import dev.blamera.image.PhotoLoader
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InlineLiveCameraTest {
    @get:Rule val compose = createComposeRule()

    @Test fun liveToggleKeepsCaptureAndGalleryInTheCurrentCameraInterface() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        val provider = object : PhotoCaptureProvider {
            override suspend fun capture() = captureLoaded()
            override fun bind(
                owner: androidx.lifecycle.LifecycleOwner,
                view: androidx.camera.view.PreviewView,
                report: (PreviewStatus) -> Unit
            ): AutoCloseable {
                report(PreviewStatus.STREAMING)
                return AutoCloseable {}
            }
        }
        compose.setContent {
            BlameraApp(
                PhotoLoader { captureLoaded() },
                VisionAnalyzer { AnalysisResult.Success(emptyList()) },
                PreviewProviderLoader { provider }
            )
        }
        compose.onNodeWithText("Capturer").assertIsEnabled()
        compose.onNodeWithText("Analyse en direct").performClick()
        compose.onNodeWithText("Choisissez une source").assertDoesNotExist()
        compose.onNodeWithText("Capturer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Galerie").assertIsDisplayed()
        compose.onNodeWithText("Arrêter l’analyse").performClick()
        compose.onNodeWithText("Analyse en direct").assertIsDisplayed()
        compose.onNodeWithText("Capturer").assertIsEnabled()
    }
}
