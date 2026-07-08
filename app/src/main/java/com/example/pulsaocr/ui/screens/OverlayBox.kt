package com.example.pulsaocr.ui.screens

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun OverlayBox(
    previewWidth: Int,
    previewHeight: Int,
    onRectChanged: (RectF) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableStateOf(280f) }
    var heightPx by remember { mutableStateOf(140f) }

    var dragLeft by remember { mutableStateOf(false) }
    var dragRight by remember { mutableStateOf(false) }
    var dragTop by remember { mutableStateOf(false) }
    var dragBottom by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val edgePx = with(density) { 20.dp.toPx() }
    val minPx = with(density) { 60.dp.toPx() }
    val handleSize = with(density) { 10.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                .clip(RoundedCornerShape(4.dp))
                .border(2.dp, Color(0xFF1976D2), RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .drawBehind {
                    val stroke = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                        )
                    )
                    drawRect(
                        color = Color(0xFF1976D2).copy(alpha = 0.4f),
                        style = stroke
                    )

                    val c = Color(0xFF1976D2)
                    val r = 2.dp.toPx()

                    drawRoundRect(c, Offset.Zero, Size(handleSize, handleSize), CornerRadius(r))
                    drawRoundRect(c, Offset(size.width - handleSize, 0f), Size(handleSize, handleSize), CornerRadius(r))
                    drawRoundRect(c, Offset(0f, size.height - handleSize), Size(handleSize, handleSize), CornerRadius(r))
                    drawRoundRect(c, Offset(size.width - handleSize, size.height - handleSize), Size(handleSize, handleSize), CornerRadius(r))

                    val midSize = Size(handleSize * 0.6f, handleSize * 0.6f)
                    val mr = 1.dp.toPx()
                    drawRoundRect(c, Offset(size.width / 2f - midSize.width / 2f, 0f), midSize, CornerRadius(mr))
                    drawRoundRect(c, Offset(size.width / 2f - midSize.width / 2f, size.height - midSize.height), midSize, CornerRadius(mr))
                    drawRoundRect(c, Offset(0f, size.height / 2f - midSize.height / 2f), midSize, CornerRadius(mr))
                    drawRoundRect(c, Offset(size.width - midSize.width, size.height / 2f - midSize.height / 2f), midSize, CornerRadius(mr))
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            dragLeft = pos.x < edgePx
                            dragRight = pos.x > widthPx - edgePx
                            dragTop = pos.y < edgePx
                            dragBottom = pos.y > heightPx - edgePx
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x
                            val dy = dragAmount.y

                            if (dragLeft) {
                                widthPx = (widthPx - 2 * dx).coerceAtLeast(minPx)
                            } else if (dragRight) {
                                widthPx = (widthPx + 2 * dx).coerceAtLeast(minPx)
                            }

                            if (dragTop) {
                                heightPx = (heightPx - 2 * dy).coerceAtLeast(minPx)
                            } else if (dragBottom) {
                                heightPx = (heightPx + 2 * dy).coerceAtLeast(minPx)
                            }

                            val left = (previewWidth - widthPx) / 2f
                            val top = (previewHeight - heightPx) / 2f
                            onRectChanged(
                                RectF(
                                    left / previewWidth,
                                    top / previewHeight,
                                    (left + widthPx) / previewWidth,
                                    (top + heightPx) / previewHeight
                                )
                            )
                        },
                        onDragEnd = {
                            dragLeft = false; dragRight = false
                            dragTop = false; dragBottom = false
                        },
                        onDragCancel = {
                            dragLeft = false; dragRight = false
                            dragTop = false; dragBottom = false
                        }
                    )
                }
        )
    }
}
