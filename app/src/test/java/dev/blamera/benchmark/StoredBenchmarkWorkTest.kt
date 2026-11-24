package dev.blamera.benchmark

import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [36])
class StoredBenchmarkWorkTest {
    @Test fun completePersistsAndReemitsWithoutInferenceButNewCancelledRunReplacesFreshness() =
        runBlocking {
            val archive =
                BenchmarkArchive(
                    Files.createTempDirectory("report-work").resolve("last.json").toFile()
                )
            var calls = 0
            var cancel = false
            val logs = mutableListOf<String>()
            val work =
                StoredBenchmarkWork(archive, { BenchmarkPlan(emptyList(), emptyList()) }, { _, _ ->
                    calls++
                    if (cancel) throw CancellationException("stop")
                    BenchmarkResult(emptyList())
                }, logs::add)
            work.run {}
            val complete = JSONObject(archive.read()!!)
            assertEquals("complete", complete.getString("status"))
            work.replay {}
            assertEquals(1, calls)
            assertTrue(logs.first().startsWith("report begin"))
            assertTrue(logs.last().startsWith("report end"))
            cancel = true
            try {
                work.run {}
                fail()
            } catch (_: CancellationException) { }
            val interrupted = JSONObject(archive.read()!!)
            assertEquals("cancelled", interrupted.getString("status"))
            assertNotEquals(complete.getString("runId"), interrupted.getString("runId"))
        }

    @Test fun preparationFailurePersistsErrorAndMissingReplayIsExplicit() = runBlocking {
        val archive =
            BenchmarkArchive(
                Files.createTempDirectory("report-error").resolve("last.json").toFile()
            )
        val work =
            StoredBenchmarkWork(archive, {
                throw IllegalArgumentException("asset")
            }, { _, _ -> error("never") }, {})
        try {
            work.replay {}
            fail()
        } catch (_: IllegalStateException) { }
        try {
            work.run {}
            fail()
        } catch (_: IllegalArgumentException) { }
        assertEquals("error", JSONObject(archive.read()!!).getString("status"))
    }
}
