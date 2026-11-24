package dev.blamera.domain

import java.io.Serializable

data class SourceSelection(val source: ImageSource? = null) : Serializable {
    fun select(selectedSource: ImageSource): SourceSelection = copy(source = selectedSource)

    private companion object {
        // Preserve the UID of the first installed version when restoring saved state.
        private const val serialVersionUID = 1578816194603308700L
    }
}
