package dev.blamera.storage

import java.io.InterruptedIOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** The caller owns the already written, synced and closed pending file. */
internal class AtomicFileReplacement(
    private val move: (Path, Path, StandardCopyOption, StandardCopyOption) -> Unit =
        { source, target, atomic, replace ->
            Files.move(source, target, atomic, replace)
        },
    private val pause: (Long) -> Unit = Thread::sleep
) {
    fun replace(pending: Path, target: Path) {
        var attempts = 0
        while (true) {
            checkInterruption()
            try {
                move(
                    pending,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                return
            } catch (denied: AccessDeniedException) {
                attempts++
                if (attempts == MAXIMUM_ATTEMPTS) throw denied
                pauseBeforeRetry()
            }
        }
    }

    private fun pauseBeforeRetry() {
        checkInterruption()
        try {
            pause(RETRY_PAUSE_MILLIS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException(INTERRUPTED_MESSAGE)
        }
    }

    private fun checkInterruption() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException(INTERRUPTED_MESSAGE)
    }
}

private const val MAXIMUM_ATTEMPTS = 3
private const val RETRY_PAUSE_MILLIS = 25L
private const val INTERRUPTED_MESSAGE = "Atomic replacement interrupted."
