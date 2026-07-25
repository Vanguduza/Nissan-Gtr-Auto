/**
 * Proof-of-delivery (POD) bridge contracts — interfaces only.
 *
 * Aligns with RPC `public.submit_delivery_pod`:
 *   (p_delivery_job_id UUID,
 *    p_pod_photo_path TEXT,
 *    p_pod_signature_path TEXT,
 *    p_otp_code TEXT,
 *    p_notes TEXT DEFAULT NULL)
 *
 * Bridges capture **local file paths** only. The delivery app uploads bytes to
 * Supabase Storage, then passes the resulting storage object paths into the RPC.
 * OTP generate/verify is app-layer (not bridge).
 *
 * Forbidden: WebView / HTML5 camera, `<input type="file" capture>`, Canvas
 * signature in a WebView. CameraX / native View canvas only in impl dirs.
 *
 * Impl ownership (see bridges/README.md):
 *   bridges/android/pod-camera/    → CameraxPodCameraBridge
 *   bridges/android/pod-signature/ → CanvasPodSignatureBridge
 *   iOS driver app: out of scope (ADR 2026-07-25)
 */

import type { CameraPermissionStatus } from "./qr-inventory.ts";

export type { CameraPermissionStatus };

/** Local capture artifact ready for app-side Storage upload. */
export type PodCaptureResult = {
  /** Absolute filesystem path (or content URI string) on device. */
  localPath: string;
  /** e.g. image/jpeg, image/png */
  mimeType: string;
  /** ISO-8601 from device clock. */
  capturedAt: string;
};

/**
 * Native POD photo (delivery package / doorstep).
 * Returns a local JPEG path — no network inside the bridge.
 */
export interface PodCameraBridge {
  getCameraPermissionStatus(): Promise<CameraPermissionStatus>;
  requestCameraPermission(): Promise<CameraPermissionStatus>;
  /** Opens native capture UI; resolves with a local image path. */
  capturePhoto(): Promise<PodCaptureResult>;
}

export type PodSignatureOptions = {
  /** Activity / toolbar title. */
  title?: string;
  /** Stroke width in CSS-ish dp (native maps to px). */
  strokeWidth?: number;
};

/**
 * Native ink signature pad (no WebView).
 * Returns a local PNG path — no network inside the bridge.
 */
export interface PodSignatureBridge {
  captureSignature(options?: PodSignatureOptions): Promise<PodCaptureResult>;
}
