package com.example.pulsaocr.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class CameraPreviewViewModel : ViewModel() {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private val _capturedImage = MutableStateFlow<Bitmap?>(null)
    val capturedImage: StateFlow<Bitmap?> = _capturedImage

    private val _overlayRect = MutableStateFlow<RectF?>(null)
    val overlayRect: StateFlow<RectF?> = _overlayRect

    private val _ocrRawText = MutableStateFlow<String?>(null)
    val ocrRawText: StateFlow<String?> = _ocrRawText

    private val _ocrExtractedDigits = MutableStateFlow<String?>(null)
    val ocrExtractedDigits: StateFlow<String?> = _ocrExtractedDigits

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn

    private var camera: Camera? = null

    private val cameraPreviewUseCase = Preview.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
        .build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequest.update { newSurfaceRequest }
        }
    }

    private val cameraCaptureUseCase = ImageCapture.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
        .setTargetRotation(android.view.Surface.ROTATION_0)
        .build()

    fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)
            camera = processCameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                cameraPreviewUseCase,
                cameraCaptureUseCase
            )
            try {
                awaitCancellation()
            } finally {
                processCameraProvider.unbindAll()
            }
        }
    }

    fun updateOverlayRect(rect: RectF) {
        _overlayRect.value = rect
    }

    fun takePhoto(context: Context, previewWidth: Int, previewHeight: Int) {
        cameraCaptureUseCase.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image) ?: return
                    image.close()

                    // Adjust overlay from screen-space to image-space coordinates
                    val currentRect = _overlayRect.value
                    if (currentRect != null) {
                        _overlayRect.value = adjustOverlayToImageSpace(
                            screenRect = currentRect,
                            previewWidth = previewWidth,
                            previewHeight = previewHeight,
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height
                        )
                    }

                    _capturedImage.value = bitmap

                    // Automatically trigger OCR right after capture
                    processImageWithOcr()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraOCR", "Capture failed", exception)
                }
            }
        )
    }

    fun clearCapturedImage() {
        _capturedImage.value = null
        _ocrRawText.value = null
        _ocrExtractedDigits.value = null
    }

    fun toggleFlash() {
        val newState = !_isFlashOn.value
        _isFlashOn.value = newState
        camera?.cameraControl?.enableTorch(newState)
    }

    private fun processImageWithOcr() {
        val fullBitmap = _capturedImage.value ?: return
        val rectF = _overlayRect.value ?: return

        // Convert RectF proportions (0.0–1.0) → actual pixel coordinates on the bitmap
        val cropLeft = (rectF.left * fullBitmap.width).toInt().coerceAtLeast(0)
        val cropTop = (rectF.top * fullBitmap.height).toInt().coerceAtLeast(0)
        val cropWidth = (rectF.width() * fullBitmap.width).toInt()
            .coerceAtMost(fullBitmap.width - cropLeft)
        val cropHeight = (rectF.height() * fullBitmap.height).toInt()
            .coerceAtMost(fullBitmap.height - cropTop)

        if (cropWidth <= 0 || cropHeight <= 0) {
            Log.e(TAG, "Invalid crop area: width=$cropWidth, height=$cropHeight")
            return
        }

        val croppedBitmap = Bitmap.createBitmap(
            fullBitmap, cropLeft, cropTop, cropWidth, cropHeight
        )

        val image = InputImage.fromBitmap(croppedBitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                Log.d(TAG, "OCR Result: $rawText")
                _ocrRawText.value = rawText

                val voucherCode = Regex("""\d{8,}""")
                    .findAll(rawText.replace("\\s".toRegex(), ""))
                    .maxByOrNull { it.value.length }
                    ?.value
                _ocrExtractedDigits.value = voucherCode
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR failed", e)
            }
    }

    private fun adjustOverlayToImageSpace(
        screenRect: RectF,
        previewWidth: Int,
        previewHeight: Int,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): RectF {
        val imageAspect = bitmapWidth.toFloat() / bitmapHeight
        val screenAspect = previewWidth.toFloat() / previewHeight

        return if (imageAspect > screenAspect) {
            val visibleW = screenAspect / imageAspect
            val xOff = (1f - visibleW) / 2f
            RectF(
                xOff + screenRect.left * visibleW,
                screenRect.top,
                xOff + screenRect.right * visibleW,
                screenRect.bottom
            )
        } else {
            val visibleH = imageAspect / screenAspect
            val yOff = (1f - visibleH) / 2f
            RectF(
                screenRect.left,
                yOff + screenRect.top * visibleH,
                screenRect.right,
                yOff + screenRect.bottom * visibleH
            )
        }
    }

    companion object {
        private const val TAG = "CameraOCR"
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        if (image.format == ImageFormat.JPEG) {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val nv21 = ByteArray(yBuffer.remaining() + uBuffer.remaining() + vBuffer.remaining())
        yBuffer.get(nv21, 0, yBuffer.remaining())
        vBuffer.get(nv21, yBuffer.remaining(), vBuffer.remaining())
        uBuffer.get(nv21, yBuffer.remaining() + vBuffer.remaining(), uBuffer.remaining())

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }
}
