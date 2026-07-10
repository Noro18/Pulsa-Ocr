# OCR Integration Guide (Automatic Extraction)

> **Status:** Validated and ready for implementation.
> **Last reviewed:** 2026-07-10

This document outlines the steps to integrate ML Kit Text Recognition into the
`PulsaOcr2` app. The OCR runs **automatically** the moment the user presses the
capture button — no extra "Scan" button required.

---

## Validation Notes

> [!WARNING]
> The plan was reviewed against the current codebase and **3 issues** were found.
> All fixes are included in the implementation steps below.

| # | Issue | Severity | Fix |
|---|-------|----------|-----|
| 1 | **`_overlayRect` is `null` if the user never drags the box.** `OverlayBox` only calls `onRectChanged` inside `onDrag`, so if the user opens the camera and taps Capture without touching the box, `_overlayRect` is still `null` and `processImageWithOcr()` silently returns — no OCR runs at all. | 🔴 High | Emit an **initial `RectF`** from `OverlayBox` on first composition (see Step 3). |
| 2 | **ML Kit dependency is not in the actual build files yet.** `libs.versions.toml` and `app/build.gradle.kts` do not contain the `text-recognition` entry — only `AGENTS.md` and `docs/architecture.md` mention it. The code will fail to compile. | 🔴 High | Add the dependency before implementing (see Prerequisites). |
| 3 | **`ContentScale.Crop` in `ImagePreviewScreen` can make the blue overlay box appear misaligned** with the actual crop region on the bitmap. `Crop` scales and center-crops the image to fill the composable, but the `RectF` proportions are computed against the raw bitmap dimensions. For now this is cosmetic only (the OCR crops the raw bitmap correctly), but the visual rectangle may look slightly off on some aspect ratios. | 🟡 Medium | Consider switching to `ContentScale.Fit` or compensating the Canvas math. Can be addressed in a follow-up. |

---

## Prerequisites

### Add ML Kit Text Recognition to your build

**`gradle/libs.versions.toml`** — add under `[versions]` and `[libraries]`:
```toml
[versions]
mlkitTextRecognition = "16.0.1"

[libraries]
mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkitTextRecognition" }
```

**`app/build.gradle.kts`** — add in `dependencies`:
```kotlin
implementation(libs.mlkit.text.recognition)
```

Sync Gradle after adding.

---

## Implementation Steps

### Step 1 — ViewModel: Add OCR state and auto-trigger (`CameraPreviewViewModel.kt`)

Add these members and functions to the existing `CameraPreviewViewModel`:

```kotlin
// ── New imports ──
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

// ── New state ──
private val _recognizedText = MutableStateFlow<String?>(null)
val recognizedText: StateFlow<String?> = _recognizedText
```

**Replace** the existing `takePhoto` with:
```kotlin
fun takePhoto(context: Context) {
    cameraCaptureUseCase.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                _capturedImage.value = imageProxyToBitmap(image)
                image.close()

                // Automatically trigger OCR right after capture
                processImageWithOcr()
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraOCR", "Capture failed", exception)
            }
        }
    )
}
```

**Add** the private OCR function:
```kotlin
private fun processImageWithOcr() {
    val fullBitmap = _capturedImage.value ?: return
    val rectF = _overlayRect.value ?: return

    // Convert RectF proportions (0.0–1.0) → actual pixel coordinates on the bitmap
    val cropLeft   = (rectF.left   * fullBitmap.width).toInt().coerceAtLeast(0)
    val cropTop    = (rectF.top    * fullBitmap.height).toInt().coerceAtLeast(0)
    val cropWidth  = (rectF.width()  * fullBitmap.width).toInt()
        .coerceAtMost(fullBitmap.width - cropLeft)
    val cropHeight = (rectF.height() * fullBitmap.height).toInt()
        .coerceAtMost(fullBitmap.height - cropTop)

    if (cropWidth <= 0 || cropHeight <= 0) {
        _recognizedText.value = "Invalid crop area."
        return
    }

    val croppedBitmap = Bitmap.createBitmap(
        fullBitmap, cropLeft, cropTop, cropWidth, cropHeight
    )

    val image = InputImage.fromBitmap(croppedBitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            _recognizedText.value = visionText.text
        }
        .addOnFailureListener { e ->
            Log.e("CameraOCR", "OCR failed", e)
            _recognizedText.value = "Error: ${e.message}"
        }
}
```

**Update** `clearCapturedImage` to also clear the text:
```kotlin
fun clearCapturedImage() {
    _capturedImage.value = null
    _recognizedText.value = null
}
```

---

### Step 2 — UI: Pass `recognizedText` down (`CameraPreviewContent.kt`)

In `CameraPreviewContent`, collect the new state and pass it to `ImagePreviewScreen`:

```kotlin
val recognizedText by viewModel.recognizedText.collectAsStateWithLifecycle()

capturedImage?.let { bitmap ->
    ImagePreviewScreen(
        bitmap = bitmap,
        overlayRect = overlayRect,
        recognizedText = recognizedText,     // NEW
        onBack = { viewModel.clearCapturedImage() }
    )
}
```

---

### Step 3 — Fix: Emit initial `RectF` from `OverlayBox` (`OverlayBox.kt`)

The box starts at a default size (400×180 px) centered on the screen, but the
ViewModel's `_overlayRect` stays `null` until the user drags. Fix this by
emitting the initial rectangle on first composition.

Add a `LaunchedEffect` at the top of the `OverlayBox` composable, after the
`remember` declarations:

```kotlin
// Inside OverlayBox, after the variable declarations (widthPx, heightPx, etc.)
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
```

This ensures `_overlayRect` has a valid value even if the user never touches the box.

---

### Step 4 — UI: Display OCR result (`ImagePreviewScreen.kt`)

Update the signature and add a result panel at the bottom:

```kotlin
@Composable
fun ImagePreviewScreen(
    bitmap: Bitmap,
    overlayRect: RectF?,
    recognizedText: String?,     // NEW
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Captured image (full screen)
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Visual overlay rectangle (cosmetic confirmation)
        if (overlayRect != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val left   = overlayRect.left   * size.width
                val top    = overlayRect.top    * size.height
                val right  = overlayRect.right  * size.width
                val bottom = overlayRect.bottom * size.height
                drawRect(
                    color = Color(0xFF1976D2).copy(alpha = 0.3f),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        // OCR result panel
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (recognizedText == null) {
                // ML Kit is still processing
                CircularProgressIndicator()
            } else {
                Text(
                    text = recognizedText.ifEmpty { "No text found in this area." },
                    color = Color.Black,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}
```

---

## Data Flow Summary

```
User drags box ──► OverlayBox emits RectF (0.0–1.0 proportions)
                         │
                         ▼
              ViewModel._overlayRect stores it
                         │
User taps Capture ──► takePhoto() fires
                         │
                         ▼
              onCaptureSuccess() saves full Bitmap
                         │
                         ▼
              processImageWithOcr() runs immediately:
                1. Reads _overlayRect
                2. Multiplies proportions × bitmap dimensions → pixel coords
                3. Bitmap.createBitmap() crops the region
                4. InputImage.fromBitmap() → ML Kit recognizer.process()
                         │
                         ▼
              _recognizedText updated via addOnSuccessListener
                         │
                         ▼
              ImagePreviewScreen observes state → displays text
```

---

## Files Changed (Checklist)

| File | What to do |
|------|-----------|
| `gradle/libs.versions.toml` | Add `mlkitTextRecognition` version + `mlkit-text-recognition` library entry |
| `app/build.gradle.kts` | Add `implementation(libs.mlkit.text.recognition)` |
| `CameraPreviewViewModel.kt` | Add `_recognizedText` state, update `takePhoto()`, add `processImageWithOcr()`, update `clearCapturedImage()` |
| `CameraPreviewContent.kt` | Collect `recognizedText`, pass it to `ImagePreviewScreen` |
| `OverlayBox.kt` | Add `LaunchedEffect` to emit initial `RectF` |
| `ImagePreviewScreen.kt` | Add `recognizedText` parameter, add loading spinner + text result panel |
