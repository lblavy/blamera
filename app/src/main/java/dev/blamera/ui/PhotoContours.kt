package dev.blamera.ui

import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import dev.blamera.vision.ImageViewport
import dev.blamera.vision.VisionDetection

internal data class PhotoContour(val category: Int, val sourcePath: Path)

internal class PhotoContours(detections: List<VisionDetection>) {
    val paths: List<PhotoContour> =
        detections.map { PhotoContour(it.classId, detectionContourPath(it, Matrix())) }
}

internal fun DrawScope.drawPhotoContours(
    geometry: PhotoContours,
    selected: Int?,
    viewport: ImageViewport
) {
    withTransform({
        translate(viewport.left, viewport.top)
        scale(viewport.scale, viewport.scale, pivot = Offset.Zero)
    }) {
        geometry.paths.forEachIndexed { index, contour ->
            val width = if (index == selected) 3.dp.toPx() else 2.dp.toPx()
            drawPath(
                contour.sourcePath,
                categoryColor(contour.category),
                style = Stroke(width / viewport.scale)
            )
        }
    }
}
