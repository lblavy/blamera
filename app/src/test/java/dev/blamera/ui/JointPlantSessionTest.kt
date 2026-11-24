package dev.blamera.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.blamera.botany.PlantHttpResponse
import dev.blamera.image.PhotoCropper
import dev.blamera.image.PhotoLoadResult
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JointPlantSessionTest {
    private val fixture = JointPlantFixture()
    private val session = CaptureSession(fixture.scope, fixture.plants)
    private val local = VisionAnalyzer { AnalysisResult.Success(listOf(captureDetection())) }

    @After fun close() {
        session.close()
        fixture.close()
    }

    @Test fun localFailureStillLaunchesTheBotanicalIdentification() {
        session.accept(capturePhoto(), VisionAnalyzer { AnalysisResult.Failed })
        assertEquals(1, fixture.requests)
        assertEquals(AnalysisResult.Failed, session.result)
        assertFalse(session.running)
        assertNotNull(fixture.plants.snapshot?.result)
    }

    @Test fun slowBotanyDoesNotBlockTheLocalResultAndNeedsNoPlantDetection() {
        val pending = CompletableDeferred<PlantHttpResponse>()
        fixture.response = { pending.await() }
        session.accept(capturePhoto(), local)
        assertEquals(1, fixture.requests)
        assertFalse(session.running)
        assertTrue(session.result is AnalysisResult.Success)
        assertTrue(fixture.plants.snapshot?.running == true)
        pending.complete(plantResponse())
    }

    @Test fun wholeImageRestoresCompletedBotanyWithoutARequestOrDependenceOnSelection() {
        session.accept(capturePhoto(), local)
        val original = session.photo
        val saved = fixture.plants.snapshot
        assertNotNull(saved)
        session.interaction!!.size = Size(640f, 480f)
        session.interaction!!.move(Offset(320f, 240f), Offset.Zero, 2f)
        session.analyzeVisible(
            local,
            PhotoCropper { _, crop ->
                PhotoLoadResult.Loaded(capturePhoto(crop.width, crop.height))
            }
        )
        assertEquals(2, fixture.requests)
        session.interaction!!.selectHits(listOf(0))
        session.restoreOriginal()
        assertSame(original, session.photo)
        assertSame(saved, fixture.plants.snapshot)
        assertEquals(2, fixture.requests)
    }

    @Test fun navigationReplacementKeyChangesAndCloseRejectLateBotanyWithoutAutomaticRestart() {
        val pending = CompletableDeferred<PlantHttpResponse>()
        fixture.response = { pending.await() }
        session.accept(capturePhoto(), local)
        session.navigate(CaptureScreen.CAMERA)
        pending.complete(plantResponse())
        assertNull(fixture.plants.snapshot)
        assertEquals(1, fixture.requests)
        fixture.response = { plantResponse() }
        session.accept(capturePhoto(), local)
        fixture.settings.save("REPLACED-FAKE")
        assertNull(fixture.plants.snapshot)
        assertEquals(2, fixture.requests)
        session.close()
        assertNull(fixture.plants.snapshot)
    }

    @Test fun keySaveSelectionGesturesAndViewportChangesNeverAnalyzeTheAlreadyOpenPhoto() {
        fixture.settings.remove()
        session.accept(capturePhoto(), local)
        assertEquals(0, fixture.requests)
        fixture.settings.save("RESTORED-FAKE")
        session.interaction!!.size = Size(640f, 480f)
        session.interaction!!.selectHits(listOf(0))
        session.interaction!!.selectHits(emptyList())
        session.interaction!!.move(Offset(320f, 240f), Offset.Zero, 2f)
        session.interaction!!.size = Size(640f, 640f)
        session.snapshotTarget()
        assertEquals(0, fixture.requests)
        session.retry(local)
        assertEquals(1, fixture.requests)
    }

    @Test fun clickSnapshotFreezesOnlyTheHypothesisForExactlyTheSamePixels() {
        session.accept(capturePhoto(), local)
        session.interaction!!.size = Size(640f, 480f)
        val whole = checkNotNull(session.snapshotTarget())
        assertEquals("Rosa canina", whole.plantHint)
        session.interaction!!.selectHits(listOf(0))
        assertNull(checkNotNull(session.snapshotTarget()).plantHint)
        session.interaction!!.selectHits(emptyList())
        session.interaction!!.move(Offset(320f, 240f), Offset.Zero, 2f)
        assertNull(checkNotNull(session.snapshotTarget()).plantHint)
        assertEquals("Rosa canina", whole.plantHint)
        assertEquals(1, fixture.requests)
    }
}
