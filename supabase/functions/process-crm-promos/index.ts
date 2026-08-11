/**
 * Cron / worker: opt-in CRM promotional outreach.
 * AuthZ: x-worker-secret ↔ WORKER_SHARED_SECRET.
 * Candidates: marketing_opt_in + inactivity + cooldown; garage → OEM fitment.
 * Gemini copy fail-closed → deterministic template. No ZIMRA / payroll tax.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { assertWorkerSecret } from "../_shared/worker_auth.ts";
import {
  allowLocalChannelStub,
  jsonErr,
  jsonOk,
} from "../_shared/channel_env.ts";
import { getEmailSendConfig, sendEmail } from "../_shared/email_send.ts";
import {
  getBrevoSendConfig,
  sendBrevoEmail,
} from "../_shared/brevo_send.ts";
import { getSmsGatewayConfig, sendSms } from "../_shared/sms_gateway.ts";
import {
  getWhatsAppCloudConfig,
  sendWhatsAppText,
} from "../_shared/whatsapp_cloud.ts";
import {
  generatePromoCopy,
  templatePromoCopy,
} from "../_shared/gemini_narrative.ts";

type Channel = "email" | "whatsapp";

type Candidate = {
  customer_id: string;
  display_name: string;
  email?: string | null;
  phone_e164?: string | null;
  whatsapp_e164?: string | null;
  vehicle_label?: string | null;
  oem_skus?: string[] | null;
};

function serviceClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
}

function asCandidates(raw: unknown): Candidate[] {
  if (!raw || typeof raw !== "object") return [];
  const list = (raw as { candidates?: unknown }).candidates;
  if (!Array.isArray(list)) return [];
  return list.filter((c) => c && typeof c === "object") as Candidate[];
}

function channelsFromPayload(raw: unknown): Channel[] {
  if (!raw || typeof raw !== "object") return ["email"];
  const ch = (raw as { channels?: unknown }).channels;
  if (!Array.isArray(ch) || !ch.length) return ["email"];
  return ch.filter((c): c is Channel => c === "email" || c === "whatsapp");
}

Deno.serve(async (req) => {
  try {
    const denied = assertWorkerSecret(req);
    if (denied) return denied;

    if (req.method !== "POST") {
      return jsonErr("POST required", 405);
    }

    const body = await req.json().catch(() => ({}));
    const limit = Number(body?.limit ?? 25);
    const force = Boolean(body?.force);

    const supabase = serviceClient();
    const { data: runId, error: runErr } = await supabase.rpc(
      "insert_ai_promo_run",
    );
    if (runErr || !runId) {
      return jsonErr(runErr?.message ?? "insert_ai_promo_run failed", 400);
    }

    const { data: payload, error: listErr } = await supabase.rpc(
      "list_crm_promo_candidates",
      { p_limit: limit, p_force: force },
    );
    if (listErr) {
      await supabase.rpc("finalize_ai_promo_run", {
        p_run_id: runId,
        p_status: "failed",
        p_candidates: 0,
        p_queued: 0,
        p_gemini_used: false,
        p_error: listErr.message,
      });
      return jsonErr(listErr.message, 400);
    }

    if (
      payload && typeof payload === "object" &&
      (payload as { settings_active?: boolean }).settings_active === false
    ) {
      await supabase.rpc("finalize_ai_promo_run", {
        p_run_id: runId,
        p_status: "skipped",
        p_candidates: 0,
        p_queued: 0,
        p_gemini_used: false,
        p_error: "promo settings inactive",
      });
      return jsonOk({ run_id: runId, status: "skipped", reason: "inactive" });
    }

    const candidates = asCandidates(payload);
    const channels = channelsFromPayload(payload);
    const includeLlm =
      payload && typeof payload === "object"
        ? (payload as { include_llm_copy?: boolean }).include_llm_copy !== false
        : true;

    const localStub = allowLocalChannelStub();
    // Promo/CRM → Brevo (DIAL §4.6). Resend remains transactional fallback only.
    const brevoCfg = getBrevoSendConfig();
    const emailCfg = getEmailSendConfig();
    const smsCfg = getSmsGatewayConfig();
    const waCfg = getWhatsAppCloudConfig();

    let queued = 0;
    let geminiUsedAny = false;
    let sent = 0;
    let failed = 0;

    for (const c of candidates) {
      const oems = Array.isArray(c.oem_skus)
        ? c.oem_skus.filter((x): x is string => typeof x === "string")
        : [];
      const copyInput = {
        display_name: c.display_name || "Customer",
        vehicle_label: c.vehicle_label ?? null,
        oem_skus: oems,
      };

      let bodyText = templatePromoCopy(copyInput);
      let geminiUsed = false;
      if (includeLlm) {
        const nr = await generatePromoCopy(copyInput);
        if (nr.narrative) {
          bodyText = nr.narrative;
          geminiUsed = nr.gemini_used;
          geminiUsedAny = geminiUsedAny || geminiUsed;
        }
      }

      for (const channel of channels) {
        let recipient = "";
        let status: "sent" | "failed" | "skipped" = "skipped";
        let providerRef: string | null = null;
        let error: string | null = null;

        try {
          if (channel === "email") {
            recipient = (c.email ?? "").trim();
            if (!recipient) {
              status = "skipped";
              error = "no_email";
            } else if (localStub && !brevoCfg && !emailCfg) {
              status = "sent";
              providerRef = `stub-email-${crypto.randomUUID()}`;
            } else if (brevoCfg) {
              const res = await sendBrevoEmail(brevoCfg, {
                to: recipient,
                subject: "Parts suggestions from Nissan GTR Auto",
                text: bodyText,
              });
              status = "sent";
              providerRef = res.messageId
                ? `brevo:${res.messageId}`
                : `brevo:${crypto.randomUUID()}`;
            } else if (emailCfg) {
              // Temporary fallback while Brevo secrets roll out — prefer Brevo.
              const res = await sendEmail(emailCfg, {
                to: recipient,
                subject: "Parts suggestions from Nissan GTR Auto",
                text: bodyText,
              });
              status = "sent";
              providerRef = res.messageId
                ? `resend-fallback:${res.messageId}`
                : `resend-fallback:${crypto.randomUUID()}`;
            } else {
              status = "failed";
              error = "brevo_unconfigured";
            }
          } else {
            recipient = (c.whatsapp_e164 || c.phone_e164 || "").trim();
            if (!recipient) {
              status = "skipped";
              error = "no_whatsapp";
            } else if (localStub && !waCfg && !smsCfg) {
              status = "sent";
              providerRef = `stub-wa-${crypto.randomUUID()}`;
            } else if (waCfg) {
              const res = await sendWhatsAppText(
                waCfg,
                recipient,
                bodyText.slice(0, 3500),
              );
              status = "sent";
              providerRef = res.messageId;
            } else if (smsCfg) {
              const res = await sendSms(smsCfg, recipient, bodyText);
              status = "sent";
              providerRef = res.messageId;
            } else {
              status = "failed";
              error = "whatsapp_unconfigured";
            }
          }
        } catch (e) {
          status = "failed";
          error = String(e).slice(0, 500);
        }

        await supabase.rpc("insert_ai_promo_delivery", {
          p_run_id: runId,
          p_customer_id: c.customer_id,
          p_channel: channel,
          p_recipient: recipient || "(none)",
          p_status: status,
          p_body_preview: bodyText.slice(0, 500),
          p_oem_skus: oems,
          p_vehicle_label: c.vehicle_label ?? null,
          p_provider_ref: providerRef,
          p_error: error,
          p_gemini_used: geminiUsed,
        });

        queued += 1;
        if (status === "sent") sent += 1;
        if (status === "failed") failed += 1;
      }
    }

    let status: "succeeded" | "partial" | "failed" | "skipped" = "succeeded";
    if (candidates.length === 0) status = "skipped";
    else if (failed > 0 && sent > 0) status = "partial";
    else if (failed > 0 && sent === 0) status = "failed";

    await supabase.rpc("finalize_ai_promo_run", {
      p_run_id: runId,
      p_status: status,
      p_candidates: candidates.length,
      p_queued: queued,
      p_gemini_used: geminiUsedAny,
      p_error: null,
    });

    return jsonOk({
      run_id: runId,
      status,
      candidates: candidates.length,
      queued,
      sent,
      failed,
      gemini_used: geminiUsedAny,
    });
  } catch (e) {
    return jsonErr(String(e), 500);
  }
});
