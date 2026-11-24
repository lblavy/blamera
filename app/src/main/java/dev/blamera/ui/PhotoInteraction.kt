package dev.blamera.ui

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import dev.blamera.vision.ImagePoint
import dev.blamera.vision.Letterbox
import dev.blamera.vision.VisionDetection

internal data class PhotoOverlay(
    val detections: List<VisionDetection>,
    val selected: Int?,
    val busy: Boolean,
    val tap: (List<Int>) -> Unit
)

internal class PhotoInteraction(
    private val bitmap: Bitmap,
    private val onSelectionChanged: () -> Unit = { },
    private val onFrameChanged: () -> Unit = { }
) {
    var exploration by mutableStateOf(ImageExploration())
        private set
    var size by mutableStateOf(Size.Zero)
    var selected by mutableStateOf<Int?>(null)
        private set
    var hits by mutableStateOf(emptyList<Int>())
        private set
    val viewport get() = exploration.viewport(bitmap.width, bitmap.height, size.width, size.height)
    val projection = Letterbox(bitmap.width, bitmap.height)
    val region get() = visibleImageCrop(
        ImageSpace(bitmap.width, bitmap.height, size.width, size.height),
        viewport
    )
    val visibleRegion get() = visibleImageRegion(
        ImageSpace(bitmap.width, bitmap.height, size.width, size.height),
        viewport
    )

    fun selectHits(indices: List<Int>) {
        val previous = selected
        hits = indices
        selected = toggleObject(selected, indices)
        if (previous != selected) onSelectionChanged()
    }

    fun select(index: Int) {
        if (index in hits) {
            selected = if (selected == index) null else index
            onSelectionChanged()
        }
    }

    fun move(centroid: Offset, pan: Offset, factor: Float) {
        val before = visibleRegion
        exploration = exploration.move(
            ImageSpace(bitmap.width, bitmap.height, size.width, size.height),
            ImagePoint(centroid.x, centroid.y),
            ImagePoint(pan.x, pan.y),
            factor
        )
        if (before != visibleRegion) onFrameChanged()
    }

    fun touch(point: Offset, overlay: PhotoOverlay) {
        val current = viewport
        if (overlay.busy || current.scale <= 0f) return
        val source = ImagePoint(
            (point.x - current.left) / current.scale,
            (point.y - current.top) / current.scale
        )
        overlay.tap(objectsAt(overlay.detections, source, projection))
    }
}

internal fun Modifier.photoGestures(
    interaction: PhotoInteraction,
    overlay: PhotoOverlay
): Modifier = onSizeChanged { interaction.size = Size(it.width.toFloat(), it.height.toFloat()) }
    .pointerInput(interaction) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            interaction.move(centroid, pan, zoom)
        }
    }
    .pointerInput(interaction, overlay.detections, overlay.busy, interaction.viewport) {
        detectTapGestures { interaction.touch(it, overlay) }
    }
