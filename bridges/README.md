# Hardware bridges

Contracts under `bridges/contracts/`. Native implementations under `bridges/android/*`
and `bridges/ios/*`. Shared UI and web **never** call camera, Bluetooth, biometric, or
geolocation APIs directly.

## Bridge-First (hard rules)

| Allowed | Forbidden |
|---------|-----------|
| Native modules under `bridges/android/*` and `bridges/ios/*` | HTML5 / browser QR libraries (`jsQR`, `html5-qrcode`, `BarcodeDetector` in WebView, etc.) |
| CameraX / AVFoundation (QR impl dirs only) | WebView or in-page camera scanners |
| BluetoothAdapter / CoreBluetooth (ESC/POS impl dirs only) | Web Bluetooth |
| BiometricPrompt / LocalAuthentication | Browser WebAuthn as staff biometric substitute |
| FusedLocationProvider / CoreLocation | Browser geolocation (`navigator.geolocation`) or WebView GPS |

## Contract path map

| Concern | Contract file → interface | Android impl | iOS impl | Native stack |
|---------|---------------------------|--------------|----------|--------------|
| QR scan | `contracts/qr-inventory.ts` → `QrScannerBridge` | `bridges/android/qr-scanner/` | `bridges/ios/QRScanner/` (stub) | CameraX+ML Kit / AVFoundation |
| ESC/POS print | `contracts/qr-inventory.ts` → `EscPosPrinterBridge` | `bridges/android/escpos-printer/` | `bridges/ios/escpos-printer/` (stub) | BluetoothAdapter / CoreBluetooth |
| Biometric auth | `contracts/biometric.ts` → `BiometricBridge` | `bridges/android/biometric-auth/` | `bridges/ios/BiometricAuth/` | BiometricPrompt / LocalAuthentication |
| GPS / delivery ingest | `contracts/gps.ts` → `GpsBridge` | `bridges/android/location-tracker/` | `bridges/ios/LocationTracker/` | FusedLocationProvider / CoreLocation |
| Barrel export | `contracts/index.ts` | — | — | — |

## Android modules (Batch 5)

| Gradle module | Path | Status |
|---------------|------|--------|
| `:location-tracker` | `android/location-tracker/` | Implemented |
| `:qr-scanner` | `android/qr-scanner/` | Implemented — CameraX + ML Kit |
| `:escpos-printer` | `android/escpos-printer/` | Implemented — RFCOMM ESC/POS |

Include from `apps/android-management/settings.gradle.kts` (already wired):

```kotlin
include(":qr-scanner")
project(":qr-scanner").projectDir = file("../../bridges/android/qr-scanner")
include(":escpos-printer")
project(":escpos-printer").projectDir = file("../../bridges/android/escpos-printer")
```

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

### Delivery GPS (Phase 10)

- Native bridge yields `GpsCoordinate` only (no network I/O inside the bridge).
- App maps via `toDeliveryLocationIngest` → RPC `ingest_delivery_location`.
