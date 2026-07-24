import type { SupabaseClient } from "@gtr/supabase-client";
import {
  requireSession,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export { requireSession };

type RealtimeChannel = ReturnType<SupabaseClient["channel"]>;

/** MapLibre demo style — OK for local demos; replace via NEXT_PUBLIC_MAP_STYLE_URL. */
export const MAP_STYLE_DEMO_URL =
  "https://demotiles.maplibre.org/style.json";

export function mapStyleUrl(): string {
  const fromEnv = process.env.NEXT_PUBLIC_MAP_STYLE_URL?.trim();
  return fromEnv || MAP_STYLE_DEMO_URL;
}

export type DeliveryJobOption = {
  id: string;
  document_number: string | null;
  delivery_note_id: string;
  status: string;
  assignee_user_id: string | null;
  eta_at: string | null;
  notes: string | null;
  created_at: string;
};

export type DeliveryLocationPoint = {
  id: string;
  delivery_job_id: string;
  lat: number;
  lng: number;
  accuracy_m: number | null;
  recorded_at: string;
  ingested_at: string;
  source: string;
};

const RECENT_POINTS_LIMIT = 500;

export async function listDeliveryJobs(
  client: SupabaseClient,
): Promise<StorefrontResult<DeliveryJobOption[]>> {
  const { data, error } = await client
    .from("delivery_jobs")
    .select(
      "id, document_number, delivery_note_id, status, assignee_user_id, eta_at, notes, created_at",
    )
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as DeliveryJobOption[]) ?? [] };
}

export async function fetchRecentDeliveryLocations(
  client: SupabaseClient,
  deliveryJobId: string,
): Promise<StorefrontResult<DeliveryLocationPoint[]>> {
  const { data, error } = await client
    .from("delivery_locations")
    .select(
      "id, delivery_job_id, lat, lng, accuracy_m, recorded_at, ingested_at, source",
    )
    .eq("delivery_job_id", deliveryJobId)
    .order("recorded_at", { ascending: true })
    .limit(RECENT_POINTS_LIMIT);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as DeliveryLocationPoint[]) ?? [] };
}

function isLocationPoint(value: unknown): value is DeliveryLocationPoint {
  if (!value || typeof value !== "object") return false;
  const row = value as Record<string, unknown>;
  return (
    typeof row.id === "string" &&
    typeof row.delivery_job_id === "string" &&
    typeof row.lat === "number" &&
    typeof row.lng === "number" &&
    typeof row.recorded_at === "string"
  );
}

/**
 * Subscribe to INSERT-only Realtime on delivery_locations for one job.
 * Web map SUBSCRIBES only — never captures GPS in the browser.
 */
export function subscribeDeliveryLocationInserts(
  client: SupabaseClient,
  deliveryJobId: string,
  onInsert: (point: DeliveryLocationPoint) => void,
): RealtimeChannel {
  const channel = client
    .channel(`delivery_locations:${deliveryJobId}`)
    .on(
      "postgres_changes",
      {
        event: "INSERT",
        schema: "public",
        table: "delivery_locations",
        filter: `delivery_job_id=eq.${deliveryJobId}`,
      },
      (payload) => {
        if (isLocationPoint(payload.new)) {
          onInsert(payload.new);
        }
      },
    )
    .subscribe();

  return channel;
}
