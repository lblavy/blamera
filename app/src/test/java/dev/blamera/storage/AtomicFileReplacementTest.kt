package dev.blamera.storage

import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicFileReplacementTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun defaultMoveAtomicallyReplacesTheCompleteRecord() {
        val files = files()
        AtomicFileReplacement().replace(files.first, files.second)
        assertEquals("replacement", files.second.toFile().readText())
        assertFalse(Files.exists(files.first))
    }

    @Test fun oneOrTwoTransientDenialsRetryTheSameClosedPendingWithTheSameOptions() {
        for (denials in 1..2) {
            val files = files()
            var moves = 0
            val pauses = mutableListOf<Long>()
            val replacement = AtomicFileReplacement(move = { source, target, atomic, replace ->
                assertEquals(files.first, source)
                assertEquals(files.second, target)
                assertEquals(StandardCopyOption.ATOMIC_MOVE, atomic)
                assertEquals(StandardCopyOption.REPLACE_EXISTING, replace)
                assertEquals("replacement", source.toFile().readText())
                assertEquals("previous", target.toFile().readText())
                moves++
                if (moves <= denials) throw AccessDeniedException("synthetic pending")
                Files.move(source, target, atomic, replace)
            }, pause = { pauses += it })
            replacement.replace(files.first, files.second)
            assertEquals(denials + 1, moves)
            assertEquals(List(denials) { 25L }, pauses)
            assertEquals("replacement", files.second.toFile().readText())
        }
    }

    @Test fun permanentDenialStopsAfterThreeAttemptsAndPreservesThePreviousDocument() {
        val files = files()
        val failure = AccessDeniedException("synthetic pending")
        var moves = 0
        val pauses = mutableListOf<Long>()
        val replacement =
            AtomicFileReplacement(move = { _, _, _, _ ->
                moves++
                throw failure
            }, pause = {
                pauses +=
                    it
            })
        assertSame(
            failure,
            assertThrows(AccessDeniedException::class.java) {
                replacement.replace(files.first, files.second)
            }
        )
        assertEquals(3, moves)
        assertEquals(listOf(25L, 25L), pauses)
        assertEquals("previous", files.second.toFile().readText())
        assertEquals("replacement", files.first.toFile().readText())
    }

    @Test fun otherIoFailuresPropagateWithoutRetryOrFallback() {
        val files = files()
        for (failure in listOf(
            IOException("synthetic"),
            AtomicMoveNotSupportedException("source", "target", "synthetic")
        )) {
            var moves = 0
            var pauses = 0
            val replacement =
                AtomicFileReplacement(move = { _, _, _, _ ->
                    moves++
                    throw failure
                }, pause = { pauses++ })
            assertSame(
                failure,
                assertThrows(IOException::class.java) {
                    replacement.replace(files.first, files.second)
                }
            )
            assertEquals(1, moves)
            assertEquals(0, pauses)
            assertEquals("previous", files.second.toFile().readText())
        }
    }

    @Test fun interruptionDuringPauseStopsRetriesAndRestoresTheThreadStatusWithoutPrivateContext() {
        val files = files()
        var moves = 0
        var pauses = 0
        val replacement =
            AtomicFileReplacement(move = { _, _, _, _ ->
                moves++
                throw AccessDeniedException("private-path")
            }, pause = {
                pauses++
                throw InterruptedException("private-context")
            })
        try {
            val failure =
                assertThrows(InterruptedIOException::class.java) {
                    replacement.replace(files.first, files.second)
                }
            assertTrue(Thread.currentThread().isInterrupted)
            assertEquals(1, moves)
            assertEquals(1, pauses)
            assertEquals("previous", files.second.toFile().readText())
            assertFalse(failure.message.orEmpty().contains("private"))
            assertEquals(null, failure.cause)
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun anAlreadyInterruptedThreadDoesNotAttemptReplacement() {
        val files = files()
        var moves = 0
        val replacement = AtomicFileReplacement(move = { source, target, atomic, replace ->
            moves++
            Files.move(source, target, atomic, replace)
        }, pause = { })
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedIOException::class.java) {
                replacement.replace(files.first, files.second)
            }
            assertTrue(Thread.currentThread().isInterrupted)
            assertEquals(0, moves)
            assertEquals("previous", files.second.toFile().readText())
        } finally {
            Thread.interrupted()
        }
    }

    private fun files(): Pair<Path, Path> {
        val directory = temporary.newFolder().toPath()
        val pending = directory.resolve("last.pending")
        val target = directory.resolve("last.json")
        pending.toFile().writeText("replacement")
        target.toFile().writeText("previous")
        return pending to target
    }
}
