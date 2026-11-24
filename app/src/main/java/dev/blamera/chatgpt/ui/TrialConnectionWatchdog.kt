package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.TrialDeadline
import dev.blamera.chatgpt.TrialLimits
import dev.blamera.chatgpt.TrialTimeoutScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun armConnectionWatchdog(
    enabled: Boolean,
    scope: CoroutineScope,
    scheduler: TrialTimeoutScheduler,
    expire: () -> Unit
): TrialDeadline? = if (enabled) {
    TrialDeadline(TrialLimits.AUTH_TIMEOUT_MILLIS, scheduler) { scope.launch { expire() } }
} else {
    null
}

internal fun currentTrialAttempt(
    closed: Boolean,
    expected: Int,
    current: Int,
    busy: Boolean
): Boolean = !closed && expected == current && busy
