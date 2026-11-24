package dev.blamera.vision

fun traceIsocontour(
    grid: LogitGrid,
    projection: Letterbox,
    crop: ImageBox = ImageBox(0f, 0f, Letterbox.EDGE.toFloat(), Letterbox.EDGE.toFloat()),
    maxSegments: Int = 8000
): List<ContourSegment> {
    val domain = ImageBox(
        maxOf(crop.left, projection.padLeft.toFloat()),
        maxOf(crop.top, projection.padTop.toFloat()),
        minOf(crop.right, (projection.padLeft + projection.scaledWidth).toFloat()),
        minOf(crop.bottom, (projection.padTop + projection.scaledHeight).toFloat())
    )
    return if (domain.area >
        0f
    ) {
        IsocontourTracer(grid, projection, domain, maxSegments).trace()
    } else {
        emptyList()
    }
}

private class IsocontourTracer(
    grid: LogitGrid,
    private val projection: Letterbox,
    domain: ImageBox,
    private val maxSegments: Int
) {
    private val xs = contourAxis(domain.left, domain.right, grid.width)
    private val ys = contourAxis(domain.top, domain.bottom, grid.height)
    private val samples = FloatArray(xs.size * ys.size) { -1f }
    private val segments = mutableListOf<ContourSegment>()

    init {
        // Duplicate boundary coordinates with a negative exterior ring: exact crop closure,
        // including image edges, without inventing a closing segment across an interior hole.
        for (y in 1 until ys.lastIndex) {
            for (x in 1 until xs.lastIndex) samples[y * xs.size + x] = grid.sample(xs[x], ys[y])
        }
    }

    fun trace(): List<ContourSegment> {
        for (y in 0 until ys.lastIndex) {
            for (x in 0 until xs.lastIndex) cell(x, y)
        }
        return segments
    }

    private fun cell(x: Int, y: Int) {
        var code = 0
        for (corner in CORNERS.indices) {
            val offset = CORNERS[corner]
            if (sample(x + offset.first, y + offset.second) > 0f) code = code or (1 shl corner)
        }
        val edges = CASE_EDGES[code]
        for (index in edges.indices step 2) {
            add(
                edgePoint(x, y, edges[index]),
                edgePoint(
                    x,
                    y,
                    edges[
                        index +
                            1
                    ]
                )
            )
        }
    }

    private fun edgePoint(x: Int, y: Int, edge: Int): ImagePoint {
        val ends = EDGE_CORNERS[edge]
        val first = CORNERS[ends.first]
        val last = CORNERS[ends.second]
        val x1 = x + first.first
        val y1 = y + first.second
        val x2 = x + last.first
        val y2 = y + last.second
        val value = sample(x1, y1)
        val weight = value / (value - sample(x2, y2))
        return projection.sourcePoint(
            xs[x1] + (xs[x2] - xs[x1]) * weight,
            ys[y1] + (ys[y2] - ys[y1]) * weight
        )
    }

    private fun add(first: ImagePoint, last: ImagePoint) {
        if (first == last) return
        if (segments.size >= maxSegments) throw VisionException("Mask contour too complex")
        segments.add(ContourSegment(first, last))
    }

    private fun sample(x: Int, y: Int) = samples[y * xs.size + x]
}

private fun contourAxis(start: Float, end: Float, samples: Int): List<Float> {
    val axis = mutableListOf(start, start)
    for (index in 0 until samples) {
        val center = (index + HALF_PIXEL) * Letterbox.EDGE / samples
        if (center > start && center < end) axis.add(center)
    }
    axis.add(end)
    axis.add(end)
    return axis
}

private val CORNERS = arrayOf(0 to 0, 1 to 0, 1 to 1, 0 to 1)
private val EDGE_CORNERS = arrayOf(0 to 1, 1 to 2, BOTTOM_LEFT to 2, 0 to BOTTOM_LEFT)

// Strictly positive foreground is 4-connected; ambiguous diagonal positives stay separate.
private const val HALF_PIXEL = 0.5f
private const val BOTTOM_LEFT = 3
private const val TOP = 0
private const val RIGHT = 1
private const val BOTTOM = 2
private const val LEFT = 3
private val CASE_EDGES = arrayOf(
    intArrayOf(), intArrayOf(LEFT, TOP), intArrayOf(TOP, RIGHT), intArrayOf(LEFT, RIGHT),
    intArrayOf(
        RIGHT,
        BOTTOM
    ),
    intArrayOf(LEFT, TOP, RIGHT, BOTTOM), intArrayOf(TOP, BOTTOM), intArrayOf(LEFT, BOTTOM),
    intArrayOf(
        LEFT,
        BOTTOM
    ),
    intArrayOf(TOP, BOTTOM), intArrayOf(TOP, RIGHT, BOTTOM, LEFT), intArrayOf(RIGHT, BOTTOM),
    intArrayOf(LEFT, RIGHT), intArrayOf(TOP, RIGHT), intArrayOf(LEFT, TOP), intArrayOf()
)
