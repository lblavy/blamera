package dev.blamera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextOverflow
import dev.blamera.camera.LiveResult
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.categoryLabel

@Composable
internal fun CameraContours(result: LiveResult?) {
    val detections = (result?.result as? AnalysisResult.Success)?.detections.orEmpty()
    Canvas(Modifier.fillMaxSize()) {
        clipRect {
            if (result != null) drawDetectionContours(detections, result.points)
        }
    }
}

@Composable
internal fun CameraAnalysisMessage(
    result: LiveResult?,
    running: Boolean,
    compact: Boolean = false
) {
    if (compact) {
        CompactCameraMessage(result, running)
        return
    }
    when (val value = result?.result) {
        AnalysisResult.Failed -> Text(
            "Analyse impossible. Arrêtez puis réessayez, ou utilisez Image."
        )
        is AnalysisResult.Success -> {
            Text("Dernière analyse : ${result.millis} ms (échantillon, pas une cadence garantie).")
            if (value.detections.isEmpty()) Text("Aucun objet reconnu à ce seuil.")
            value.detections.forEach { Text(categoryLabel(it.classId)) }
        }
        null -> if (running) {
            Text(
                "Analyse locale en direct… Gardez la scène immobile pour juger les contours."
            )
        }
    }
}

@Composable
private fun CompactCameraMessage(result: LiveResult?, running: Boolean) {
    val message = when (val value = result?.result) {
        AnalysisResult.Failed -> "Analyse impossible. Arrêtez puis réessayez, ou utilisez Galerie."
        is AnalysisResult.Success -> value.detections.map { categoryLabel(it.classId) }.distinct()
            .joinToString(" · ").ifEmpty { "Aucun objet reconnu à ce seuil." }
        null -> if (running) "Analyse locale en direct…" else ""
    }
    Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis)
}
