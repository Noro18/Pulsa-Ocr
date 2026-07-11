package com.example.pulsaocr.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Isp(val label: String, val ussdFormat: String, val color: Color) {
    TELKOMCEL("Telkomcel", "*122*%s#", Color(0xFFE53935)),
    TELEMOR("Telemor", "*120*%s#", Color(0xFFFFA000)),
    TT("TT", "100%s", Color(0xFF1565C0))
}

@Composable
fun ImagePreviewScreen(
    bitmap: Bitmap,
    overlayRect: RectF?,
    ocrRawText: String?,
    ocrExtractedDigits: String?,
    onBack: () -> Unit
) {
    var selectedIsp by remember { mutableStateOf<Isp?>(null) }
    var showControls by remember { mutableStateOf(false) }
    val boxPulse = remember { Animatable(1f) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        boxPulse.animateTo(1.12f, tween(300))
        boxPulse.animateTo(1f, tween(300))
        showControls = true
    }

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

                // Pulse the rect from center
                val cx = (left + right) / 2f
                val cy = (top + bottom) / 2f
                val halfW = (right - left) / 2f * boxPulse.value
                val halfH = (bottom - top) / 2f * boxPulse.value
                val pl = cx - halfW
                val pt = cy - halfH
                val pr = cx + halfW
                val pb = cy + halfH

                val dim = Color.Black.copy(alpha = 0.45f)
                drawRect(dim, size = Size(size.width, pt))
                drawRect(dim, topLeft = Offset(0f, pb), size = Size(size.width, size.height - pb))
                drawRect(dim, topLeft = Offset(0f, pt), size = Size(pl, pb - pt))
                drawRect(dim, topLeft = Offset(pr, pt), size = Size(size.width - pr, pb - pt))

                val bracketLen = 22.dp.toPx()
                val bracketW = 5.dp.toPx()

                drawCornerBracket(Corner.TOP_LEFT, Offset(pl, pt), bracketLen, bracketW)
                drawCornerBracket(Corner.TOP_RIGHT, Offset(pr, pt), bracketLen, bracketW)
                drawCornerBracket(Corner.BOTTOM_LEFT, Offset(pl, pb), bracketLen, bracketW)
                drawCornerBracket(Corner.BOTTOM_RIGHT, Offset(pr, pb), bracketLen, bracketW)
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column {
                if (ocrExtractedDigits != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Select ISP:",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Isp.entries.forEach { isp ->
                            Button(
                                onClick = { selectedIsp = isp },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedIsp == isp) isp.color
                                        else isp.color.copy(alpha = 0.5f)
                                )
                            ) {
                            Text(
                                text = isp.label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                if (ocrExtractedDigits != null) {
                    val displayText = selectedIsp?.let { it.ussdFormat.format(ocrExtractedDigits) }
                        ?: ocrExtractedDigits
                    Text(
                        text = displayText,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                val currentIsp = selectedIsp
                if (currentIsp != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val dialCode = currentIsp.ussdFormat.format(ocrExtractedDigits)
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.fromParts("tel", dialCode, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text(
                            text = "Dial ${currentIsp.label}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    }
}
