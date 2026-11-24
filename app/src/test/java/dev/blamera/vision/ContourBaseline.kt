package dev.blamera.vision

import java.io.File

/** Version0.3.0 geometry retained only as the comparison fixture, never app code. */
fun cellEdges(mask: MaskGrid, projection: Letterbox): List<ContourSegment> {
    val segments = mutableListOf<ContourSegment>()
    fun edge(x1: Int, y1: Int, x2: Int, y2: Int) {
        fun point(x: Int, y: Int) = projection.sourcePoint(
            x.toFloat() * 640 / mask.width,
            y.toFloat() * 640 / mask.height
        )
        val first = point(x1, y1)
        val last = point(x2, y2)
        if (first != last) segments.add(ContourSegment(first, last))
    }
    for (y in 0 until mask.height) {
        for (x in 0 until mask.width) {
            if (mask.contains(x, y)) {
                if (!mask.contains(x, y - 1)) edge(x, y, x + 1, y)
                if (!mask.contains(x + 1, y)) edge(x + 1, y, x + 1, y + 1)
                if (!mask.contains(x, y + 1)) edge(x + 1, y + 1, x, y + 1)
                if (!mask.contains(x - 1, y)) edge(x, y + 1, x, y)
            }
        }
    }
    return segments
}

fun saveContourComparison(name: String, before: List<ContourSegment>, after: List<ContourSegment>) {
    val directory =
        File(File(System.getProperty("blamera.fixture")).parentFile.parentFile, "m1-06-image")
    directory.mkdirs()
    fun json(segments: List<ContourSegment>) = segments.joinToString(prefix = "[", postfix = "]") {
        "[${it.start.x},${it.start.y},${it.end.x},${it.end.y}]"
    }
    File(directory, "$name-contours.json").writeText(
        "{\"before\":${json(before)},\"after\":${json(after)}}\n"
    )
    fun oblique(segments: List<ContourSegment>) = segments.count {
        it.start.x != it.end.x &&
            it.start.y != it.end.y
    }
    println(
        "$name segments before=${before.size}, after=${after.size}, oblique before=${oblique(
            before
        )}, after=${oblique(after)}"
    )
}
