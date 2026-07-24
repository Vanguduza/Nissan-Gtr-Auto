/**
 * Biometric auth bridge contracts — interfaces only.
 *
 * Used by management-app gate flows (POS unlock, sensitive actions).
 * No payroll / tax coupling — auth presence only.
 *
 * Forbidden: WebAuthn / browser Credential Management as a substitute for
 * device biometrics inside staff apps. BiometricPrompt / LocalAuthentication
 * only in impl dirs.
 *
 * Impl ownership (see bridges/README.md):
 *   bridges/android/biometric-auth/ | bridges/ios/BiometricAuth/
 */

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
