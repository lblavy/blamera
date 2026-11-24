package dev.blamera.faces

import dev.blamera.chatgpt.storage.SessionStorageException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FaceGalleryImportTest {
    @Test fun streamIsBoundedAndClosedOnSuccessOversizeAndReadFailure() {
        for (size in listOf(0, 12, MAX_FACE_GALLERY_BYTES, MAX_FACE_GALLERY_BYTES + 1)) {
            var closed = false
            val stream = object : ByteArrayInputStream(ByteArray(size)) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
            if (size <= MAX_FACE_GALLERY_BYTES) {
                assertArrayEquals(ByteArray(size), readFaceGallery(stream))
            } else {
                assertThrows(FaceGalleryException::class.java) { readFaceGallery(stream) }
            }
            assertTrue(closed)
        }
        var closed = false
        val broken = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException()
            override fun close() {
                closed = true
            }
        }
        assertThrows(IOException::class.java) { readFaceGallery(broken) }
        assertTrue(closed)
    }

    @Test fun privateStorageEncryptsMaximumPackageAndRetainsOldRecordOnFailure() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = faceGalleryStore(context) { key }
        store.clear()
        val value = faceFixture().padEnd(MAX_FACE_GALLERY_BYTES, ' ')
        store.save(value)
        val path = File(context.noBackupFilesDir, FACE_GALLERY_FILENAME)
        assertFalse(path.readText().contains("Personne synthétique"))
        assertFalse(File(context.filesDir, FACE_GALLERY_FILENAME).exists())
        assertEquals(value, faceGalleryStore(context) { key }.load())
        val bytes = path.readBytes()
        assertThrows(SessionStorageException::class.java) {
            runBlocking { faceGalleryStore(context) { throw IOException() }.save("replacement") }
        }
        assertArrayEquals(bytes, path.readBytes())
        store.clear()
    }
}
