/**
 * Dedicated delivery app RPC helpers + row types.
 * Plan: docs/plans/2026-07-25-dedicated-delivery-app.md
 */
import type { Database } from "./database.types";

export const DELIVERY_PODS_BUCKET = "delivery-pods" as const;

/** Object keys only (no bucket prefix) for `pod_photo_path` / `pod_signature_path`. */
export function deliveryPodPhotoPath(jobId: string, ext: "jpg" | "jpeg" | "png" | "webp" = "jpg") {
  return `${jobId}/photo.${ext}` as const;
}

export function deliveryPodSignaturePath(
  jobId: string,
  ext: "png" | "jpg" | "jpeg" | "webp" = "png",
) {
  return `${jobId}/signature.${ext}` as const;
}

export const DELIVERY_RPC = {
  setDriverPresence: "set_driver_presence",
  suggestAssignees: "suggest_delivery_assignees",
  assignJob: "assign_delivery_job",
  setJobGeo: "set_delivery_job_geo",
  ingestLocation: "ingest_delivery_location",
  getTrackPoint: "get_delivery_track_point",
  submitPod: "submit_delivery_pod",
  mintTrackToken: "mint_delivery_track_token",
  updateJobStatus: "update_delivery_job_status",
  generatePodOtp: "generate_delivery_pod_otp",
  verifyPodOtp: "verify_delivery_pod_otp",
  geofenceSuggestion: "delivery_geofence_suggestion",
  failJob: "fail_delivery_job",
  raisePanic: "raise_delivery_panic",
  optimizeStops: "optimize_driver_stops",
} as const;

/** Result of `update_delivery_job_status` — track_token only on dispatch. */
export type UpdateDeliveryJobStatusResult = {
  delivery_job_id: string;
  track_token?: string | null;
};

export type DriverPresenceRow =
  Database["public"]["Tables"]["driver_presence"]["Row"];
export type DeliveryJobRow = Database["public"]["Tables"]["delivery_jobs"]["Row"];
export type DeliveryTrackTokenRow =
  Database["public"]["Tables"]["delivery_track_tokens"]["Row"];
export type DeliveryPodOtpRow =
  Database["public"]["Tables"]["delivery_pod_otps"]["Row"];
export type PanicEventRow = Database["public"]["Tables"]["panic_events"]["Row"];

export type DriverPresenceStatus =
  Database["public"]["Enums"]["driver_presence_status"];
export type DeliveryEtaSource =
  Database["public"]["Enums"]["delivery_eta_source"];
export type DeliveryCompletedVia =
  Database["public"]["Enums"]["delivery_completed_via"];
export type DeliveryFailureReason =
  Database["public"]["Enums"]["delivery_failure_reason"];

export function setDriverPresenceArgs(
  status: DriverPresenceStatus,
  opts?: {
    capacity?: number;
    shiftStartsAt?: string;
    shiftEndsAt?: string;
    lastLat?: number;
    lastLng?: number;
  },
) {
  return {
    p_status: status,
    p_capacity: opts?.capacity,
    p_shift_starts_at: opts?.shiftStartsAt,
    p_shift_ends_at: opts?.shiftEndsAt,
    p_last_lat: opts?.lastLat,
    p_last_lng: opts?.lastLng,
  } as const;
}

export function suggestDeliveryAssigneesArgs(jobId: string, limit = 5) {
  return { p_delivery_job_id: jobId, p_limit: limit } as const;
}

export function assignDeliveryJobArgs(
  jobId: string,
  assigneeUserId: string,
  override = false,
) {
  return {
    p_delivery_job_id: jobId,
    p_assignee_user_id: assigneeUserId,
    p_override: override,
  } as const;
}

export function setDeliveryJobGeoArgs(
  jobId: string,
  opts: {
    pickupLat?: number | null;
    pickupLng?: number | null;
    dropoffLat?: number | null;
    dropoffLng?: number | null;
  },
) {
  return {
    p_delivery_job_id: jobId,
    p_pickup_lat: opts.pickupLat ?? null,
    p_pickup_lng: opts.pickupLng ?? null,
    p_dropoff_lat: opts.dropoffLat ?? null,
    p_dropoff_lng: opts.dropoffLng ?? null,
  } as const;
}

export function updateDeliveryJobStatusArgs(
  jobId: string,
  status: Database["public"]["Enums"]["delivery_job_status"],
) {
  return {
    p_delivery_job_id: jobId,
    p_status: status,
  } as const;
}

export function getDeliveryTrackPointArgs(opts: {
  jobId?: string;
  token?: string;
}) {
  return {
    p_delivery_job_id: opts.jobId,
    p_token: opts.token,
  } as const;
}

export function submitDeliveryPodArgs(
  jobId: string,
  photoPath: string,
  signaturePath: string,
  otpCode: string,
  notes?: string,
) {
  return {
    p_delivery_job_id: jobId,
    p_pod_photo_path: photoPath,
    p_pod_signature_path: signaturePath,
    p_otp_code: otpCode,
    p_notes: notes,
  } as const;
}

export function generateDeliveryPodOtpArgs(jobId: string, ttl?: string) {
  return {
    p_delivery_job_id: jobId,
    p_ttl: ttl,
  } as const;
}

export function verifyDeliveryPodOtpArgs(jobId: string, code: string) {
  return {
    p_delivery_job_id: jobId,
    p_code: code,
  } as const;
}

export function deliveryGeofenceSuggestionArgs(
  jobId: string,
  lat: number,
  lng: number,
  opts?: { arriveRadiusM?: number; completeRadiusM?: number },
) {
  return {
    p_delivery_job_id: jobId,
    p_lat: lat,
    p_lng: lng,
    p_arrive_radius_m: opts?.arriveRadiusM,
    p_complete_radius_m: opts?.completeRadiusM,
  } as const;
}

export function failDeliveryJobArgs(
  jobId: string,
  reason: DeliveryFailureReason,
  opts?: { notes?: string; createReattempt?: boolean },
) {
  return {
    p_delivery_job_id: jobId,
    p_reason: reason,
    p_notes: opts?.notes,
    p_create_reattempt: opts?.createReattempt ?? false,
  } as const;
}

export function raiseDeliveryPanicArgs(opts?: {
  jobId?: string;
  lat?: number;
  lng?: number;
}) {
  return {
    p_delivery_job_id: opts?.jobId,
    p_lat: opts?.lat,
    p_lng: opts?.lng,
  } as const;
}

export function optimizeDriverStopsArgs(driverUserId: string) {
  return { p_driver_user_id: driverUserId } as const;
}

export function ingestDeliveryLocationArgs(
  jobId: string,
  lat: number,
  lng: number,
  recordedAt?: string,
  accuracyM?: number,
) {
  return {
    p_delivery_job_id: jobId,
    p_lat: lat,
    p_lng: lng,
    p_recorded_at: recordedAt,
    p_accuracy_m: accuracyM,
  } as const;
}
