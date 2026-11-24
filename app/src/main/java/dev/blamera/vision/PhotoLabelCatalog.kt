package dev.blamera.vision

import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONException

internal class PhotoLabelCatalog(
    private val asset: ModelAsset,
    private val count: Int,
    private val decodeArray: (String) -> JSONArray = ::JSONArray
) {
    val sha256: String get() = asset.sha256

    @Volatile private var names: List<String>? = null

    fun load(open: (String) -> InputStream) {
        synchronized(this) {
            if (names != null) return
            require(asset.size <= MAX_LABEL_BYTES && count > 0)
            val buffer = ByteArrayOutputStream(asset.size.toInt())
            open(asset.name).use { copyVerifiedAsset(it, buffer, asset) }
            names = parse(buffer.toString(Charsets.UTF_8.name()))
        }
    }

    fun label(classId: Int): String = names?.getOrNull(classId) ?: "Objet"

    private fun parse(json: String): List<String> = try {
        val array = decodeArray(json)
        require(array.length() == count) { "Unexpected category count" }
        List(count) {
            val name = array.get(it)
            require(name is String && name.isNotBlank()) { "Invalid category label" }
            name
        }
    } catch (_: JSONException) {
        throw VisionException("Invalid photo category catalog")
    }

    private companion object {
        const val MAX_LABEL_BYTES = 1024 * 1024
    }
}
