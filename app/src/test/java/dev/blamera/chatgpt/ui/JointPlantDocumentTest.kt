package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.TrialAppForeground
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class JointPlantDocumentTest {
    private val gateway = FakeTrialGateway()
    private val model =
        ChatGptTrialViewModel(
            gateway,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            appForeground = TrialAppForeground {
                0L
            }
        )

    @After fun close() = model.close()

    @Test fun replacingTheDocumentWithTheSameKeyRejectsThePreviousUncancelableAnswer() {
        model.connect { }
        val key = ObjectDocumentKey(Any(), 1, -1)
        model.objects.select(key)
        model.objects.prepare(key, byteArrayOf(1), FRAME_CHATGPT_INSTRUCTION)
        val pending = CompletableDeferred<String>()
        gateway.responseGate = pending
        model.objects.send()
        model.objects.prepare(key, byteArrayOf(2), FRAME_CHATGPT_INSTRUCTION, replace = true)
        assertFalse(model.state.busy)
        pending.complete("Obsolete request")
        assertNull(model.state.response)
        gateway.responseGate = null
        model.objects.send()
        assertEquals(2, gateway.sent.size)
    }

    @Test fun anExplicitNewRequestCanRefreshItsPromptEvenWhenThePixelKeyIsUnchanged() {
        model.connect { }
        val key = ObjectDocumentKey(Any(), 1, -1)
        model.objects.select(key)
        model.objects.prepare(key, byteArrayOf(1), FRAME_CHATGPT_INSTRUCTION)
        model.objects.send()
        val old = model.objects.document
        val enriched = FRAME_CHATGPT_INSTRUCTION + "\nIndice botanique : Rosa canina"
        model.objects.prepare(key, byteArrayOf(2), enriched, replace = true)
        assertNotSame(old, model.objects.document)
        model.objects.send()
        assertEquals(enriched, gateway.sent.last().instruction)
        assertEquals(2, gateway.sent.size)
    }
}
