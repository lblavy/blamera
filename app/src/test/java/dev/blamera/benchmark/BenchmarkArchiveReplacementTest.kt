package dev.blamera.benchmark

import dev.blamera.storage.AtomicFileReplacement
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BenchmarkArchiveReplacementTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun transientDenialsKeepTheSameReadyDocumentUntilAtomicReplacement() {
        val path = File(temporary.root, "last.json")
        val previous = document("old", "running")
        val next = document("new", "running")
        path.writeText(previous)
        var moves = 0
        val pauses = mutableListOf<Long>()
        val replacement = AtomicFileReplacement(move = { source, target, atomic, replace ->
            assertEquals(next, source.toFile().readText())
            assertEquals(previous, path.readText())
            moves++
            if (moves < 3) throw AccessDeniedException("synthetic")
            Files.move(source, target, atomic, replace)
        }, pause = { pauses += it })
        val archive = BenchmarkArchive(path, replacement)
        archive.begin(next)
        assertEquals(3, moves)
        assertEquals(listOf(25L, 25L), pauses)
        assertEquals(next, archive.read())
        assertFalse(File(path.path + ".pending").exists())
    }

    @Test fun permanentDenialKeepsTheLastValidReportAndCleansThePendingFile() {
        val path = File(temporary.root, "last.json")
        val previous = document("old", "running")
        path.writeText(previous)
        var moves = 0
        val replacement =
            AtomicFileReplacement(move = { _, _, _, _ ->
                moves++
                throw AccessDeniedException("synthetic")
            }, pause = { })
        val archive = BenchmarkArchive(path, replacement)
        assertThrows(AccessDeniedException::class.java) {
            archive.begin(document("new", "running"))
        }
        assertEquals(3, moves)
        assertEquals(previous, archive.read())
        assertFalse(File(path.path + ".pending").exists())
    }

    @Test fun oldRunAndInvalidDocumentGuardsDoNotStartAReplacement() {
        val path = File(temporary.root, "last.json")
        val previous = document("new", "running")
        path.writeText(previous)
        var moves = 0
        val archive =
            BenchmarkArchive(
                path,
                AtomicFileReplacement(move = { _, _, _, _ ->
                    moves++
                }, pause = { })
            )
        assertFalse(archive.update("old", document("old", "complete")))
        assertThrows(IllegalArgumentException::class.java) { archive.begin("not-json") }
        assertEquals(0, moves)
        assertEquals(previous, archive.read())
        assertFalse(File(path.path + ".pending").exists())
    }

    private fun document(id: String, status: String) =
        JSONObject().put("schema", 2).put("runId", id).put("status", status).toString()
}
