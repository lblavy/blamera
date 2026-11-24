package dev.blamera.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import dev.blamera.chatgpt.ui.FRAME_CHATGPT_INSTRUCTION
import dev.blamera.chatgpt.ui.FakeTrialGateway
import dev.blamera.chatgpt.ui.OBJECT_CHATGPT_INSTRUCTION
import dev.blamera.image.ImageCrop
import dev.blamera.information.AndroidSearchImagePreparer
import dev.blamera.information.FakeSharedImage
import dev.blamera.information.ObjectInformationTools
import dev.blamera.information.ObjectSearchState
import dev.blamera.information.SearchImage
import dev.blamera.information.SearchImagePreparer
import dev.blamera.information.SearchImagePublisher
import dev.blamera.information.SharedSearchImage
import dev.blamera.information.fakeImageShareSender
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.ImageBox
import dev.blamera.vision.VisionAnalyzer
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
class PhotoTargetActionsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val gateway = FakeTrialGateway()
    private val model =
        ChatGptTrialViewModel(gateway, scope = scope, appForeground = TrialAppForeground { 0L })
    private val session = CaptureSession(scope)
    private val crops = mutableListOf<ImageCrop>()
    private val jpeg = byteArrayOf(1, 2, 3)
    private var pending: CompletableDeferred<SearchImage>? = null
    private var failure: Throwable? = null
    private var published = 0
    private var opened = 0
    private val entry = FakeSharedImage()
    private val tools = ObjectInformationTools(
        object : SearchImagePreparer {
            override suspend fun prepare(source: Bitmap, box: ImageBox): SearchImage =
                error("Exact region required")
            override suspend fun prepareRegion(source: Bitmap, crop: ImageCrop): SearchImage {
                crops.add(crop)
                failure?.let { throw it }
                return pending?.let { withContext(NonCancellable) { it.await() } } ?: image()
            }
        },
        SearchImagePublisher {
            published++
            entry
        },
        fakeImageShareSender { opened++ }
    )
    private val actions = session.information(tools, model)
    private val analyzer = VisionAnalyzer { AnalysisResult.Success(listOf(captureDetection())) }

    @After fun close() {
        session.close()
        model.close()
        scope.cancel()
    }

    @Test fun noPreparationBeforeClickAndChatGptGetsOnlyTheExactFrameAndFrameInstruction() {
        ready()
        model.connect { }
        assertTrue(crops.isEmpty())
        assertTrue(gateway.sent.isEmpty())
        val snapshot = checkNotNull(session.snapshotTarget())
        actions.send(snapshot, lens = false)
        assertEquals(listOf(ImageCrop(0, 0, 640, 480)), crops)
        assertArrayEquals(jpeg, gateway.sent.single().jpeg)
        assertEquals(FRAME_CHATGPT_INSTRUCTION, gateway.sent.single().instruction)
        assertEquals(0, published)
        session.interaction!!.selectHits(emptyList())
        assertNotNull(model.state.response)
    }

    @Test fun objectHasTheSamePaddedPixelTargetForLensAndChatGptAndKeepsTheObjectPrompt() {
        ready()
        model.connect { }
        session.interaction!!.selectHits(listOf(0))
        val snapshot = checkNotNull(session.snapshotTarget())
        actions.send(snapshot, lens = false)
        assertEquals(OBJECT_CHATGPT_INSTRUCTION, gateway.sent.single().instruction)
        actions.send(checkNotNull(session.snapshotTarget()), lens = true)
        assertEquals(listOf(snapshot.crop, snapshot.crop), crops)
        assertEquals(1, opened)
        session.interaction!!.selectHits(emptyList())
        assertFalse("Already handed Lens file must remain available", entry.closed)
        assertNull(model.state.response)
        assertNull(model.objects.document)
    }

    @Test fun rotationKeepsInFlightDocumentThenNextExplicitSendUsesFreshViewportPixels() {
        ready()
        model.connect { }
        gateway.responseGate = CompletableDeferred()
        session.interaction!!.move(Offset(320f, 240f), Offset.Zero, 2f)
        actions.send(checkNotNull(session.snapshotTarget()), false)
        val sent = model.objects.document
        session.interaction!!.size = Size(640f, 640f)
        assertSame(sent, model.objects.document)
        assertTrue(model.state.busy)
        gateway.responseGate!!.complete("Réponse actuelle")
        assertEquals("Réponse actuelle", model.state.response)
        actions.send(checkNotNull(session.snapshotTarget()), false)
        assertEquals(listOf(ImageCrop(160, 120, 480, 360), ImageCrop(160, 80, 480, 400)), crops)
        assertEquals(2, gateway.sent.size)
    }

    @Test fun gestureCancelsTheCurrentFrameAndRejectsItsLateAnswer() {
        ready()
        model.connect { }
        val response = CompletableDeferred<String>()
        gateway.responseGate = response
        actions.send(checkNotNull(session.snapshotTarget()), false)
        session.interaction!!.move(Offset(320f, 240f), Offset.Zero, 2f)
        assertFalse(model.state.busy)
        assertNull(actions.image)
        response.complete("Obsolète")
        assertNull(model.state.response)
        assertEquals(1, gateway.sent.size)
    }

    @Test fun replacingThePhotoDisposesNonCancellableLateJpegAndNeverSendsIt() {
        ready()
        model.connect { }
        pending = CompletableDeferred()
        val deferred = checkNotNull(pending)
        actions.send(checkNotNull(session.snapshotTarget()), false)
        assertTrue(actions.preparing)
        session.accept(capturePhoto(), analyzer)
        val obsolete = image()
        deferred.complete(obsolete)
        assertTrue(obsolete.preview.isRecycled)
        assertFalse(session.photo!!.bitmap.isRecycled)
        assertTrue(gateway.sent.isEmpty())
        assertFalse(actions.preparing)
    }

    @Test fun cancellationAndMemoryOrEncodingFailureRemainRetryableWithoutAnyTransfer() {
        ready()
        model.connect { }
        for (error in listOf(IOException("fixture"), OutOfMemoryError("fixture"))) {
            failure = error
            actions.send(checkNotNull(session.snapshotTarget()), false)
            assertNotNull(actions.message)
            assertFalse(actions.preparing)
            assertTrue(gateway.sent.isEmpty())
        }
        failure = null
        pending = CompletableDeferred()
        actions.send(checkNotNull(session.snapshotTarget()), false)
        actions.cancel()
        val late = image()
        pending!!.complete(late)
        assertTrue(late.preview.isRecycled)
        assertTrue(gateway.sent.isEmpty())
        pending = null
        actions.send(checkNotNull(session.snapshotTarget()), false)
        assertEquals(1, gateway.sent.size)
    }

    private fun ready() {
        session.accept(capturePhoto(), analyzer)
        session.interaction!!.size = Size(640f, 480f)
    }
    private fun image() = SearchImage(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888), jpeg)
}
