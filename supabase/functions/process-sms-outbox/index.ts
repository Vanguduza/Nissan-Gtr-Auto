/**
 * Drain manager sms_outbox via stub gateway (RPC).
 * Real provider secrets (e.g. SMS_GATEWAY_API_KEY) stay in Edge env only.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

Deno.serve(async (req) => {
  try {
    const body = req.method === "POST" ? await req.json().catch(() => ({})) : {};
    const limit = Number(body.limit ?? 50);
    const stubSuccess = body.stub_success !== false;

    if (!Deno.env.get("SMS_GATEWAY_API_KEY")) {
      console.warn("process-sms-outbox: SMS_GATEWAY_API_KEY unset — stub drain");
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data, error } = await supabase.rpc("drain_sms_outbox_batch", {
      p_limit: limit,
      p_stub_success: stubSuccess,
    });

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(
      JSON.stringify({ drained: data, stub: true }),
      { headers: { "Content-Type": "application/json" } },
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
