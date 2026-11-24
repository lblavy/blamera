package dev.blamera.ui

import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import dev.blamera.vision.VisionDetection
import dev.blamera.vision.categoryLabel

internal fun DrawScope.drawDetectionContours(
    detections: List<VisionDetection>,
    matrix: Matrix,
    top: Float = 0f
) {
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.YELLOW
        textSize = 14.dp.toPx()
        setShadowLayer(2.dp.toPx(), 0f, 0f, android.graphics.Color.BLACK)
    }
    detections.forEach { detection ->
        val path = detectionContourPath(detection, matrix)
        drawPath(path, Color.Yellow, style = Stroke(width = 2.dp.toPx()))
        val anchor = floatArrayOf(detection.box.left, detection.box.top)
        matrix.mapPoints(anchor)
        drawContext.canvas.nativeCanvas.drawText(
            categoryLabel(detection.classId),
            anchor[0].coerceIn(0f, size.width),
            maxOf(anchor[1] + label.textSize, top + label.textSize),
            label
        )
    }
}

internal fun detectionContourPath(detection: VisionDetection, matrix: Matrix): Path {
    val path = Path()
    detection.contours.forEach { segment ->
        path.moveTo(segment.start.x, segment.start.y)
        path.lineTo(segment.end.x, segment.end.y)
    }
    path.asAndroidPath().transform(matrix)
    return path
}
