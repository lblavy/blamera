package dev.blamera.benchmark

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class StoredBenchmarkWork(
    private val archive: BenchmarkArchive,
    private val prepare: () -> BenchmarkPlan,
    private val compare: suspend (BenchmarkPlan, suspend (String) -> Unit) -> BenchmarkResult,
    private val emit: (String) -> Unit
) : BenchmarkWork {
    override suspend fun run(progress: suspend (String) -> Unit): BenchmarkResult {
        val id = UUID.randomUUID().toString()
        val started = System.currentTimeMillis()
        archive.begin(benchmarkDocument(id, "running", started, null, null))
        var status = "error"
        var plan: BenchmarkPlan? = null
        var result: BenchmarkResult? = null
        try {
            return withContext(Dispatchers.Default) {
                val prepared = prepare()
                plan = prepared
                val measured = compare(prepared, progress)
                currentCoroutineContext().ensureActive()
                result = measured
                status = "complete"
                measured
            }
        } catch (error: CancellationException) {
            status = "cancelled"
            throw error
        } finally {
            // Native work has returned and released its resources before this durable terminal state.
            withContext(NonCancellable + Dispatchers.IO) {
                archive.update(id, benchmarkDocument(id, status, started, plan, result))
            }
        }
    }
    override suspend fun replay(progress: suspend (String) -> Unit) {
        val document =
            withContext(Dispatchers.IO) { checkNotNull(archive.read()) { "Aucun rapport" } }
        val transfer = UUID.randomUUID().toString()
        for ((index, line) in benchmarkReportLines(document, transfer).withIndex()) {
            currentCoroutineContext().ensureActive()
            emit(line)
            if (index % PROGRESS_CHUNKS == 0) progress("Réémission : fragment $index…")
            delay(CHUNK_DELAY_MILLIS)
        }
    }
}

private const val PROGRESS_CHUNKS = 100
private const val CHUNK_DELAY_MILLIS = 5L
