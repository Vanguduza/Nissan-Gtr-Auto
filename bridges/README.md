# Hardware bridges

Contracts under `bridges/contracts/`. Native implementations under `bridges/android/*`
and `bridges/ios/*`. Shared UI and web **never** call camera, Bluetooth, biometric, or
geolocation APIs directly.

## Bridge-First (hard rules)

| Allowed | Forbidden |
|---------|-----------|
| Native modules under `bridges/android/*` and `bridges/ios/*` | HTML5 / browser QR libraries (`jsQR`, `html5-qrcode`, `BarcodeDetector` in WebView, etc.) |
| CameraX / AVFoundation (QR + POD photo impl dirs only) | WebView or in-page camera scanners / `<input capture>` |
| Native Canvas signature pad (`pod-signature`) | WebView HTML canvas signature |
| BluetoothAdapter / CoreBluetooth (ESC/POS impl dirs only) | Web Bluetooth |
| BiometricPrompt / LocalAuthentication | Browser WebAuthn as staff biometric substitute |
| FusedLocationProvider / CoreLocation | Browser geolocation (`navigator.geolocation`) or WebView GPS |

## Contract path map

| Concern | Contract file → interface | Android impl | iOS impl | Native stack |
|---------|---------------------------|--------------|----------|--------------|
| QR scan | `contracts/qr-inventory.ts` → `QrScannerBridge` | `bridges/android/qr-scanner/` | `bridges/ios/QRScanner/` (stub) | CameraX+ML Kit / AVFoundation |
| ESC/POS print | `contracts/qr-inventory.ts` → `EscPosPrinterBridge` | `bridges/android/escpos-printer/` | `bridges/ios/escpos-printer/` (stub) | BluetoothAdapter / CoreBluetooth |
| Biometric auth | `contracts/biometric.ts` → `BiometricBridge` | `bridges/android/biometric-auth/` (stub) | `bridges/ios/BiometricAuth/` | BiometricPrompt / LocalAuthentication |
| Biometric photo (HR) | `contracts/biometric.ts` → `BiometricPhotoCaptureBridge` | `bridges/android/biometric-photo/` | — | CameraX ImageCapture (profile photo only) |
| GPS / delivery ingest | `contracts/gps.ts` → `GpsBridge` | `bridges/android/location-tracker/` | `bridges/ios/LocationTracker/` | FusedLocationProvider / CoreLocation |
| POD photo | `contracts/pod.ts` → `PodCameraBridge` | `bridges/android/pod-camera/` | — (no iOS driver app) | CameraX ImageCapture |
| Review photo (customer) | same local-path shape as POD | Android pod-camera reuse | `bridges/ios/ReviewCamera/` | UIImagePickerController |
| POD signature | `contracts/pod.ts` → `PodSignatureBridge` | `bridges/android/pod-signature/` | — (no iOS driver app) | Compose Canvas pad |
| Delivery maps (display) | — (helper) | `bridges/android/maps-nav/` | — | **MapLibre SoR** + OSRM distance; Google Maps/Directions deprecated fallback |
| Barrel export | `contracts/index.ts` | — | — | — |

## Android modules

| Gradle module | Path | Status |
|---------------|------|--------|
| `:location-tracker` | `android/location-tracker/` | **Hardened** — FGS + battery cadence (`AUTO`/`MOVING`/`IDLE`) + `GpsPingBuffer` |
| `:qr-scanner` | `android/qr-scanner/` | Implemented — CameraX + ML Kit |
| `:escpos-printer` | `android/escpos-printer/` | Implemented — RFCOMM ESC/POS |
| `:pod-camera` | `android/pod-camera/` | **P0** — CameraX still capture → local JPEG path |
| `:pod-signature` | `android/pod-signature/` | **P0** — Compose Canvas ink pad → local PNG path |
| `:maps-nav` | `android/maps-nav/` | **MapLibre SoR** (address pick + shared helpers); OSRM prefer; Google deprecated fallback |
| `:biometric-photo` | `android/biometric-photo/` | **P0** — HR onboarding profile photo (CameraX; no matching) |

Include from `apps/android-delivery/settings.gradle.kts` (scaffold lane):

```kotlin
include(":location-tracker")
project(":location-tracker").projectDir = file("../../bridges/android/location-tracker")
include(":pod-camera")
project(":pod-camera").projectDir = file("../../bridges/android/pod-camera")
include(":pod-signature")
project(":pod-signature").projectDir = file("../../bridges/android/pod-signature")
include(":maps-nav")
project(":maps-nav").projectDir = file("../../bridges/android/maps-nav")
```

Management app may keep `:qr-scanner` / `:escpos-printer` / `:location-tracker` until
driver GPS is gated out of management (plan mandate).

See each module README for permissions and API surface.

## Domain alignment

### Inventory QR (Phase 4)

```
gtr://part/{OEM_PART_NUMBER}?batch={BATCH_ID}&valuation={FIFO|AVG}
```

- Bridge returns `QrScanResult.rawValue` (full URI) — no Supabase inside the bridge.
- Shared helpers / Kotlin `parseInventoryQrPayload` build/parse the URI.
- POS: `add_cart_line_from_qr(cart_id, rawValue, qty)`.
- Warehouse: parse OEM → `lookupStockItemByOem` → fill receive / cycle-count fields.
- ESC/POS jobs carry the same full `qrPayload` plus OEM, batch, and valuation.
- Forbidden: ZIMRA / fiscal QR payloads.

### Delivery GPS (Phase 10 + dedicated delivery app)

- Native bridge yields `GpsCoordinate` only (no network I/O inside the bridge).
- App maps via `toDeliveryLocationIngest` → RPC `ingest_delivery_location`.
- Continuous watch starts Android FGS + persistent notification; cadence
  `AUTO` switches moving (~5s high accuracy) ↔ idle (~30s balanced).
- **Offline:** `GpsPingBuffer` is ephemeral only; durable queue + flush is **app**.

### Proof of delivery (POD)

- `PodCameraBridge.capturePhoto()` → local JPEG path.
- `PodSignatureBridge.captureSignature()` → local PNG path.
- App uploads both to Storage, then `submit_delivery_pod` with storage paths + OTP
  (OTP is **not** a bridge concern).
- **Storage gap:** no dedicated `delivery-pods` (or similar) bucket migration found —
  `@backend_agent` / delivery scaffold must add private bucket + RLS before upload works.
