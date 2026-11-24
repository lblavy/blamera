package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.ChatGptTrialException
import dev.blamera.chatgpt.ChatGptTrialGateway
import dev.blamera.chatgpt.ConnectedAccount
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.TrialConnectionPhase
import dev.blamera.chatgpt.TrialFailure
import dev.blamera.chatgpt.TrialModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalStreamingStateTest {
    private val gateway = StreamingTrialGateway()
    private val model = ChatGptTrialViewModel(
        gateway,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        appForeground = TrialAppForeground { 0L },
        image = { byteArrayOf(1, 2, 3) }
    )

    @After fun close() {
        model.close()
        gateway.completion.complete("Finished after close")
    }

    @Test fun textAppearsWhileTheRequestStaysBusyUntilCompleted() = runBlocking {
        ready()
        model.analyze()
        assertTrue("A streaming request must wait for completion", model.state.busy)
        gateway.emit("**Tasse**")
        assertEquals("**Tasse**", model.state.response)
        assertEquals("gpt-6.1-sol", model.state.responseModel)
        assertTrue(model.state.responseIncomplete)
        assertTrue(model.state.busy)
        gateway.completion.complete("**Tasse** bleue.")
        assertFalse(model.state.busy)
        assertFalse(model.state.responseIncomplete)
        assertEquals("**Tasse** bleue.", model.state.response)
        assertEquals("Analyse terminée.", model.state.status)
        assertEquals(1, gateway.sends)
    }

    @Test fun cancellationKeepsThePartialTextAndRejectsLateChunksAndCompletion() = runBlocking {
        ready()
        model.analyze()
        gateway.emit("Premier fragment")
        model.cancel()
        gateway.emit("Fragment tardif après arrêt")
        gateway.completion.complete("Terminé trop tard")
        assertEquals("Premier fragment", model.state.response)
        assertTrue(model.state.responseIncomplete)
        assertFalse(model.state.busy)
        assertEquals(1, gateway.sends)
    }

    @Test fun anotherSelectionAndCloseBothDiscardTheOldDocumentsStream() = runBlocking {
        ready()
        val first = ObjectDocumentKey(Any(), 1, 0)
        model.objects.select(first)
        model.objects.prepare(first, byteArrayOf(4, 5))

        model.objects.send()
        gateway.emit("Ancien objet")
        model.objects.select(first.copy(index = 1))
        gateway.emit("Ancien objet tardif")
        assertNull(model.state.response)
        assertNull(model.objects.document)
        model.close()
        gateway.emit("Après fermeture")
        gateway.completion.complete("Après fermeture")
        assertNull(model.state.response)
    }

    @Test fun failedTerminalEventPreservesTheLastKnownTextAsIncompleteWithoutRetry() = runBlocking {
        ready()
        model.analyze()
        gateway.emit("Réponse déjà reçue")
        gateway.completion.completeExceptionally(ChatGptTrialException(TrialFailure.NETWORK))
        assertEquals("Réponse déjà reçue", model.state.response)
        assertTrue(model.state.responseIncomplete)
        assertEquals(TrialFailure.NETWORK.message, model.state.error)
        assertFalse(model.state.busy)
        assertEquals(1, gateway.sends)
    }

    @Test fun reasoningChoiceAndModelChangesNeverSubmitAndPersistOnlySupportedValues() {
        ready()
        model.selectReasoningEffort("high")
        assertEquals("high", model.state.selectedReasoningEffort)
        assertEquals("high", gateway.preferredReasoningEffort)
        model.selectReasoningEffort("arbitrary")
        assertEquals("high", model.state.selectedReasoningEffort)
        model.selectModel("unfamiliar")
        assertNull(model.state.selectedReasoningEffort)
        assertEquals(0, gateway.sends)
    }

    @Test fun preferredReasoningEffortIsRestoredWhenTheChosenModelSupportsIt() {
        gateway.preferredReasoningEffort = "high"
        model.connect {}
        assertEquals("high", model.state.selectedReasoningEffort)
        assertEquals(0, gateway.sends)
    }

    @Test fun explicitSendCapturesEffortAndDoesNotAllowChangesDuringTheStream() = runBlocking {
        ready()
        model.selectReasoningEffort("high")
        model.analyze()
        assertTrue(model.state.busy)
        model.selectReasoningEffort("low")
        model.selectModel("unfamiliar")
        assertEquals("high", gateway.sentEffort)
        assertEquals("gpt-6.1-sol", model.state.selectedModel)
        gateway.completion.complete("Terminé")
        assertFalse(model.state.busy)
    }

    @Test fun authenticationFailureOffersARealReconnectWithoutResendingTheObject() {
        ready()
        val key = ObjectDocumentKey(Any(), 1, 0)
        model.objects.select(key)
        model.objects.prepare(key, byteArrayOf(4, 5))
        val document = model.objects.document
        gateway.failure = ChatGptTrialException(TrialFailure.AUTHENTICATION)
        model.analyze(byteArrayOf(4, 5), OBJECT_CHATGPT_INSTRUCTION)
        assertNull(
            "An expired RAM account must not block explicit reconnection",
            model.state.account
        )
        assertSame(document, model.objects.document)
        assertEquals(1, gateway.sends)
        gateway.failure = null
        model.connect {}
        assertEquals(2, gateway.connections)
        assertEquals(1, gateway.sends)
        assertTrue(model.state.canAnalyze)
    }

    private fun ready() {
        model.connect {}
    }
}

internal class StreamingTrialGateway : ChatGptTrialGateway {
    override var preferredModel: String? = "gpt-6.1-sol"
    override var preferredReasoningEffort: String? = null
    var connections = 0
    var sends = 0
    var sentEffort: String? = null
    var failure: ChatGptTrialException? = null
    val completion = CompletableDeferred<String>()
    private var textReceiver: (suspend (String) -> Unit)? = null
    override suspend fun connect(
        onPhase: (TrialConnectionPhase) -> Unit,
        openBrowser: (String) -> Unit
    ): ConnectedAccount {
        connections++
        return ConnectedAccount("Compte personnel", true)
    }
    override suspend fun models() = listOf(
        TrialModel("gpt-6.1-sol", "GPT-6.1 Sol"),
        TrialModel("unfamiliar", "Autre modèle")
    )
    override suspend fun rememberModel(slug: String) {
        preferredModel = slug
    }
    override suspend fun rememberReasoningEffort(effort: String?) {
        preferredReasoningEffort =
            effort
    }
    override suspend fun analyze(model: String, jpeg: ByteArray, instruction: String): String {
        sends++
        failure?.let { throw it }
        return "Legacy non-streaming route"
    }
    override suspend fun analyzeStreaming(
        model: String,
        jpeg: ByteArray,
        instruction: String,
        effort: String?,
        onText: suspend (String) -> Unit
    ): String {
        sends++
        sentEffort = effort
        failure?.let { throw it }
        textReceiver = onText
        return withContext(NonCancellable) { completion.await() }
    }
    suspend fun emit(text: String) {
        val receiver = checkNotNull(textReceiver) { "The ViewModel must start the streaming route" }
        withContext(NonCancellable) { receiver(text) }
    }
    override fun cancel() = Unit
    override suspend fun disconnect() = Unit
    override fun close() = Unit
}
