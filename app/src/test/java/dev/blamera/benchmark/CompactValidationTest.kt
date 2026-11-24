package dev.blamera.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CompactValidationTest {
    private val shape = longArrayOf(1, 20, 38)
    private val protoShape = longArrayOf(1, 32, 160, 160)
    private fun rows() = FloatArray(20 * 38).apply {
        this[0] = 1f
        this[1] = 2f
        this[2] = 30f
        this[3] = 40f
        this[4] =
            .5f
        this[5] = 79f
    }

    @Test fun activeRowsAndZeroPaddingAreCountedForBothClassVocabularies() {
        val rows = rows()
        val proto = FloatArray(32 * 160 * 160)
        assertEquals(1, validateCompact(rows, proto, shape, protoShape, 80))
        rows[5] = 4584f
        assertEquals(1, validateCompact(rows, proto, shape, protoShape, 4585))
        assertEquals(0, validateCompact(FloatArray(760), proto, shape, protoShape, 80))
    }

    @Test fun rejectsShapeNonFiniteInvalidPaddingScoresBoxesAndClassIds() {
        val proto = FloatArray(32 * 160 * 160)
        assertThrows(IllegalArgumentException::class.java) {
            validateCompact(rows(), proto, longArrayOf(1, 20, 37), protoShape, 80)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateCompact(rows(), proto, shape, longArrayOf(1, 32, 80, 80), 80)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateCompact(FloatArray(1), proto, shape, protoShape, 80)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateCompact(rows(), FloatArray(1), shape, protoShape, 80)
        }
        for (change in listOf(
            5 to -1f,
            5 to 80f,
            5 to .5f,
            4 to .2f,
            4 to 2f,
            0 to Float.NaN,
            2 to -2f,
            38 to 3f
        )) {
            val bad = rows()
            bad[change.first] = change.second
            assertThrows(IllegalArgumentException::class.java) {
                validateCompact(bad, proto, shape, protoShape, 80)
            }
        }
        proto[0] = Float.POSITIVE_INFINITY
        assertThrows(IllegalArgumentException::class.java) {
            validateCompact(rows(), proto, shape, protoShape, 80)
        }
    }
}
