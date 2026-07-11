package com.example.pulsaocr.ui.screens

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ImagePreviewScreen(
    bitmap: Bitmap,
    overlayRect: RectF?,
    ocrRawText: String?,
    ocrExtractedDigits: String?,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (overlayRect != null) {
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()

            Canvas(modifier = Modifier.fillMaxSize()) {
                val scale = maxOf(size.width / bitmapWidth, size.height / bitmapHeight)
                val displayedWidth = bitmapWidth * scale
                val displayedHeight = bitmapHeight * scale
                val offsetX = (size.width - displayedWidth) / 2f
                val offsetY = (size.height - displayedHeight) / 2f

                val left = offsetX + overlayRect.left * displayedWidth
                val top = offsetY + overlayRect.top * displayedHeight
                val right = offsetX + overlayRect.right * displayedWidth
                val bottom = offsetY + overlayRect.bottom * displayedHeight

                drawRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (ocrExtractedDigits != null) {
                Text(
                    text = ocrExtractedDigits,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (ocrRawText != null) {
                Text(
                    text = "Raw: $ocrRawText",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
