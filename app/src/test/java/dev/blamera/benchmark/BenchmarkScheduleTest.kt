package dev.blamera.benchmark

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkScheduleTest {
    @Test fun fourSessionsMeasureTwoFixturesBeforeFourteenQualityCasesWithExactly168Calls() =
        runBlocking {
            val events = mutableListOf<String>()
            var active = 0
            var calls = 0
            var qualityCalls = 0
            val backend = BenchmarkBackend { model ->
                assertEquals(0, active)
                active++
                object : BenchmarkSession {
                    override val readHashNanos = 1L
                    override val sessionNanos = 2L
                    override fun input(values: FloatArray): BenchmarkExecution =
                        object : BenchmarkExecution {
                            override fun execute(): BenchmarkSample {
                                calls++
                                events += "perf:${model.id}:${values.first().toInt()}"
                                return BenchmarkSample(10, 2, 1)
                            }
                            override fun quality(): BenchmarkSample {
                                calls++
                                qualityCalls++
                                events += "quality:${model.id}:${values.first().toInt()}"
                                return BenchmarkSample(10, 2, 1)
                            }
                            override fun close() = Unit
                        }
                    override fun close() {
                        active--
                    }
                }
            }
            val models = listOf("baseline", "small", "large", "xlarge").map {
                BenchmarkModel(
                    it,
                    it,
                    "h",
                    1,
                    if (it ==
                        "baseline"
                    ) {
                        80
                    } else {
                        4585
                    }
                )
            }
            val inputs = listOf(
                "nasa",
                "synthetic",
                "lvis_371699",
                *(0 until 11).map {
                    "fixture$it"
                }.toTypedArray()
            ).mapIndexed { i, id -> BenchmarkInput(id, "h", floatArrayOf(i.toFloat())) }
            val result = BenchmarkProtocol(backend).compare(models, inputs)
            assertEquals(168, calls)
            assertEquals(56, qualityCalls)
            assertEquals(56, result.quality.size)
            assertEquals(8, result.blocks.size)
            assertEquals(listOf("nasa", "lvis_371699"), result.blocks.take(2).map { it.fixture })
            assertEquals(listOf(true, false), result.blocks.take(2).map { it.sessionFirst })
            assertEquals(0, active)
            for (model in models) {
                val local = events.filter { ":${model.id}:" in it }
                assertTrue(local.take(28).all { it.startsWith("perf:") })
                assertTrue(local.drop(28).all { it.startsWith("quality:") })
            }
        }
}
