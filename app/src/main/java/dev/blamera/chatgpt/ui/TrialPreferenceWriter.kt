package dev.blamera.chatgpt.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Preference failures cannot overwrite a newer account operation or a closed screen. */
internal class TrialPreferenceWriter(
    private val scope: CoroutineScope,
    private val onFailure: (Throwable) -> Unit
) {
    private var job: Job? = null
    fun save(isCurrent: () -> Boolean, write: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            val result = trialOperation(write)
            if (isCurrent()) result.exceptionOrNull()?.let(onFailure)
        }
    }
    fun cancel() {
        job?.cancel()
    }
}
