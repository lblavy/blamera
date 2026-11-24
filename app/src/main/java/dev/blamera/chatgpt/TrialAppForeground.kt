package dev.blamera.chatgpt

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Retained with the trial session; never holds an Activity or starts one. */
internal class TrialAppForeground(
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime
) {
    private val resumed = MutableStateFlow(false)

    @Volatile private var connectionStartedAt = elapsedMillis()

    fun beginConnection() {
        connectionStartedAt = elapsedMillis()
    }

    fun update(resumed: Boolean) {
        this.resumed.value = resumed
    }

    suspend fun await() {
        resumed.first { it }
        // A cached process can delay its timer; elapsedRealtime also counts time asleep.
        requireTrial(
            elapsedMillis() - connectionStartedAt < TrialLimits.AUTH_TIMEOUT_MILLIS,
            TrialFailure.TIMEOUT
        )
    }

    /** Refreshes may happen hours after login; they must never reuse its old start time. */
    suspend fun awaitRefresh() {
        val startedAt = elapsedMillis()
        val returned = withTimeoutOrNull(TrialLimits.AUTH_TIMEOUT_MILLIS) {
            resumed.first { it }
            requireTrial(
                elapsedMillis() - startedAt < TrialLimits.AUTH_TIMEOUT_MILLIS,
                TrialFailure.TIMEOUT
            )
            true
        }
        requireTrial(returned == true, TrialFailure.TIMEOUT)
    }
}

/** Cancelling a gateway also releases its pending foreground wait, without owning the UI scope. */
internal class TrialForegroundWait(private val awaitForeground: suspend () -> Unit) {
    private val pending = AtomicReference<Job?>()

    suspend fun await(checkCurrent: suspend () -> Unit) = coroutineScope {
        val waiter = async(start = CoroutineStart.LAZY) { awaitForeground() }
        pending.set(waiter)
        try {
            checkCurrent()
            waiter.await()
            checkCurrent()
        } finally {
            pending.compareAndSet(waiter, null)
            waiter.cancel()
        }
    }

    fun cancel() {
        pending.getAndSet(null)?.cancel()
    }
}
