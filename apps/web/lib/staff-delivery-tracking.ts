import {
  DELIVERY_RPC,
  assignDeliveryJobArgs,
  getDeliveryTrackPointArgs,
  optimizeDriverStopsArgs,
  setDeliveryJobGeoArgs,
  suggestDeliveryAssigneesArgs,
  updateDeliveryJobStatusArgs,
  type SupabaseClient,
  type UpdateDeliveryJobStatusResult,
} from "@gtr/supabase-client";
import {
  requireSession,
  type StorefrontResult,
} from "@/lib/customer-storefront";
import {
  configuredMapStyleUrl,
  fetchCustomerTrackPoint,
  type CustomerTrackPoint,
} from "@/lib/customer-delivery-track";

export { requireSession, configuredMapStyleUrl };
export type { UpdateDeliveryJobStatusResult };

type RealtimeChannel = ReturnType<SupabaseClient["channel"]>;

/** MapLibre demo style — OK for local staff demos; replace via NEXT_PUBLIC_MAP_STYLE_URL. */
export const MAP_STYLE_DEMO_URL =
  "https://demotiles.maplibre.org/style.json";

export function mapStyleUrl(): string {
  return configuredMapStyleUrl() || MAP_STYLE_DEMO_URL;
}

export type DeliveryJobOption = {
  id: string;
  document_number: string | null;
  delivery_note_id: string;
  status: string;
  assignee_user_id: string | null;
  eta_at: string | null;
  eta_seconds: number | null;
  eta_source: string | null;
  pickup_lat: number | null;
  pickup_lng: number | null;
  dropoff_lat: number | null;
  dropoff_lng: number | null;
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

export type AssigneeSuggestion = {
  user_id: string;
  status: string;
  capacity: number;
  open_jobs: number;
  distance_m: number | null;
  last_lat: number | null;
  last_lng: number | null;
  last_seen_at: string | null;
};

export type PanicEventRow = {
  id: string;
  driver_user_id: string;
  delivery_job_id: string | null;
  lat: number | null;
  lng: number | null;
  created_at: string;
  acknowledged_at: string | null;
  acknowledged_by: string | null;
};

const RECENT_POINTS_LIMIT = 500;

export async function listDeliveryJobs(
  client: SupabaseClient,
): Promise<StorefrontResult<DeliveryJobOption[]>> {
  const { data, error } = await client
    .from("delivery_jobs")
    .select(
      "id, document_number, delivery_note_id, status, assignee_user_id, eta_at, eta_seconds, eta_source, pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, notes, created_at",
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

export async function suggestDeliveryAssignees(
  client: SupabaseClient,
  deliveryJobId: string,
  limit = 5,
): Promise<StorefrontResult<AssigneeSuggestion[]>> {
  const { data, error } = await client.rpc(
    DELIVERY_RPC.suggestAssignees,
    suggestDeliveryAssigneesArgs(deliveryJobId, limit),
  );
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as AssigneeSuggestion[]) ?? [] };
}

export async function assignDeliveryJob(
  client: SupabaseClient,
  deliveryJobId: string,
  assigneeUserId: string,
  override = false,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc(
    DELIVERY_RPC.assignJob,
    assignDeliveryJobArgs(deliveryJobId, assigneeUserId, override),
  );
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "assign_delivery_job returned no id." };
  return { ok: true, data: data as string };
}

function parseUpdateDeliveryJobStatusResult(
  raw: unknown,
): UpdateDeliveryJobStatusResult | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  if (typeof o.delivery_job_id !== "string") return null;
  let track_token: string | null = null;
  if (typeof o.track_token === "string" && o.track_token.length > 0) {
    track_token = o.track_token;
  }
  return { delivery_job_id: o.delivery_job_id, track_token };
}

/**
 * Mark job dispatched → backend mints ONE track token (returned in jsonb) +
 * enqueues SMS outbox. Do NOT call mint_delivery_track_token after this —
 * reminting revokes the share/SMS token.
 */
export async function dispatchDeliveryJob(
  client: SupabaseClient,
  deliveryJobId: string,
): Promise<StorefrontResult<UpdateDeliveryJobStatusResult>> {
  const { data, error } = await client.rpc(
    DELIVERY_RPC.updateJobStatus,
    updateDeliveryJobStatusArgs(deliveryJobId, "dispatched"),
  );
  if (error) return { ok: false, error: error.message };
  const parsed = parseUpdateDeliveryJobStatusResult(data);
  if (!parsed) {
    return {
      ok: false,
      error: "update_delivery_job_status returned unexpected shape.",
    };
  }
  return { ok: true, data: parsed };
}

export async function setDeliveryJobGeo(
  client: SupabaseClient,
  deliveryJobId: string,
  opts: {
    pickupLat?: number | null;
    pickupLng?: number | null;
    dropoffLat?: number | null;
    dropoffLng?: number | null;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc(
    DELIVERY_RPC.setJobGeo,
    setDeliveryJobGeoArgs(deliveryJobId, opts),
  );
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "set_delivery_job_geo returned no id." };
  return { ok: true, data: data as string };
}

/**
 * Privacy-safe last point + ETA for staff (dispatched jobs). Prefer trail
 * Realtime for the map path; use this for ETA refresh / last-point when needed.
 */
export async function fetchStaffTrackPoint(
  client: SupabaseClient,
  deliveryJobId: string,
): Promise<StorefrontResult<CustomerTrackPoint | null>> {
  const { fetchCustomerTrackPoint } = await import(
    "@/lib/customer-delivery-track"
  );
  return fetchCustomerTrackPoint(client, { jobId: deliveryJobId });
}

export async function optimizeDriverStops(
  client: SupabaseClient,
  driverUserId: string,
): Promise<
  StorefrontResult<
    Array<{
      delivery_job_id: string;
      route_sequence: number;
      distance_m: number | null;
    }>
  >
> {
  const { data, error } = await client.rpc(
    DELIVERY_RPC.optimizeStops,
    optimizeDriverStopsArgs(driverUserId),
  );
  if (error) return { ok: false, error: error.message };
  return {
    ok: true,
    data:
      (data as Array<{
        delivery_job_id: string;
        route_sequence: number;
        distance_m: number | null;
      }>) ?? [],
  };
}

export async function listPanicEvents(
  client: SupabaseClient,
  opts?: { unackedOnly?: boolean; limit?: number },
): Promise<StorefrontResult<PanicEventRow[]>> {
  let q = client
    .from("panic_events")
    .select(
      "id, driver_user_id, delivery_job_id, lat, lng, created_at, acknowledged_at, acknowledged_by",
    )
    .order("created_at", { ascending: false })
    .limit(opts?.limit ?? 40);
  if (opts?.unackedOnly) {
    q = q.is("acknowledged_at", null);
  }
  const { data, error } = await q;
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as PanicEventRow[]) ?? [] };
}

export async function acknowledgePanicEvent(
  client: SupabaseClient,
  panicId: string,
  staffUserId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client
    .from("panic_events")
    .update({
      acknowledged_at: new Date().toISOString(),
      acknowledged_by: staffUserId,
    })
    .eq("id", panicId)
    .is("acknowledged_at", null)
    .select("id")
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  if (!data?.id) {
    return { ok: false, error: "Panic already acknowledged or not found." };
  }
  return { ok: true, data: data.id };
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

function isPanicEvent(value: unknown): value is PanicEventRow {
  if (!value || typeof value !== "object") return false;
  const row = value as Record<string, unknown>;
  return (
    typeof row.id === "string" &&
    typeof row.driver_user_id === "string" &&
    typeof row.created_at === "string"
  );
}

/**
 * Build an INSERT-only Realtime channel for delivery_locations (one job).
 * Caller must `.subscribe()` / `removeChannel`. Web map SUBSCRIBES only —
 * never captures GPS in the browser.
 */
export function deliveryLocationInsertChannel(
  client: SupabaseClient,
  deliveryJobId: string,
  onInsert: (point: DeliveryLocationPoint) => void,
): RealtimeChannel {
  return client
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
    );
}

/** Staff panic inbox — Realtime INSERT (and UPDATE for ack from other clients). */
export function panicEventsChannel(
  client: SupabaseClient,
  handlers: {
    onInsert?: (row: PanicEventRow) => void;
    onUpdate?: (row: PanicEventRow) => void;
  },
): RealtimeChannel {
  let channel = client.channel("panic_events:staff");
  if (handlers.onInsert) {
    channel = channel.on(
      "postgres_changes",
      { event: "INSERT", schema: "public", table: "panic_events" },
      (payload) => {
        if (isPanicEvent(payload.new)) handlers.onInsert?.(payload.new);
      },
    );
  }
  if (handlers.onUpdate) {
    channel = channel.on(
      "postgres_changes",
      { event: "UPDATE", schema: "public", table: "panic_events" },
      (payload) => {
        if (isPanicEvent(payload.new)) handlers.onUpdate?.(payload.new);
      },
    );
  }
  return channel;
}
