package dev.blamera.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blamera.faces.FacePhotoFace
import dev.blamera.vision.ImageViewport
import dev.blamera.vision.Letterbox
import dev.blamera.vision.VisionDetection

@Composable
internal fun ExplorablePhoto(
    bitmap: Bitmap,
    overlay: PhotoOverlay,
    interaction: PhotoInteraction,
    modifier: Modifier = Modifier,
    faces: List<FacePhotoFace> = emptyList()
) {
    val contours = remember(overlay.detections) { PhotoContours(overlay.detections) }
    val projection = interaction.projection
    val selectedDetection = overlay.selected?.let(overlay.detections::getOrNull)
    val mask = remember(selectedDetection) { selectedDetection?.let(::coloredMask) }
    val scan = if (overlay.busy) scanPosition() else null
    val imagePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val facePainter = remember { FaceOverlayPainter() }
    Canvas(
        modifier.testTag("photo-exploration").semantics {
            contentDescription = "Image analysée"
            stateDescription = "Zoom ${(interaction.exploration.zoom * FULL_PERCENT).toInt()} %"
        }
            .photoGestures(interaction, overlay)
    ) {
        clipRect {
            val current = interaction.exploration.viewport(
                bitmap.width,
                bitmap.height,
                this.size.width,
                this.size.height
            )
            val matrix = current.matrix()
            drawContext.canvas.nativeCanvas.drawBitmap(bitmap, matrix, imagePaint)
            clipRect(
                current.left,
                current.top,
                current.left + bitmap.width * current.scale,
                current.top + bitmap.height * current.scale
            ) {
                if (mask != null) drawMask(mask, current, projection, imagePaint)
                drawPhotoContours(contours, overlay.selected, current)
                if (faces.isNotEmpty()) {
                    facePainter.drawFaces(
                        drawContext.canvas.nativeCanvas,
                        faces,
                        current,
                        density,
                        FACE_TEXT_SIZE_SP.sp.toPx()
                    )
                }
                if (scan != null) drawScan(current, bitmap, scan)
            }
        }
    }
}

@Composable
private fun scanPosition(): Float {
    val animation = rememberInfiniteTransition(label = "Analyse")
    return animation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SCAN_DURATION_MILLIS), RepeatMode.Reverse),
        label = "Balayage"
    ).value
}

private fun ImageViewport.matrix() = Matrix().apply {
    setScale(scale, scale)
    postTranslate(left, top)
}

private fun coloredMask(detection: VisionDetection): Bitmap {
    val color = categoryColor(detection.classId)
    val argb = color.copy(alpha = 0.36f).toArgb()
    val mask = detection.mask
    return Bitmap.createBitmap(
        IntArray(mask.active.size) {
            if (mask.active[it]) argb else android.graphics.Color.TRANSPARENT
        },
        mask.width,
        mask.height,
        Bitmap.Config.ARGB_8888
    )
}

private fun DrawScope.drawMask(
    bitmap: Bitmap,
    viewport: ImageViewport,
    projection: Letterbox,
    paint: Paint
) {
    val scale = Letterbox.EDGE / bitmap.width.toFloat() / projection.scale * viewport.scale
    val matrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate(
            viewport.left - projection.padLeft / projection.scale * viewport.scale,
            viewport.top - projection.padTop / projection.scale * viewport.scale
        )
    }
    drawContext.canvas.nativeCanvas.drawBitmap(bitmap, matrix, paint)
}

private fun DrawScope.drawScan(viewport: ImageViewport, bitmap: Bitmap, position: Float) {
    val y = viewport.top + bitmap.height * viewport.scale * position
    val band = 28.dp.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, Color.Cyan.copy(alpha = 0.35f), Color.Transparent),
            y - band,
            y + band
        ),
        topLeft = Offset(viewport.left, y - band),
        size = Size(
            bitmap.width * viewport.scale,
            band * 2
        )
    )
    drawLine(
        Color.Cyan.copy(alpha = 0.8f),
        Offset(viewport.left, y),
        Offset(viewport.left + bitmap.width * viewport.scale, y),
        2.dp.toPx()
    )
}

private const val FULL_PERCENT = 100
private const val SCAN_DURATION_MILLIS = 1800
private const val FACE_TEXT_SIZE_SP = 16
