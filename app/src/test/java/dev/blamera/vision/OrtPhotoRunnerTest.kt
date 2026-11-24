package dev.blamera.vision

import org.junit.Assert.assertThrows
import org.junit.Test

class OrtPhotoRunnerTest {
    @Test fun malformedInputFailsBeforeOpeningTheModelOrNativeSession() {
        val runner = OrtPhotoRunner { error("No file access is expected") }
        assertThrows(IllegalArgumentException::class.java) { runner.run(FloatArray(1)) }
        val nonFinite = FloatArray(3 * 640 * 640).apply { this[20] = Float.NaN }
        assertThrows(IllegalArgumentException::class.java) { runner.run(nonFinite) }
    }
}
