package com.example.pulsaocr.ui.screens

import android.graphics.RectF
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
    var widthPx by remember { mutableStateOf(400f) }
    var heightPx by remember { mutableStateOf(180f) }

    var dragLeft by remember { mutableStateOf(false) }
    var dragRight by remember { mutableStateOf(false) }
    var dragTop by remember { mutableStateOf(false) }
    var dragBottom by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val edgePx = with(density) { 20.dp.toPx() }
    val minPx = with(density) { 60.dp.toPx() }

    // Emit the initial RectF so _overlayRect is never null, even if the user never drags
    LaunchedEffect(previewWidth, previewHeight) {
        if (previewWidth > 0 && previewHeight > 0) {
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
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                .drawBehind {
                    val len = 22.dp.toPx()
                    val bracketWidth = 5.dp.toPx()

                    drawCornerBracket(Corner.TOP_LEFT, Offset.Zero, len, bracketWidth)
                    drawCornerBracket(Corner.TOP_RIGHT, Offset(size.width, 0f), len, bracketWidth)
                    drawCornerBracket(Corner.BOTTOM_LEFT, Offset(0f, size.height), len, bracketWidth)
                    drawCornerBracket(Corner.BOTTOM_RIGHT, Offset(size.width, size.height), len, bracketWidth)
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
