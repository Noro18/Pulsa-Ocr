# OCR Overlay Architecture

## Feature Overview
Capture a photo from the camera preview framed by a fixed guide box, then run ML Kit OCR on the cropped area to extract numbers.

---

## Flow Diagram

```
CameraPreviewContent
│
├─ CameraXViewfinder (live feed, fullscreen)
├─ OverlayBox composable
│   └─ Fixed guide box (corner brackets + scrim) on top of preview
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
├─ overlayRect: StateFlow<RectF>                       # Fixed guide box position (normalized)
├─ ocrText: StateFlow<String?>                         # Extracted OCR result
│
├─ fun takePhoto()         → sets capturedImage
├─ fun extractText()       → runs ML Kit OCR on cropped bitmap → sets ocrText
├─ fun clearCapturedImage()→ resets capturedImage & ocrText
```

---

## OverlayBox Composable

```
OverlayBox(modifier, rect: RectF)
│
└─ Canvas(Modifier.fillMaxSize())
   ├─ Draw scrim (semi-transparent dim outside the rect)
   └─ Draw 4 corner brackets (cyan L-shapes) at each corner
```

Constraints:
- `rect` is normalized (0f..1f) relative to preview dimensions
- No gesture handling — static visual guide (QR-scanner style)
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

> Uses `text-recognition` — model is packed in the APK (~5MB). No download needed, works offline immediately from first use. Chosen over the default unbundled variant because users may have slow internet (e.g. 90 KBPS).

---

## File Changes Summary

| File | Action |
|------|--------|
| `gradle/libs.versions.toml` | Add `mlkitTextRecognition` version + library entry |
| `app/build.gradle.kts` | Add `implementation(libs.mlkit.text.recognition)` |
| `ui/screens/CameraPreviewViewModel.kt` | Add `overlayRect` StateFlow |
| `ui/screens/CameraPreviewContent.kt` | Add `OverlayBox` on top of `CameraXViewfinder` |
| `ui/screens/ImagePreviewScreen.kt` | Add overlay rect display + "Extract Numbers" button + OCR text result |
| `ui/screens/OverlayBox.kt` | **New** — fixed guide box with corner brackets (QR-scanner style) |
