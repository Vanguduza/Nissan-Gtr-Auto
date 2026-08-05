# Android biometric **photo** bridge — biometric-photo

Implements `BiometricPhotoCaptureBridge` from `bridges/contracts/biometric.ts`
using **CameraX ImageCapture**. Emits **local JPEG paths only** — **no Supabase /
network / fingerprint / face-matching** inside this module.

This is **not** `BiometricBridge` (BiometricPrompt unlock). Photo capture is for
HR onboarding stage 3 (profile photo) only.

## Include from android-management

```kotlin
include(":biometric-photo")
project(":biometric-photo").projectDir =
    file("../../bridges/android/biometric-photo")
```

```kotlin
implementation(project(":biometric-photo"))
```

## API surface

```kotlin
val bridge = CameraxBiometricPhotoBridge(context)
bridge.attachActivity(activity)

// onRequestPermissionsResult → REQUEST_CAMERA → bridge.onPermissionResult()
// onActivityResult → REQUEST_CAPTURE → bridge.onCaptureActivityResult(resultCode, data)

bridge.requestCameraPermission()
val photo = bridge.capturePhoto(
    BiometricPhotoCaptureOptions(title = "Staff photo", preferFrontCamera = true),
)
// photo.localPath → upload to Storage → save path on hr_onboarding_drafts.payload
```

## Permissions

| Permission | Why |
|------------|-----|
| `CAMERA` | Preview + still capture (prefer front; falls back to back) |

## Web / desk path

Web staff wizard (`/staff/hr?tab=onboarding`) uses **file upload** for stage 3.
Android management Compose wizard (`HR → Onboarding`) uses this Bridge.

## App responsibilities (not this bridge)

1. Upload `localPath` bytes to a private Storage bucket (e.g. `staff-ids`).
2. Persist the storage object path on the onboarding draft payload.
3. Delete or retain local cache files after successful upload.

## Hard rules

- Bridge-First only — no WebView / HTML5 camera for staff scan flows.
- No network or Supabase inside this module.
- No biometric template matching / fingerprint auth here.
- No ZIMRA / fiscal payloads.
