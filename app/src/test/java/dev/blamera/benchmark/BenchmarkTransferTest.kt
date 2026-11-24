package dev.blamera.benchmark

import java.security.MessageDigest
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BenchmarkTransferTest {
    private fun document() = JSONObject().put("schema", 2).put("runId", "run-one")
        .put("status", "complete").put("message", "é猫".repeat(1500)).toString()

    @Test fun unicodePayloadIsNumberedHashedBoundedAndReemissionDoesNotChangeTheRun() {
        val payload = document()
        val lines = benchmarkReportLines(payload, "transfer-one").toList()
        assertTrue(lines.size > 4)
        assertTrue(lines.all { it.length < 1800 })
        assertTrue(lines.first().startsWith("report begin transfer=transfer-one run=run-one "))
        assertTrue(lines.last().startsWith("report end transfer=transfer-one run=run-one "))
        val chunks = lines.drop(1).dropLast(1)
        val reconstructed = chunks.flatMapIndexed { index, line ->
            assertTrue(line.contains("part=${index + 1}/${chunks.size}"))
            Base64.getDecoder().decode(line.substringAfter(" data=")).toList()
        }.toByteArray()
        assertArrayEquals(payload.toByteArray(Charsets.UTF_8), reconstructed)
        val hash = MessageDigest.getInstance("SHA-256").digest(reconstructed).joinToString("") {
            "%02x".format(it)
        }
        assertTrue(lines.first().contains("sha256=$hash"))
        assertTrue(lines.last().contains("sha256=$hash"))
        val again = benchmarkReportLines(payload, "transfer-two").toList()
        assertTrue(again.first().contains("run=run-one"))
        assertEquals(chunks.size, again.size - 2)
    }

    @Test fun badIdentityCorruptionAndOversizeAreNeverEmittedAsCompleteTransfer() {
        assertThrows(IllegalArgumentException::class.java) {
            benchmarkReportLines(document(), "bad\nlog")
        }
        assertThrows(IllegalArgumentException::class.java) {
            benchmarkReportLines("bad-json", "ok")
        }
        assertThrows(IllegalArgumentException::class.java) {
            benchmarkReportLines(document() + " ".repeat(8 * 1024 * 1024), "ok")
        }
    }
}
