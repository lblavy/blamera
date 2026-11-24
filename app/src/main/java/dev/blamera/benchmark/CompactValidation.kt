package dev.blamera.benchmark

internal fun validateCompact(
    rows: FloatArray,
    prototypes: FloatArray,
    rowShape: LongArray,
    prototypeShape: LongArray,
    classes: Int
): Int {
    require(rowShape.contentEquals(longArrayOf(1, MAX_ROWS.toLong(), ROW_WIDTH.toLong())))
    require(
        prototypeShape.contentEquals(
            longArrayOf(1, COEFFICIENTS.toLong(), PROTOTYPE_EDGE.toLong(), PROTOTYPE_EDGE.toLong())
        )
    )
    require(
        rows.size == MAX_ROWS * ROW_WIDTH &&
            prototypes.size == COEFFICIENTS * PROTOTYPE_EDGE * PROTOTYPE_EDGE
    )
    require(rows.all { it.isFinite() } && prototypes.all { it.isFinite() })
    return (0 until MAX_ROWS).count { validateRow(rows, it * ROW_WIDTH, classes) }
}

private fun validateRow(rows: FloatArray, start: Int, classes: Int): Boolean {
    val score = rows[start + SCORE]
    if (score == 0f) {
        require((start until start + ROW_WIDTH).all { rows[it] == 0f })
    } else {
        require(score in MIN_SCORE..1f)
        val category = rows[start + CATEGORY]
        require(category >= 0 && category < classes && category == category.toInt().toFloat())
        require(rows[start + RIGHT] > rows[start] && rows[start + BOTTOM] > rows[start + TOP])
    }
    return score != 0f
}

private const val MAX_ROWS = 20
private const val ROW_WIDTH = 38
private const val COEFFICIENTS = 32
private const val PROTOTYPE_EDGE = 160
private const val SCORE = 4
private const val CATEGORY = 5
private const val TOP = 1
private const val RIGHT = 2
private const val BOTTOM = 3
private const val MIN_SCORE = 0.35f
