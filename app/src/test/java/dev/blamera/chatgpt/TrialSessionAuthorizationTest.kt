package dev.blamera.chatgpt

import dev.blamera.chatgpt.storage.SessionStorageException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialSessionAuthorizationTest {
    @Test fun ephemeralDiagnosticsNeverReadClearOrUnblockThePrincipalSession() = runBlocking {
        val fixture = SessionFixture()
        fixture.store.value = "synthetic principal session"
        fixture.registration.sessionBlocked = true
        val diagnostic = fixture.gateway(persistent = false)
        assertFalse(diagnostic.retainsSession)
        assertNull(diagnostic.preferredModel)
        assertNull(diagnostic.restore())
        fixture.connect(diagnostic)
        diagnostic.models()
        diagnostic.rememberModel("preferred")
        assertEquals("preferred", diagnostic.preferredModel)
        diagnostic.disconnect()
        assertTrue(fixture.registration.sessionBlocked)
        assertEquals("synthetic principal session", fixture.store.value)
        diagnostic.close()
        assertNull(diagnostic.preferredModel)
    }

    @Test fun aRejectedBearerIsClearedInEitherSessionModeWithoutAutomaticRetry() = runBlocking {
        for (persistent in listOf(false, true)) {
            val fixture = SessionFixture()
            val subject = fixture.gateway(persistent = persistent)
            fixture.connect(subject)
            subject.models()
            fixture.alter = { request, response ->
                if (request.url.endsWith("models")) TrialHttpResponse(401, "{}") else response
            }
            val count = fixture.requests.size
            expect(TrialFailure.AUTHENTICATION) { subject.models() }
            assertEquals(count + 1, fixture.requests.size)
            assertNull(fixture.store.value)
            expect(TrialFailure.AUTHENTICATION) { subject.models() }
            subject.close()
        }
    }

    @Test fun aRevokedRecordThatCannotBeDeletedCannotBeRestored() = runBlocking {
        val fixture = SessionFixture()
        val subject = fixture.gateway()
        fixture.connect(subject)
        fixture.store.beforeClear = { throw SessionStorageException() }
        fixture.alter = { request, response ->
            if (request.url.endsWith("models")) TrialHttpResponse(401, "{}") else response
        }
        expect(TrialFailure.STORAGE) { subject.models() }
        assertNotNull(fixture.store.value)
        assertNull(
            "Rejected credentials must stay blocked across restart",
            fixture.gateway().restore()
        )
        subject.close()
    }

    @Test fun aLateRejectionCannotClearOrOverwriteAMoreRecentlyRotatedGrant() = runBlocking {
        val fixture = SessionFixture()
        val stale = fixture.gateway()
        fixture.connect(stale)
        stale.models()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.before = { request ->
            if (request.url.endsWith("models") && request.bearer == "access-0") {
                entered.complete(Unit)
                release.await()
            }
        }
        fixture.alter = { request, response ->
            if (request.url.endsWith("models") && request.bearer == "access-0") {
                TrialHttpResponse(401, "{}")
            } else {
                response
            }
        }
        val rejected = async { runCatching { stale.models() } }
        entered.await()
        fixture.now = 4000
        val current = fixture.gateway()
        assertNotNull(current.restore())
        release.complete(Unit)
        assertEquals(
            TrialFailure.AUTHENTICATION,
            (rejected.await().exceptionOrNull() as ChatGptTrialException).kind
        )
        assertTrue(fixture.store.value!!.contains("refresh-1"))
        assertFalse(fixture.registration.sessionBlocked)
        assertTrue(current.models().isNotEmpty())
        stale.rememberModel("preferred")
        assertTrue(fixture.store.value!!.contains("refresh-1"))
        current.disconnect()
        expect(TrialFailure.AUTHENTICATION) { stale.rememberModel("first") }
        stale.close()
        current.close()
    }

    private suspend fun expect(kind: TrialFailure, work: suspend () -> Unit) {
        try {
            work()
            fail("Expected $kind")
        } catch (
            failure: ChatGptTrialException
        ) {
            assertEquals(kind, failure.kind)
        }
    }
}
