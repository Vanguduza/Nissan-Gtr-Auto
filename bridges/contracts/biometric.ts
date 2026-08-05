/**

 * Biometric bridge contracts — interfaces only.

 *

 * 1) BiometricBridge — device unlock (POS / sensitive actions).

 * 2) BiometricPhotoCaptureBridge — HR onboarding stage-3 **profile photo**

 *    capture only (NOT fingerprint/face matching auth).

 *

 * Forbidden: WebAuthn / browser Credential Management as a substitute for

 * device biometrics inside staff apps. No WebView / HTML5 camera /

 * `<input capture>` for staff scan flows — CameraX / native only.

 *

 * Impl ownership (see bridges/README.md):

 *   bridges/android/biometric-auth/   → BiometricPrompt (stub)

 *   bridges/android/biometric-photo/  → CameraxBiometricPhotoBridge

 *   bridges/ios/BiometricAuth/

 */



import type { CameraPermissionStatus } from "./qr-inventory.ts";



export type { CameraPermissionStatus };



export type BiometricStrength = "strong" | "weak" | "device_credential";



export type BiometricPromptOptions = {

  title: string;

  subtitle?: string;

  /** Prefer strong biometrics when available; device_credential as fallback. */

  allowedStrengths?: BiometricStrength[];

  /** Cancel / negative button label where the OS shows one. */

  cancelLabel?: string;

};



export type BiometricAuthResult =

  | { ok: true; method: BiometricStrength }

  | {

      ok: false;

      reason:

        | "cancelled"

        | "unavailable"

        | "failed"

        | "locked_out"

        | "not_enrolled";

    };



/**

 * Device biometric (or device-credential fallback) challenge.

 * Implementations own permission / enrollment checks; never call WebAuthn from UI.

 */

export interface BiometricBridge {

  /** True when at least one allowed strength can run a challenge. */

  isAvailable(): Promise<boolean>;

  /** Strengths currently usable on this device (empty if none enrolled). */

  getAvailableStrengths(): Promise<BiometricStrength[]>;

  authenticate(options: BiometricPromptOptions): Promise<BiometricAuthResult>;

}



/** Local capture artifact ready for app-side Storage upload. */

export type BiometricPhotoCaptureResult = {

  /** Absolute filesystem path (or content URI string) on device. */

  localPath: string;

  /** e.g. image/jpeg */

  mimeType: string;

  /** ISO-8601 from device clock. */

  capturedAt: string;

};



export type BiometricPhotoCaptureOptions = {

  /** Activity / toolbar title. */

  title?: string;

  /** Prefer front (selfie) camera for HR profile photos. Default true. */

  preferFrontCamera?: boolean;

};



/**

 * Native HR onboarding profile-photo capture (CameraX still image).

 * Returns a local JPEG path — no network / matching / template storage inside the bridge.

 */

export interface BiometricPhotoCaptureBridge {

  getCameraPermissionStatus(): Promise<CameraPermissionStatus>;

  requestCameraPermission(): Promise<CameraPermissionStatus>;

  /** Opens native capture UI; resolves with a local image path. */

  capturePhoto(

    options?: BiometricPhotoCaptureOptions,

  ): Promise<BiometricPhotoCaptureResult>;

}


