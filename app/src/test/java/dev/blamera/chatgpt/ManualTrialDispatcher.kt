package dev.blamera.chatgpt

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi

/** Exercises the real withTimeout owner semantics without waiting five minutes. */
@OptIn(InternalCoroutinesApi::class)
internal class ManualTrialDispatcher :
    CoroutineDispatcher(),
    Delay {
    private val alarms = mutableListOf<Alarm>()

    override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()

    override fun scheduleResumeAfterDelay(
        timeMillis: Long,
        continuation: CancellableContinuation<Unit>
    ) {
        val alarm =
            invokeOnTimeout(
                timeMillis,
                Runnable {
                    continuation.resume(Unit)
                },
                continuation.context
            )
        continuation.invokeOnCancellation { alarm.dispose() }
    }

    override fun invokeOnTimeout(
        timeMillis: Long,
        block: Runnable,
        context: CoroutineContext
    ): DisposableHandle {
        val alarm = Alarm(timeMillis, block)
        synchronized(alarms) { alarms.add(alarm) }
        return DisposableHandle { synchronized(alarms) { alarms.remove(alarm) } }
    }

    fun hasAlarm(millis: Long) = synchronized(alarms) { alarms.any { it.millis == millis } }

    fun expire(millis: Long) {
        val alarm = synchronized(alarms) {
            alarms.first { it.millis == millis }.also { alarms.remove(it) }
        }
        alarm.action.run()
    }

    private data class Alarm(val millis: Long, val action: Runnable)
}
