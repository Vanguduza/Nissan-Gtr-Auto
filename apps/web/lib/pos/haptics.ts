/**
 * Web POS haptic feedback (Vibration API). Works on Android browsers; iOS Safari and desktops have
 * no vibration, so every call is a silent no-op there. Patterns are kept short so they confirm an
 * action without slowing the counter. The tablet app uses native Android haptics for the same events.
 */
export type HapticEvent = "tap" | "select" | "longPress" | "success" | "error";

const PATTERNS: Record<HapticEvent, number | number[]> = {
  tap: 10, // add part, quantity +/-
  select: 18, // pin / remove from Popular, choose vehicle
  longPress: 35, // long-press registered
  success: [20, 60, 30], // sale complete, manager approved, sale parked
  error: [60, 40, 60, 40, 60], // refused action or failed request
};

const STORAGE_KEY = "gtr.pos.haptics";

export function hapticsSupported(): boolean {
  return typeof navigator !== "undefined" && typeof navigator.vibrate === "function";
}

/** Operator preference (device-local). Defaults to on. */
export function hapticsEnabled(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) !== "off";
  } catch {
    return true;
  }
}

export function setHapticsEnabled(on: boolean): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, on ? "on" : "off");
  } catch {
    // Preference is a convenience; ignore storage failures.
  }
}

function reducedMotion(): boolean {
  return typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches === true;
}

export function haptic(event: HapticEvent): void {
  if (!hapticsSupported() || !hapticsEnabled() || reducedMotion()) return;
  try {
    navigator.vibrate(PATTERNS[event]);
  } catch {
    // Some browsers throw if vibration is blocked by permissions policy; feedback is optional.
  }
}
