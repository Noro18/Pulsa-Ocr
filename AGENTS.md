# CameraX Implementation Guide (CameraOCR)

> Architecture & feature plan: [`docs/architecture.md`](./docs/architecture.md)

## Architecture Pattern
- **MVVM with Jetpack Compose**: `CameraPreviewViewModel` holds all camera state and business logic; UI composables observe `StateFlow` via `collectAsStateWithLifecycle()`.
- Camera lifecycle is bound to the composable's `LifecycleOwner` — auto start/stop.

---

## Project Structure

```
app/src/main/java/com/example/cameraocr/
├── MainActivity.kt              # Entry point + permission handling composable
└── ui/
    ├── screens/
    │   ├── CameraPreviewContent.kt    # Camera preview + capture button UI
    │   ├── CameraPreviewViewModel.kt  # CameraX setup, binding, photo capture logic
    │   └── ImagePreviewScreen.kt      # Shows captured photo full-screen
    └── theme/                         # Material 3 theme files
```

---

## Dependencies (libs.versions.toml / build.gradle.kts)

```toml
[versions]
camerax = "1.5.9-alpha06"
accompanist = "0.37.2"
lifecycleRuntimeKtx = "2.10.0"

[libraries]
androidx-camera-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
androidx-camera-compose = { module = "androidx.camera:camera-compose", version.ref = "camerax" }
androidx-camera-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
androidx-camera-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
accompanist-permissions = { module = "com.google.accompanist:accompanist-permissions", version.ref = "accompanist" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
```

In `app/build.gradle.kts`:
```kotlin
implementation(libs.androidx.camera.core)
implementation(libs.androidx.camera.compose)
implementation(libs.androidx.camera.lifecycle)
implementation(libs.androidx.camera.camera2)
implementation(libs.accompanist.permissions)
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.androidx.lifecycle.runtime.compose)
```

---

## 1. AndroidManifest Declarations

```xml
<uses-feature android:name="android.hardware.camera" android:required="true" />
<uses-permission android:name="android.permission.CAMERA" />
```

- `uses-feature` — Play Store filter: app won't install on devices without a camera.
- `uses-permission` — Required declaration; on Android 6+ you still need runtime prompting.

---

## 2. Runtime Permission Handling (Accompanist)

```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPreviewScreen(modifier: Modifier = Modifier) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        CameraPreviewContent()
    } else {
        // Show rationale text + "Grant Camera Permission" button
        val message = if (cameraPermissionState.status.shouldShowRationale) {
            "This app needs the camera to scan text. Please allow it."
        } else {
            "Camera permission is required to use this feature."
        }
        Text(message)
        Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
            Text("Grant Camera Permission")
        }
    }
}
```

- `rememberPermissionState` — tracks grant status as Compose state; auto-recomposes on change.
- `shouldShowRationale` — `true` after user denied once (explain why you need it).
- `launchPermissionRequest()` — triggers the Android system permission dialog.

---

## 3. CameraPreviewViewModel — CameraX Setup

### StateFlows

```kotlin
private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

private val _capturedImgage = MutableStateFlow<Bitmap?>(null)
val capturedImage: StateFlow<Bitmap?> = _capturedImgage
```

### Creating Use Cases (in constructor)

```kotlin
// Live preview use case
private val cameraPreviewUseCase = Preview.Builder().build().apply {
    setSurfaceProvider { newSurfaceRequest ->
        _surfaceRequest.update { newSurfaceRequest }
    }
}

// Photo capture use case
private val cameraCaptureUseCase = ImageCapture.Builder()
    .setTargetRotation(Surface.ROTATION_0)
    .build()
```

### Binding to Camera (suspend function)

```kotlin
suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
    val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)

    processCameraProvider.bindToLifecycle(
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
```

- `ProcessCameraProvider.awaitInstance(context)` — suspends until CameraX provider is ready.
- `bindToLifecycle(lifecycleOwner, cameraSelector, vararg useCases)` — binds camera to lifecycle.
- `awaitCancellation()` — keeps coroutine alive indefinitely.
- `finally { unbindAll() }` — releases camera when lifecycle/coroutine ends.

### Taking a Photo

```kotlin
fun takePhoto(context: Context) {
    cameraCaptureUseCase.takePicture(
        ContextCompat.getMainExecutor(context),
        object: ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                _capturedImgage.value = bitmap
                image.close()
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraOCR", "Capture failed", exception)
            }
        }
    )
}
```

### ImageProxy to Bitmap Conversion

Handles both JPEG and YUV_420_888 formats:

```kotlin
private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    if (image.format == ImageFormat.JPEG) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // YUV_420_888 → NV21 reorder → JPEG compress → Bitmap
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
```

---

## 4. CameraPreviewContent — UI Layer

```kotlin
@Composable
fun CameraPreviewContent(
    viewModel: CameraPreviewViewModel = viewModel(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    val context = LocalContext.current
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val capturedImage by viewModel.capturedImage.collectAsStateWithLifecycle()

    // Bind camera when lifecycle becomes active
    LaunchedEffect(lifecycleOwner) {
        viewModel.bindToCamera(context.applicationContext, lifecycleOwner)
    }

    // Fork: show captured image OR live preview
    capturedImage?.let { bitmap ->
        ImagePreviewScreen(bitmap = bitmap, onBack = { viewModel.clearCapturedImage() })
    } ?: surfaceRequest?.let { request ->
        Box(modifier = Modifier.fillMaxSize()) {
            CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
            Button(
                onClick = { viewModel.takePhoto(context) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            ) {
                Text("Capture")
            }
        }
    }
}
```

- `CameraXViewfinder` — Compose widget that renders the camera preview from a `SurfaceRequest`.
- `capturedImage` being non-null replaces the live preview with `ImagePreviewScreen`.
- `clearCapturedImage()` resets `_capturedImgage` to `null`, returning to live preview.

---

## 5. ImagePreviewScreen — Photo Review

```kotlin
@Composable
fun ImagePreviewScreen(bitmap: Bitmap, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
    }
}
```

---

## Key Patterns Summary

| Step | What Happens | Key API |
|------|-------------|---------|
| Permission | Check + request at runtime | `rememberPermissionState`, `launchPermissionRequest()` |
| Camera setup | Get provider, build use cases, bind to lifecycle | `ProcessCameraProvider.awaitInstance()`, `bindToLifecycle()` |
| Live preview | SurfaceRequest → CameraXViewfinder | `Preview.setSurfaceProvider{}`, `CameraXViewfinder` |
| Take photo | ImageCapture callback → ImageProxy → Bitmap | `ImageCapture.takePicture()`, `imageProxyToBitmap()` |
| Show result | Bitmap → Image composable | `Image(bitmap.asImageBitmap())` |
| Return to preview | Clear captured image state | `_capturedImgage.value = null` |

## Reusable Snippets

### CameraX Binding (ViewModel)
```kotlin
suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
    val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)
    processCameraProvider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        cameraPreviewUseCase,
        cameraCaptureUseCase
    )
    try { awaitCancellation() } finally { processCameraProvider.unbindAll() }
}
```

### Photo Capture (ViewModel)
```kotlin
fun takePhoto(context: Context) {
    cameraCaptureUseCase.takePicture(ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                _capturedImgage.value = imageProxyToBitmap(image)
                image.close()
            }
            override fun onError(e: ImageCaptureException) { Log.e("Cam", "fail", e) }
        })
}
```

### ImageProxy → Bitmap
```kotlin
private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    if (image.format == ImageFormat.JPEG) {
        val bytes = ByteArray(image.planes[0].buffer.remaining())
        image.planes[0].buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    // YUV_420_888 → NV21
    val y = image.planes[0].buffer; val u = image.planes[1].buffer; val v = image.planes[2].buffer
    val nv21 = ByteArray(y.remaining() + u.remaining() + v.remaining())
    y.get(nv21, 0, y.remaining())
    v.get(nv21, y.remaining(), v.remaining())
    u.get(nv21, y.remaining() + v.remaining(), u.remaining())
    val out = ByteArrayOutputStream()
    YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        .compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
    return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}
```
