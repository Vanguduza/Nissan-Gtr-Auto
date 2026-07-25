/**
 * Cron / worker: due AI report subscriptions → KPI aggregates → optional Gemini
 * → email + WhatsApp. Numeric-only delivery when Gemini absent.
 *
 * AuthZ: x-worker-secret ↔ WORKER_SHARED_SECRET (see _shared/worker_auth.ts).
 * No ZIMRA / payroll tax / PII dumps to Gemini.
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
  getWhatsAppCloudConfig,
  sendWhatsAppText,
} from "../_shared/whatsapp_cloud.ts";
import { generateKpiNarrative } from "../_shared/gemini_narrative.ts";

type Cadence = "daily" | "weekly" | "monthly";
type Channel = "email" | "whatsapp";

type Subscription = {
  id: string;
  cadence: Cadence;
  channels: Channel[];
  recipient_emails: string[];
  recipient_whatsapp_e164: string[];
  include_narrative: boolean;
  kpi_set: string;
  timezone: string;
};

function serviceClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
}

function getReportEmailConfig() {
  const cfg = getEmailSendConfig();
  if (!cfg) return null;
  const reportFrom = Deno.env.get("REPORT_FROM_EMAIL")?.trim();
  if (reportFrom) return { ...cfg, from: reportFrom };
  return cfg;
}

function periodForCadence(
  cadence: Cadence,
  now = new Date(),
): { from: string; to: string } {
  const to = now;
  const from = new Date(to);
  if (cadence === "weekly") from.setUTCDate(from.getUTCDate() - 7);
  else if (cadence === "monthly") from.setUTCDate(from.getUTCDate() - 30);
  else from.setUTCDate(from.getUTCDate() - 1);
  return { from: from.toISOString(), to: to.toISOString() };
}

function formatNumericBody(kpis: unknown, narrative: string | null): string {
  const lines: string[] = [
    "Nissan GTR Auto — staff ops report",
    "(aggregates only; tax-agnostic)",
    "",
  ];
  if (narrative) {
    lines.push(narrative, "");
  } else {
    lines.push("(Numeric only — AI narrative unavailable)", "");
  }
  lines.push(JSON.stringify(kpis, null, 2));
  return lines.join("\n");
}

async function deliverChannels(
  supabase: SupabaseClient,
  runId: string,
  sub: Subscription,
  bodyText: string,
  subject: string,
): Promise<{ sent: number; failed: number; skipped: number }> {
  let sent = 0;
  let failed = 0;
  let skipped = 0;
  const localStub = allowLocalChannelStub();
  const emailCfg = getReportEmailConfig();
  const waCfg = getWhatsAppCloudConfig();
  const channels = sub.channels ?? [];

  const wantEmail = channels.includes("email");
  const wantWa = channels.includes("whatsapp");

  if (wantEmail) {
    const recipients = (sub.recipient_emails ?? []).map((e) => e.trim())
      .filter(Boolean);
    if (!recipients.length) {
      await supabase.rpc("insert_ai_report_delivery", {
        p_run_id: runId,
        p_channel: "email",
        p_recipient: "(none)",
        p_status: "skipped",
        p_error: "no recipient_emails",
      });
      skipped++;
    } else if (!emailCfg && !localStub) {
      for (const to of recipients) {
        await supabase.rpc("insert_ai_report_delivery", {
          p_run_id: runId,
          p_channel: "email",
          p_recipient: to,
          p_status: "failed",
          p_error:
            "EMAIL_API_KEY / EMAIL_FROM (or REPORT_FROM_EMAIL) required",
        });
        failed++;
      }
    } else {
      for (const to of recipients) {
        try {
          let providerRef: string | null = null;
          if (localStub && !emailCfg) {
            providerRef = `stub-email-${crypto.randomUUID()}`;
          } else if (emailCfg) {
            const result = await sendEmail(emailCfg, {
              to,
              subject,
              text: bodyText,
            });
            providerRef = result.messageId;
          }
          await supabase.rpc("insert_ai_report_delivery", {
            p_run_id: runId,
            p_channel: "email",
            p_recipient: to,
            p_status: "sent",
            p_provider_ref: providerRef,
          });
          sent++;
        } catch (e) {
          await supabase.rpc("insert_ai_report_delivery", {
            p_run_id: runId,
            p_channel: "email",
            p_recipient: to,
            p_status: "failed",
            p_error: String(e).slice(0, 500),
          });
          failed++;
        }
      }
    }
  }

  if (wantWa) {
    const recipients = (sub.recipient_whatsapp_e164 ?? []).map((e) =>
      e.trim()
    ).filter(Boolean);
    if (!recipients.length) {
      await supabase.rpc("insert_ai_report_delivery", {
        p_run_id: runId,
        p_channel: "whatsapp",
        p_recipient: "(none)",
        p_status: "skipped",
        p_error: "no recipient_whatsapp_e164",
      });
      skipped++;
    } else if (!waCfg && !localStub) {
      for (const to of recipients) {
        await supabase.rpc("insert_ai_report_delivery", {
          p_run_id: runId,
          p_channel: "whatsapp",
          p_recipient: to,
          p_status: "failed",
          p_error: "WHATSAPP_ACCESS_TOKEN / WHATSAPP_PHONE_NUMBER_ID required",
        });
        failed++;
      }
    } else {
      // WhatsApp text body cap — keep numeric summary short
      const waText = bodyText.length > 3500
        ? bodyText.slice(0, 3490) + "…"
        : bodyText;
      for (const to of recipients) {
        try {
          let providerRef: string | null = null;
          if (localStub && !waCfg) {
            providerRef = `stub-wa-${crypto.randomUUID()}`;
          } else if (waCfg) {
            const result = await sendWhatsAppText(waCfg, to, waText);
            providerRef = result.messageId;
          }
          await supabase.rpc("insert_ai_report_delivery", {
            p_run_id: runId,
            p_channel: "whatsapp",
            p_recipient: to,
            p_status: "sent",
            p_provider_ref: providerRef,
          });
          sent++;
        } catch (e) {
          await supabase.rpc("insert_ai_report_delivery", {
            p_run_id: runId,
            p_channel: "whatsapp",
            p_recipient: to,
            p_status: "failed",
            p_error: String(e).slice(0, 500),
          });
          failed++;
        }
      }
    }
  }

  return { sent, failed, skipped };
}

Deno.serve(async (req) => {
  try {
    const denied = assertWorkerSecret(req);
    if (denied) return denied;

    if (req.method !== "POST") {
      return jsonErr("POST required", 405);
    }

    const body = await req.json().catch(() => ({}));
    const cadence = body?.cadence as Cadence | undefined;
    const force = Boolean(body?.force);
    const subscriptionId = (body?.subscription_id as string | undefined) ??
      null;

    if (!cadence || !["daily", "weekly", "monthly"].includes(cadence)) {
      return jsonErr("cadence must be daily|weekly|monthly", 400);
    }

    const supabase = serviceClient();
    const { data: subs, error: listErr } = await supabase.rpc(
      "list_due_ai_report_subscriptions",
      {
        p_cadence: cadence,
        p_now: new Date().toISOString(),
        p_force: force,
        p_subscription_id: subscriptionId,
      },
    );

    if (listErr) {
      return jsonErr(listErr.message, 400);
    }

    const due = (subs ?? []) as Subscription[];
    const results: unknown[] = [];
    const period = periodForCadence(cadence);

    for (const sub of due) {
      const { data: runId, error: runErr } = await supabase.rpc(
        "insert_ai_report_run",
        {
          p_subscription_id: sub.id,
          p_cadence: cadence,
          p_period_start: period.from,
          p_period_end: period.to,
        },
      );
      if (runErr || !runId) {
        results.push({
          subscription_id: sub.id,
          error: runErr?.message ?? "insert_ai_report_run failed",
        });
        continue;
      }

      try {
        const kpiSet = sub.kpi_set || "ops_sales_v1";
        if (kpiSet !== "ops_sales_v1") {
          await supabase.rpc("finalize_ai_report_run", {
            p_run_id: runId,
            p_status: "failed",
            p_error: `unsupported kpi_set: ${kpiSet}`,
            p_gemini_used: false,
          });
          results.push({ subscription_id: sub.id, run_id: runId, status: "failed" });
          continue;
        }

        const { data: kpis, error: kpiErr } = await supabase.rpc(
          "kpi_ops_sales_v1",
          {
            p_from: period.from,
            p_to: period.to,
            p_top_limit: 10,
          },
        );
        if (kpiErr) {
          await supabase.rpc("finalize_ai_report_run", {
            p_run_id: runId,
            p_status: "failed",
            p_error: kpiErr.message,
            p_gemini_used: false,
          });
          results.push({
            subscription_id: sub.id,
            run_id: runId,
            status: "failed",
            error: kpiErr.message,
          });
          continue;
        }

        let narrative: string | null = null;
        let geminiUsed = false;
        let narrativeError: string | null = null;
        if (sub.include_narrative) {
          const nr = await generateKpiNarrative(kpis);
          narrative = nr.narrative;
          geminiUsed = nr.gemini_used;
          narrativeError = nr.error;
          // Soft-fail narrative — still deliver numeric body
        }

        const subject =
          `GTR ops report (${cadence}) ${period.from.slice(0, 10)} → ${
            period.to.slice(0, 10)
          }`;
        const bodyText = formatNumericBody(kpis, narrative);
        const delivery = await deliverChannels(
          supabase,
          runId as string,
          sub,
          bodyText,
          subject,
        );

        let status: "succeeded" | "partial" | "failed" = "succeeded";
        if (delivery.failed > 0 && delivery.sent > 0) status = "partial";
        else if (delivery.failed > 0 && delivery.sent === 0) status = "failed";
        else if (delivery.sent === 0 && delivery.skipped > 0) status = "partial";

        await supabase.rpc("finalize_ai_report_run", {
          p_run_id: runId,
          p_status: status,
          p_kpi_json: kpis,
          p_narrative: narrative,
          p_error: narrativeError,
          p_gemini_used: geminiUsed,
        });

        results.push({
          subscription_id: sub.id,
          run_id: runId,
          status,
          gemini_used: geminiUsed,
          delivery,
        });
      } catch (e) {
        await supabase.rpc("finalize_ai_report_run", {
          p_run_id: runId,
          p_status: "failed",
          p_error: String(e).slice(0, 500),
          p_gemini_used: false,
        });
        results.push({
          subscription_id: sub.id,
          run_id: runId,
          status: "failed",
          error: String(e),
        });
      }
    }

    return jsonOk({
      cadence,
      force,
      processed: due.length,
      results,
    });
  } catch (e) {
    return jsonErr(String(e), 500);
  }
});
