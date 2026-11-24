package dev.blamera.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.blamera.faces.FaceIdentityDecision
import dev.blamera.faces.FaceIdentityStatus
import dev.blamera.faces.FacePhotoFace
import dev.blamera.faces.FacePhotoState
import dev.blamera.faces.runtime.FaceBounds
import dev.blamera.vision.ImageViewport

internal fun projectFaceBounds(bounds: FaceBounds, viewport: ImageViewport): FaceBounds =
    FaceBounds(
        viewport.left + bounds.left * viewport.scale,
        viewport.top + bounds.top * viewport.scale,
        viewport.left + bounds.right * viewport.scale,
        viewport.top + bounds.bottom * viewport.scale
    )

internal fun faceIdentityLabel(decision: FaceIdentityDecision?): String = when (decision?.status) {
    FaceIdentityStatus.RECOGNIZED -> decision.name ?: "Identité incertaine"
    FaceIdentityStatus.UNKNOWN -> "Inconnu"
    FaceIdentityStatus.UNCERTAIN -> "Identité incertaine"
    null -> "Visage non analysé"
}

internal data class FaceLabelPlacement(
    val text: String,
    val bounds: FaceBounds,
    val faceBounds: FaceBounds
)

internal class FaceOverlayPainter {
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LABEL_BACKGROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun placements(
        faces: List<FacePhotoFace>,
        viewport: ImageViewport,
        clip: FaceBounds,
        density: Float,
        textSize: Float
    ): List<FaceLabelPlacement> {
        text.textSize = textSize
        val placed = mutableListOf<FaceLabelPlacement>()
        for (face in faces) {
            val mapped = projectFaceBounds(face.bounds, viewport)
            if (!RectF.intersects(mapped.rect(), clip.rect())) continue
            val value = faceIdentityLabel(face.identity)
            val bounds = labelBounds(value, mapped, clip, LABEL_PADDING_DP * density, placed)
            placed += FaceLabelPlacement(value, bounds, mapped)
        }
        return java.util.List.copyOf(placed)
    }

    fun drawFaces(
        canvas: Canvas,
        faces: List<FacePhotoFace>,
        viewport: ImageViewport,
        density: Float,
        textSize: Float
    ) {
        val clip = canvas.clipBounds
        val bounds =
            FaceBounds(
                clip.left.toFloat(),
                clip.top.toFloat(),
                clip.right.toFloat(),
                clip.bottom.toFloat()
            )
        val padding = LABEL_PADDING_DP * density
        placements(faces, viewport, bounds, density, textSize).forEach { label ->
            canvas.drawRoundRect(label.bounds.rect(), padding, padding, background)
            canvas.drawText(
                label.text,
                label.bounds.left + padding,
                label.bounds.top + padding - text.fontMetrics.ascent,
                text
            )
        }
    }

    private fun labelBounds(
        value: String,
        face: FaceBounds,
        clip: FaceBounds,
        padding: Float,
        placed: List<FaceLabelPlacement>
    ): FaceBounds {
        val width = (text.measureText(value) + padding * 2).coerceAtMost(clip.right - clip.left)
        val height = (text.fontSpacing + padding * 2).coerceAtMost(clip.bottom - clip.top)
        val left = face.left.coerceIn(clip.left, clip.right - width)
        val preferred = (face.top - height).coerceIn(clip.top, clip.bottom - height)
        val candidates = listOf(preferred) + placed.flatMap {
            listOf(it.bounds.bottom + padding, it.bounds.top - height - padding)
        }
        val top = candidates.firstOrNull { candidate ->
            candidate >= clip.top && candidate + height <= clip.bottom && placed.none {
                RectF.intersects(
                    RectF(left, candidate, left + width, candidate + height),
                    it.bounds.rect()
                )
            }
        } ?: preferred
        return FaceBounds(left, top, left + width, top + height)
    }
}

private fun FaceBounds.rect() = RectF(left, top, right, bottom)

@Composable
internal fun FacePhotoFeedback(state: FacePhotoState) {
    val message = when (state) {
        FacePhotoState.Empty -> null
        FacePhotoState.Running -> "Reconnaissance en cours…"
        FacePhotoState.NoFace -> "Aucun visage détecté."
        FacePhotoState.TooManyFaces -> "Trop de visages, recadrez l’image."
        FacePhotoState.Unavailable -> "Reconnaissance faciale indisponible."
        is FacePhotoState.Detected -> "${state.faces.size} visage(s) détecté(s)."
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

private const val LABEL_PADDING_DP = 4f
private const val LABEL_BACKGROUND = 0xCC000000.toInt()
