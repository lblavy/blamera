package dev.blamera.ui

import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CancellationException

internal suspend fun previewAttempt(block: suspend () -> Unit): Boolean = try {
    block()
    true
} catch (error: CancellationException) {
    throw error
} catch (_: ExecutionException) {
    false
} catch (_: SecurityException) {
    false
} catch (_: IllegalArgumentException) {
    false
} catch (_: IllegalStateException) {
    false
}
