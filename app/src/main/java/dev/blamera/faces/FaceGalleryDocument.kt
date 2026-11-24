package dev.blamera.faces

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlin.math.abs
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal const val MAX_FACE_GALLERY_BYTES = 122880

internal class FaceGalleryException : Exception("Galerie invalide ou incompatible.")

internal class FaceGalleryIdentity(
    val id: String,
    val name: String,
    val referenceCount: Int,
    val prototype: List<Double>
)

internal class FaceGalleryDocument(
    val json: String,
    val identities: List<FaceGalleryIdentity>,
    val policy: FacePolicy
)

internal class FaceGalleryDecoder(private val approved: String) {
    fun decode(bytes: ByteArray): FaceGalleryDocument = try {
        require(bytes.size in 1..MAX_FACE_GALLERY_BYTES)
        val json = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(
                CodingErrorAction.REPORT
            ).decode(ByteBuffer.wrap(bytes)).toString()
        require(!json.startsWith('\uFEFF'))
        val value = strictFaceJson(json)
        validateHeader(value)
        val policy = validatePolicy(value.getJSONObject("policy"))
        validateQualification(value.getJSONObject("qualification"))
        FaceGalleryDocument(json, identities(value.getJSONArray("identities")), policy)
    } catch (_: java.io.IOException) {
        throw FaceGalleryException()
    } catch (_: IllegalArgumentException) {
        throw FaceGalleryException()
    } catch (_: IllegalStateException) {
        throw FaceGalleryException()
    } catch (_: JSONException) {
        throw FaceGalleryException()
    } catch (_: java.time.DateTimeException) {
        throw FaceGalleryException()
    }

    private fun validateHeader(value: JSONObject) {
        value.exactKeys(
            "format",
            "schema",
            "exported_utc",
            "profile",
            "pipeline",
            "policy",
            "identities",
            "qualification"
        )
        require(value.get("format") == "blamera-face-gallery")
        require(value.integer("schema", 1..1) == 1)
        val date = value.get("exported_utc")
        require(date is String && UTC_FORMAT.matches(date))
        require(LocalDateTime.parse(date, UTC_PARSER).year >= 1)
        val expected = strictFaceJson(approved)
        require(sameFaceJson(value.get("profile"), expected.get("profile")))
        require(sameFaceJson(value.get("pipeline"), expected.get("pipeline")))
    }

    private fun identities(values: JSONArray): List<FaceGalleryIdentity> {
        require(values.length() in 1..MAX_IDENTITIES)
        val result = (0 until values.length()).map { identity(values.getJSONObject(it)) }
        require(result.map { it.id }.distinct().size == result.size)
        require(result.map { it.name }.distinct().size == result.size)
        return result
    }

    private fun identity(value: JSONObject): FaceGalleryIdentity {
        value.exactKeys("id", "name", "reference_count", "prototype")
        val id = value.get("id")
        require(id is String && ID_FORMAT.matches(id))
        val name = value.get("name")
        require(name is String && validFaceName(name))
        val count = value.integer("reference_count", 1..MAX_REFERENCES)
        val numbers = value.getJSONArray("prototype")
        require(numbers.length() == PROTOTYPE_DIMENSION)
        val vector = (0 until numbers.length()).map { faceNumber(numbers.get(it)) }
        require(abs(sqrt(vector.sumOf { it * it }) - 1.0) <= UNIT_NORM_TOLERANCE)
        return FaceGalleryIdentity(id, name, count, vector)
    }
}

private fun validatePolicy(value: JSONObject): FacePolicy {
    value.exactKeys("reject", "accept", "margin")
    val reject = faceNumber(value.get("reject"))
    val accept = faceNumber(value.get("accept"))
    val margin = faceNumber(value.get("margin"))
    return FacePolicy(reject, accept, margin)
}

private fun validateQualification(value: JSONObject) {
    value.exactKeys("status", "frozen_context", "test_report", "counts", "scope")
    require(value.get("status") == "exploratory")
    require(value.get("scope") == "held-out-local-corpus")
    require(SHA_FORMAT.matches(value.get("frozen_context") as? String ?: ""))
    require(SHA_FORMAT.matches(value.get("test_report") as? String ?: ""))
    validateCounts(value.getJSONObject("counts"))
}

private fun validateCounts(value: JSONObject) {
    val fields = listOf(
        "total", "usable", "unusable", "correct_names", "false_names", "false_names_known",
        "false_names_unknown", "correct_unknown", "false_rejections", "uncertain"
    )
    value.exactKeys(fields.toSet())
    val counts = fields.associateWith { value.integer(it, 0..MAX_QUALIFICATION_COUNT) }
    val total = counts.getValue("total")
    require(total >= 1 && counts.values.all { it <= total })
    require(counts.getValue("usable") + counts.getValue("unusable") == total)
    require(
        counts.getValue("false_names_known") + counts.getValue("false_names_unknown") ==
            counts.getValue("false_names")
    )
    val outcomes =
        listOf(
            "correct_names",
            "false_names",
            "correct_unknown",
            "false_rejections",
            "uncertain",
            "unusable"
        )
    require(outcomes.sumOf(counts::getValue) == total)
}

private fun validFaceName(name: String): Boolean = name.isNotEmpty() && name.trim() == name &&
    name.codePointCount(0, name.length) <= MAX_NAME_CODEPOINTS && name.codePoints().noneMatch {
        Character.getType(it) in FORBIDDEN_NAME_TYPES
    }

internal fun JSONObject.exactKeys(vararg keys: String) {
    exactKeys(keys.toSet())
}

private fun JSONObject.exactKeys(keys: Set<String>) {
    require(keys().asSequence().toSet() == keys)
}

private fun JSONObject.integer(key: String, range: IntRange): Int {
    val value = get(key)
    require(value is Long && value >= range.first && value <= range.last)
    return value.toInt()
}

private fun faceNumber(value: Any): Double {
    require(value is Number)
    return value.toDouble().also { require(it.isFinite()) }
}

private val ID_FORMAT = Regex("[0-9a-f]{32}")
private val SHA_FORMAT = Regex("[0-9a-f]{64}")
private val UTC_FORMAT = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")
private val UTC_PARSER = DateTimeFormatter.ofPattern(
    "uuuu-MM-dd'T'HH:mm:ss'Z'"
).withResolverStyle(ResolverStyle.STRICT)
private val FORBIDDEN_NAME_TYPES = setOf(
    Character.CONTROL,
    Character.FORMAT,
    Character.SURROGATE,
    Character.PRIVATE_USE,
    Character.UNASSIGNED
).map {
    it.toInt()
}.toSet()

private const val MAX_IDENTITIES = 10
private const val MAX_REFERENCES = 10000
private const val PROTOTYPE_DIMENSION = 512
private const val UNIT_NORM_TOLERANCE = .001
private const val MAX_NAME_CODEPOINTS = 80
private const val MAX_QUALIFICATION_COUNT = 100000
