package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.ConnectedAccount
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.TrialConnectionPhase
import dev.blamera.chatgpt.TrialDiagnostic
import dev.blamera.chatgpt.TrialFailure
import dev.blamera.chatgpt.TrialModel
import dev.blamera.chatgpt.TrialTimeoutScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptConnectionProgressTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val gateway = FakeTrialGateway()
    private val alarm = ManualConnectionTimeout()
    private val events = mutableListOf<TrialDiagnostic>()
    private val model = ChatGptTrialViewModel(
        gateway,
        scope,
        timeoutScheduler = alarm,
        diagnostics = events::add,
        appForeground = TrialAppForeground { 0L }
    ) { byteArrayOf(1) }

    @After fun close() {
        model.close()
        gateway.connectionGate?.complete(gateway.account)
        gateway.modelsGate?.complete(gateway.availableModels)
        scope.cancel()
    }

    @Test fun phasesDistinguishBrowserReturnAndModelDiscoveryAndRejectLateEvents() {
        val first = CompletableDeferred<ConnectedAccount>()
        gateway.connectionGate = first
        model.connect { }
        assertEquals(TrialConnectionPhase.WAITING_BROWSER, model.state.connectionPhase)
        assertEquals(TrialConnectionPhase.WAITING_BROWSER.status, model.state.status)
        val oldObserver = gateway.phaseObservers.single()
        model.cancel()
        assertTrue(alarm.cancelled)
        val second = CompletableDeferred<ConnectedAccount>()
        gateway.connectionGate = second
        model.connect { }
        val size = events.size
        oldObserver(TrialConnectionPhase.VERIFYING_ACCOUNT)
        assertEquals(size, events.size)
        assertEquals(TrialConnectionPhase.WAITING_BROWSER, model.state.connectionPhase)
        second.complete(gateway.account)
        assertTrue(events.any { it.phase == TrialConnectionPhase.LOADING_MODELS })
        assertEquals(gateway.account, model.state.account)
        first.complete(gateway.account.copy(displayName = "Late account"))
        assertEquals(gateway.account, model.state.account)
        assertEquals(
            "A cancelled connection must not request a later model catalog",
            1,
            gateway.modelLists
        )
        val completedSize = events.size
        gateway.phaseObservers.last()(TrialConnectionPhase.VERIFYING_ACCOUNT)
        assertEquals(completedSize, events.size)
        model.close()
        val closedSize = events.size
        gateway.phaseObservers.last()(TrialConnectionPhase.EXCHANGING_CODE)
        assertEquals(closedSize, events.size)
    }

    @Test fun globalBudgetIncludesCatalogAndReleasesUiWithoutUploadOrRetry() {
        val models = CompletableDeferred<List<TrialModel>>()
        gateway.modelsGate = models
        model.connect { }
        assertNotNull("A complete connection budget must be armed", alarm.expire)
        assertTrue(model.state.busy)
        assertEquals(TrialConnectionPhase.LOADING_MODELS, model.state.connectionPhase)
        alarm.fire()
        assertFalse(model.state.busy)
        assertEquals(TrialFailure.TIMEOUT.message, model.state.error)
        assertEquals(TrialConnectionPhase.LOADING_MODELS, model.state.connectionPhase)
        assertTrue(gateway.cancellations > 0)

        assertTrue(gateway.sent.isEmpty())
        assertEquals(1, gateway.connections)
        models.complete(gateway.availableModels)
        assertNull(model.state.account)
        assertFalse(model.state.busy)
        assertTrue(alarm.cancelled)
    }

    @Test fun successfulConnectionDisarmsItsAlarmAndCannotExpireLater() {
        model.connect { }
        assertNotNull(alarm.expire)
        assertTrue(alarm.cancelled)
        val state = model.state
        val cancellations = gateway.cancellations
        alarm.fire()
        assertEquals(state, model.state)
        assertEquals(cancellations, gateway.cancellations)
    }

    private class ManualConnectionTimeout : TrialTimeoutScheduler {
        var expire: (() -> Unit)? = null
        var cancelled = false
        override fun schedule(timeoutMillis: Long, expired: () -> Unit): AutoCloseable {
            assertEquals(300_000L, timeoutMillis)
            expire = expired
            cancelled = false
            return AutoCloseable { cancelled = true }
        }
        fun fire() {
            expire?.invoke()
        }
    }
}
