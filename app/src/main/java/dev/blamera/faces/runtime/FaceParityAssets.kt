package dev.blamera.faces.runtime

import android.content.Context
import android.graphics.BitmapFactory
import dev.blamera.faces.exactKeys
import dev.blamera.faces.strictFaceJson
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

internal fun loadFaceParityCases(
    context: Context,
    profile: String,
    open: (String) -> InputStream = context.assets::open
): List<FaceParityCase> {
    val read: (String, Int) -> ByteArray = { path, maximum -> faceAssetBytes(open, path, maximum) }
    val manifest =
        strictFaceJson(
            read("faces/parity/manifest.json", MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)
        )
    manifest.exactKeys("schema", "profile", "tolerances", "cases")
    require(manifest.get("schema") == 2L)
    val expectedProfile = manifest.getJSONObject("profile")
    expectedProfile.exactKeys("key", "dimension")
    require(
        expectedProfile.get("key") == profile &&
            expectedProfile.get("dimension") == FACE_EMBEDDING_DIMENSION.toLong()
    )
    checkTolerances(manifest.getJSONObject("tolerances"))
    val cases = manifest.getJSONArray("cases")
    require(cases.length() in MINIMUM_PARITY_CASES..MAXIMUM_PARITY_CASES)
    val values = (0 until cases.length()).map { loadCase(read, cases.getJSONObject(it)) }
    require(values.map { it.expected::class }.toSet().size == MINIMUM_PARITY_CASES)
    return values
}

private fun checkTolerances(value: JSONObject) {
    val expected = mapOf(
        "bounds_abs_px" to MAXIMUM_POINT_ERROR,
        "landmarks_abs_px" to MAXIMUM_POINT_ERROR,
        "aligned_rgb_mae" to MAXIMUM_RGB_MAE,
        "embedding_cosine_min" to MINIMUM_EMBEDDING_COSINE,
        "score_abs" to MAXIMUM_PROTOTYPE_DELTA
    )
    require(value.keys().asSequence().toSet() == expected.keys)
    expected.forEach { (key, required) ->
        val actual = value.get(key)
        require(actual is Number && actual.toDouble() == required)
    }
}

private fun loadCase(read: (String, Int) -> ByteArray, value: JSONObject): FaceParityCase {
    val base = listOf("id", "image", "image_sha256", "width", "height", "expected_kind")
    val kind = value.getString("expected_kind")
    val extras = when (kind) {
        "single" -> listOf("bounds", "landmarks", "aligned", "embedding")
        "multiple" -> listOf("faces")
        else -> emptyList()
    }
    require(value.keys().asSequence().toSet() == (base + extras).toSet())
    val bytes = read(parityPath(value.getString("image")), MAX_IMAGE_BYTES)
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
        "%02x".format(it)
    }
    require(hash == value.getString("image_sha256"))
    val frame = decodeFrame(bytes)
    require(
        value.get("width") == frame.width.toLong() && value.get("height") == frame.height.toLong()
    )
    val expected = when (kind) {
        "none" -> FaceParityExpected.None
        "multiple" -> expectedMultiple(read, value)
        "single" -> expectedSingle(read, value)
        else -> error("Unsupported public parity case")
    }
    return FaceParityCase(frame, expected)
}

private fun expectedMultiple(
    read: (String, Int) -> ByteArray,
    value: JSONObject
): FaceParityExpected.Multiple {
    val faces = value.getJSONArray("faces")
    require(faces.length() in 2..FACE_MAX_CANDIDATES)
    return FaceParityExpected.Multiple(
        (0 until faces.length()).map {
            val face = faces.getJSONObject(it)
            face.exactKeys("bounds", "landmarks", "aligned", "embedding")
            expectedSingle(read, face)
        }
    )
}

private fun expectedSingle(
    read: (String, Int) -> ByteArray,
    value: JSONObject
): FaceParityExpected.Single {
    val image = decodeFrame(read(parityPath(value.getString("aligned")), MAX_IMAGE_BYTES))
    require(image.width == FACE_ALIGNED_SIZE && image.height == FACE_ALIGNED_SIZE)
    val embedding = numbers(value.getJSONArray("embedding"), FACE_EMBEDDING_DIMENSION)
    val norm = embedding.fold(0.0) { total, current -> Math.hypot(total, current) }
    require(abs(norm - 1.0) <= REFERENCE_UNIT_TOLERANCE)
    return FaceParityExpected.Single(
        numbers(value.getJSONArray("bounds"), BOUNDS_COORDINATES),
        numbers(value.getJSONArray("landmarks"), LANDMARK_COORDINATES),
        image.pixels,
        embedding
    )
}

private fun numbers(value: JSONArray, size: Int): List<Double> {
    require(value.length() == size)
    return (0 until size).map {
        val number = value.get(it)
        require(number is Number && number.toDouble().isFinite())
        number.toDouble()
    }
}

private fun parityPath(relative: String): String {
    require(PARITY_IMAGE.matches(relative))
    return "faces/parity/$relative"
}

private fun decodeFrame(bytes: ByteArray): FaceRgbFrame {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outWidth in 1..MAX_FRAME_EDGE && options.outHeight in 1..MAX_FRAME_EDGE)
    val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    try {
        val colors = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(colors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val rgb = ByteArray(colors.size * FACE_RGB_CHANNELS)
        colors.forEachIndexed { index, color ->
            val offset = index * FACE_RGB_CHANNELS
            rgb[offset] = (color shr RED_SHIFT).toByte()
            rgb[offset + 1] = (color shr GREEN_SHIFT).toByte()
            rgb[offset + 2] = color.toByte()
        }
        return FaceRgbFrame(bitmap.width, bitmap.height, rgb)
    } finally {
        bitmap.recycle()
    }
}

internal fun faceAssetBytes(context: Context, path: String, maximum: Int): ByteArray =
    faceAssetBytes(context.assets::open, path, maximum)

private fun faceAssetBytes(open: (String) -> InputStream, path: String, maximum: Int): ByteArray =
    open(path).use {
        val result = it.readNBytes(maximum + 1)
        require(result.isNotEmpty() && result.size <= maximum)
        result
    }

private val PARITY_IMAGE = Regex("(images|expected)/[a-z0-9._-]+\\.png")
private const val MAX_MANIFEST_BYTES = 1024 * 1024
private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
private const val MAX_FRAME_EDGE = 2048
private const val MINIMUM_PARITY_CASES = 3
private const val MAXIMUM_PARITY_CASES = 20
private const val REFERENCE_UNIT_TOLERANCE = .001
private const val BOUNDS_COORDINATES = 4
private const val LANDMARK_COORDINATES = 10
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
