package dev.blamera.chatgpt

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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
class TrialRefreshForegroundTest {
    @Test fun refreshWaitsAfterPreflightWithANewPresenceBudget() = runBlocking {
        val fixture = SessionFixture()
        var elapsed = 0L
        val foreground = TrialAppForeground { elapsed }.apply { update(true) }
        val subject = fixture.gateway(foreground::await, foreground::awaitRefresh)
        fixture.connect(subject)
        fixture.now = 4000
        elapsed = 3_600_000L
        val preflight = CompletableDeferred<Unit>()
        fixture.before = {
            if (it.url.endsWith("openid-configuration")) {
                foreground.update(false)
                preflight.complete(Unit)
            }
        }
        val result = async { subject.models() }
        preflight.await()
        yield()
        assertFalse("No refresh POST while paused after the public preflight", hasRefresh(fixture))
        assertNotNull("The refresh token must remain unconsumed while waiting", fixture.store.value)
        foreground.update(true)
        assertTrue(result.await().isNotEmpty())
        assertEquals(1, fixture.refreshCount)
        subject.close()
    }

    @Test fun expiredElapsedRefreshWaitDoesNotPostEvenBeforeTheTimerIsDelivered() = runBlocking {
        val fixture = SessionFixture()
        var elapsed = 0L
        val foreground = TrialAppForeground { elapsed }.apply { update(true) }
        val subject = fixture.gateway(foreground::await, foreground::awaitRefresh)
        fixture.connect(subject)
        fixture.now = 4000
        val preflight = CompletableDeferred<Unit>()
        fixture.before = {
            if (it.url.endsWith("openid-configuration")) {
                foreground.update(false)
                preflight.complete(Unit)
            }
        }
        val result = async { runCatching { subject.models() } }
        preflight.await()
        yield()
        assertFalse(hasRefresh(fixture))
        elapsed += 300_000L
        foreground.update(true)
        val failure = result.await().exceptionOrNull() as ChatGptTrialException
        assertEquals(TrialFailure.TIMEOUT, failure.kind)
        assertFalse(hasRefresh(fixture))
        assertNotNull(fixture.store.value)
        subject.close()
    }

    private fun hasRefresh(fixture: SessionFixture) =
        fixture.requests.any { it.body?.contains("grant_type=refresh_token") == true }
}
