package dev.blamera.benchmark

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkModelCacheTest {
    @Test fun streamsToVerifiedPrivateFileAndRevalidatesCachedBytesBeforeReuse() {
        val directory = Files.createTempDirectory("benchmark-cache").toFile()
        val bytes = ByteArray(100000) { (it % 251).toByte() }
        var reads = 0
        var closes = 0
        val cache = BenchmarkModelCache(directory) {
            reads++
            object : ByteArrayInputStream(bytes) {
                override fun close() {
                    closes++
                    super.close()
                }
            }
        }
        val model = BenchmarkModel("b", "b.onnx", benchmarkSha256(bytes), bytes.size.toLong(), 80)
        val first = cache.prepare(model)
        assertFalse(first.hit)
        assertArrayEquals(bytes, first.file.readBytes())
        assertTrue(cache.prepare(model).hit)
        assertEquals(1, reads)
        first.file.writeBytes(byteArrayOf(1))
        assertFalse(cache.prepare(model).hit)
        assertEquals(2, reads)
        assertEquals(2, closes)
        assertTrue(directory.listFiles()!!.none { it.name.endsWith(".pending") })
    }

    @Test fun truncatedOversizedOrBadHashNeverPublishAndAlwaysCloseStream() {
        val directory = Files.createTempDirectory("benchmark-corrupt").toFile()
        var closes = 0
        val cache = BenchmarkModelCache(directory) {
            object : ByteArrayInputStream(byteArrayOf(1, 2)) {
                override fun close() {
                    closes++
                }
            }
        }
        for (size in listOf(1L, 2L, 3L)) {
            assertThrows(IllegalArgumentException::class.java) {
                cache.prepare(BenchmarkModel("x", "x", "0".repeat(64), size, 80))
            }
        }
        assertEquals(3, closes)
        assertTrue(directory.listFiles()!!.isEmpty())
    }
}
