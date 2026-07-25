# Android QR scanner bridge — qr-scanner

Implements `QrScannerBridge` from `bridges/contracts/qr-inventory.ts` using
**CameraX + ML Kit barcode scanning**. Emits decoded **payload strings only** —
**no Supabase / network** inside this module.

Canonical inventory URI (Phase 4 / `build_qr_payload`):

```
gtr://part/{OEM_PART_NUMBER}?batch={BATCH_ID}&valuation={FIFO|AVG}
```

## Include from management app

In `apps/android-management/settings.gradle.kts`:

```kotlin
include(":qr-scanner")
project(":qr-scanner").projectDir =
    file("../../bridges/android/qr-scanner")
```

In the POS / warehouse (or app) module `build.gradle.kts`:

```kotlin
implementation(project(":qr-scanner"))
```

Root `apps/android-management/build.gradle.kts` already applies
`com.android.library` / Kotlin Android plugins — this module uses those.

## API surface

```kotlin
val bridge = CameraxQrScannerBridge(context)
bridge.attachActivity(activity) // required before permission / scan

// In Activity.onRequestPermissionsResult:
// if (requestCode == CameraxQrScannerBridge.REQUEST_CAMERA) bridge.onPermissionResult()

// In Activity.onActivityResult:
// if (requestCode == CameraxQrScannerBridge.REQUEST_SCAN)
//     bridge.onScanActivityResult(resultCode, data)

val status = bridge.getCameraPermissionStatus()
val after = bridge.requestCameraPermission()

val result = bridge.scanOnce() // suspends until first QR decode
// result.rawValue → add_cart_line_from_qr / warehouse field fill
val fields = parseInventoryQrPayload(result.rawValue)

bridge.cancel() // abort in-flight scanOnce
```

## Permissions

| Permission | Why |
|------------|-----|
| `CAMERA` | Preview + ML Kit decode |

Host Activity must forward `onRequestPermissionsResult` for `REQUEST_CAMERA` and
`onActivityResult` for `REQUEST_SCAN` (or migrate to Activity Result API later).

## Hard rules

- Bridge-First only — no HTML5 / `jsQR` / WebView `BarcodeDetector`.
- No ZIMRA / fiscal QR payloads — inventory `gtr://part/…` only.
- No network or Supabase inside this module.
