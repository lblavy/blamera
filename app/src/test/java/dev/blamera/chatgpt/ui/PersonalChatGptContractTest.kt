package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.TrialAppForeground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalChatGptContractTest {
    private val gateway = FakeTrialGateway()
    private val model = ChatGptTrialViewModel(
        gateway,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        appForeground = TrialAppForeground { 0L },
        image = { byteArrayOf(1, 2, 3) }
    )

    @After fun close() = model.close()

    @Test fun aConnectedPersonalAccountCanSendWithoutAConfirmationStep() {
        model.connect {}
        assertTrue(model.state.canAnalyze)
        assertTrue(gateway.sent.isEmpty())
        model.analyze()
        assertEquals(1, gateway.sent.size)
    }

    @Test fun aPreparedSelectedObjectCanBeSentDirectlyWithoutExpandingAnotherForm() {
        model.connect {}
        val key = ObjectDocumentKey(Any(), 0, 0)
        model.objects.select(key)
        model.objects.prepare(key, byteArrayOf(4, 5, 6))
        assertTrue(gateway.sent.isEmpty())
        model.objects.send()
        assertEquals(1, gateway.sent.size)
        assertEquals(OBJECT_CHATGPT_INSTRUCTION, gateway.sent.single().instruction)
    }
}
