# Hardware bridges

Phase 4 ships **contracts only** (`contracts/qr-inventory.ts`).

Native implementations (CameraX, AVFoundation, ESC/POS Bluetooth) are Phase 12 under:

- `bridges/android/qr-scanner/`
- `bridges/android/escpos-printer/`
- `bridges/ios/QRScanner/`
- `bridges/ios/escpos-printer/`

**Never** use browser/HTML5 QR libraries.
