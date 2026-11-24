package dev.blamera.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.blamera.vision.ImageBox
import dev.blamera.vision.MaskGrid
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoInteractionTest {
    @Test fun letterboxBandsAndExclusiveEdgesDoNotSelectAnObject() {
        val interaction = PhotoInteraction(capturePhoto(400, 200).bitmap)
        interaction.size = Size(400f, 500f)
        val detection = captureDetection().copy(
            box = ImageBox(0f, 0f, 400f, 200f),
            mask = MaskGrid(160, 160, BooleanArray(160 * 160) { true })
        )
        val hits = mutableListOf<List<Int>>()
        val overlay = PhotoOverlay(listOf(detection), null, false, hits::add)
        // The source occupies x=[0,400), y=[150,350) inside this taller view.
        listOf(Offset(0f, 150f), Offset(399.9f, 349.9f), Offset(200f, 250f)).forEach {
            interaction.touch(it, overlay)
            assertEquals(listOf(0), hits.last())
        }
        listOf(
            Offset(200f, 149f),
            Offset(200f, 350f),
            Offset(400f, 250f),
            Offset(-1f, 250f),
            Offset(200f, 499f)
        ).forEach {
            interaction.touch(it, overlay)
            assertEquals(emptyList<Int>(), hits.last())
        }
    }

    @Test fun tapsDuringAnalysisOrBeforeLayoutCannotChangeTheSelection() {
        val interaction = PhotoInteraction(capturePhoto().bitmap)
        var taps = 0
        val overlay = PhotoOverlay(listOf(captureDetection()), null, false) { taps++ }
        interaction.touch(Offset.Zero, overlay)
        interaction.size = Size(640f, 480f)
        interaction.touch(Offset(320f, 240f), overlay.copy(busy = true))
        assertEquals(0, taps)
        interaction.touch(Offset(320f, 240f), overlay)
        assertEquals(1, taps)
    }
}
