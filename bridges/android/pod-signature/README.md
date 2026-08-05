# Android POD signature bridge — pod-signature

Implements `PodSignatureBridge` from `bridges/contracts/pod.ts` using a native
**Compose Canvas** ink pad (`ComposeSignaturePad`) plus legacy `SignaturePadView`.
Emits **local PNG paths only** — **no Supabase / network / WebView** inside this module.

Prefer embedding `ComposeSignaturePad` on POD UI; `captureSignature()` opens the
full-screen Compose Activity when a larger pad is needed.

## Include from android-delivery

```kotlin
include(":pod-signature")
project(":pod-signature").projectDir =
    file("../../bridges/android/pod-signature")
```

```kotlin
implementation(project(":pod-signature"))
```

## API surface

```kotlin
val bridge = CanvasPodSignatureBridge(context)
bridge.attachActivity(activity)

// onActivityResult → REQUEST_SIGNATURE → bridge.onSignatureActivityResult(resultCode, data)

val sig = bridge.captureSignature(
    PodSignatureOptions(title = "Customer signature", strokeWidth = 3f),
)
// sig.localPath → upload to Storage → pass storage path to submit_delivery_pod
```

## Permissions

None beyond normal Activity launch (no camera).

## App responsibilities (not this bridge)

1. Upload `localPath` PNG to private Storage.
2. Collect OTP in app UI; call `submit_delivery_pod(jobId, photoPath, signaturePath, otp, notes?)`.
3. Offline POD queue (photo + signature + OTP payload) is **app** responsibility.

## Hard rules

- Bridge-First only — no WebView signature canvas.
- No network or Supabase inside this module.
- No ZIMRA / fiscal payloads.
