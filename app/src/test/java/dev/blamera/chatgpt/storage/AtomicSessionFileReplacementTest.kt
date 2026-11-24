package dev.blamera.chatgpt.storage

import dev.blamera.storage.AtomicFileReplacement
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicSessionFileReplacementTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun transientDenialsRetryOnlyTheMoveAfterOneCompleteWrite() {
        val path = File(temporary.root, "session.bin")
        val previous = byteArrayOf(3, 0, -1, 7)
        val payload = byteArrayOf(8, -2, 0, 5)
        path.writeBytes(previous)
        var moves = 0
        var writes = 0
        val pauses = mutableListOf<Long>()
        val replacement = AtomicFileReplacement(move = { source, target, atomic, replace ->
            assertArrayEquals(payload, source.toFile().readBytes())
            assertArrayEquals(previous, path.readBytes())
            moves++
            if (moves < 3) throw AccessDeniedException("synthetic")
            Files.move(source, target, atomic, replace)
        }, pause = { pauses += it })
        val file =
            AtomicSessionFile(path, replacement) { output, bytes ->
                writes++
                output.write(bytes)
            }
        file.write(payload)
        assertEquals(1, writes)
        assertEquals(3, moves)
        assertEquals(listOf(25L, 25L), pauses)
        assertArrayEquals(payload, file.read())
        assertFalse(File(path.path + ".new").exists())
    }

    @Test fun permanentDenialKeepsTheLastValidBytesAndCleansThePendingFile() {
        val path = File(temporary.root, "session.bin")
        val previous = byteArrayOf(3, 0, -1, 7)
        path.writeBytes(previous)
        var moves = 0
        val replacement =
            AtomicFileReplacement(move = { _, _, _, _ ->
                moves++
                throw AccessDeniedException("synthetic")
            }, pause = { })
        val file = AtomicSessionFile(path, replacement)
        assertThrows(AccessDeniedException::class.java) { file.write(byteArrayOf(4, 5)) }
        assertEquals(3, moves)
        assertArrayEquals(previous, file.read())
        assertFalse(File(path.path + ".new").exists())
    }

    @Test fun sizeGuardRunsBeforeWritingOrMovingAndKeepsThePreviousBytes() {
        val path = File(temporary.root, "session.bin")
        path.writeBytes(byteArrayOf(1, 2))
        var moves = 0
        var writes = 0
        val replacement = AtomicFileReplacement(move = { _, _, _, _ -> moves++ }, pause = { })
        val file = AtomicSessionFile(path, replacement) { _, _ -> writes++ }
        assertThrows(SessionStorageException::class.java) {
            file.write(ByteArray(MAX_ENCRYPTED_SESSION_BYTES + 1))
        }
        assertEquals(0, moves)
        assertEquals(0, writes)
        assertArrayEquals(byteArrayOf(1, 2), file.read())
        assertFalse(File(path.path + ".new").exists())
    }
}
