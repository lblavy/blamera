package dev.blamera.botany

import org.json.JSONException
import org.json.JSONObject

internal data class PlantCandidate(
    val scientificName: String,
    val commonName: String?,
    val score: Double
)
internal data class PlantIdentification(val candidates: List<PlantCandidate>, val remaining: Int?) {
    val hypothesis: String? get() = candidates.firstOrNull()?.takeIf {
        it.score >= HYPOTHESIS_SCORE
    }?.scientificName
}

internal enum class PlantFailure(val explanation: String) {
    KEY("Clé Pl@ntNet refusée. Vérifiez la configuration."),
    QUOTA("Quota Pl@ntNet atteint. Réessayez demain (UTC)."),
    NETWORK("Pl@ntNet inaccessible. Vérifiez la connexion."),
    SERVICE("Service Pl@ntNet indisponible. Réessayez plus tard."),
    INVALID("Réponse Pl@ntNet invalide."),
    TIMEOUT("Délai Pl@ntNet dépassé."),
    STORAGE("Stockage protégé de la clé Pl@ntNet indisponible.")
}
internal class PlantException(val failure: PlantFailure) : Exception(failure.explanation)
internal fun parsePlantIdentification(body: String): PlantIdentification = try {
    val json = JSONObject(body)
    val results = json.getJSONArray("results")
    plantRequire(results.length() > 0)
    val candidates = (0 until minOf(results.length(), MAX_CANDIDATES)).map { index ->
        val candidate = results.getJSONObject(index)
        val rawScore = candidate.get("score")
        plantRequire(rawScore is Number)
        val score = (rawScore as Number).toDouble()
        plantRequire(score.isFinite() && score in 0.0..1.0)
        val species = candidate.getJSONObject("species")
        val scientificName = plantName(species.getString("scientificNameWithoutAuthor"))
        val commonName = commonPlantName(species)
        PlantCandidate(scientificName, commonName, score)
    }
    val remaining = json.optInt("remainingIdentificationRequests", -1).takeIf { it >= 0 }
    PlantIdentification(candidates, remaining)
} catch (_: JSONException) {
    throw PlantException(PlantFailure.INVALID)
}

private fun plantName(value: String): String {
    plantRequire(value.isNotBlank() && value.length <= MAX_NAME_LENGTH)
    // Botanical names are data, not markup, control characters or appended instructions.
    plantRequire(value.all { it.isLetterOrDigit() || it in " .'-×()" })
    return value
}

private fun commonPlantName(species: JSONObject): String? {
    val name = species.optJSONArray("commonNames")?.opt(0) as? String ?: return null
    val bounded = name.isNotBlank() && name.length <= MAX_NAME_LENGTH
    // Optional display data; only the strictly validated scientific name can enrich a prompt.
    return name.takeIf { bounded && it.none(Char::isISOControl) }
}

internal fun plantRequire(condition: Boolean) {
    if (!condition) throw PlantException(PlantFailure.INVALID)
}

private const val MAX_CANDIDATES = 3
private const val MAX_NAME_LENGTH = 160
private const val HYPOTHESIS_SCORE = 0.5
