# Android POD camera bridge — pod-camera

Implements `PodCameraBridge` from `bridges/contracts/pod.ts` using
**CameraX ImageCapture**. Emits **local JPEG paths only** — **no Supabase /
network** inside this module.

OTP for POD remains app-layer (`generate_delivery_pod_otp` /
`verify_delivery_pod_otp` / `submit_delivery_pod`).

## Include from android-delivery

```kotlin
include(":pod-camera")
project(":pod-camera").projectDir =
    file("../../bridges/android/pod-camera")
```

```kotlin
implementation(project(":pod-camera"))
```

## API surface

```kotlin
val bridge = CameraxPodCameraBridge(context)
bridge.attachActivity(activity)

// onRequestPermissionsResult → REQUEST_CAMERA → bridge.onPermissionResult()
// onActivityResult → REQUEST_CAPTURE → bridge.onCaptureActivityResult(resultCode, data)

bridge.requestCameraPermission()
val photo = bridge.capturePhoto()
// photo.localPath → upload to Storage → pass storage path to submit_delivery_pod
```

## Permissions

| Permission | Why |
|------------|-----|
| `CAMERA` | Preview + still capture |

## App responsibilities (not this bridge)

1. Upload `localPath` bytes to a private Storage bucket (see Manager note: bucket may be missing).
2. Call `submit_delivery_pod` with the **storage object path**, signature path, and OTP.
3. Delete or retain local cache files after successful upload.

## Hard rules

- Bridge-First only — no WebView / HTML5 camera / `<input capture>`.
- No network or Supabase inside this module.
- No ZIMRA / fiscal payloads.
