package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.SessionFixture
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.TrialFailure
import dev.blamera.chatgpt.TrialHttpResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatGptSessionRestartTest {
    @Test fun restartRestoresModelAndAllowsExplicitSendWithoutAnotherBrowser() = runBlocking {
        val fixture = SessionFixture()
        val first = model(fixture)
        first.connect { fixture.browserCount++ }
        idle(first)
        first.selectModel("preferred")
        yield()

        assertTrue(first.state.canAnalyze)
        first.close()
        val second = model(fixture)
        second.restoreOnce()
        idle(second)
        assertNotNull("A fresh ViewModel must restore the protected account", second.state.account)
        assertEquals("preferred", second.state.selectedModel)

        assertTrue(second.state.canAnalyze)
        second.restoreOnce()
        assertEquals(1, fixture.browserCount)
        assertFalse(fixture.requests.any { it.url.endsWith("responses") })

        second.analyze()
        idle(second)
        assertEquals(1, fixture.requests.count { it.url.endsWith("responses") })
        second.close()
    }

    @Test fun aNewPhotoDuringSlowRestorationDoesNotCancelTheAccountOperation() = runBlocking {
        val fixture = SessionFixture()
        val seed = fixture.gateway()
        fixture.connect(seed)
        seed.close()
        val arrived = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.before = {
            if (it.url.endsWith("models")) {
                arrived.complete(Unit)
                release.await()
            }
        }
        val model = model(fixture)
        model.restoreOnce()
        assertTrue("Restoration must start independently of the document", model.state.busy)
        withTimeout(10_000L) { arrived.await() }
        val key = ObjectDocumentKey(Any(), 1, 0)
        model.objects.select(key)
        model.objects.prepare(key, byteArrayOf(1, 2, 3))

        assertTrue(
            "Document selection must leave account restoration in progress",
            model.state.busy
        )
        release.complete(Unit)
        idle(model)
        assertNotNull(model.state.account)
        assertSame(key, model.objects.document?.key)
        assertFalse(fixture.requests.any { it.url.endsWith("responses") })
        model.close()
    }

    @Test fun offlineCatalogRemainsRetryableAndAllowsAChangedModel() = runBlocking {
        val fixture = SessionFixture()
        val seed = fixture.gateway()
        fixture.connect(seed)
        seed.models()
        seed.rememberModel("preferred")
        seed.close()
        fixture.alter =
            { request, response ->
                if (request.url.endsWith("models")) TrialHttpResponse(503, "{}") else response
            }
        val model = model(fixture)
        model.restoreOnce()
        idle(model)
        assertEquals(TrialFailure.SERVICE_UNAVAILABLE.message, model.state.error)
        assertTrue(model.state.restoreAvailable)
        assertNotNull(fixture.store.value)
        fixture.alter =
            { request, response ->
                if (request.url.endsWith("models")) {
                    TrialHttpResponse(
                        200,
                        """{"models":[{"slug":"replacement",
                        "display_name":"Replacement","visibility":"list"}]}"""
                    )
                } else {
                    response
                }
            }
        model.restore()
        idle(model)
        assertNotNull(model.state.account)
        assertEquals("replacement", model.state.selectedModel)

        assertEquals(1, fixture.browserCount)
        assertFalse(fixture.requests.any { it.url.endsWith("responses") })
        model.close()
    }

    private fun model(fixture: SessionFixture): ChatGptTrialViewModel {
        val foreground = TrialAppForeground { 0L }.apply { update(true) }
        return ChatGptTrialViewModel(
            fixture.gateway(foreground::await),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            appForeground = foreground
        ) {
            byteArrayOf(-1, -40, 1, -1, -39)
        }
    }

    private suspend fun idle(model: ChatGptTrialViewModel) = withTimeout(10_000L) {
        while (model.state.busy) yield()
    }
}
