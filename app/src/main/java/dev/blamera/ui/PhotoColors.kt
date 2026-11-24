package dev.blamera.ui

import androidx.compose.ui.graphics.Color

internal fun categoryColor(classId: Int): Color =
    PHOTO_COLORS[Math.floorMod(classId, PHOTO_COLORS.size)]

private const val PHOTO_YELLOW = 0xFFFFD54F
private const val PHOTO_CYAN = 0xFF4DD0E1
private const val PHOTO_CORAL = 0xFFFF8A80
private const val PHOTO_GREEN = 0xFF81C784
private const val PHOTO_PURPLE = 0xFFB39DDB
private const val PHOTO_ORANGE = 0xFFFFB74D
private const val PHOTO_TEAL = 0xFF80CBC4
private const val PHOTO_PINK = 0xFFF48FB1

private val PHOTO_COLORS = listOf(
    Color(PHOTO_YELLOW),
    Color(PHOTO_CYAN),
    Color(PHOTO_CORAL),
    Color(PHOTO_GREEN),
    Color(PHOTO_PURPLE),
    Color(PHOTO_ORANGE),
    Color(PHOTO_TEAL),
    Color(PHOTO_PINK)
)
