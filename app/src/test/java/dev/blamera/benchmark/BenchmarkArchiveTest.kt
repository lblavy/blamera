package dev.blamera.benchmark

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class BenchmarkArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun document(id: String, status: String, extra: String = "") =
        JSONObject().put("schema", 2).put("runId", id).put("status", status)
            .put("appVersion", "0.6.0").put("startedAtMillis", 10L).put("data", extra).toString()

    @Test fun invalidSchemaIdentityStatusAndOversizedStoredReportAreExplicitErrors() {
        val file = File(temporary.root, "last.json")
        val archive = BenchmarkArchive(file)
        assertFalse(archive.update("missing", document("missing", "complete")))
        for (bad in listOf(
            document("bad!", "running"),
            document("one", "unknown"),
            document("one", "complete"),
            document("one", "running").replace("\"schema\":2", "\"schema\":1")
        )) {
            assertThrows(IllegalArgumentException::class.java) { archive.begin(bad) }
        }
        file.outputStream().use { stream ->
            val block = ByteArray(1024 * 1024)
            repeat(9) { stream.write(block) }
        }
        assertThrows(IllegalArgumentException::class.java) { archive.read() }
        archive.begin(document("retry", "running"))
        assertEquals("retry", JSONObject(archive.read()!!).getString("runId"))
    }

    @Test fun lastRunSurvivesRecreationAndOldCompletionCannotOverwriteNewInterruptedRun() {
        val file = File(temporary.root, "last.json")
        val archive = BenchmarkArchive(file)
        assertEquals(null, archive.read())
        archive.begin(document("old", "running"))
        assertTrue(archive.update("old", document("old", "complete", "old-complete")))
        assertEquals("complete", JSONObject(BenchmarkArchive(file).read()!!).getString("status"))
        BenchmarkArchive(file).begin(document("new", "running"))
        assertFalse(archive.update("old", document("old", "complete")))
        assertEquals("new", JSONObject(archive.read()!!).getString("runId"))
        assertEquals("running", JSONObject(archive.read()!!).getString("status"))
        assertTrue(archive.update("new", document("new", "cancelled")))
        assertEquals("cancelled", JSONObject(BenchmarkArchive(file).read()!!).getString("status"))
    }

    @Test fun corruptionOversizeAndMismatchedIdentityFailClosedWithoutDestroyingLastSnapshot() {
        val file = File(temporary.root, "last.json")
        val archive = BenchmarkArchive(file)
        archive.begin(document("one", "running"))
        assertThrows(IllegalArgumentException::class.java) {
            archive.update("one", document("other", "complete"))
        }
        assertThrows(IllegalArgumentException::class.java) { archive.begin("not-json") }
        assertThrows(IllegalArgumentException::class.java) {
            archive.begin(document("huge", "running", "x".repeat(8 * 1024 * 1024)))
        }
        assertEquals("one", JSONObject(archive.read()!!).getString("runId"))
        file.writeText("corrupt")
        assertThrows(IllegalArgumentException::class.java) { archive.read() }
    }
}
