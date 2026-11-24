package dev.blamera.benchmark

import ai.onnxruntime.OrtException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.blamera.vision.VisionException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun interface BenchmarkWork {
    suspend fun run(progress: suspend (String) -> Unit): BenchmarkResult
    suspend fun replay(progress: suspend (String) -> Unit) {
        error("Aucun résultat enregistré")
    }
}
internal sealed interface BenchmarkState {
    data object Idle : BenchmarkState
    data class Running(val message: String) : BenchmarkState
    data object Stopping : BenchmarkState
    data object Cancelled : BenchmarkState
    data object Failed : BenchmarkState
    data object Reemitted : BenchmarkState
    data class Complete(val result: BenchmarkResult) : BenchmarkState
}
internal class BenchmarkController(
    private val scope: CoroutineScope,
    private val work: BenchmarkWork,
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    var state: BenchmarkState by mutableStateOf(BenchmarkState.Idle)
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    private var job: Job? = null
    private var epoch = 0
    private var closed = false
    fun start() {
        if (!busy && !closed) {
            busy = true
            val token = ++epoch
            state = BenchmarkState.Running("Préparation des quatorze fixtures publiques…")
            // Enter finally even if lifecycle cancellation happens before the first dispatch.
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) { execute(token) }
        }
    }
    fun replay() {
        if (!busy && !closed) {
            busy = true
            val token = ++epoch
            state = BenchmarkState.Running("Réémission du dernier rapport…")
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) { execute(token, true) }
        }
    }
    private suspend fun execute(token: Int, replay: Boolean = false) {
        try {
            val outcome = runBenchmarkSafely(work, replay) { message ->
                withContext(callbackDispatcher) {
                    if (token == epoch && !closed) state = BenchmarkState.Running(message)
                }
            }
            currentCoroutineContext().ensureActive()
            if (token == epoch && !closed) state = outcome
        } catch (_: CancellationException) {
            if (!closed) state = BenchmarkState.Cancelled
        } finally {
            busy = false
        }
    }
    fun cancel() {
        if (busy) {
            epoch++
            state = BenchmarkState.Stopping
            job?.cancel()
        }
    }
    fun close() {
        closed = true
        cancel()
    }
}

private suspend fun runBenchmarkSafely(
    work: BenchmarkWork,
    replay: Boolean,
    progress: suspend (String) -> Unit
): BenchmarkState = try {
    executeBenchmarkWork(work, replay, progress)
} catch (error: CancellationException) {
    throw error
} catch (_: IOException) {
    BenchmarkState.Failed
} catch (_: OrtException) {
    BenchmarkState.Failed
} catch (_: IllegalArgumentException) {
    BenchmarkState.Failed
} catch (_: IllegalStateException) {
    BenchmarkState.Failed
} catch (_: VisionException) {
    BenchmarkState.Failed
} catch (_: OutOfMemoryError) {
    BenchmarkState.Failed
} catch (_: UnsatisfiedLinkError) {
    BenchmarkState.Failed
}

private suspend fun executeBenchmarkWork(
    work: BenchmarkWork,
    replay: Boolean,
    progress: suspend (String) -> Unit
): BenchmarkState = if (replay) {
    work.replay(progress)
    BenchmarkState.Reemitted
} else {
    BenchmarkState.Complete(work.run(progress))
}
