package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.ManualTrialDispatcher
import dev.blamera.chatgpt.SessionFixture
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.TrialFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatGptRefreshTimeoutTest {
    @Test
    fun theRefreshPresenceTimerEndsAnalysisWithAnExplicitTimeoutAndNoLatePost() = runBlocking {
        val dispatcher = ManualTrialDispatcher()
        val fixture = SessionFixture()
        val foreground = TrialAppForeground { 0L }.apply { update(true) }
        val gateway = fixture.gateway(foreground::await, foreground::awaitRefresh)
        val model = ChatGptTrialViewModel(
            gateway,
            CoroutineScope(SupervisorJob() + dispatcher),
            appForeground = foreground
        ) {
            byteArrayOf(-1, -40, 1, -1, -39)
        }
        try {
            model.connect { }
            withTimeout(10_000L) { while (model.state.busy) yield() }
            assertNotNull(model.state.account)

            fixture.now = 4000
            fixture.before =
                { if (it.url.endsWith("openid-configuration")) foreground.update(false) }
            model.analyze()
            withTimeout(10_000L) { while (!dispatcher.hasAlarm(300_000L)) yield() }
            assertTrue(model.state.busy)
            dispatcher.expire(300_000L)
            withTimeout(10_000L) { while (model.state.busy) yield() }
            assertEquals(TrialFailure.TIMEOUT.message, model.state.error)
            assertEquals("Essai interrompu.", model.state.status)
            foreground.update(true)
            assertEquals(0, fixture.refreshCount)
            assertNotNull(fixture.store.value)
            assertFalse(fixture.requests.any { it.url.endsWith("responses") })
        } finally {
            model.close()
        }
    }

    @Test fun aParentTimeoutIsStillCancellationRatherThanTheRefreshOwnFailure() = runBlocking {
        val dispatcher = ManualTrialDispatcher()
        val foreground = TrialAppForeground { 0L }
        val result =
            async(dispatcher) { runCatching { withTimeout(1_000L) { foreground.awaitRefresh() } } }
        assertTrue(dispatcher.hasAlarm(1_000L))
        assertTrue(dispatcher.hasAlarm(300_000L))
        dispatcher.expire(1_000L)
        assertTrue(result.await().exceptionOrNull() is TimeoutCancellationException)
        assertFalse(dispatcher.hasAlarm(300_000L))
    }

    @Test fun humanCancellationIsNotReclassifiedAsARefreshTimeout() = runBlocking {
        val dispatcher = ManualTrialDispatcher()
        val foreground = TrialAppForeground { 0L }
        val observed = CompletableDeferred<Throwable>()
        val result = async(dispatcher) {
            try {
                foreground.awaitRefresh()
            } catch (error: CancellationException) {
                observed.complete(error)
                throw error
            }
        }
        assertTrue(dispatcher.hasAlarm(300_000L))
        result.cancel(CancellationException("synthetic human cancellation"))
        result.join()
        assertTrue(observed.await() is CancellationException)
        assertFalse(dispatcher.hasAlarm(300_000L))
    }
}
