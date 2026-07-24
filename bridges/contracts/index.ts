/**
 * Hardware bridge contracts — interfaces only.
 * Re-export for consumers; implementations live under bridges/android and bridges/ios.
 */

export type {
  QrScanResult,
  QrScannerBridge,
  EscPosPrintJob,
  EscPosPrinterBridge,
} from "./qr-inventory.ts";

export type {
  BiometricStrength,
  BiometricPromptOptions,
  BiometricAuthResult,
  BiometricBridge,
} from "./biometric.ts";

export type {
  GpsCoordinate,
  GpsWatchHandle,
  GpsBridge,
} from "./gps.ts";
