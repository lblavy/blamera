package dev.blamera.vision

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IsocontourTest {
    @Test
    fun zeroCrossingUsesLogitMagnitudeBetweenSampleCenters() {
        // Centers x=160 and x=480, crossing -1 -> +3 is at x=240, not a cell edge.
        val segments =
            traceIsocontour(LogitGrid(2, 2, floatArrayOf(-1f, 3f, -1f, 3f)), Letterbox(640, 640))
        assertTrue(
            segments.any {
                close(it.start.x, 240f) && close(it.end.x, 240f) &&
                    abs(it.start.y - it.end.y) > 100f
            }
        )
    }

    @Test
    fun diagonalFieldHasObliqueSegmentsInsteadOfStairSteps() {
        val grid = LogitGrid(
            4,
            4,
            FloatArray(16) { index ->
                (index % 4 + index / 4).toFloat() -
                    2.3f
            }
        )
        val segments = traceIsocontour(grid, Letterbox(640, 640))
        assertTrue(
            segments.any {
                abs(it.start.x - it.end.x) > 1f && abs(it.start.y - it.end.y) > 1f
            }
        )
        assertTrue(
            segments.filter { it.start.x in 100f..500f && it.start.y in 100f..500f }.all {
                close(it.start.x + it.start.y, 528f)
            }
        )
    }

    @Test
    fun holesAndSeparateComponentsRemainClosed() {
        val values = FloatArray(81) { -1f }
        for (y in 3..5) for (x in 3..5) values[y * 9 + x] = 1f
        values[4 * 9 + 4] = -1f
        values[1 * 9 + 1] = 1f
        assertEquals(3, closedLoops(traceIsocontour(LogitGrid(9, 9, values), Letterbox(640, 640))))
    }

    @Test
    fun bothAmbiguousDiagonalCasesKeepPositiveIslandsSeparate() {
        listOf(floatArrayOf(1f, -1f, -1f, 1f), floatArrayOf(-1f, 1f, 1f, -1f)).forEach {
            assertEquals(2, closedLoops(traceIsocontour(LogitGrid(2, 2, it), Letterbox(640, 640))))
        }
    }

    @Test
    fun cropAndImageEdgesCloseExactlyInNonSquareSourceCoordinates() {
        val grid = LogitGrid(4, 4, FloatArray(16) { 1f })
        val contour = traceIsocontour(grid, Letterbox(800, 400), ImageBox(117f, 199f, 501f, 421f))
        val points = contour.flatMap { listOf(it.start, it.end) }
        assertEquals(146.25f, points.minOf { it.x }, 0.001f)
        assertEquals(626.25f, points.maxOf { it.x }, 0.001f)
        assertEquals(48.75f, points.minOf { it.y }, 0.001f)
        assertEquals(326.25f, points.maxOf { it.y }, 0.001f)
        assertEquals(1, closedLoops(contour))
        val full = traceIsocontour(grid, Letterbox(800, 400))
        assertTrue(full.any { it.start == ImagePoint(0f, 0f) || it.end == ImagePoint(0f, 0f) })
        assertTrue(
            full.any {
                it.start == ImagePoint(800f, 400f) || it.end == ImagePoint(800f, 400f)
            }
        )
        assertTrue(
            traceIsocontour(grid, Letterbox(640, 640), ImageBox(-20f, -20f, -1f, -1f)).isEmpty()
        )
    }

    @Test
    fun subpixelDiskIsCloserToItsAnalyticBoundaryThanOldCellEdges() {
        val grid = LogitGrid(
            32,
            32,
            FloatArray(32 * 32) { index ->
                val dx = (index % 32 + 0.5f) * 20 - 313f
                val dy = (index / 32 + 0.5f) * 20 - 297f
                185f * 185f - dx * dx - dy * dy
            }
        )
        val contour = traceIsocontour(grid, Letterbox(640, 640))
        val old =
            cellEdges(
                MaskGrid(
                    32,
                    32,
                    BooleanArray(grid.values.size) {
                        grid.values[it] > 0
                    }
                ),
                Letterbox(640, 640)
            )
        fun radialError(segment: ContourSegment): Float {
            val x = (segment.start.x + segment.end.x) / 2 - 313f
            val y = (segment.start.y + segment.end.y) / 2 - 297f
            return abs(hypot(x, y) - 185f)
        }
        assertTrue(contour.map { radialError(it) }.max() < 1f)
        assertTrue(old.map(::radialError).average() > 3)
        assertEquals(1, closedLoops(contour))
        saveContourComparison("disk", old, contour)
        println(
            "Synthetic disk radial error: oldMean=${old.map(
                ::radialError
            ).average()}, newMean=${contour.map(
                ::radialError
            ).average()}, newMax=${contour.map { radialError(it) }.max()}"
        )
    }

    @Test
    fun emptyFieldsAndLimitsFailSafely() {
        assertTrue(
            traceIsocontour(LogitGrid(1, 1, floatArrayOf(-1f)), Letterbox(640, 640)).isEmpty()
        )
        assertThrows(VisionException::class.java) {
            traceIsocontour(LogitGrid(1, 1, floatArrayOf(1f)), Letterbox(640, 640), maxSegments = 0)
        }
        assertThrows(VisionException::class.java) { LogitGrid(1, 1, floatArrayOf(Float.NaN)) }
        assertThrows(IllegalArgumentException::class.java) { LogitGrid(0, 1, floatArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { LogitGrid(161, 1, FloatArray(161)) }
        assertThrows(IllegalArgumentException::class.java) { LogitGrid(1, 1, floatArrayOf()) }
    }

    private fun closedLoops(segments: List<ContourSegment>): Int {
        val neighbors = mutableMapOf<ImagePoint, MutableList<ImagePoint>>()
        for (segment in segments) {
            neighbors.getOrPut(segment.start) { mutableListOf() }.add(segment.end)
            neighbors.getOrPut(segment.end) { mutableListOf() }.add(segment.start)
        }
        assertTrue("Every vertex must have degree2", neighbors.values.all { it.size == 2 })
        val unseen = neighbors.keys.toMutableSet()
        var components = 0
        while (unseen.isNotEmpty()) {
            components++
            val pending = ArrayDeque<ImagePoint>()
            pending.add(unseen.first())
            while (pending.isNotEmpty()) {
                val point = pending.removeFirst()
                if (unseen.remove(point)) pending.addAll(neighbors.getValue(point))
            }
        }
        return components
    }

    private fun close(left: Float, right: Float) = abs(left - right) < 0.01f
}
