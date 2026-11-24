package dev.blamera.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import dev.blamera.botany.PlantHttpResponse
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import dev.blamera.chatgpt.ui.FRAME_CHATGPT_INSTRUCTION
import dev.blamera.chatgpt.ui.FakeTrialGateway
import dev.blamera.chatgpt.ui.OBJECT_CHATGPT_INSTRUCTION
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JointPlantTargetTest {
    private val fixture = JointPlantFixture()
    private val session = CaptureSession(fixture.scope, fixture.plants)
    private val gateway = FakeTrialGateway()
    private val model =
        ChatGptTrialViewModel(gateway, fixture.scope, appForeground = TrialAppForeground { 0L })
    private var jpegGate: CompletableDeferred<Unit>? = null
    private val tools = ObjectInformationTools(
        object : SearchImagePreparer {
            override suspend fun prepare(source: Bitmap, box: ImageBox): SearchImage =
                error("Exact crop required")
            override suspend fun prepareRegion(source: Bitmap, crop: ImageCrop): SearchImage {
                jpegGate?.await()
                return SearchImage(
                    Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888),
                    byteArrayOf(1, 2, 3)
                )
            }
        },
        SearchImagePublisher { FakeSharedImage() },
        fakeImageShareSender { }
    )
    private val actions = session.information(tools, model)
    private val local = VisionAnalyzer { AnalysisResult.Success(listOf(captureDetection())) }

    @After fun close() {
        session.close()
        model.close()
        fixture.close()
    }

    @Test fun anAvailableHintIsFrozenAtClickEvenIfKeyRemovalClearsBotanyBeforeJpegReturns() {
        ready()
        ShadowLog.clear()
        jpegGate = CompletableDeferred()
        val snapshot = checkNotNull(session.snapshotTarget())
        assertEquals("Rosa canina", snapshot.plantHint)
        actions.send(snapshot, false)
        fixture.settings.remove()
        assertNull(fixture.plants.snapshot)
        assertTrue(gateway.sent.isEmpty())
        jpegGate!!.complete(Unit)
        assertTrue(gateway.sent.single().instruction.contains("« Rosa canina »"))
        assertEquals(1, fixture.requests)
        assertEquals(listOf("PROMPT_PREPARED_WITH_HINT"), plantLogs())
    }

    @Test fun arrivalDuringJpegPreparationDoesNotEnrichOrResubmitUntilAnotherExplicitClick() {
        val botany = CompletableDeferred<PlantHttpResponse>()
        fixture.response = { botany.await() }
        ready()
        jpegGate = CompletableDeferred()
        val snapshot = checkNotNull(session.snapshotTarget())
        assertNull(snapshot.plantHint)
        actions.send(snapshot, false)
        botany.complete(plantResponse())
        assertEquals("Rosa canina", checkNotNull(session.snapshotTarget()).plantHint)
        assertTrue(gateway.sent.isEmpty())
        jpegGate!!.complete(Unit)
        assertEquals(FRAME_CHATGPT_INSTRUCTION, gateway.sent.single().instruction)
        val first = model.objects.document
        jpegGate = null
        actions.send(checkNotNull(session.snapshotTarget()), false)
        assertEquals(2, gateway.sent.size)
        assertTrue(gateway.sent.last().instruction.contains("« Rosa canina »"))
        assertNotSame(first, model.objects.document)
        assertEquals(1, fixture.requests)
    }

    @Test fun arrivingBotanyKeepsTheAlreadyRunningChatGptDocumentAndNeverSubmitsAgain() {
        val botany = CompletableDeferred<PlantHttpResponse>()
        fixture.response = { botany.await() }
        ready()
        val response = CompletableDeferred<String>()
        gateway.responseGate = response
        actions.send(checkNotNull(session.snapshotTarget()), false)
        val document = model.objects.document
        assertTrue(model.state.busy)
        botany.complete(plantResponse())
        assertSame(document, model.objects.document)
        assertTrue(model.state.busy)
        assertEquals(1, gateway.sent.size)
        response.complete("Réponse sans indice disponible au clic")
        assertEquals(FRAME_CHATGPT_INSTRUCTION, gateway.sent.single().instruction)
        assertEquals("Réponse sans indice disponible au clic", model.state.response)
    }

    @Test fun selectedObjectNeverReceivesTheWholeImagesBotanicalHypothesis() {
        ready()
        val localResult = session.result
        session.interaction!!.selectHits(listOf(0))
        val selected = checkNotNull(session.snapshotTarget())
        assertNull(selected.plantHint)
        actions.send(selected, false)
        assertEquals(OBJECT_CHATGPT_INSTRUCTION, gateway.sent.single().instruction)
        assertEquals("PROMPT_PREPARED_WITHOUT_HINT", plantLogs().last())
        assertFalse(gateway.sent.single().instruction.contains("Rosa canina"))
        assertEquals(localResult, session.result)
        assertEquals(1, fixture.requests)
    }

    @Test fun aClosedChatGptDocumentDoesNotReportAPromptThatWasNeverPrepared() {
        ready()
        jpegGate = CompletableDeferred()
        actions.send(checkNotNull(session.snapshotTarget()), false)
        model.objects.close()
        ShadowLog.clear()
        jpegGate!!.complete(Unit)
        assertTrue(gateway.sent.isEmpty())
        assertTrue(plantLogs().isEmpty())
    }

    private fun plantLogs() = ShadowLog.getLogsForTag("BlameraPlantNet").map { it.msg }

    private fun ready() {
        session.accept(capturePhoto(), local)
        session.interaction!!.size = Size(640f, 480f)
        model.connect { }
    }
}
