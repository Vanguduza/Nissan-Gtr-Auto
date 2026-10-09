import type { SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";

/** One person's own phone alerts (texted through the SMS outbox). */
export type AlertSettings = {
  phoneE164: string | null;
  channel: "sms" | "whatsapp";
  urgentApprovals: boolean;
  morningSummary: boolean;
  /** Managers, finance and admin get the 07:00 summary; others can switch it on but receive nothing. */
  canGetSummary: boolean;
  saved: boolean;
};

function rpc(client: SupabaseClient, fn: string, args: Record<string, unknown>) {
  return (client as unknown as {
    rpc: (name: string, params: Record<string, unknown>) => PromiseLike<{ data: unknown; error: { message: string } | null }>;
  }).rpc(fn, args);
}

function from(r: Record<string, unknown>): AlertSettings {
  return {
    phoneE164: typeof r.phone_e164 === "string" && r.phone_e164 ? r.phone_e164 : null,
    channel: r.channel === "whatsapp" ? "whatsapp" : "sms",
    urgentApprovals: Boolean(r.urgent_approvals),
    morningSummary: Boolean(r.morning_summary),
    canGetSummary: Boolean(r.can_get_summary),
    saved: Boolean(r.saved),
  };
}

export async function getMyAlertSettings(client: SupabaseClient): Promise<StorefrontResult<AlertSettings>> {
  const { data, error } = await rpc(client, "get_my_alert_settings", {});
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: from((data ?? {}) as Record<string, unknown>) };
}

export async function setMyAlertSettings(client: SupabaseClient, s: Omit<AlertSettings, "canGetSummary" | "saved">): Promise<StorefrontResult<AlertSettings>> {
  const { data, error } = await rpc(client, "set_my_alert_settings", {
    p_phone_e164: s.phoneE164?.trim() || null,
    p_channel: s.channel,
    p_urgent_approvals: s.urgentApprovals,
    p_morning_summary: s.morningSummary,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: from((data ?? {}) as Record<string, unknown>) };
}
