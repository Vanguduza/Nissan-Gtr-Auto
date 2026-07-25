import type { SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";
import type { CurrencyCode } from "@/lib/staff-finance";

export type StaffOpsNotification = {
  id: string;
  kind: "sales_prep" | "driver_assigned";
  sales_invoice_id: string | null;
  delivery_job_id: string | null;
  title: string;
  body: string;
  read_at: string | null;
  created_at: string;
};

export type OnlinePrepQueueRow = {
  invoice_id: string;
  document_number: string | null;
  posted_at: string | null;
  currency: CurrencyCode;
  total: number;
  pick_list_id: string | null;
  pick_status: "draft" | "done" | "cancelled" | null;
  delivery_job_id: string | null;
  delivery_job_status:
    | "pending"
    | "dispatched"
    | "completed"
    | "failed"
    | null;
  assignee_user_id: string | null;
};

export async function listStaffOpsNotifications(
  client: SupabaseClient,
  limit = 40,
): Promise<StorefrontResult<StaffOpsNotification[]>> {
  const { data, error } = await client.rpc("list_staff_ops_notifications", {
    p_limit: limit,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as StaffOpsNotification[]) ?? [] };
}

export async function markStaffOpsNotificationRead(
  client: SupabaseClient,
  id: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("mark_staff_ops_notification_read", {
    p_id: id,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "mark read returned no id" };
  return { ok: true, data: data as string };
}

export async function listOnlinePrepQueue(
  client: SupabaseClient,
  limit = 50,
): Promise<StorefrontResult<OnlinePrepQueueRow[]>> {
  const { data, error } = await client.rpc("list_online_prep_queue", {
    p_limit: limit,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as OnlinePrepQueueRow[]) ?? [] };
}
