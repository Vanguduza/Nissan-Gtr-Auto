import {
  DELIVERY_RPC,
  getDeliveryTrackPointArgs,
  type SupabaseClient,
} from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";

/** Privacy-safe last point only — never a historical trail. */
export type CustomerTrackPoint = {
  delivery_job_id: string;
  lat: number;
  lng: number;
  recorded_at: string;
  eta_at: string | null;
  eta_seconds: number | null;
  status: string;
};

export function configuredMapStyleUrl(): string | null {
  const fromEnv = process.env.NEXT_PUBLIC_MAP_STYLE_URL?.trim();
  return fromEnv || null;
}

function isTrackPoint(value: unknown): value is CustomerTrackPoint {
  if (!value || typeof value !== "object") return false;
  const row = value as Record<string, unknown>;
  return (
    typeof row.delivery_job_id === "string" &&
    typeof row.lat === "number" &&
    typeof row.lng === "number" &&
    typeof row.recorded_at === "string" &&
    typeof row.status === "string" &&
    (row.eta_at == null || typeof row.eta_at === "string") &&
    (row.eta_seconds == null || typeof row.eta_seconds === "number")
  );
}

function normalizeTrackPoint(row: CustomerTrackPoint): CustomerTrackPoint {
  return {
    delivery_job_id: row.delivery_job_id,
    lat: row.lat,
    lng: row.lng,
    recorded_at: row.recorded_at,
    eta_at: row.eta_at ?? null,
    eta_seconds: row.eta_seconds ?? null,
    status: row.status,
  };
}

/**
 * Last point + ETA via SECURITY DEFINER RPC.
 * - Share token: anon/authenticated (deep link `/track/[token]`)
 * - Job id: authenticated owner, assignee, or staff
 * Never returns a historical trail.
 */
export async function fetchCustomerTrackPoint(
  client: SupabaseClient,
  opts: { token?: string; jobId?: string },
): Promise<StorefrontResult<CustomerTrackPoint | null>> {
  const token = opts.token?.trim();
  const jobId = opts.jobId?.trim();

  if (!token && !jobId) {
    return { ok: false, error: "Track token or delivery job id required." };
  }
  if (token && token.length < 8) {
    return { ok: false, error: "Invalid track token." };
  }

  const { data, error } = await client.rpc(
    DELIVERY_RPC.getTrackPoint,
    getDeliveryTrackPointArgs(
      token ? { token } : { jobId: jobId as string },
    ),
  );
  if (error) return { ok: false, error: error.message };

  const rows = (data ?? []) as unknown[];
  if (rows.length === 0) return { ok: true, data: null };
  const first = rows[0];
  if (!isTrackPoint(first)) {
    return { ok: false, error: "Unexpected track point shape." };
  }
  return { ok: true, data: normalizeTrackPoint(first) };
}

export function formatEtaLabel(
  etaAt: string | null,
  etaSeconds: number | null,
): string | null {
  if (etaAt) {
    try {
      return new Date(etaAt).toLocaleString();
    } catch {
      /* fall through */
    }
  }
  if (etaSeconds != null && Number.isFinite(etaSeconds) && etaSeconds >= 0) {
    const mins = Math.round(etaSeconds / 60);
    if (mins < 1) return "Less than a minute";
    if (mins < 60) return `About ${mins} min`;
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    return m ? `About ${h} h ${m} min` : `About ${h} h`;
  }
  return null;
}
