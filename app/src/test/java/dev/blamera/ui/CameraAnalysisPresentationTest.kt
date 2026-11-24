package dev.blamera.ui

import android.graphics.Matrix
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.blamera.camera.LiveResult
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.ContourSegment
import dev.blamera.vision.ImageBox
import dev.blamera.vision.ImagePoint
import dev.blamera.vision.MaskGrid
import dev.blamera.vision.VisionDetection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraAnalysisPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsCategoriesTimingThenEmptyErrorAndPendingWithoutRetainingOldLabels() {
        val result = mutableStateOf<LiveResult?>(null)
        compose.setContent {
            MaterialTheme {
                Column {
                    Box(Modifier.height(320.dp)) { CameraContours(result.value) }
                    CameraAnalysisMessage(result.value, true)
                }
            }
        }
        compose.onNodeWithText(
            "Analyse locale en direct… Gardez la scène immobile pour juger les contours."
        ).assertIsDisplayed()
        val detection =
            VisionDetection(
                0,
                0.9f,
                ImageBox(2f, 2f, 30f, 30f),
                MaskGrid(1, 1, booleanArrayOf(true)),
                listOf(ContourSegment(ImagePoint(2f, 2f), ImagePoint(30f, 30f)))
            )
        compose.runOnIdle {
            result.value =
                LiveResult(AnalysisResult.Success(listOf(detection)), Matrix(), 125)
        }
        compose.onNodeWithText("Personne").assertIsDisplayed()
        compose.onNodeWithText(
            "Dernière analyse : 125 ms (échantillon, pas une cadence garantie)."
        ).assertIsDisplayed()
        compose.runOnIdle {
            result.value =
                LiveResult(AnalysisResult.Success(emptyList()), Matrix(), 100)
        }
        compose.onNodeWithText("Aucun objet reconnu à ce seuil.").assertIsDisplayed()
        compose.runOnIdle { result.value = LiveResult(AnalysisResult.Failed, Matrix(), 0) }
        compose.onNodeWithText(
            "Analyse impossible. Arrêtez puis réessayez, ou utilisez Image."
        ).assertIsDisplayed()
        compose.runOnIdle { result.value = null }
        compose.onNodeWithText(
            "Analyse locale en direct… Gardez la scène immobile pour juger les contours."
        ).assertIsDisplayed()
    }
}
