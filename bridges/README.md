# Hardware bridges

**Contracts only** under `bridges/contracts/` — no CameraX, AVFoundation, Bluetooth ESC/POS, BiometricPrompt, or FusedLocation implementations in this phase.

**Never** use browser/HTML5 QR libraries or WebView camera/geolocation APIs. Bridge-First: all hardware goes through native modules listed below.

## Contract path map

| Concern | Contract | Future Android impl | Future iOS impl |
|---------|----------|---------------------|-----------------|
| QR scan | `contracts/qr-inventory.ts` → `QrScannerBridge` | `bridges/android/qr-scanner/` | `bridges/ios/QRScanner/` |
| ESC/POS label print | `contracts/qr-inventory.ts` → `EscPosPrinterBridge` | `bridges/android/escpos-printer/` | `bridges/ios/escpos-printer/` |
| Biometric auth | `contracts/biometric.ts` → `BiometricBridge` | `bridges/android/biometric/` | `bridges/ios/Biometric/` |
| GPS / delivery | `contracts/gps.ts` → `GpsBridge` | `bridges/android/gps/` | `bridges/ios/GPS/` |
| Barrel export | `contracts/index.ts` | — | — |

## Phase notes

- Phase 4 introduced QR + ESC/POS contracts.
- Phase 11–12 extends biometric + GPS contracts and documents this path map.
- Native implementations remain a Phase 12 follow-on (management hardware lane), not part of the mobile scaffold slice.
