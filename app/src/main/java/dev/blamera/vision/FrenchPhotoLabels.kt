package dev.blamera.vision

import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject

internal class FrenchPhotoLabels(
    private val canonical: PhotoLabelCatalog,
    asset: ModelAsset,
    count: Int
) {
    private val translated = PhotoLabelCatalog(asset, count) { json ->
        val document = JSONObject(json)
        require(document.get("schema") == 1) { "Unknown localized label schema" }
        require(document.getString("locale") == "fr") { "Unexpected label language" }
        require(document.getString("englishSha256") == canonical.sha256) {
            "Localized labels do not match the canonical vocabulary"
        }
        document.getJSONArray("labels")
    }

    fun load(open: (String) -> InputStream) {
        canonical.load(open)
        translated.load(open)
    }

    fun label(classId: Int): String = translated.label(classId)
}
