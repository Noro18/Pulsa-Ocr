package com.example.pulsaocr.ui.screens

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

private val ScrimColor = Color(0x80000000)
private val BracketColor = Color(0xFF00BCD4)

@Composable
fun OverlayBox(
    modifier: Modifier = Modifier,
    rect: RectF
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val w = size.width
        val h = size.height
        val l = rect.left * w
        val t = rect.top * h
        val r = rect.right * w
        val b = rect.bottom * h
        val gap = 40f

        drawRect(color = ScrimColor, topLeft = Offset.Zero, size = Size(w, t))
        drawRect(color = ScrimColor, topLeft = Offset(0f, b), size = Size(w, h - b))
        drawRect(color = ScrimColor, topLeft = Offset(0f, t), size = Size(l, b - t))
        drawRect(color = ScrimColor, topLeft = Offset(r, t), size = Size(w - r, b - t))

        drawRect(color = BracketColor, topLeft = Offset(l, t), size = Size(gap, 3f))
        drawRect(color = BracketColor, topLeft = Offset(l, t), size = Size(3f, gap))

        drawRect(color = BracketColor, topLeft = Offset(r - gap, t), size = Size(gap, 3f))
        drawRect(color = BracketColor, topLeft = Offset(r - 3f, 2t), size = Size(3f, gap))

        drawRect(color = BracketColor, topLeft = Offset(l, b - 3f), size = Size(gap, 3f))
        drawRect(color = BracketColor, topLeft = Offset(l, b - gap), size = Size(3f, gap))

        drawRect(color = BracketColor, topLeft = Offset(r - gap, b - 3f), size = Size(gap, 3f))
        drawRect(color = BracketColor, topLeft = Offset(r - 3f, b - gap), size = Size(3f, gap))
    }
}
