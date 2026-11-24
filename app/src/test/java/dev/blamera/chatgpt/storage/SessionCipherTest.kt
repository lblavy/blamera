package dev.blamera.chatgpt.storage

import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionCipherTest {
    @Test fun savedCredentialsAreEncryptedWithFreshNonces() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = SessionCipher { key }
        val record = """{"access_token":"synthetic-private-token","account":"fixture"}"""
        val first = cipher.encrypt(record)
        val second = cipher.encrypt(record)
        assertFalse(String(first, Charsets.UTF_8).contains("synthetic-private-token"))
        assertFalse(first.contentEquals(second))
        assertEquals(record, cipher.decrypt(first))
        assertEquals(record, cipher.decrypt(second))
    }

    @Test fun unicodeAndExactUtf8ByteBoundaryRoundTrip() {
        val cipher = cipher()
        for (value in listOf("", "compte café 🐈", "é".repeat(MAX_SESSION_BYTES / 2))) {
            assertEquals(value, cipher.decrypt(cipher.encrypt(value)))
        }
    }

    @Test fun oversizedOrMalformedTextCannotBePersisted() {
        val cipher = cipher()
        for (value in listOf(
            "a".repeat(MAX_SESSION_BYTES + 1),
            "é".repeat(MAX_SESSION_BYTES / 2) + "é",
            "\uD800"
        )) {
            assertThrows(SessionStorageException::class.java) { cipher.encrypt(value) }
        }
    }

    @Test fun malformedTruncatedAndTamperedRecordsAreRejected() {
        val cipher = cipher()
        val encrypted = cipher.encrypt("synthetic credentials")
        val wrongVersion = encrypted.copyOf().apply { this[0] = 2 }
        val changedTag = encrypted.copyOf().apply {
            this[lastIndex] =
                (last().toInt() xor 1).toByte()
        }
        for (value in listOf(
            byteArrayOf(),
            byteArrayOf(1),
            encrypted.copyOf(12),
            wrongVersion,
            changedTag,
            ByteArray(MAX_SESSION_BYTES + 100)
        )) {
            assertThrows(SessionStorageException::class.java) { cipher.decrypt(value) }
        }
    }

    @Test fun wrongOrUnavailableKeyFailsWithoutLeakingItsError() {
        val encrypted = cipher().encrypt("synthetic credentials")
        assertThrows(SessionStorageException::class.java) { cipher().decrypt(encrypted) }
        val unavailable =
            SessionCipher { throw java.security.KeyStoreException("synthetic secret") }
        val error = assertThrows(SessionStorageException::class.java) {
            unavailable.encrypt("synthetic credentials")
        }
        assertFalse(error.message.orEmpty().contains("synthetic secret"))
        assertNull(error.cause)
    }

    private fun cipher(): SessionCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return SessionCipher { key }
    }
}
