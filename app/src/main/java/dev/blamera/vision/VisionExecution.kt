package dev.blamera.vision

import kotlinx.coroutines.sync.Mutex

// A cancelled native call keeps ownership until it returns, across Activities and diagnostics.
internal object VisionExecution {
    val mutex = Mutex()
}
