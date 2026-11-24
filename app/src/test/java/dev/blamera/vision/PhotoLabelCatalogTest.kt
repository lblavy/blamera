package dev.blamera.vision

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoLabelCatalogTest {
    @Test fun labelsStayInTheirOfficialOrderAndLookupPerformsNoIo() {
        val bytes = "[\"mirror\",\"door knob\",\"towel\"]".toByteArray()
        val catalog = PhotoLabelCatalog(spec(bytes), 3)
        assertEquals("Objet", catalog.label(0))
        var reads = 0
        catalog.load {
            reads++
            bytes.inputStream()
        }
        catalog.load { error("A loaded catalog must not be read again") }
        assertEquals(1, reads)
        assertEquals("door knob", catalog.label(1))
        assertEquals("Objet", catalog.label(-1))
        assertEquals("Objet", catalog.label(3))
    }

    @Test fun malformedBlankOversizedAndEmptyCatalogsFailWithoutPublishingNames() {
        for (text in listOf("not-json", "[\"\"]")) {
            val bytes = text.toByteArray()
            val catalog = PhotoLabelCatalog(spec(bytes), 1)
            assertThrows(RuntimeException::class.java) { catalog.load { bytes.inputStream() } }
            assertEquals("Objet", catalog.label(0))
        }
        val oversized = PhotoLabelCatalog(ModelAsset("large-labels", 2_000_000, "a".repeat(64)), 1)
        assertThrows(IllegalArgumentException::class.java) {
            oversized.load { error("Must fail before opening") }
        }
        val bytes = "[]".toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            PhotoLabelCatalog(spec(bytes), 0).load { bytes.inputStream() }
        }
    }

    @Test fun changedBytesOrWrongNumberOfCategoriesCannotPublishNames() {
        val original = "[\"cat\",\"dog\"]".toByteArray()
        val catalog = PhotoLabelCatalog(spec(original), 2)
        assertThrows(IllegalArgumentException::class.java) {
            catalog.load { "[\"dog\",\"cat\"]".byteInputStream() }
        }
        assertEquals("Objet", catalog.label(0))
        assertThrows(IllegalArgumentException::class.java) {
            PhotoLabelCatalog(spec(original), 3).load { original.inputStream() }
        }
    }

    private fun spec(bytes: ByteArray) = ModelAsset(
        "labels.json",
        bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    )
}
