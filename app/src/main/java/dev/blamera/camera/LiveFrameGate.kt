package dev.blamera.camera

/** Process-wide admission survives Activity recreation and non-interruptible native calls. */
internal class LiveFrameGate(private val clock: () -> Long) {
    private var busy = false
    private var lastStart: Long? = null
    private var generation = 0

    @Synchronized fun acquire(): Int? {
        val now = clock()
        if (busy || lastStart?.let { now - it < MIN_INTERVAL_MILLIS } == true) return null
        busy = true
        lastStart = now
        return generation
    }

    @Synchronized fun release() {
        busy = false
    }

    @Synchronized fun invalidate() {
        generation++
    }

    @Synchronized fun current(token: Int): Boolean = token == generation
    private companion object {
        const val MIN_INTERVAL_MILLIS = 1000L
    }
}
