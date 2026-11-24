package dev.blamera.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFrameGateTest {
    @Test fun exactCadenceBoundaryAndSlowInferenceHaveNoQueue() {
        var now = 0L
        val gate = LiveFrameGate { now }
        assertNotNull(gate.acquire())
        gate.release()
        now = 999
        assertNull(gate.acquire())
        now = 1000
        assertNotNull(gate.acquire())
        now = 5000
        assertNull(gate.acquire())
        gate.release()
        assertNotNull(gate.acquire())
    }

    @Test fun onlyOneFrameAndOneStartPerSecondEvenAcrossInvalidation() {
        var now = 0L
        val gate = LiveFrameGate { now }
        val token = checkNotNull(gate.acquire())
        assertNull(gate.acquire())
        gate.invalidate()
        assertFalse(gate.current(token))
        now = 2000
        assertNull(gate.acquire())
        gate.release()
        val next = checkNotNull(gate.acquire())
        assertTrue(gate.current(next))
        gate.release()
        now = 2999
        assertNull(gate.acquire())
        now = 3000
        assertNotNull(gate.acquire())
    }
}
