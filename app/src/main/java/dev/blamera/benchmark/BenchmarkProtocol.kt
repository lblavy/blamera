package dev.blamera.benchmark

import dev.blamera.vision.VisionExecution
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun benchmarkStats(values: List<Long>): BenchmarkStats {
    require(values.isNotEmpty() && values.all { it >= 0 })
    val sorted = values.sorted()
    val center = sorted.size / 2
    val median = if (sorted.size % 2 ==
        0
    ) {
        (sorted[center - 1].toDouble() + sorted[center]) / 2
    } else {
        sorted[center].toDouble()
    }
    return BenchmarkStats(sorted.size, median, sorted.first(), sorted.last())
}

private data class BenchmarkCase(
    val model: BenchmarkModel,
    val input: BenchmarkInput,
    val sessionFirst: Boolean
)

internal class BenchmarkProtocol(
    private val backend: BenchmarkBackend,
    private val pss: () -> Long? = { null },
    private val log: (String) -> Unit = {}
) {
    suspend fun compare(
        models: List<BenchmarkModel>,
        inputs: List<BenchmarkInput>,
        progress: suspend (String) -> Unit = {}
    ): BenchmarkResult {
        val runId = UUID.randomUUID().toString()
        val emit: (String) -> Unit = { log("run=$runId $it") }
        emit(
            "start order=baseline,small,large,xlarge cpu=2/1 fp32=1 edge=640 " +
                "nms=1 conf=0.35 iou=0.5 " +
                "max=20 agnostic=per-model warmups=3 measured=10 first=1"
        )
        var status = "error"
        try {
            val result = withContext(Dispatchers.Default) {
                VisionExecution.mutex.withLock {
                    currentCoroutineContext().ensureActive()
                    compareOwned(models, inputs, progress, emit)
                }
            }
            currentCoroutineContext().ensureActive()
            status = "complete"
            return result
        } catch (error: CancellationException) {
            status = "cancelled"
            throw error
        } finally {
            emit("end status=$status")
        }
    }

    private suspend fun compareOwned(
        models: List<BenchmarkModel>,
        inputs: List<BenchmarkInput>,
        progress: suspend (String) -> Unit,
        emit: (String) -> Unit
    ): BenchmarkResult {
        val blocks = mutableListOf<BenchmarkBlock>()
        val timings = mutableListOf<BenchmarkModelTiming>()
        val quality = mutableListOf<BenchmarkQuality>()
        for (model in models) {
            currentCoroutineContext().ensureActive()
            progress("Chargement ${model.id}")
            val before = memory()
            var read = 0L
            var create = 0L
            var cached = false
            backend.open(model).use { session ->
                read = session.readHashNanos
                create = session.sessionNanos
                cached = session.cacheHit
                emit(
                    "model=${model.id} hash=${model.hash} readHashNs=$read sessionNs=$create " +
                        "classes=${model.classes}"
                )
                val performance = inputs.filter { it.id == "nasa" || it.id == "lvis_371699" }
                for ((index, input) in performance.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    blocks +=
                        measure(session, BenchmarkCase(model, input, index == 0), progress, emit)
                }
                for (input in inputs) {
                    currentCoroutineContext().ensureActive()
                    progress("${model.id} / ${input.id} : qualité")
                    quality += qualityCase(session, model, input)
                }
            }
            val timing = BenchmarkModelTiming(model.id, read, create, before, memory(), cached)
            timings += timing
            emit(
                "model=${model.id} phase=modelEnd pssBeforeModelKiB=${timing.pssBefore} " +
                    "pssAfterSessionCloseKiB=${timing.pssAfter}"
            )
        }
        return BenchmarkResult(blocks, timings, quality)
    }

    private suspend fun qualityCase(
        session: BenchmarkSession,
        model: BenchmarkModel,
        input: BenchmarkInput
    ): BenchmarkQuality {
        val image = input.load()
        val sample = session.input(image).use { prepared ->
            currentCoroutineContext().ensureActive()
            val result = prepared.quality()
            currentCoroutineContext().ensureActive()
            result
        }
        return BenchmarkQuality(
            model.id,
            input.id,
            input.tensorHash(image.values),
            image.letterbox,
            sample
        )
    }

    private suspend fun measure(
        session: BenchmarkSession,
        case: BenchmarkCase,
        progress: suspend (String) -> Unit,
        emit: (String) -> Unit
    ): BenchmarkBlock {
        val (model, input, sessionFirst) = case
        val before = memory()
        val samples = mutableListOf<BenchmarkSample>()
        val image = input.load()
        val first = session.input(image).use { prepared ->
            val initial = executeActive(prepared)
            repeat(WARMUPS) { executeActive(prepared) }
            repeat(MEASURED) { index ->
                val sample = executeActive(prepared)
                samples += sample
                progress("${model.id} / ${input.id} : ${index + 1}/$MEASURED")
                emit(
                    "model=${model.id} fixture=${input.id} measured=${index + 1} " +
                        "runNs=${sample.runNanos} copyNs=${sample.copyNanos} " +
                        "detections=${sample.detections}"
                )
            }
            initial
        }
        val block = BenchmarkBlock(
            model, input.id, input.hash, sessionFirst, first,
            benchmarkStats(
                samples.map {
                    it.runNanos
                }
            ),
            benchmarkStats(samples.map { it.copyNanos }), before, memory(),
            samples, input.tensorHash(image.values)
        )
        emit(
            "model=${model.id} fixture=${input.id} fixtureHash=${input.hash} " +
                "sessionFirst=$sessionFirst firstRunNs=${first.runNanos} " +
                "firstCopyNs=${first.copyNanos} n=${block.runs.count} " +
                "medianNs=${block.runs.medianNanos} minNs=${block.runs.minNanos} " +
                "maxNs=${block.runs.maxNanos} copyMedianNs=${block.copies.medianNanos} " +
                "pssBeforeKiB=${block.pssBefore} pssAfterKiB=${block.pssAfter}"
        )
        return block
    }

    private suspend fun executeActive(prepared: BenchmarkExecution): BenchmarkSample {
        currentCoroutineContext().ensureActive()
        val sample = prepared.execute()
        currentCoroutineContext().ensureActive()
        return sample
    }
    private fun memory(): Long? = pss()?.takeIf { it > 0 }
    private companion object {
        const val WARMUPS = 3
        const val MEASURED = 10
    }
}
