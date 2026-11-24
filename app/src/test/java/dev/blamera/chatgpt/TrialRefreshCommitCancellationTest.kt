package dev.blamera.chatgpt

import dev.blamera.chatgpt.storage.AtomicSessionFile
import dev.blamera.chatgpt.storage.EncryptedSessionStore
import dev.blamera.chatgpt.storage.SessionCipher
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.KeyGenerator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialRefreshCommitCancellationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cancellingAnalysisKeepsTheValidatedRotationForRamAndRestart() = runBlocking {
        val encrypted = PausedEncryptedCommit(temporary.root)
        val fixture = encrypted.fixture
        val gateway = fixture.gateway()
        fixture.connect(gateway)
        gateway.models()
        fixture.now = 4000
        encrypted.pause.set(true)
        val analysis =
            async { runCatching { gateway.analyze("first", JPEG, "Synthetic instruction") } }
        try {
            encrypted.awaitWrite()
            analysis.cancel()
            gateway.cancel()
        } finally {
            encrypted.release.countDown()
        }
        analysis.join()
        val stored = fixture.activeStore.load()
        assertNotNull("Cancelling the image must retain the validated rotating grant", stored)
        assertEquals(
            "refresh-1",
            JSONObject(checkNotNull(stored)).getJSONObject("grant").getString("refresh_token")
        )
        assertFalse(fixture.registration.sessionBlocked)
        assertTrue(gateway.models().isNotEmpty())
        gateway.close()
        val restart = fixture.gateway()
        assertNotNull(restart.restore())
        assertEquals(1, fixture.browserCount)
        assertEquals(1, fixture.refreshCount)
        assertFalse(fixture.requests.any { it.url.endsWith("responses") })
        restart.close()
    }

    @Test fun cancelledInitialLoginStillCannotPersistWithoutAnyLogout() = runBlocking {
        val encrypted = PausedEncryptedCommit(temporary.root)
        val fixture = encrypted.fixture
        val gateway = fixture.gateway()
        encrypted.pause.set(true)
        val connection = async { runCatching { fixture.connect(gateway) } }
        try {
            encrypted.awaitWrite()
            connection.cancel()
            gateway.cancel()
        } finally {
            encrypted.release.countDown()
        }
        connection.join()
        assertNull(fixture.activeStore.load())
        assertTrue(fixture.registration.sessionBlocked)
        assertNull(fixture.gateway().restore())
        gateway.close()
    }

    @Test fun closingDuringAValidatedRotationKeepsOnlyItsProtectedSession() = runBlocking {
        val encrypted = PausedEncryptedCommit(temporary.root)
        val fixture = encrypted.fixture
        val gateway = fixture.gateway()
        fixture.connect(gateway)
        gateway.models()
        gateway.rememberModel("preferred")
        fixture.now = 4000
        encrypted.pause.set(true)
        val analysis =
            async { runCatching { gateway.analyze("first", JPEG, "Synthetic instruction") } }
        try {
            encrypted.awaitWrite()
            gateway.close()
        } finally {
            encrypted.release.countDown()
        }
        assertTrue(analysis.await().isFailure)
        assertNull("A closed gateway must not repopulate its RAM session", gateway.preferredModel)
        val stored = checkNotNull(fixture.activeStore.load())
        assertEquals(
            "refresh-1",
            JSONObject(stored).getJSONObject("grant").getString("refresh_token")
        )
        val restart = fixture.gateway()
        assertNotNull(restart.restore())
        assertEquals("preferred", restart.preferredModel)
        assertEquals(1, fixture.browserCount)
        restart.close()
    }

    @Test fun logoutDuringRotationRevokesTheNewRefreshAndCannotBeUndoneByTheCommit() = runBlocking {
        val encrypted = PausedEncryptedCommit(temporary.root)
        val fixture = encrypted.fixture
        val gateway = fixture.gateway()
        fixture.connect(gateway)
        gateway.models()
        val other = fixture.gateway()
        other.restore()
        fixture.now = 4000
        encrypted.pause.set(true)
        val analysis =
            async { runCatching { gateway.analyze("first", JPEG, "Synthetic instruction") } }
        encrypted.awaitWrite()
        val logout = async { other.disconnect() }
        yield()
        encrypted.release.countDown()
        assertTrue(analysis.await().isFailure)
        logout.await()
        val revocations = fixture.requests.filter { it.url.endsWith("revoke") }
        assertEquals(
            "Logout must revoke the rotated grant, not the consumed one",
            1,
            revocations.size
        )
        assertTrue(revocations.single().body!!.contains("token=refresh-1"))
        assertNull(fixture.activeStore.load())
        assertTrue(fixture.registration.sessionBlocked)
        assertNull(fixture.gateway().restore())
        assertFalse(fixture.requests.any { it.url.endsWith("responses") })
        gateway.close()
        other.close()
    }

    private class PausedEncryptedCommit(root: File) {
        val fixture = SessionFixture()
        val pause = AtomicBoolean()
        val release = CountDownLatch(1)
        private val writing = CompletableDeferred<Unit>()

        init {
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            fixture.activeStore =
                EncryptedSessionStore(
                    AtomicSessionFile(File(root, "session.bin")) {
                            output,
                            bytes
                        ->
                        if (pause.get()) {
                            writing.complete(Unit)
                            check(release.await(10, TimeUnit.SECONDS))
                        }
                        output.write(bytes)
                    },
                    SessionCipher { key }
                )
        }

        suspend fun awaitWrite() = withTimeout(10_000L) { writing.await() }
    }

    private companion object {
        val JPEG = byteArrayOf(-1, -40, 1, -1, -39)
    }
}
