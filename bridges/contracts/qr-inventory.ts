/**
 * QR inventory + ESC/POS bridge contracts — interfaces only.
 *
 * Canonical payload (Phase 4):
 *   gtr://part/{OEM_PART_NUMBER}?batch={BATCH_ID}&valuation={FIFO|AVG}
 *
 * Parsing/building of the URI lives in `packages/shared` (`buildInventoryQrPayload` /
 * `parseInventoryQrPayload`). Native impls decode bytes → string; shared UI parses.
 *
 * Forbidden: HTML5/browser QR (jsQR, html5-qrcode, BarcodeDetector in WebView, etc.),
 * WebView camera APIs, or any in-page scanner. CameraX / AVFoundation only in impl dirs.
 *
 * Impl ownership (see bridges/README.md):
 *   QR  → bridges/android/qr-scanner/     | bridges/ios/QRScanner/
 *   Print → bridges/android/escpos-printer/ | bridges/ios/escpos-printer/
 */

/** Valuation bracket encoded in inventory QR query string. */
export type InventoryQrValuation = "FIFO" | "AVG";

/**
 * Fields decoded from a valid inventory QR URI.
 * Matches regexp used by POS/sales RPCs:
 *   ^gtr://part/([^?]+)\?batch=([^&]+)&valuation=(FIFO|AVG)$
 */
export type InventoryQrFields = {
  oemPartNumber: string;
  batchCode: string;
  valuation: InventoryQrValuation;
};

/** Result of a native camera decode (raw string + device timestamp). */
export type QrScanResult = {
  /** Full decoded string (expect `gtr://part/…` for inventory stickers). */
  rawValue: string;
  /** ISO-8601 timestamp from device when decoded. */
  scannedAt: string;
};

export type CameraPermissionStatus =
  | "granted"
  | "denied"
  | "restricted"
  | "not_determined";

/**
 * Native QR scanner. Returns the raw payload; callers parse with shared helpers
 * and pass `rawValue` into inventory/sales RPCs that accept the full URI.
 */
export interface QrScannerBridge {
  /** Camera permission state (bridge-owned; never navigator.mediaDevices). */
  getCameraPermissionStatus(): Promise<CameraPermissionStatus>;
  /** Request camera access if not yet determined / denied-retryable. */
  requestCameraPermission(): Promise<CameraPermissionStatus>;
  /** Start native preview; resolve on first successful decode. */
  scanOnce(): Promise<QrScanResult>;
  /** Abort an in-flight scanOnce without resolving a value. */
  cancel(): Promise<void>;
}

/**
 * One thermal label: QR glyph + human-readable OEM / batch / date.
 * `qrPayload` must be the full canonical `gtr://part/…` string (not OEM alone).
 */
export type EscPosPrintJob = {
  /** Full `gtr://part/{OEM}?batch=…&valuation=FIFO|AVG` for the QR glyph. */
  qrPayload: string;
  oemPartNumber: string;
  batchCode: string;
  valuation: InventoryQrValuation;
  /** Optional ISO date printed under the QR (defaults to device local date). */
  labelDate?: string;
};

export type BluetoothPermissionStatus =
  | "granted"
  | "denied"
  | "restricted"
  | "not_determined";

/** One text line for a thermal receipt (UTF-8; bridge maps to ESC/POS). */
export type EscPosReceiptLine = {
  text: string;
  /** When true, use double-height emphasis if the printer supports it. */
  emphasis?: boolean;
};

/**
 * Bluetooth ESC/POS inventory label + receipt printer.
 * CoreBluetooth / BluetoothAdapter only in impl dirs — never Web Bluetooth.
 */
export interface EscPosPrinterBridge {
  getBluetoothPermissionStatus(): Promise<BluetoothPermissionStatus>;
  requestBluetoothPermission(): Promise<BluetoothPermissionStatus>;
  /** Open / pair session to the configured printer. */
  connect(): Promise<void>;
  disconnect(): Promise<void>;
  isConnected(): Promise<boolean>;
  /** Persist bonded printer address (MAC / UUID) before connect. */
  configurePrinterAddress(address: string): Promise<void>;
  /** Print one inventory label (QR + OEM + batch + date). */
  printInventoryLabel(job: EscPosPrintJob): Promise<void>;
  /** Best-effort POS/customer receipt (plain lines → ESC/POS text). */
  printReceiptLines(lines: EscPosReceiptLine[]): Promise<void>;
  /** Escape hatch: send pre-built ESC/POS bytes. */
  printRaw(bytes: Uint8Array): Promise<void>;
}
