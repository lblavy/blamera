package dev.blamera.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceSelectionTest {
    @Test
    fun `no source is selected before the user chooses`() {
        assertNull(SourceSelection().source)
    }

    @Test
    fun `the camera can be selected`() {
        val selected = SourceSelection().select(ImageSource.CAMERA)

        assertEquals(ImageSource.CAMERA, selected.source)
    }

    @Test
    fun `choosing an image replaces the previous camera selection`() {
        val camera = SourceSelection().select(ImageSource.CAMERA)

        val image = camera.select(ImageSource.IMAGE)

        assertEquals(ImageSource.IMAGE, image.source)
        assertEquals(ImageSource.CAMERA, camera.source)
    }

    @Test
    fun `the camera can replace an image selection`() {
        val image = SourceSelection().select(ImageSource.IMAGE)

        assertEquals(ImageSource.CAMERA, image.select(ImageSource.CAMERA).source)
    }

    @Test
    fun `selecting the same source preserves the selection`() {
        val image = SourceSelection(ImageSource.IMAGE)

        assertEquals(image, image.select(ImageSource.IMAGE))
    }
}
