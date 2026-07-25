import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  requireSession,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export { requireSession };

export type FleetVehicleStatus =
  Database["public"]["Enums"]["fleet_vehicle_status"];

export type FleetVehicleRow =
  Database["public"]["Tables"]["fleet_vehicles"]["Row"];

export const FLEET_STATUSES: FleetVehicleStatus[] = [
  "active",
  "in_service",
  "retired",
];

export async function listFleetVehicles(
  client: SupabaseClient,
  status?: FleetVehicleStatus | null,
): Promise<StorefrontResult<FleetVehicleRow[]>> {
  const { data, error } = await client.rpc("list_fleet_vehicles", {
    p_status: status ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as FleetVehicleRow[]) ?? [] };
}

export async function upsertFleetVehicle(
  client: SupabaseClient,
  args: {
    plate: string;
    label?: string | null;
    status?: FleetVehicleStatus;
    assignedDriverUserId?: string | null;
    notes?: string | null;
    id?: string | null;
  },
): Promise<StorefrontResult<string>> {
  const assignee = args.assignedDriverUserId?.trim() || null;
  const { data, error } = await client.rpc("upsert_fleet_vehicle", {
    p_plate: args.plate,
    p_label: args.label?.trim() || undefined,
    p_status: args.status ?? "active",
    p_assigned_driver_user_id: assignee || undefined,
    p_notes: args.notes?.trim() || undefined,
    p_id: args.id || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "upsert_fleet_vehicle returned no id." };
  return { ok: true, data };
}

export async function setFleetVehicleStatus(
  client: SupabaseClient,
  id: string,
  status: FleetVehicleStatus,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("set_fleet_vehicle_status", {
    p_id: id,
    p_status: status,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "set_fleet_vehicle_status returned no id." };
  }
  return { ok: true, data };
}
