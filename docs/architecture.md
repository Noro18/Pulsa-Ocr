# OCR Overlay Architecture

## Status

| Feature | Status |
|---------|--------|
| OverlayBox (centered, resizable) | ✅ Implemented |
| Photo capture with overlay preservation | ✅ Implemented |
| ML Kit OCR pipeline | 🔜 Planned — doc below |

## Feature Overview
Capture a photo from the camera preview, select a region of interest (ROI) via a draggable/resizable overlay box, then run ML Kit OCR on the cropped area to extract numbers.

---

## Flow Diagram

```
CameraPreviewContent
│
├─ CameraXViewfinder (live feed, fullscreen)
├─ OverlayBox composable
│   └─ Draggable/resizable Rect drawn on top of preview
├─ Button("Capture")
│   └─ → takePhoto() → ImageProxy → Bitmap (full frame)
│
ImagePreviewScreen
│
├─ Show full captured Bitmap
├─ OverlayBox (same position, visible)
├─ Button("Extract Numbers")
│   └─ → crop Bitmap to overlay rect
│      → InputImage.fromBitmap(cropped)
│      → TextRecognition.getClient().process(image)
│      → emit ocrText via StateFlow
│      → show result in a Text composable
```

---

## Data Flow (State)

```
CameraPreviewViewModel
├─ surfaceRequest: StateFlow<SurfaceRequest?>          # CameraX preview surface
├─ capturedImage: StateFlow<Bitmap?>                   # Full captured frame
├─ overlayRect: StateFlow<RectF?>                      # ROI position on preview
├─ ocrText: StateFlow<String?>                         # Extracted OCR result
│
├─ fun takePhoto()         → sets capturedImage
├─ fun updateOverlayRect() → sets overlayRect
├─ fun extractText()       → runs ML Kit OCR on cropped bitmap → sets ocrText
├─ fun clearCapturedImage()→ resets capturedImage & ocrText
```

---

## OverlayBox Composable

```
OverlayBox(modifier, rect: RectF, onRectChanged: (RectF) -> Unit)
│
└─ Box(Modifier.fillMaxSize())
   ├─ DrawRect (semi-transparent dim outside + border on rect)
   ├─ 4 corner handles (draggable circles)
   └─ pointerInput / detectDragGestures to resize/move
```

Constraints:
- `rect` is normalized (0f..1f) relative to preview dimensions
- On capture, scale to actual Bitmap dimensions before cropping

---

## OCR Pipeline (in ViewModel)

```
fun extractText(context: Context)
│
├─ bitmap = _capturedImage.value ?: return
├─ rect = _overlayRect.value ?: return
├─ scale rect to bitmap pixel coordinates
├─ cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
├─ image = InputImage.fromBitmap(cropped, 0)
│
├─ val recognizer = TextRecognition.getClient()
├─ recognizer.process(image).addOnSuccessListener { result ->
│     _ocrText.value = result.text
│   }.addOnFailureListener { e ->
│     Log.e("OCR", "failed", e)
│     _ocrText.value = null
│   }
```

---

## New Dependencies

```toml
[versions]
mlkitTextRecognition = "16.0.1"

[libraries]
mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkitTextRecognition" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.mlkit.text.recognition)
```

> Uses `text-recognition` (bundled variant) — model is packed in the APK (~5MB). No download needed, works offline immediately from first use. Chosen over the unbundled Play Services variant because users may have slow internet (e.g. 90 KBPS).

---

## File Changes Summary

| File | Action |
|------|--------|
| `gradle/libs.versions.toml` | Add `mlkitTextRecognition` version + library entry |
| `app/build.gradle.kts` | Add `implementation(libs.mlkit.text.recognition)` |
| `ui/screens/CameraPreviewViewModel.kt` | Add `overlayRect`, `ocrText` StateFlows + `updateOverlayRect()`, `extractText()` |
| `ui/screens/CameraPreviewContent.kt` | Add `OverlayBox` on top of `CameraXViewfinder` |
| `ui/screens/ImagePreviewScreen.kt` | Add overlay rect display + "Extract Numbers" button + OCR text result |
| `ui/screens/OverlayBox.kt` | **New** — draggable/resizable selection rectangle composable |
