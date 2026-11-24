package dev.blamera.chatgpt.storage

import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EncryptedSessionStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun failedReplacementPreservesThePreviousEncryptedRecord() = runBlocking {
        val path = File(temporary.root, "session.bin")
        val cipher = cipher()
        val store = EncryptedSessionStore(AtomicSessionFile(path), cipher)
        store.save("synthetic previous account")
        val failing = EncryptedSessionStore(
            AtomicSessionFile(path) { output, bytes ->
                output.write(bytes, 0, 4)
                throw IOException("synthetic write failure")
            },
            cipher
        )
        assertThrows(SessionStorageException::class.java) {
            runBlocking { failing.save("synthetic replacement account") }
        }
        assertEquals("synthetic previous account", store.load())
    }

    @Test fun absentRoundTripClearAndReloadNeverExposePlaintextOnDisk() = runBlocking {
        val path = File(temporary.root, "private/session.bin")
        val cipher = cipher()
        val store = EncryptedSessionStore(AtomicSessionFile(path), cipher)
        assertNull(store.load())
        store.clear()
        val record = "synthetic-private-token"
        store.save(record)
        assertFalse(path.readText().contains(record))
        assertEquals(record, EncryptedSessionStore(AtomicSessionFile(path), cipher).load())
        store.clear()
        assertNull(store.load())
    }

    @Test fun corruptedAndOversizedFilesFailClosed() = runBlocking {
        val path = temporary.newFile()
        val store = EncryptedSessionStore(AtomicSessionFile(path), cipher())
        for (value in listOf(byteArrayOf(1, 2, 3), ByteArray(MAX_ENCRYPTED_SESSION_BYTES + 1))) {
            path.writeBytes(value)
            assertThrows(SessionStorageException::class.java) { runBlocking { store.load() } }
        }
    }

    @Test fun fileBoundaryRejectsOversizedReadsAndWrites() {
        val path = temporary.newFile()
        val file = AtomicSessionFile(path)
        val tooLarge = ByteArray(MAX_ENCRYPTED_SESSION_BYTES + 1)
        path.writeBytes(tooLarge)
        assertThrows(SessionStorageException::class.java) { file.read() }
        assertThrows(SessionStorageException::class.java) { file.write(tooLarge) }
    }

    @Test fun undeletableSessionDoesNotReportSuccessfulClear() {
        val directory = temporary.newFolder()
        File(directory, "still-here").writeText("synthetic")
        val store = EncryptedSessionStore(AtomicSessionFile(directory), cipher())
        assertThrows(SessionStorageException::class.java) { runBlocking { store.clear() } }
    }

    @Test fun cancellationDuringEncryptionNeverReplacesTheExistingRecord() = runBlocking {
        val path = temporary.newFile()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val original = EncryptedSessionStore(AtomicSessionFile(path), SessionCipher { key })
        original.save("synthetic old record")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocked = EncryptedSessionStore(
            AtomicSessionFile(path),
            SessionCipher {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                key
            }
        )
        val saving = async(Dispatchers.Default) { blocked.save("synthetic cancelled replacement") }
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            saving.cancel()
        } finally {
            release.countDown()
        }
        saving.join()
        assertTrue(saving.isCancelled)
        assertEquals("synthetic old record", original.load())
    }

    @Test fun separateInstancesSerializeConcurrentSavesAndReadOnlyCompleteRecords() = runBlocking {
        val path = temporary.newFile()
        val cipher = cipher()
        val records = (0 until 20).map { "synthetic account $it" }
        records.map { record ->
            async(Dispatchers.Default) {
                EncryptedSessionStore(AtomicSessionFile(path), cipher).save(record)
            }
        }.awaitAll()
        assertTrue(records.contains(EncryptedSessionStore(AtomicSessionFile(path), cipher).load()))
    }

    @Test fun expectedBoundaryFailuresExposeOnlyAGenericMessageAndNoCause() = runBlocking {
        val failures =
            listOf(
                IOException("synthetic secret"),
                SecurityException("synthetic secret"),
                java.security.ProviderException("synthetic secret"),
                IllegalArgumentException("secret")
            )
        for (failure in failures) {
            val file = object : SessionFile {
                override fun read(): ByteArray? = throw failure
                override fun write(value: ByteArray) = throw failure
                override fun clear() = throw failure
            }
            val store = EncryptedSessionStore(file, cipher())
            for (operation in listOf<suspend () -> Unit>(
                {
                    store.load()
                    Unit
                },
                { store.save("synthetic") },
                { store.clear() }
            )) {
                val error = assertThrows(SessionStorageException::class.java) {
                    runBlocking { operation() }
                }
                var current: Throwable? = error
                repeat(8) {
                    current?.let {
                        assertFalse(it === failure)
                        assertEquals(SessionStorageException::class.java, it.javaClass)
                        assertFalse(it.message.orEmpty().contains("secret"))
                        current = it.cause
                    }
                }
                assertNull(current)
            }
        }
    }

    @Test fun fileAndKeyAccessRunOffTheCallingThread() = runBlocking {
        val caller = Thread.currentThread()
        var data: ByteArray? = null
        val file = object : SessionFile {
            override fun read(): ByteArray? {
                assertFalse(Thread.currentThread() === caller)
                return data
            }
            override fun write(value: ByteArray) {
                assertFalse(Thread.currentThread() === caller)
                data = value
            }
            override fun clear() {
                assertFalse(Thread.currentThread() === caller)
                data = null
            }
        }
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = EncryptedSessionStore(
            file,
            SessionCipher {
                assertFalse(Thread.currentThread() === caller)
                key
            }
        )
        store.save("synthetic")
        assertEquals("synthetic", store.load())
        store.clear()
        assertNull(store.load())
    }

    private fun cipher(): SessionCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return SessionCipher { key }
    }
}
