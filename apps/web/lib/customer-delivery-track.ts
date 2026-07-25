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
    typeof row.status === "string"
  );
}

/**
 * Public token track via SECURITY DEFINER RPC (anon allowed).
 * Returns at most one last point for an active dispatched job.
 */
export async function fetchCustomerTrackPoint(
  client: SupabaseClient,
  token: string,
): Promise<StorefrontResult<CustomerTrackPoint | null>> {
  const trimmed = token.trim();
  if (!trimmed || trimmed.length < 8) {
    return { ok: false, error: "Invalid track token." };
  }

  const { data, error } = await client.rpc(
    DELIVERY_RPC.getTrackPoint,
    getDeliveryTrackPointArgs({ token: trimmed }),
  );
  if (error) return { ok: false, error: error.message };

  const rows = (data ?? []) as unknown[];
  if (rows.length === 0) return { ok: true, data: null };
  const first = rows[0];
  if (!isTrackPoint(first)) {
    return { ok: false, error: "Unexpected track point shape." };
  }
  return { ok: true, data: first };
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
