package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.ChatGptTrialGateway
import dev.blamera.chatgpt.TrialDeadline
import kotlinx.coroutines.Job

/** Cancellation owns the job, budget and network together, including obsolete results. */
internal class TrialOperationLifetime(private val gateway: ChatGptTrialGateway) {
    var generation = 0
    var job: Job? = null
    var deadline: TrialDeadline? = null

    fun invalidate() {
        generation++
        deadline?.close()
        deadline = null
        job?.cancel()
        gateway.cancel()
    }
}
