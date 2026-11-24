package dev.blamera.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.blamera.image.PhotoCropper
import dev.blamera.image.PhotoLoadResult
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureObjectIdentityTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val session = CaptureSession(scope)
    private val analyzer = VisionAnalyzer {
        AnalysisResult.Success(listOf(captureDetection(), captureDetection()))
    }

    @After fun close() = scope.cancel()

    @Test fun selectionChangesNotifySynchronouslyAndNewPhotosNeverReuseAnIndexIdentity() {
        val notifications = mutableListOf<dev.blamera.chatgpt.ui.ObjectDocumentKey?>()
        session.onDocumentChanged = notifications::add
        session.accept(capturePhoto(), analyzer)
        assertNotNull(session.documentKey)
        session.interaction!!.selectHits(listOf(0, 1))
        val first = session.documentKey
        assertNotNull(first)
        assertEquals(first, notifications.last())
        session.interaction!!.select(1)
        assertNotEquals(first, session.documentKey)
        assertEquals(session.documentKey, notifications.last())
        session.interaction!!.select(1)
        assertEquals(PhotoTarget.FRAME, session.target)
        assertNotNull(notifications.last())
        session.interaction!!.selectHits(listOf(0))
        val before = session.documentKey
        session.accept(capturePhoto(), analyzer)
        assertNull(notifications.last())
        session.interaction!!.selectHits(listOf(0))
        assertNotEquals(before, session.documentKey)
        session.interaction!!.selectHits(listOf(99))
        assertEquals(PhotoTarget.FRAME, session.target)
        assertNotNull(session.documentKey)
        assertNotNull(notifications.last())
    }

    @Test fun loadingRegionOriginalAndCameraInvalidateBeforeAnyNewPreparation() {
        val notifications = mutableListOf<dev.blamera.chatgpt.ui.ObjectDocumentKey?>()
        session.onDocumentChanged = notifications::add
        session.accept(capturePhoto(), analyzer)
        session.interaction!!.selectHits(listOf(0))
        val pending = CompletableDeferred<PhotoLoadResult>()
        session.capture({ pending.await() }, analyzer)
        assertNull(session.documentKey)
        assertTrue("Loading invalidates the document synchronously", notifications.isNotEmpty())
        assertNull(notifications.last())
        pending.complete(PhotoLoadResult.Loaded(capturePhoto()))
        session.interaction!!.selectHits(listOf(0))
        val whole = session.documentKey
        val interaction = session.interaction!!
        interaction.size = Size(640f, 480f)
        interaction.move(Offset(320f, 240f), Offset.Zero, 2f)
        session.analyzeVisible(
            analyzer,
            PhotoCropper { _, crop ->
                PhotoLoadResult.Loaded(capturePhoto(crop.width, crop.height))
            }
        )
        assertNull(notifications.last())
        session.interaction!!.selectHits(listOf(0))
        assertNotEquals(whole, session.documentKey)
        session.restoreOriginal()
        assertNull(notifications.last())
        session.interaction!!.selectHits(listOf(0))
        assertNotEquals(whole, session.documentKey)
        session.navigate(CaptureScreen.CAMERA)
        assertNull(notifications.last())
        session.interaction!!.selectHits(listOf(0))
        assertNull(session.documentKey)
    }
}
