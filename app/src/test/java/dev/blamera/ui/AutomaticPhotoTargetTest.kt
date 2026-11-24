package dev.blamera.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import dev.blamera.chatgpt.ui.FakeTrialGateway
import dev.blamera.image.ImageCrop
import dev.blamera.information.FakeSharedImage
import dev.blamera.information.ObjectInformationTools
import dev.blamera.information.SearchImage
import dev.blamera.information.SearchImagePreparer
import dev.blamera.information.SearchImagePublisher
import dev.blamera.information.fakeImageShareSender
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.ImageBox
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class AutomaticPhotoTargetTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val session = CaptureSession(scope)
    private val gateway = FakeTrialGateway()
    private val model =
        ChatGptTrialViewModel(gateway, scope = scope, appForeground = TrialAppForeground { 0L })
    private var exports = 0
    private val tools = ObjectInformationTools(
        object : SearchImagePreparer {
            override suspend fun prepare(source: Bitmap, box: ImageBox): SearchImage =
                error("Exact crop required")
            override suspend fun prepareRegion(source: Bitmap, crop: ImageCrop): SearchImage {
                exports++
                return SearchImage(
                    Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888),
                    byteArrayOf(1, 2, 3)
                )
            }
        },
        SearchImagePublisher { FakeSharedImage() },
        fakeImageShareSender { }
    )
    private val actions = session.information(tools, model)

    @After fun close() {
        session.close()
        model.close()
        scope.cancel()
    }

    @Test fun selectingAMaskImmediatelyMakesItsPaddedPixelsTheCommonTarget() {
        ready()
        session.interaction!!.selectHits(listOf(0))
        assertEquals(PhotoTarget.OBJECT, session.target)
        val snapshot = checkNotNull(session.snapshotTarget())
        assertEquals(ImageCrop(144, 108, 496, 372), snapshot.crop)
        assertEquals(PhotoTarget.OBJECT, snapshot.target)
        assertEquals(0, exports)
        assertTrue(gateway.sent.isEmpty())
    }

    @Test fun selectionCancelsTheOldFrameFlowSynchronouslyAndRejectsItsLateAnswer() {
        ready()
        model.connect { }
        val pending = CompletableDeferred<String>()
        gateway.responseGate = pending
        actions.send(checkNotNull(session.snapshotTarget()), false)
        assertTrue(model.state.busy)
        session.interaction!!.selectHits(listOf(0))
        assertFalse(model.state.busy)
        assertNull(actions.image)
        pending.complete("Obsolete frame")
        assertNull(model.state.response)
        assertEquals(1, gateway.sent.size)
        assertEquals(1, exports)
    }

    @Test fun emptyTapWithoutASelectionKeepsTheExistingFrameAnswerAndOwnedPreview() {
        ready()
        model.connect { }
        actions.send(checkNotNull(session.snapshotTarget()), false)
        val key = session.documentKey
        val preview = actions.image
        val response = model.state.response
        session.interaction!!.selectHits(emptyList())
        assertEquals(key, session.documentKey)
        assertSame(preview, actions.image)
        assertNotNull(response)
        assertEquals(response, model.state.response)
        assertEquals(1, gateway.sent.size)
    }

    @Test fun retappingAnObjectReturnsToTheCurrentZoomedFrameWithoutAnotherExport() {
        ready()
        session.interaction!!.selectHits(listOf(0))
        assertEquals(PhotoTarget.OBJECT, session.target)
        val selected = checkNotNull(session.snapshotTarget())
        session.interaction!!.move(Offset(320f, 240f), Offset.Zero, 2f)
        assertEquals(selected.key, session.documentKey)
        session.interaction!!.selectHits(listOf(0))
        assertEquals(PhotoTarget.FRAME, session.target)
        val frame = checkNotNull(session.snapshotTarget())
        assertEquals(ImageCrop(160, 120, 480, 360), frame.crop)
        assertNotEquals(selected.key, frame.key)
        assertEquals(0, exports)
    }

    @Test fun invalidSelectedObjectNeverFallsBackToTheWholeFrame() {
        session.accept(
            capturePhoto(),
            VisionAnalyzer {
                AnalysisResult.Success(
                    listOf(captureDetection().copy(box = ImageBox(900f, 900f, 1000f, 1000f)))
                )
            }
        )
        session.interaction!!.size = Size(640f, 480f)
        session.interaction!!.selectHits(listOf(0))
        assertNull(session.snapshotTarget())
        assertEquals(PhotoTarget.OBJECT, session.target)
        assertNotNull(session.targeting.message)
        assertEquals(0, exports)
    }

    private fun ready() {
        session.accept(
            capturePhoto(),
            VisionAnalyzer {
                AnalysisResult.Success(listOf(captureDetection()))
            }
        )
        session.interaction!!.size = Size(640f, 480f)
    }
}
