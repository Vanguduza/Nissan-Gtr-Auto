/**
 * Drain manager sms_outbox via real SMS gateway (or local stub).
 *
 * AuthZ: x-worker-secret ↔ WORKER_SHARED_SECRET.
 * When SMS_GATEWAY_API_KEY present: claim → send → complete (never stub:true on failure).
 * When absent: 503 fail-closed UNLESS WORKER_ALLOW_UNVERIFIED_LOCAL=1 with
 * WORKER_SHARED_SECRET unset (local stub via drain_sms_outbox_batch).
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { assertWorkerSecret } from "../_shared/worker_auth.ts";
import {
  allowLocalChannelStub,
  jsonErr,
  jsonOk,
} from "../_shared/channel_env.ts";
import { getSmsGatewayConfig, sendSms } from "../_shared/sms_gateway.ts";

type SmsOutboxRow = {
  id: string;
  phone_e164: string;
  body: string;
  event_code: string;
};

Deno.serve(async (req) => {
  try {
    const denied = assertWorkerSecret(req);
    if (denied) return denied;

    const body = req.method === "POST" ? await req.json().catch(() => ({})) : {};
    const limit = Number(body.limit ?? 50);
    const localStub = allowLocalChannelStub();
    const smsCfg = getSmsGatewayConfig();

    if (!smsCfg) {
      if (!localStub) {
        return jsonErr(
          "SMS_GATEWAY_API_KEY unset — refuse (set WORKER_ALLOW_UNVERIFIED_LOCAL=1 with WORKER_SHARED_SECRET unset for local stub only)",
          503,
        );
      }
      console.warn(
        "process-sms-outbox: SMS_GATEWAY_API_KEY unset — local stub drain",
      );
      const supabase = createClient(
        Deno.env.get("SUPABASE_URL")!,
        Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
      );
      const stubSuccess = body.stub_success !== false;
      const { data, error } = await supabase.rpc("drain_sms_outbox_batch", {
        p_limit: limit,
        p_stub_success: stubSuccess,
      });
      if (error) return jsonErr(error.message, 400);
      return jsonOk({ drained: data, stub: true });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: rows, error: claimErr } = await supabase.rpc(
      "claim_sms_outbox_batch",
      { p_limit: limit },
    );
    if (claimErr) return jsonErr(claimErr.message, 400);

    const list = (rows ?? []) as SmsOutboxRow[];
    let sent = 0;
    let failed = 0;
    const errors: { id: string; error: string }[] = [];

    for (const row of list) {
      try {
        const result = await sendSms(smsCfg, row.phone_e164, row.body);
        await supabase.rpc("complete_sms_outbox", {
          p_id: row.id,
          p_success: true,
          p_error: null,
          p_provider_message_id: result.messageId,
        });
        sent += 1;
      } catch (e) {
        const msg = String(e);
        await supabase.rpc("complete_sms_outbox", {
          p_id: row.id,
          p_success: false,
          p_error: msg,
          p_provider_message_id: null,
        });
        failed += 1;
        errors.push({ id: row.id, error: msg.slice(0, 200) });
      }
    }

    // Never claim stub:true when real key is set — even if all sends failed.
    return jsonOk({
      claimed: list.length,
      sent,
      failed,
      stub: false,
      errors: errors.length ? errors : undefined,
    });
  } catch (e) {
    return jsonErr(String(e), 500);
  }
});
