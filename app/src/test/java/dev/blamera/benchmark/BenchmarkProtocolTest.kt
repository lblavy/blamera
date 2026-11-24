package dev.blamera.benchmark

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkProtocolTest {
    @Test fun protocolOwnsOneSessionAtATimeAndCountsFirstWarmupsAndMeasurements() = runBlocking {
        var sessions = 0
        var calls = 0
        var tensors = 0
        var closes = 0
        val backend = BenchmarkBackend { _ ->
            assertEquals(0, sessions)
            sessions++
            object : BenchmarkSession {
                override val readHashNanos = 10L
                override val sessionNanos = 20L
                override fun input(values: FloatArray): BenchmarkExecution {
                    assertEquals(0, tensors)
                    tensors++
                    return object : BenchmarkExecution {
                        override fun execute(): BenchmarkSample {
                            calls++
                            return BenchmarkSample(100L, 20L, 1)
                        }
                        override fun close() {
                            tensors--
                            closes++
                        }
                    }
                }
                override fun close() {
                    sessions--
                    closes++
                }
            }
        }
        val models =
            listOf(BenchmarkModel("base", "a", "h", 1, 80), BenchmarkModel("pf", "b", "h", 1, 4585))
        val inputs =
            listOf(
                BenchmarkInput("nasa", "h", floatArrayOf(1f)),
                BenchmarkInput("lvis_371699", "h", floatArrayOf(2f))
            )
        val report = BenchmarkProtocol(backend).compare(models, inputs)
        assertEquals(4, report.blocks.size)
        assertEquals(60, calls)
        assertEquals(10, closes)
        assertEquals(0, sessions)
        assertEquals(0, tensors)
        assertEquals(listOf("base", "base", "pf", "pf"), report.blocks.map { it.model.id })
        assertEquals(10, report.blocks.first().runs.count)
        assertEquals(100.0, report.blocks.first().runs.medianNanos, 0.0)
    }

    @Test fun statisticsUseAllSamplesAndAnEvenMedianWithoutTruncation() {
        val stats = benchmarkStats(listOf(8L, 1L, 4L, 3L))
        assertEquals(4, stats.count)
        assertEquals(3.5, stats.medianNanos, 0.0)
        assertEquals(1L, stats.minNanos)
        assertEquals(8L, stats.maxNanos)
    }

    @Test fun failedRunClosesInputAndSessionAndNeverEmitsComplete() = runBlocking {
        val events = mutableListOf<String>()
        val backend = BenchmarkBackend { _ ->
            object : BenchmarkSession {
                override val readHashNanos = 0L
                override val sessionNanos = 0L
                override fun input(values: FloatArray): BenchmarkExecution =
                    object : BenchmarkExecution {
                        override fun execute(): BenchmarkSample {
                            events += "execute"
                            throw IllegalArgumentException("native result")
                        }
                        override fun close() {
                            events += "tensor-close"
                        }
                    }
                override fun close() {
                    events += "session-close"
                }
            }
        }
        try {
            BenchmarkProtocol(backend, {
                -1
            }, events::add).compare(
                listOf(BenchmarkModel("b", "a", "h", 1, 80)),
                listOf(BenchmarkInput("n", "h", floatArrayOf()))
            )
            org.junit.Assert.fail("Expected failure")
        } catch (_: IllegalArgumentException) { }
        assertEquals(
            listOf("execute", "tensor-close", "session-close"),
            events.filter {
                !it.startsWith("run=")
            }
        )
        org.junit.Assert.assertTrue(events.last().endsWith("end status=error"))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            benchmarkStats(emptyList())
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            benchmarkStats(listOf(-1L))
        }
        assertEquals(3.0, benchmarkStats(listOf(3L)).medianNanos, 0.0)
    }

    @Test fun modelEndReportsExistingSamplesAfterSessionClose() = runBlocking {
        val events = mutableListOf<String>()
        val samples = mutableListOf<Long?>(11, 22, null, 44)
        val backend = BenchmarkBackend { model ->
            events += "open=${model.id}"
            object : BenchmarkSession {
                override val readHashNanos = 1L
                override val sessionNanos = 2L
                override fun input(values: FloatArray): BenchmarkExecution =
                    error("No fixture in phase test")
                override fun close() {
                    events += "close=${model.id}"
                }
            }
        }
        val models =
            listOf(BenchmarkModel("base", "a", "h", 1, 80), BenchmarkModel("pf", "b", "h", 1, 4585))
        val result = BenchmarkProtocol(backend, {
            samples.removeAt(0)
        }, events::add).compare(models, emptyList())
        val ends = events.filter { "phase=modelEnd" in it }
        assertEquals(2, ends.size)
        org.junit.Assert.assertTrue(
            ends[0].endsWith(
                "model=base phase=modelEnd pssBeforeModelKiB=11 " +
                    "pssAfterSessionCloseKiB=22"
            )
        )
        org.junit.Assert.assertTrue(
            ends[1].endsWith(
                "model=pf phase=modelEnd pssBeforeModelKiB=null " +
                    "pssAfterSessionCloseKiB=44"
            )
        )
        org.junit.Assert.assertTrue(events.indexOf("close=base") < events.indexOf(ends[0]))
        org.junit.Assert.assertTrue(events.indexOf(ends[0]) < events.indexOf("open=pf"))
        org.junit.Assert.assertTrue(events.indexOf("close=pf") < events.indexOf(ends[1]))
        assertEquals(listOf(11L, null), result.models.map { it.pssBefore })
        assertEquals(listOf(22L, 44L), result.models.map { it.pssAfter })
        org.junit.Assert.assertTrue(samples.isEmpty())
    }
}
