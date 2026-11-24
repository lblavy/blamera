package dev.blamera.faces.runtime

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FaceQualificationTest {
    @Test fun successfulChecksDoNotPoisonLaterKernelVerification() {
        val qualification = FaceQualification()
        var calls = 0
        repeat(2) { qualification.verify { calls++ } }
        assertEquals(2, calls)
    }

    @Test fun deterministicParityFailureDisablesThisInstance() {
        val qualification = FaceQualification()
        var calls = 0
        repeat(2) {
            try {
                qualification.verify {
                    calls++
                    throw FaceParityFailure(FaceParityReport(3, 2.0, 0.0, 0.0, 1.0, 0.0, true))
                }
                fail("An incompatible runtime must stay disabled")
            } catch (_: RuntimeException) {
                // No output may escape a failed qualification.
            } catch (_: FaceEngineException) {
                // The remembered failure exposes only the public typed error.
            }
        }
        assertEquals(1, calls)
    }

    @Test fun cancellationAndTransientIoDoNotPermanentlyDisableTheRuntime() {
        val qualification = FaceQualification()
        try {
            qualification.verify { throw CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Leaving an image is not a parity failure.
        }
        try {
            qualification.verify { throw IOException() }
            fail("I/O failure must propagate")
        } catch (_: IOException) {
            // A cache copy can be retried.
        }
        var calls = 0
        qualification.verify { calls++ }
        assertEquals(1, calls)
    }
}
