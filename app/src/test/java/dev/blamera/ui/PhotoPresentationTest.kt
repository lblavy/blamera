package dev.blamera.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import dev.blamera.vision.categoryLabel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedObjectAndActionsFitPortrait() = render("portrait")

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp")
    fun selectedObjectAndActionsFitLandscape() = render("landscape")

    private fun render(name: String) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val analyzer = VisionAnalyzer { AnalysisResult.Success(listOf(captureDetection())) }
            val session = CaptureSession(scope)
            session.accept(capturePhoto(), analyzer)
            session.interaction!!.selectHits(listOf(0))
            lateinit var view: View
            compose.setContent {
                view = LocalView.current
                MaterialTheme {
                    CaptureHome(
                        session,
                        CaptureTools(
                            PreviewProviderLoader {
                                error("Camera must stay closed")
                            },
                            analyzer,
                            ::categoryLabel
                        ),
                        {}
                    )
                }
            }
            compose.onNodeWithText("Chat").assertIsDisplayed()
            compose.onNodeWithContentDescription("Caméra").assertIsDisplayed()
            compose.onNodeWithContentDescription("Galerie").assertIsDisplayed()
            val current = File(checkNotNull(System.getProperty("user.dir")))
            val root = if (File(
                    current,
                    "AGENTS.md"
                ).isFile
            ) {
                current
            } else {
                checkNotNull(current.parentFile)
            }
            val output =
                File(
                    root,
                    "build/reports/validation/m1-photo-refinements/app/screenshots/$name.png"
                )
            checkNotNull(output.parentFile).mkdirs()
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                output.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
        } finally {
            scope.cancel()
        }
    }
}
