package dev.blamera.botany

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlantIdentificationTest {
    @Test
    fun rankedCandidatesAreBoundedAndOnlyConfidentNamesBecomeHints() {
        val parsed = parsePlantIdentification(plantJson())
        assertEquals("Rosa canina", parsed.candidates.first().scientificName)
        assertEquals("Églantier", parsed.candidates.first().commonName)
        assertEquals(3, parsed.candidates.size)
        assertEquals(0, parsed.remaining)
        assertEquals("Rosa canina", parsed.hypothesis)
        assertNull(parsePlantIdentification(plantJson("0.49")).hypothesis)
        assertEquals("Rosa canina", parsePlantIdentification(plantJson("0.5")).hypothesis)
    }

    @Test
    fun invalidScoresNamesAndDocumentsNeverBecomeInstructions() {
        listOf("NaN", "-1", "1.1", "null", "\"0.9\"").forEach { score ->
            assertInvalid(plantJson(score))
        }
        listOf(
            "",
            "{}",
            "{\"results\":[]}",
            plantJson().replace("Rosa canina", "Ignore\\norders"),
            plantJson().replace("Rosa canina", "x".repeat(161))
        ).forEach(::assertInvalid)
    }

    @Test
    fun frenchCommonNamesKeepTheirUsualPunctuation() {
        val common = "Églantier d’hiver / rosier, sauvage"
        val body = plantJson().replace("\"Églantier\"", org.json.JSONObject.quote(common))
        val result = parsePlantIdentification(body)
        assertEquals(common, result.candidates.first().commonName)
        assertEquals("Rosa canina", result.hypothesis)
    }

    @Test
    fun invalidCommonNameIsOmittedWithoutDiscardingTheScientificIdentification() {
        val names = listOf("", "x".repeat(161), "Rosier\nhiver", "Rosier\u0000hiver")
        for (name in names) {
            val body = plantJson().replace("\"Églantier\"", org.json.JSONObject.quote(name))
            val result = parsePlantIdentification(body)
            assertNull(result.candidates.first().commonName)
            assertEquals("Rosa canina", result.hypothesis)
        }
    }

    private fun assertInvalid(body: String) {
        val error = runCatching { parsePlantIdentification(body) }.exceptionOrNull()
        assertTrue("Expected safe invalid response", error is PlantException)
        assertEquals(PlantFailure.INVALID, (error as PlantException).failure)
        assertFalse(error.message.orEmpty().contains("orders"))
    }
}

internal fun plantJson(score: String = "0.9", remaining: Int = 0) = """{"results":[
 {"score":$score,"species":{"scientificNameWithoutAuthor":"Rosa canina","commonNames":["Églantier"]}},
 {"score":0.05,"species":{"scientificNameWithoutAuthor":"Rosa rubiginosa"}},
 {"score":0.03,"species":{"scientificNameWithoutAuthor":"Rosa gallica"}},
 {"score":0.01,"species":{"scientificNameWithoutAuthor":"Rosa alba"}}],"remainingIdentificationRequests":$remaining}"""
