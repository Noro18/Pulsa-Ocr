# OCR Overlay Architecture

## Status

| Feature | Status |
|---------|--------|
| OverlayBox (centered, resizable) | ✅ Implemented |
| Photo capture with overlay preservation | ✅ Implemented |
| ML Kit OCR pipeline | ✅ Implemented |
| Regex digit extraction (voucher code) | ✅ Implemented |

## Feature Overview
Capture a photo from the camera preview, select a region of interest (ROI) via a draggable/resizable overlay box, then run ML Kit OCR on the cropped area and extract the voucher number by filtering out non-digit garbage via regex.

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
│                     → crop bitmap to overlay rect
│                     → processImageWithOcr() ← automatic, no extra button
│
ImagePreviewScreen
│
├─ Show full captured Bitmap
├─ OverlayRect outline (same position, visible)
├─ Extracted voucher code (large, bold, white)
├─ Raw OCR text (smaller, dimmed, below)
└─ Back button (top-left)
```

---

## Data Flow (State)

```
CameraPreviewViewModel
├─ surfaceRequest: StateFlow<SurfaceRequest?>            # CameraX preview surface
├─ capturedImage: StateFlow<Bitmap?>                     # Full captured frame
├─ overlayRect: StateFlow<RectF?>                        # ROI position on preview
├─ ocrRawText: StateFlow<String?>                        # Raw OCR output
├─ ocrExtractedDigits: StateFlow<String?>                # Filtered voucher digits
│
├─ fun takePhoto()              → sets capturedImage, calls processImageWithOcr()
├─ fun updateOverlayRect()      → sets overlayRect
├─ fun processImageWithOcr()    → runs ML Kit → stores raw text → regex → stores digits
├─ fun clearCapturedImage()     → resets capturedImage, ocrRawText, ocrExtractedDigits
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
processImageWithOcr()
│
├─ bitmap = _capturedImage.value ?: return
├─ rect = _overlayRect.value ?: return
├─ scale rect to bitmap pixel coordinates
├─ cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
├─ image = InputImage.fromBitmap(cropped, 0)
│
├─ val recognizer = TextRecognition.getClient()
├─ recognizer.process(image).addOnSuccessListener { result ->
│     _ocrRawText.value = result.text
│     // Strip whitespace, find all digit runs of 8+ chars, pick longest
│     _ocrExtractedDigits.value = Regex("""\d{8,}""")
│       .findAll(result.text.replace("\\s".toRegex(), ""))
│       .maxByOrNull { it.value.length }?.value
│   }
```

### Regex logic explained

Input example:
```
2024 7570 4266 014
ICLAUUEL
Produtiorn ate 1022022
```

1. Remove all whitespace → `202475704266014ICLAUUELProdutiornate1022022`
2. Find all `\d{8,}` matches → `["202475704266014", "1022022"]`
3. Pick longest → `"202475704266014"` (15 digits, the voucher code)
4. Short matches like `1022022` (7 digits) and `2024`, `7570`, etc. are correctly filtered out by the `{8,}` threshold

This works across all three ISPs since voucher codes are always 8+ digits.

---

## Dependencies

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

> Uses `text-recognition` (bundled variant) — model is packed in the APK (~5MB). No download needed, works offline immediately from first use. Chosen over the unbundled Play Services variant because users may have slow internet (e.g. 60 KBPS in Timor-Leste).

---

## File Changes Summary

| File | Action |
|------|--------|
| `gradle/libs.versions.toml` | Add `mlkitTextRecognition` version + library entry |
| `app/build.gradle.kts` | Add `implementation(libs.mlkit.text.recognition)` |
| `ui/screens/CameraPreviewViewModel.kt` | Add `overlayRect`, `ocrRawText`, `ocrExtractedDigits` StateFlows + `processImageWithOcr()` with regex |
| `ui/screens/CameraPreviewContent.kt` | Add `OverlayBox` on top of `CameraXViewfinder`, pass OCR state to `ImagePreviewScreen` |
| `ui/screens/ImagePreviewScreen.kt` | Show overlay rect outline + extracted voucher code + raw OCR text |
| `ui/screens/OverlayBox.kt` | **New** — draggable/resizable selection rectangle composable |

### Key implementation details

- OCR runs **automatically** right after photo capture (no separate "Extract Numbers" button)
- `clearCapturedImage()` resets both OCR state flows to `null`
- Extracted digits shown bold/24sp in a bottom overlay panel; raw text shown smaller/dimmed below
