package dev.blamera.vision

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoModelAssetsTest {
    @Test fun verifiedStreamingCachePublishesAndRepairsWithoutRereadingValidAsset() {
        val content = ByteArray(180_000) { (it % 241).toByte() }
        val directory = File(Files.createTempDirectory("photo-model-test").toFile(), "store")
        var reads = 0
        val cache = ModelFileCache(directory) {
            reads++
            content.inputStream()
        }
        val asset = ModelAsset("large.onnx", content.size.toLong(), sha(content))
        val file = cache.prepare(asset).file
        assertTrue("The verified model file must exist", file.isFile)
        assertArrayEquals(content, file.readBytes())
        assertEquals(file, cache.prepare(asset).file)
        assertEquals(1, reads)
        file.writeBytes(byteArrayOf(9))
        assertArrayEquals(content, cache.prepare(asset).file.readBytes())
        assertEquals(2, reads)
        file.writeBytes(ByteArray(content.size))
        assertArrayEquals(content, cache.prepare(asset).file.readBytes())
        assertEquals(3, reads)
        assertTrue(directory.listFiles()!!.none { it.name.endsWith(".pending") })
    }

    @Test fun invalidMetadataAndDamagedStreamsCannotPublishAModel() {
        assertThrows(IllegalArgumentException::class.java) { ModelAsset("x", 0, "a".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { ModelAsset("x", 1, "../bad") }
        val bytes = byteArrayOf(1, 2, 3)
        val directory = Files.createTempDirectory("photo-damaged").toFile()
        var closed = 0
        val cache = ModelFileCache(directory) {
            object : ByteArrayInputStream(bytes) {
                override fun close() {
                    closed++
                    super.close()
                }
            }
        }
        for (size in listOf(2L, 3L, 4L)) {
            assertThrows(IllegalArgumentException::class.java) {
                cache.prepare(ModelAsset("bad", size, "0".repeat(64)))
            }
        }
        assertEquals(3, closed)
        assertTrue(directory.listFiles()!!.isEmpty())
        val blocked = File(directory, "not-directory").apply { writeText("blocked") }
        assertThrows(IllegalStateException::class.java) {
            ModelFileCache(blocked) { bytes.inputStream() }.prepare(ModelAsset("x", 3, sha(bytes)))
        }
        assertThrows(IOException::class.java) {
            ModelFileCache(directory) {
                throw IOException("unavailable")
            }.prepare(ModelAsset("x", 3, sha(bytes)))
        }
        assertTrue(directory.listFiles()!!.none { it.name.endsWith(".pending") })
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
