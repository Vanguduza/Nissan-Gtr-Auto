/**
 * Hardware bridge contracts — interfaces only.
 * Re-export for consumers; implementations live under bridges/android and bridges/ios
 * (path map in bridges/README.md). No CameraX / AVFoundation / Bluetooth / HTML5 QR here.
 */

export type {
  InventoryQrValuation,
  InventoryQrFields,
  QrScanResult,
  CameraPermissionStatus,
  QrScannerBridge,
  EscPosPrintJob,
  EscPosReceiptLine,
  BluetoothPermissionStatus,
  EscPosPrinterBridge,
} from "./qr-inventory.ts";

export type {
  BiometricStrength,
  BiometricPromptOptions,
  BiometricAuthResult,
  BiometricBridge,
  BiometricPhotoCaptureResult,
  BiometricPhotoCaptureOptions,
  BiometricPhotoCaptureBridge,
} from "./biometric.ts";

export type {
  LocationPermissionStatus,
  GpsCoordinate,
  DeliveryLocationIngest,
  GpsToIngestMapper,
  GpsWatchHandle,
  GpsWatchCadence,
  GpsWatchOptions,
  GpsBridge,
} from "./gps.ts";

export { toDeliveryLocationIngest } from "./gps.ts";

export type {
  PodCaptureResult,
  PodCameraBridge,
  PodSignatureOptions,
  PodSignatureBridge,
} from "./pod.ts";
