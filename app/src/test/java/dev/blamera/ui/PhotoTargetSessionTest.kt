package dev.blamera.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.blamera.image.ImageCrop
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoTargetSessionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val session = CaptureSession(scope)
    private val analyzer = VisionAnalyzer { AnalysisResult.Success(listOf(captureDetection())) }

    @After fun close() = scope.cancel()

    @Test fun frameWorksAfterFailureAndWholeImageIsDifferentFromUnmeasuredViewport() {
        session.accept(capturePhoto(), VisionAnalyzer { AnalysisResult.Failed })
        assertNull(session.snapshotTarget())
        session.interaction!!.size = Size(800f, 800f)
        val snapshot = assertNotNullSnapshot()
        assertEquals(ImageCrop(0, 0, 640, 480), snapshot.crop)
        assertEquals(PhotoTarget.FRAME, snapshot.target)
    }

    @Test fun selectionDefinesObjectIdentityAndUsesContextMargin() {
        ready()
        val frame = assertNotNullSnapshot()
        session.interaction!!.selectHits(listOf(0))
        assertEquals(PhotoTarget.OBJECT, session.target)
        assertNotEquals(frame.key, session.documentKey)
        val selected = assertNotNullSnapshot()
        assertEquals(ImageCrop(144, 108, 496, 372), selected.crop)
        assertNotEquals(frame.key, selected.key)
        session.interaction!!.selectHits(emptyList())
        assertEquals(PhotoTarget.FRAME, session.target)
        assertNotEquals(selected.key, session.documentKey)
    }

    @Test fun rotationPreservesFrozenIdentityButNextClickCapturesTheNewVisiblePixels() {
        ready()
        session.interaction!!.move(Offset(320f, 240f), Offset.Zero, 2f)
        val first = assertNotNullSnapshot()
        assertEquals(ImageCrop(160, 120, 480, 360), first.crop)
        session.interaction!!.size = Size(640f, 640f)
        assertEquals(first.key, session.documentKey)
        val next = assertNotNullSnapshot()
        assertEquals(ImageCrop(160, 80, 480, 400), next.crop)
        assertNotEquals(first.key, next.key)
    }

    @Test fun onlyActualFrameGesturesInvalidateTheFrozenRequestSynchronously() {
        ready()
        val first = assertNotNullSnapshot()
        val seen = mutableListOf<dev.blamera.chatgpt.ui.ObjectDocumentKey?>()
        session.onDocumentChanged = seen::add
        session.interaction!!.move(Offset(320f, 240f), Offset.Zero, 1f)
        assertEquals(first.key, session.documentKey)
        assertTrue(seen.isEmpty())
        session.interaction!!.move(Offset(320f, 240f), Offset.Zero, 2f)
        assertNotEquals(first.key, session.documentKey)
        assertEquals(session.documentKey, seen.last())
    }

    @Test fun invalidDetectedBoxCannotCrashTheTargetSnapshotOnTheUiThread() {
        session.accept(
            capturePhoto(),
            VisionAnalyzer {
                AnalysisResult.Success(
                    listOf(
                        captureDetection().copy(
                            box = dev.blamera.vision.ImageBox(900f, 900f, 1000f, 1000f)
                        )
                    )
                )
            }
        )
        session.interaction!!.size = Size(640f, 480f)
        session.interaction!!.selectHits(listOf(0))
        assertNull(session.snapshotTarget())
        assertEquals(PhotoTarget.OBJECT, session.target)
        assertNotNull(session.targeting.message)
    }

    private fun ready() {
        session.accept(capturePhoto(), analyzer)
        session.interaction!!.size = Size(640f, 480f)
    }
    private fun assertNotNullSnapshot(): PhotoTargetSnapshot =
        checkNotNull(session.snapshotTarget())
}
