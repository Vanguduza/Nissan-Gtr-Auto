/**
 * POS companion Realtime channels (Batch 1 §1.5).
 * Tablet SUBSCRIBES only — never captures camera in the browser.
 * Pattern mirrors deliveryLocationInsertChannel in staff-delivery-tracking.ts.
 */
import type { SupabaseClient } from "@gtr/supabase-client";

type RealtimeChannel = ReturnType<SupabaseClient["channel"]>;

export type PosCartLineChange = {
  id: string;
  cart_id: string;
};

export type PosScanSessionChange = {
  id: string;
  cart_id: string;
  status: string;
};

function isCartLineRow(value: unknown): value is PosCartLineChange {
  if (!value || typeof value !== "object") return false;
  const row = value as Record<string, unknown>;
  return typeof row.id === "string" && typeof row.cart_id === "string";
}

function isScanSessionRow(value: unknown): value is PosScanSessionChange {
  if (!value || typeof value !== "object") return false;
  const row = value as Record<string, unknown>;
  return (
    typeof row.id === "string" &&
    typeof row.cart_id === "string" &&
    typeof row.status === "string"
  );
}

/**
 * INSERT/UPDATE/DELETE on pos_cart_lines for one cart.
 * Caller must `.subscribe()` / `removeChannel`.
 */
export function posCartLinesChannel(
  client: SupabaseClient,
  cartId: string,
  onChange: (row: PosCartLineChange) => void,
): RealtimeChannel {
  return client
    .channel(`pos_cart_lines:${cartId}`)
    .on(
      "postgres_changes",
      {
        event: "*",
        schema: "public",
        table: "pos_cart_lines",
        filter: `cart_id=eq.${cartId}`,
      },
      (payload) => {
        const row = payload.new ?? payload.old;
        if (isCartLineRow(row)) onChange(row);
      },
    );
}

/**
 * INSERT/UPDATE on pos_scan_sessions for one cart (pair / revoke).
 */
export function posScanSessionsChannel(
  client: SupabaseClient,
  cartId: string,
  onChange: (row: PosScanSessionChange) => void,
): RealtimeChannel {
  return client
    .channel(`pos_scan_sessions:${cartId}`)
    .on(
      "postgres_changes",
      {
        event: "*",
        schema: "public",
        table: "pos_scan_sessions",
        filter: `cart_id=eq.${cartId}`,
      },
      (payload) => {
        const row = payload.new ?? payload.old;
        if (isScanSessionRow(row)) onChange(row);
      },
    );
}
