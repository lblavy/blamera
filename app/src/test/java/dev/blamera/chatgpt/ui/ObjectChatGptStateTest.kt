package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.ChatGptTrialException
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.TrialFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectChatGptStateTest {
    private val gateway = FakeTrialGateway()
    private val model = ChatGptTrialViewModel(
        gateway,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        appForeground = TrialAppForeground { 0L }
    ) { error("Inline requests must never create the synthetic image") }
    private val objects = model.objects
    private val source = Any()
    private val first = ObjectDocumentKey(source, 1, 0)
    private val jpeg = byteArrayOf(4, 5, 6)

    @After fun close() = model.close()

    @Test fun prepareConnectAndModelChoiceDoNotSendWithoutAnExplicitAction() {
        objects.select(first)

        objects.send()

        assertTrue(gateway.sent.isEmpty())
        objects.prepare(first, jpeg)
        objects.send()
        assertTrue(gateway.sent.isEmpty())

        assertTrue(gateway.sent.isEmpty())
        objects.send()
        model.connect { }
        model.selectModel("other-fixture")
        assertTrue(gateway.sent.isEmpty())

        objects.send()
        assertEquals(1, gateway.sent.size)
        assertArrayEquals(jpeg, gateway.sent.single().jpeg)
        assertEquals(OBJECT_CHATGPT_INSTRUCTION, gateway.sent.single().instruction)
        assertEquals("other-fixture", gateway.sent.single().model)
        assertEquals("Une tasse bleue.", model.state.response)
        assertEquals("other-fixture", model.state.responseModel)
    }

    @Test fun sameDocumentAndRepreparedPreviewKeepTheResponseAndExactOwnedJpeg() {
        ready()
        val retained = objects.document
        jpeg[0] = 99
        objects.send()
        assertEquals(1, gateway.sent.size)
        assertArrayEquals(byteArrayOf(4, 5, 6), gateway.sent.single().jpeg)
        objects.select(first.copy())
        objects.prepare(first, byteArrayOf(9, 9))
        assertSame(retained, objects.document)
        assertEquals("Une tasse bleue.", model.state.response)

        assertEquals(1, gateway.connections)
        assertEquals(1, gateway.sent.size)
    }

    @Test fun selectionAndSameIndexOnANewDocumentCancelLateResponsesButKeepTheSession() {
        ready()
        val pending = CompletableDeferred<String>()
        gateway.responseGate = pending
        objects.send()
        val second = ObjectDocumentKey(source, 1, 1)
        objects.select(second)
        assertFalse(model.state.busy)
        assertNull(objects.document)
        assertNull(model.state.response)

        assertEquals("other-fixture", model.state.selectedModel)
        pending.complete("Old object")
        assertNull(model.state.response)
        objects.prepare(first, byteArrayOf(8))
        assertNull(objects.document)
        objects.prepare(second, byteArrayOf(7))

        gateway.responseGate = null
        objects.send()
        assertEquals(2, gateway.sent.size)
        assertArrayEquals(byteArrayOf(7), gateway.sent.last().jpeg)
        val newPhotoSameIndex = ObjectDocumentKey(source, 2, 1)
        objects.select(newPhotoSameIndex)
        assertNull(model.state.response)
        assertNull(objects.document)
        assertEquals(1, gateway.connections)
        assertEquals(2, gateway.sent.size)
    }

    @Test fun deselectCancelErrorAndRetryNeverSendOrPublishAutomatically() {
        ready()
        val pending = CompletableDeferred<String>()
        gateway.responseGate = pending
        objects.send()
        model.cancel()
        pending.complete("Late cancelled answer")
        assertNull(model.state.response)
        assertEquals(1, gateway.sent.size)
        gateway.responseGate = null
        gateway.analysisFailure = ChatGptTrialException(TrialFailure.USAGE_LIMIT)
        objects.send()
        assertEquals(TrialFailure.USAGE_LIMIT.message, model.state.error)
        assertEquals(2, gateway.sent.size)
        gateway.analysisFailure = null
        objects.send()
        assertEquals("Une tasse bleue.", model.state.response)
        objects.select(null)
        assertNull(objects.document)
        assertNull(model.state.response)
        objects.send()
        assertEquals(3, gateway.sent.size)
        assertTrue(gateway.cancellations > 0)
    }

    @Test fun aClosedModelCannotAcceptANewDocumentOrSend() {
        ready()
        assertTrue(objects.document != null)
        model.close()
        assertNull(objects.document)
        assertNull(objects.key)
        objects.select(first.copy(revision = 2))
        objects.prepare(first, jpeg)

        objects.send()
        assertNull(objects.document)
        assertNull(objects.key)
        assertTrue(gateway.sent.isEmpty())
    }

    @Test fun closingTheObjectSurfaceRejectsItsLateAnswerWhileKeepingTheConnectedSession() {
        ready()
        val pending = CompletableDeferred<String>()
        gateway.responseGate = pending
        objects.send()
        assertTrue(model.state.busy)
        objects.close()
        pending.complete("A closed surface must never receive this answer")
        assertNull(model.state.response)
        assertFalse(model.state.busy)
        assertNull(objects.document)
        assertTrue(model.state.account?.planUsageEnabled == true)
        assertEquals(1, gateway.sent.size)
    }

    @Test fun aChangedCropOrTargetRevisionInvalidatesTheOldJpegAndRejectsLatePreparation() {
        ready()
        val pending = CompletableDeferred<String>()
        gateway.responseGate = pending
        objects.send()
        val cropped = first.copy(crop = dev.blamera.image.ImageCrop(10, 20, 110, 120))
        objects.select(cropped)
        objects.prepare(first, byteArrayOf(99), FRAME_CHATGPT_INSTRUCTION)
        assertNull(objects.document)
        pending.complete("Obsolete crop")
        assertNull(model.state.response)
        gateway.responseGate = null
        objects.prepare(cropped, byteArrayOf(7, 8), FRAME_CHATGPT_INSTRUCTION)
        objects.send()
        assertArrayEquals(byteArrayOf(7, 8), gateway.sent.last().jpeg)
        assertEquals(FRAME_CHATGPT_INSTRUCTION, gateway.sent.last().instruction)
        objects.select(cropped.copy(targetRevision = 1))
        assertNull(objects.document)
        assertNull(model.state.response)
        assertEquals(2, gateway.sent.size)
        assertEquals(1, gateway.connections)
    }

    private fun ready() {
        objects.select(first)
        objects.prepare(first, jpeg)

        model.connect { }
        model.selectModel("other-fixture")
    }
}
