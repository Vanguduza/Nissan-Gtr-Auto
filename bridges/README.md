# Hardware bridges

**Contracts only** under `bridges/contracts/` — no CameraX, AVFoundation, Bluetooth ESC/POS, BiometricPrompt, FusedLocation, or CoreLocation implementations in this phase.

## Bridge-First (hard rules)

| Allowed | Forbidden |
|---------|-----------|
| Native modules under `bridges/android/*` and `bridges/ios/*` | HTML5 / browser QR libraries (`jsQR`, `html5-qrcode`, `BarcodeDetector` in WebView, etc.) |
| CameraX / AVFoundation (QR impl dirs only) | WebView or in-page camera scanners |
| BluetoothAdapter / CoreBluetooth (ESC/POS impl dirs only) | Web Bluetooth |
| BiometricPrompt / LocalAuthentication | Browser WebAuthn as staff biometric substitute |
| FusedLocationProvider / CoreLocation | Browser geolocation (`navigator.geolocation`) or WebView GPS |

Shared UI and web storefront **never** call camera, Bluetooth, biometric, or geolocation APIs directly. They depend on these contracts; native apps inject implementations.

## Contract path map

Which contract each future impl directory owns:

| Concern | Contract file → interface | Future Android impl | Future iOS impl | Native stack (later) |
|---------|---------------------------|---------------------|-----------------|----------------------|
| QR scan | `contracts/qr-inventory.ts` → `QrScannerBridge` | `bridges/android/qr-scanner/` | `bridges/ios/QRScanner/` | CameraX / AVFoundation |
| ESC/POS label print | `contracts/qr-inventory.ts` → `EscPosPrinterBridge` | `bridges/android/escpos-printer/` | `bridges/ios/escpos-printer/` | BluetoothAdapter / CoreBluetooth |
| Biometric auth | `contracts/biometric.ts` → `BiometricBridge` | `bridges/android/biometric-auth/` | `bridges/ios/BiometricAuth/` | BiometricPrompt / LocalAuthentication |
| GPS / delivery ingest | `contracts/gps.ts` → `GpsBridge` | `bridges/android/location-tracker/` | `bridges/ios/LocationTracker/` | FusedLocationProvider / CoreLocation |
| Barrel export | `contracts/index.ts` | — | — | — |

## Domain alignment

### Inventory QR (Phase 4)

```
gtr://part/{OEM_PART_NUMBER}?batch={BATCH_ID}&valuation={FIFO|AVG}
```

- Bridge returns `QrScanResult.rawValue` (full URI).
- Shared helpers in `packages/shared` build/parse the URI (`InventoryQrFields`).
- ESC/POS jobs carry the same full `qrPayload` plus OEM, batch, and valuation for the human-readable line.

### Delivery GPS (Phase 10)

- Native bridge yields `GpsCoordinate` only (no network I/O inside the bridge).
- App/service maps via `toDeliveryLocationIngest` → RPC `ingest_delivery_location`
  `(delivery_job_id, lat, lng, recorded_at, accuracy_m)`.
- Server rate limit ~5s per job; trail `source = 'bridge'`. Do not INSERT `delivery_locations` from clients.

## Phase notes

- Phase 4 introduced QR + ESC/POS contracts.
- Phase 11–12 extends biometric + GPS contracts, permissions surfaces, ingest mapping, and this path map (aligned with `AGENTS.md` / hardware specialist lanes).
- Native implementations remain a follow-on in the management hardware lane — not part of the contract polish slice.
