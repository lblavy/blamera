package dev.blamera.botany

import dev.blamera.chatgpt.storage.SessionCipher
import dev.blamera.chatgpt.storage.SessionStorageException
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PlantKeyIsolationTest {
    @Test
    fun botanicalCipherCannotBeReadAsAChatGptSessionEvenWithTheSameTestKey() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val botanical = SessionCipher("dev.blamera.plantnet.key.v1", { key })
        val chatGpt = SessionCipher({ key })
        val encrypted = botanical.encrypt("FAKE-KEY")
        assertEquals("FAKE-KEY", botanical.decrypt(encrypted))
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("FAKE-KEY"))
        assertThrows(SessionStorageException::class.java) { chatGpt.decrypt(encrypted) }
    }
}
