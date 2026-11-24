package dev.blamera.chatgpt

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal fun interface TrialTimeoutScheduler {
    fun schedule(timeoutMillis: Long, expired: () -> Unit): AutoCloseable
}

internal object SystemTrialTimeoutScheduler : TrialTimeoutScheduler {
    private val executor = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "blamera-request-deadline").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    override fun schedule(timeoutMillis: Long, expired: () -> Unit): AutoCloseable {
        val task = executor.schedule(expired, timeoutMillis, TimeUnit.MILLISECONDS)
        return AutoCloseable { task.cancel(false) }
    }
}

internal class TrialDeadline(
    timeoutMillis: Long,
    scheduler: TrialTimeoutScheduler,
    closeResource: () -> Unit
) : AutoCloseable {
    private val state = AtomicReference(State.ACTIVE)
    private val alarm = scheduler.schedule(timeoutMillis) {
        if (state.compareAndSet(State.ACTIVE, State.EXPIRED)) closeResource()
    }

    val expired: Boolean get() = state.get() == State.EXPIRED

    fun check() {
        requireTrial(!expired, TrialFailure.TIMEOUT)
    }

    fun finish() {
        if (!state.compareAndSet(State.ACTIVE, State.CLOSED)) check()
        alarm.close()
    }

    fun failure(fallback: TrialFailure): ChatGptTrialException =
        ChatGptTrialException(if (expired) TrialFailure.TIMEOUT else fallback)

    override fun close() {
        state.compareAndSet(State.ACTIVE, State.CLOSED)
        alarm.close()
    }

    private enum class State { ACTIVE, EXPIRED, CLOSED }
}
