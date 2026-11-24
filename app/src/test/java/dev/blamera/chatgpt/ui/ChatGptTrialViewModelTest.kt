package dev.blamera.chatgpt.ui

import androidx.lifecycle.ViewModelStore
import dev.blamera.chatgpt.ChatGptTrialException
import dev.blamera.chatgpt.ChatGptTrialGateway
import dev.blamera.chatgpt.ConnectedAccount
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.TrialFailure
import dev.blamera.chatgpt.TrialModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptTrialViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val gateway = FakeTrialGateway()
    private val jpeg = byteArrayOf(1, 2, 3)
    private val model = ChatGptTrialViewModel(
        gateway,
        scope,
        appForeground = TrialAppForeground { 0L }
    ) { jpeg }

    @After fun tearDown() {
        model.close()
        scope.cancel()
    }

    @Test fun openingAndConnectingNeverUploadsAnImage() {
        assertEquals(0, gateway.connections)
        val opened = mutableListOf<String>()
        model.connect(opened::add)
        assertEquals(listOf("https://auth.openai.com/fixture"), opened)
        assertEquals("Compte de test", model.state.account?.displayName)
        assertEquals("vision-fixture", model.state.selectedModel)
        assertEquals(0, gateway.sent.size)
        assertTrue(model.state.canAnalyze)
    }

    @Test fun analysisRequiresAnAuthorizedAccountAndKnownModelBeforeExplicitSend() {
        model.analyze()
        assertTrue(gateway.sent.isEmpty())
        model.connect {}
        assertTrue(gateway.sent.isEmpty())
        model.selectModel("unknown")
        assertEquals("vision-fixture", model.state.selectedModel)

        assertTrue(model.state.canAnalyze)
        model.analyze()
        assertEquals(1, gateway.sent.size)
        assertArrayEquals(jpeg, gateway.sent.single().jpeg)
        assertEquals(TRIAL_INSTRUCTION, gateway.sent.single().instruction)
        assertEquals("vision-fixture", gateway.sent.single().model)
        assertEquals("Une tasse bleue.", model.state.response)
    }

    @Test fun identityWithoutPlanPermissionCannotListModelsOrSend() {
        gateway.account = ConnectedAccount("Identité seule", false)
        model.connect {}

        model.analyze()
        assertEquals(0, gateway.modelLists)
        assertFalse(model.state.canAnalyze)
        assertTrue(gateway.sent.isEmpty())
        assertTrue(model.state.status.contains("non autorisé"))
    }

    @Test fun noModelsIsRecoverableAndDoesNotGuessAModel() {
        gateway.availableModels = emptyList()
        model.connect {}

        model.analyze()
        assertNull(model.state.selectedModel)
        assertFalse(model.state.canAnalyze)
        assertTrue(model.state.status.contains("Aucun modèle"))
    }

    @Test fun concurrentTapsAndCancelledLateResponseNeverPublishOrRetry() {
        gateway.responseGate = CompletableDeferred()
        model.connect {}

        model.analyze()
        model.analyze()
        model.connect {}
        model.selectModel("other-fixture")

        assertTrue(model.state.busy)
        assertEquals(1, gateway.sent.size)
        assertEquals(1, gateway.connections)
        model.cancel()
        gateway.responseGate!!.complete("Réponse périmée")
        assertNull(model.state.response)
        assertFalse(model.state.busy)
        assertEquals("Essai annulé.", model.state.status)
        assertEquals(1, gateway.sent.size)
    }

    @Test fun quotaFailureIsVisibleAndDoesNotRetryOrExposeServerText() {
        gateway.analysisFailure = ChatGptTrialException(TrialFailure.USAGE_LIMIT)
        model.connect {}

        model.analyze()
        assertEquals(TrialFailure.USAGE_LIMIT.message, model.state.error)
        assertEquals(1, gateway.sent.size)
        assertNull(model.state.response)
        assertFalse(model.state.busy)
    }

    @Test fun unknownFailuresNeverExposeTokensOrPersonalData() {
        gateway.analysisFailure = IllegalStateException("secret-token personal-image")
        model.connect {}

        model.analyze()
        assertFalse(checkNotNull(model.state.error).contains("secret-token"))
        assertFalse(checkNotNull(model.state.error).contains("personal-image"))
    }

    @Test fun disconnectClearsSessionWithoutAutomaticReconnectOrSend() {
        model.connect {}

        model.disconnect()
        assertEquals(1, gateway.disconnections)

        assertNull(model.state.account)
        model.connect {}

        assertEquals(2, gateway.connections)
        assertTrue(gateway.sent.isEmpty())
    }

    @Test fun closeCancelsPendingSignInAndClearsSession() {
        gateway.connectionGate = CompletableDeferred()
        model.connect {}
        assertTrue(model.state.busy)
        model.close()
        gateway.connectionGate!!.complete(ConnectedAccount("Late", true))
        assertNull(model.state.account)
        assertFalse(model.state.busy)
        assertTrue(gateway.closed)
        model.connect {}
        assertEquals(1, gateway.connections)
    }

    @Test fun clearingRetainedViewModelClosesTheGatewayAndClearsTheAccount() {
        val store = ViewModelStore()
        store.put("trial", model)
        model.connect {}

        model.browserUnavailable()
        assertEquals("Impossible d’ouvrir le navigateur.", model.state.error)
        store.clear()
        assertTrue(gateway.closed)

        assertNull(model.state.account)
    }

    @Test fun cancellationRejectsABrowserOpeningFromAnUncooperativeLateCallback() {
        gateway.connectionGate = CompletableDeferred()
        gateway.openBrowserAfterGate = true
        val browser = mutableListOf<String>()
        model.connect(browser::add)
        model.cancel()
        gateway.connectionGate!!.complete(ConnectedAccount("Late", true))
        assertTrue(browser.isEmpty())
    }

    @Test fun expectedBoundaryFailuresAreSanitizedWithoutAutomaticRetries() {
        model.connect {}

        listOf(IllegalArgumentException("private-image"), java.io.IOException("secret-token"))
            .forEachIndexed { index, failure ->
                gateway.analysisFailure = failure
                model.analyze()
                assertEquals("L’essai a échoué. Vous pouvez réessayer.", model.state.error)
                assertEquals(index + 1, gateway.sent.size)
                assertNull(model.state.response)
            }
    }
}

internal class FakeTrialGateway : ChatGptTrialGateway {
    data class Sent(val model: String, val jpeg: ByteArray, val instruction: String)
    var connections = 0
    var disconnections = 0
    var modelLists = 0
    var cancellations = 0
    var closed = false
    var account = ConnectedAccount("Compte de test", true)
    var availableModels = listOf(
        TrialModel("vision-fixture", "Vision de test"),
        TrialModel("other-fixture", "Autre modèle")
    )
    var connectionGate: CompletableDeferred<ConnectedAccount>? = null
    var responseGate: CompletableDeferred<String>? = null
    var analysisFailure: Exception? = null
    var openBrowserAfterGate = false
    val phaseObservers = mutableListOf<(dev.blamera.chatgpt.TrialConnectionPhase) -> Unit>()
    var modelsGate: CompletableDeferred<List<TrialModel>>? = null
    val sent = mutableListOf<Sent>()
    override suspend fun connect(
        onPhase: (dev.blamera.chatgpt.TrialConnectionPhase) -> Unit,
        openBrowser: (String) -> Unit
    ): ConnectedAccount {
        connections++
        phaseObservers.add(onPhase)
        onPhase(dev.blamera.chatgpt.TrialConnectionPhase.WAITING_BROWSER)
        if (!openBrowserAfterGate) openBrowser("https://auth.openai.com/fixture")
        return connectionGate?.let {
            withContext(NonCancellable) {
                it.await().also {
                    if (openBrowserAfterGate) openBrowser("https://auth.openai.com/fixture")
                }
            }
        } ?: account
    }
    override suspend fun models(): List<TrialModel> {
        modelLists++
        return modelsGate?.let { withContext(NonCancellable) { it.await() } } ?: availableModels
    }
    override suspend fun analyze(model: String, jpeg: ByteArray, instruction: String): String {
        sent.add(Sent(model, jpeg, instruction))
        analysisFailure?.let { throw it }
        return responseGate?.let { withContext(NonCancellable) { it.await() } }
            ?: "Une tasse bleue."
    }
    override fun cancel() {
        cancellations++
    }
    override suspend fun disconnect() {
        disconnections++
    }
    override fun close() {
        closed = true
    }
}
