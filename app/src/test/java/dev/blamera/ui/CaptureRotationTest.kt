package dev.blamera.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import dev.blamera.MainActivity
import dev.blamera.image.PhotoCropper
import dev.blamera.image.PhotoLoadResult
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureRotationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun activityRecreationKeepsThePhotoItsResultsAndSelectionWithoutNewInference() {
        var calls = 0
        val photo = capturePhoto()
        lateinit var original: CaptureSession
        compose.runOnIdle {
            original = ViewModelProvider(compose.activity)[CaptureHolder::class.java].session
            original.accept(
                photo,
                VisionAnalyzer {
                    calls++
                    AnalysisResult.Success(listOf(captureDetection()))
                }
            )
        }
        compose.waitForIdle()
        compose.runOnIdle { original.interaction!!.selectHits(listOf(0)) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Image analysée").assertIsDisplayed()
        compose.onNodeWithContentDescription("Caméra").assertIsDisplayed()
        compose.runOnIdle {
            val retained = ViewModelProvider(compose.activity)[CaptureHolder::class.java].session
            assertSame(original, retained)
            assertSame(photo, retained.photo)
            assertEquals(0, retained.selected)
            assertEquals(1, (retained.result as AnalysisResult.Success).detections.size)
            assertEquals(1, calls)
        }
    }

    @Test fun originalAndRegionSurviveActivityRecreation() {
        var calls = 0
        val originalPhoto = capturePhoto(400, 200)
        lateinit var originalResult: AnalysisResult
        lateinit var workspace: CaptureSession
        compose.runOnIdle {
            workspace = ViewModelProvider(compose.activity)[CaptureHolder::class.java].session
            val analyzer = VisionAnalyzer {
                calls++
                AnalysisResult.Success(emptyList())
            }
            workspace.accept(originalPhoto, analyzer)
            originalResult = checkNotNull(workspace.result)
            val interaction = checkNotNull(workspace.interaction)
            interaction.size = Size(400f, 200f)
            interaction.move(Offset(200f, 100f), Offset.Zero, 2f)
            workspace.analyzeVisible(
                analyzer,
                PhotoCropper { _, crop ->
                    PhotoLoadResult.Loaded(capturePhoto(crop.width, crop.height))
                }
            )
        }
        compose.onNodeWithContentDescription("Image entière").assertIsDisplayed()
        val region = workspace.photo
        compose.activityRule.scenario.recreate()
        compose.runOnIdle {
            assertSame(
                workspace,
                ViewModelProvider(compose.activity)[CaptureHolder::class.java].session
            )
            assertSame(region, workspace.photo)
            org.junit.Assert.assertTrue(workspace.isRegion)
        }
        compose.onNodeWithContentDescription("Image entière").performClick()
        compose.runOnIdle {
            assertSame(originalPhoto, workspace.photo)
            assertSame(originalResult, workspace.result)
            assertEquals(2, calls)
        }
    }
}
