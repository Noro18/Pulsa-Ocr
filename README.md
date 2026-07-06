# PulsaOCR

Camera-based OCR scanner for pulsa (voucher credit) numbers, built with Jetpack Compose and CameraX.

Users frame the number printed on a physical voucher card using an on-screen selection box, snap a photo, and the app extracts the digits using ML Kit text recognition. The overlay lets you crop out background clutter so OCR only sees the relevant region.

## Current Features

- **CameraX live preview** — full-screen camera feed bound to Compose lifecycle
- **Draggable overlay box** — pinch/drag to position and resize the region of interest
- **Photo capture** — JPEG capture via `ImageCapture`, converted to `Bitmap`
- **Region crop** — captured image cropped to the overlay coordinates
- **ML Kit OCR** — text recognition on the cropped bitmap, returns extracted digits
- **MVVM architecture** — `CameraPreviewViewModel` owns all state via `StateFlow`; composables observe with `collectAsStateWithLifecycle()`

## How It Works

1. Open the app — camera preview shows with a selection box overlay
2. Drag/resize the box to frame the number
3. Tap **Capture** — takes a photo
4. Review the captured image with the overlay position visible
5. Tap **Extract Numbers** — ML Kit OCR runs on the cropped region
6. Result appears on screen; tap back to retry

## For Contributors

### Branch workflow

Create a feature branch off `main` for your work. Do not commit directly to `main`. Open a pull request for review.

### If you use an AI coding agent

1. The agent **must read `AGENTS.md` first**. It contains the implementation guide, dependency list, and code patterns used throughout the project.
2. After any feature change, **update `AGENTS.md`** and/or `docs/architecture.md` to keep them in sync.
3. If `docs/architecture.md` exists for the feature you're working on, check there first.

### If you code manually

- Follow existing patterns: MVVM, `StateFlow` in ViewModel, `collectAsStateWithLifecycle()` in composables.
- Match the project's code style — no comments, consistent import order, same dependency conventions.

## Project Structure

```
app/src/main/java/com/example/pulsaocr/
├── MainActivity.kt                  # Entry point
└── ui/
    ├── screens/
    │   ├── CameraPreviewContent.kt   # Permission handling, camera preview, overlay, capture button
    │   ├── CameraPreviewViewModel.kt # CameraX binding, photo capture, OCR pipeline
    │   ├── ImagePreviewScreen.kt     # Photo review, OCR trigger, result display
    │   └── OverlayBox.kt            # Draggable/resizable selection rectangle composable
    └── theme/
```

## Build

Open in Android Studio, sync Gradle, and run. No API keys or external services required.
