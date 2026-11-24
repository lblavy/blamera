package dev.blamera.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.blamera.faces.FaceIdentityDecision
import dev.blamera.faces.FaceIdentityStatus
import dev.blamera.faces.FacePhotoFace
import dev.blamera.faces.FacePhotoState
import dev.blamera.faces.runtime.FaceBounds
import dev.blamera.vision.ImageViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FacePhotoOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun faceBoundsFollowSamePortraitLandscapeAndZoomPanViewportAsPhoto() {
        val face = FaceBounds(10f, 20f, 30f, 60f)
        assertEquals(
            FaceBounds(90f, 30f, 120f, 90f),
            projectFaceBounds(face, ImageViewport(1.5f, 75f, 0f))
        )
        assertEquals(
            FaceBounds(15f, 105f, 45f, 165f),
            projectFaceBounds(face, ImageViewport(1.5f, 0f, 75f))
        )
        assertEquals(
            FaceBounds(5f, 50f, 65f, 170f),
            projectFaceBounds(face, ImageViewport(3f, -25f, -10f))
        )
    }

    @Test fun facePainterDrawsTheProjectedLabelWithoutAFacialBorder() {
        val image = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.WHITE)
        FaceOverlayPainter().drawFaces(
            Canvas(image),
            listOf(known()),
            ImageViewport(2f, 5f, 7f),
            1f,
            16f
        )
        assertEquals(Color.WHITE, image.getPixel(25, 80))
        assertNotEquals(Color.WHITE, image.getPixel(35, 30))
        assertEquals(Color.WHITE, image.getPixel(15, 80))
        image.recycle()
    }

    @Test fun feedbackShowsTheNumberOfFacesWithoutAskingForACrop() {
        compose.setContent {
            MaterialTheme { FacePhotoFeedback(FacePhotoState.Detected(listOf(known()))) }
        }
        compose.onNodeWithText("1 visage(s) détecté(s).").assertIsDisplayed()
    }

    @Test fun uncertainUnknownAndSeveralFacesHaveExplicitDifferentLabels() {
        assertEquals(
            "Inconnu",
            faceIdentityLabel(FaceIdentityDecision(FaceIdentityStatus.UNKNOWN, null, .1, null))
        )
        assertEquals(
            "Identité incertaine",
            faceIdentityLabel(FaceIdentityDecision(FaceIdentityStatus.UNCERTAIN, null, .4, .3))
        )
        compose.setContent { MaterialTheme { FacePhotoFeedback(FacePhotoState.TooManyFaces) } }
        compose.onNodeWithText("Trop de visages, recadrez l’image.").assertIsDisplayed()
    }

    @Test fun offscreenFacesHaveNoFloatingLabelsAndUnknownFacesHaveNoBorder() {
        val image = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.WHITE)
        val painter = FaceOverlayPainter()
        painter.drawFaces(Canvas(image), listOf(known()), ImageViewport(2f, -250f, 7f), 1f, 16f)
        assertEquals(Color.WHITE, image.getPixel(0, 50))
        val unknown = FacePhotoFace(
            known().bounds,
            FaceIdentityDecision(FaceIdentityStatus.UNKNOWN, null, .1, null)
        )
        painter.drawFaces(Canvas(image), listOf(unknown), ImageViewport(2f, 5f, 7f), 1f, 16f)
        assertEquals(Color.WHITE, image.getPixel(25, 80))
        assertNotEquals(Color.WHITE, image.getPixel(35, 30))
        image.recycle()
    }

    @Test fun emptyRunningNoFaceAndUnavailableStatesStayDistinct() {
        val state = mutableStateOf<FacePhotoState>(FacePhotoState.Running)
        compose.setContent { MaterialTheme { FacePhotoFeedback(state.value) } }
        compose.onNodeWithText("Reconnaissance en cours…").assertIsDisplayed()
        compose.runOnIdle { state.value = FacePhotoState.NoFace }
        compose.onNodeWithText("Aucun visage détecté.").assertIsDisplayed()
        compose.runOnIdle { state.value = FacePhotoState.Unavailable }
        compose.onNodeWithText("Reconnaissance faciale indisponible.").assertIsDisplayed()
        compose.runOnIdle { state.value = FacePhotoState.Empty }
        compose.onNodeWithText("Reconnaissance faciale indisponible.").assertDoesNotExist()
    }

    private fun known() = FacePhotoFace(
        FaceBounds(10f, 20f, 60f, 70f),
        FaceIdentityDecision(FaceIdentityStatus.RECOGNIZED, "Personne synthétique A", .8, .1)
    )
}
