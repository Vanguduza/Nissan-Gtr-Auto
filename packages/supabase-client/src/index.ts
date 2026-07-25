import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import type { Database } from "./database.types.js";

/**
 * Browser/mobile anon client. Never ship the service_role key here.
 */
export function createBrowserClient(
  url: string,
  anonKey: string,
): SupabaseClient<Database> {
  if (!url || !anonKey) {
    throw new Error("Supabase URL and anon key are required");
  }
  return createClient<Database>(url, anonKey, {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
    },
  });
}

/** Admin RPC args — call via `supabase.rpc('assign_staff_role', …)`. */
export function assignStaffRoleArgs(userId: string, role: Database["public"]["Enums"]["staff_role"]) {
  return { p_user_id: userId, p_role: role } as const;
}

export function revokeStaffRoleArgs(userId: string, role: Database["public"]["Enums"]["staff_role"]) {
  return { p_user_id: userId, p_role: role } as const;
}

export type { Database, SupabaseClient };
export type StaffRole = Database["public"]["Enums"]["staff_role"];
export type ProcurementDocStatus = Database["public"]["Enums"]["procurement_doc_status"];
export type AiReportCadence = Database["public"]["Enums"]["ai_report_cadence"];
export type AiDeliveryChannel = Database["public"]["Enums"]["ai_delivery_channel"];

/** Staff analytics subscription row — CRUD via table RLS (admin|finance|sales). */
export type AiReportSubscriptionRow =
  Database["public"]["Tables"]["ai_report_subscriptions"]["Row"];
export type AiReportRunRow = Database["public"]["Tables"]["ai_report_runs"]["Row"];
export type AiReportDeliveryRow =
  Database["public"]["Tables"]["ai_report_deliveries"]["Row"];

/** Call `kpi_ops_sales_v1` / edge `analytics-insights` for staff KPI + narrative. */
export function kpiOpsSalesV1Args(fromIso: string, toIso: string, topLimit = 10) {
  return { p_from: fromIso, p_to: toIso, p_top_limit: topLimit } as const;
}

/** Staff: `create_rfq` → `submit_rfq`; suppliers: `upsert_supplier_quotation` → `submit_supplier_quotation`; award: `award_quotation_to_po`. */
export type RfqRow = Database["public"]["Tables"]["rfqs"]["Row"];
export type SupplierQuotationRow = Database["public"]["Tables"]["supplier_quotations"]["Row"];

export {
  CHAT_REALTIME_TABLES,
  CHAT_RPC,
  CHAT_THREAD_KINDS,
  CHAT_THREAD_STATUSES,
  CHAT_SENDER_KINDS,
  CHAT_PARTICIPANT_ROLES,
  CHAT_STAFF_ROLES,
  startChatThreadArgs,
  claimChatThreadArgs,
  closeChatThreadArgs,
  markChatThreadReadArgs,
  postChatMessageArgs,
  chatUnreadCountArgs,
  isChatStaffRole,
  defaultChatThreadKind,
  type ChatRealtimeTable,
  type ChatThreadRow,
  type ChatMessageRow,
  type ChatParticipantRow,
  type ChatThread,
  type ChatMessage,
  type ChatParticipant,
  type ChatThreadKind,
  type ChatThreadStatus,
  type ChatSenderKind,
  type ChatParticipantRole,
  type ChatStaffRole,
  type StartChatThreadInput,
} from "./chat.js";

export {
  DELIVERY_RPC,
  setDriverPresenceArgs,
  suggestDeliveryAssigneesArgs,
  assignDeliveryJobArgs,
  getDeliveryTrackPointArgs,
  submitDeliveryPodArgs,
  ingestDeliveryLocationArgs,
  type DriverPresenceRow,
  type DeliveryJobRow,
  type DeliveryTrackTokenRow,
  type DriverPresenceStatus,
  type DeliveryEtaSource,
  type DeliveryCompletedVia,
} from "./delivery.js";
