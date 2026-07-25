/**
 * Dedicated delivery app RPC helpers + row types.
 * Plan: docs/plans/2026-07-25-dedicated-delivery-app.md
 */
import type { Database } from "./database.types.js";

export const DELIVERY_RPC = {
  setDriverPresence: "set_driver_presence",
  suggestAssignees: "suggest_delivery_assignees",
  assignJob: "assign_delivery_job",
  ingestLocation: "ingest_delivery_location",
  getTrackPoint: "get_delivery_track_point",
  submitPod: "submit_delivery_pod",
  mintTrackToken: "mint_delivery_track_token",
  updateJobStatus: "update_delivery_job_status",
} as const;

export type DriverPresenceRow =
  Database["public"]["Tables"]["driver_presence"]["Row"];
export type DeliveryJobRow = Database["public"]["Tables"]["delivery_jobs"]["Row"];
export type DeliveryTrackTokenRow =
  Database["public"]["Tables"]["delivery_track_tokens"]["Row"];

export type DriverPresenceStatus =
  Database["public"]["Enums"]["driver_presence_status"];
export type DeliveryEtaSource =
  Database["public"]["Enums"]["delivery_eta_source"];
export type DeliveryCompletedVia =
  Database["public"]["Enums"]["delivery_completed_via"];

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
  notes?: string,
) {
  return {
    p_delivery_job_id: jobId,
    p_pod_photo_path: photoPath,
    p_pod_signature_path: signaturePath,
    p_notes: notes,
  } as const;
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
