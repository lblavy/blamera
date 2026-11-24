package dev.blamera.chatgpt

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Local request lifetime is distinct from the protected session's logout revision. */
internal class TrialGatewayEpoch {
    private val generation = AtomicLong()

    @Volatile private var closed = false

    fun begin(): Long {
        requireTrial(!closed, TrialFailure.AUTHENTICATION)
        return generation.get()
    }

    suspend fun check(epoch: Long) {
        currentCoroutineContext().ensureActive()
        requireTrial(!closed && generation.get() == epoch, TrialFailure.CANCELLED)
    }

    fun cancel() {
        generation.incrementAndGet()
    }
    fun close() {
        closed = true
        cancel()
    }
}
