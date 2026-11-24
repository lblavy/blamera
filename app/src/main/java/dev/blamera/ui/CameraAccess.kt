package dev.blamera.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class CameraDenial { NONE, DENIED, BLOCKED, REVOKED }

class CameraAccess(initialGranted: Boolean, initialActivation: Boolean = false) {
    var granted by mutableStateOf(initialGranted)
        private set
    var activated by mutableStateOf(initialActivation && initialGranted)
        private set
    var pending by mutableStateOf(false)
        private set
    var denial by mutableStateOf(
        if (initialActivation &&
            !initialGranted
        ) {
            CameraDenial.REVOKED
        } else {
            CameraDenial.NONE
        }
    )
        private set
    private var alive = true

    fun refresh(permissionGranted: Boolean) {
        granted = permissionGranted
        if (activated && !permissionGranted) {
            activated = false
            denial = CameraDenial.REVOKED
        }
    }

    fun activateOrRequest(): Boolean {
        denial = CameraDenial.NONE
        if (granted) {
            activated = true
            return false
        }
        pending = true
        return true
    }

    fun acceptPermission(permissionGranted: Boolean, showRationale: Boolean) {
        if (!alive || !pending) return
        pending = false
        granted = permissionGranted
        activated = permissionGranted
        denial = when {
            permissionGranted -> CameraDenial.NONE
            showRationale -> CameraDenial.DENIED
            else -> CameraDenial.BLOCKED
        }
    }

    fun dispose() {
        alive = false
        pending = false
    }
}
