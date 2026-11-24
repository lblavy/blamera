package dev.blamera.vision

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FrenchPhotoLabelsTest {
    private val englishBytes = "[\"cat\",\"dog\",\"bottle\",\"mouse\",\"bat\"]".toByteArray()
    private val english = spec("canonical.json", englishBytes)

    @Test fun frenchDisplayKeepsCanonicalIdsAndRawEnglishUntouchedWithoutUiIo() {
        val bytes = localized(listOf("Chat", "Chien", "Bouteille", "Souris", "Chauve-souris"))
        val canonical = PhotoLabelCatalog(english, 5)
        val labels = FrenchPhotoLabels(canonical, spec("fr.json", bytes), 5)
        assertEquals("Objet", labels.label(0))
        val reads = mutableListOf<String>()
        labels.load { name ->
            reads.add(name)
            if (name == english.name) englishBytes.inputStream() else bytes.inputStream()
        }
        labels.load { error("Loaded labels must not read assets again") }
        assertEquals(listOf(english.name, "fr.json"), reads)
        assertEquals("cat", canonical.label(0))
        assertEquals("Chat", labels.label(0))
        assertEquals("Chien", labels.label(1))
        assertEquals("Bouteille", labels.label(2))
        assertEquals("Chauve-souris", labels.label(4))
        assertEquals("Objet", labels.label(-1))
        assertEquals("Objet", labels.label(5))
    }

    @Test fun changedCanonicalOrTranslatedBytesAreRejectedBeforePublishingFrenchNames() {
        val bytes = localized(listOf("Chat", "Chien", "Bouteille", "Souris", "Chauve-souris"))
        val canonical = PhotoLabelCatalog(english, 5)
        val labels = FrenchPhotoLabels(canonical, spec("fr.json", bytes), 5)
        var frenchOpened = false
        assertThrows(IllegalArgumentException::class.java) {
            labels.load { name ->
                if (name == english.name) {
                    "[\"dog\"]".byteInputStream()
                } else {
                    frenchOpened = true
                    bytes.inputStream()
                }
            }
        }
        assertFalse(frenchOpened)
        assertEquals("Objet", labels.label(0))
        assertThrows(IllegalArgumentException::class.java) {
            labels.load { name ->
                if (name ==
                    english.name
                ) {
                    englishBytes.inputStream()
                } else {
                    bytes.copyOf(bytes.size - 1).inputStream()
                }
            }
        }
        assertEquals("cat", canonical.label(0))
        assertEquals("Objet", labels.label(0))
    }

    @Test fun partialWrongLanguageOrMisalignedCatalogCannotPublishAnyTranslation() {
        val valid = localized(listOf("Chat", "Chien", "Bouteille", "Souris", "Chauve-souris"))
        for (edit in listOf<(JSONObject) -> Unit>(
            { it.put("schema", 2) },
            { it.put("schema", "1") },
            { it.put("locale", "en") },
            { it.put("englishSha256", "0".repeat(64)) },
            { it.put("labels", JSONArray(listOf("Chat"))) },
            { it.getJSONArray("labels").put(2, " ") },
            { it.getJSONArray("labels").put(2, 2) },
            { it.remove("englishSha256") }
        )) {
            val changed = JSONObject(
                valid.toString(Charsets.UTF_8)
            ).also(edit).toString().toByteArray()
            val labels =
                FrenchPhotoLabels(PhotoLabelCatalog(english, 5), spec("fr.json", changed), 5)
            assertThrows(RuntimeException::class.java) {
                labels.load { name ->
                    if (name ==
                        english.name
                    ) {
                        englishBytes.inputStream()
                    } else {
                        changed.inputStream()
                    }
                }
            }
            assertEquals("Objet", labels.label(0))
        }
    }

    private fun localized(names: List<String>) = JSONObject()
        .put("schema", 1).put("locale", "fr").put("englishSha256", english.sha256)
        .put("labels", JSONArray(names)).toString().toByteArray()

    private fun spec(name: String, bytes: ByteArray) = ModelAsset(
        name,
        bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    )
}
