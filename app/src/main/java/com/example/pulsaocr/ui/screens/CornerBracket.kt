package com.example.pulsaocr.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope

enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

fun DrawScope.drawCornerBracket(
    corner: Corner,
    offset: Offset,
    length: Float,
    strokeWidth: Float,
    color: Color = Color.White,
    cap: StrokeCap = StrokeCap.Round
) {
    val (dx, dy) = when (corner) {
        Corner.TOP_LEFT -> 1f to 1f
        Corner.TOP_RIGHT -> -1f to 1f
        Corner.BOTTOM_LEFT -> 1f to -1f
        Corner.BOTTOM_RIGHT -> -1f to -1f
    }
    val r = strokeWidth / 2f

    drawLine(color, offset, Offset(offset.x + dx * length, offset.y), strokeWidth, cap)
    drawLine(color, offset, Offset(offset.x, offset.y + dy * length), strokeWidth, cap)
    drawCircle(color, r, offset)
}
