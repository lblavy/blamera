package dev.blamera.image

import kotlinx.coroutines.sync.Mutex

// Cancelled synchronous bitmap work retains its memory slot until it actually returns.
internal object ImageMemory {
    val mutex = Mutex()
}
