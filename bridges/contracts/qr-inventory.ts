/**
 * Bridge contracts only — Phase 4. No CameraX / AVFoundation / HTML5 QR.
 * Implementations land in Phase 12 under bridges/android and bridges/ios.
 */

export type QrScanResult = {
  rawValue: string;
  /** ISO timestamp from device when decoded */
  scannedAt: string;
};

export interface QrScannerBridge {
  /** Start camera preview and resolve on first valid decode. */
  scanOnce(): Promise<QrScanResult>;
  cancel(): Promise<void>;
}

export type EscPosPrintJob = {
  /** Full `gtr://part/...` payload for QR glyph */
  qrPayload: string;
  oemPartNumber: string;
  batchCode: string;
  labelDate?: string;
};

export interface EscPosPrinterBridge {
  /** Print one inventory label via Bluetooth ESC/POS. */
  printInventoryLabel(job: EscPosPrintJob): Promise<void>;
  isConnected(): Promise<boolean>;
}
