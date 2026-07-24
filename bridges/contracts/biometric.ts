/**
 * Bridge contracts only — Phase 11–12.
 * No platform biometric SDK wiring here (BiometricPrompt / LocalAuthentication later).
 */

export type BiometricStrength = "strong" | "weak" | "device_credential";

export type BiometricPromptOptions = {
  title: string;
  subtitle?: string;
  /** Prefer strong biometrics when available */
  allowedStrengths?: BiometricStrength[];
};

export type BiometricAuthResult =
  | { ok: true; method: BiometricStrength }
  | { ok: false; reason: "cancelled" | "unavailable" | "failed" | "locked_out" };

export interface BiometricBridge {
  isAvailable(): Promise<boolean>;
  authenticate(options: BiometricPromptOptions): Promise<BiometricAuthResult>;
}
