/**
 * Paynow result/status webhook settle stub.
 * Env: PAYNOW_INTEGRATION_KEY — verify Paynow hash in production; never commit.
 * Contract: POST JSON with external_ref (or reference), optional status/success,
 * allocations, dual-currency settlement fields. Idempotent via payload_hash.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

Deno.serve(async (req) => {
  try {
    const raw = await req.text();
    const integrationKey = Deno.env.get("PAYNOW_INTEGRATION_KEY");
    const hash = req.headers.get("x-paynow-hash") ?? "";

    // Stub verify: if key configured, require non-empty hash header (real Paynow hash TBD).
    if (integrationKey && !hash) {
      return new Response(JSON.stringify({ error: "missing hash" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    const payload = JSON.parse(raw || "{}");
    const external_ref = payload.external_ref ?? payload.reference ?? payload.reference_;
    const success =
      payload.success !== false &&
      payload.status !== "failed" &&
      payload.status !== "Cancelled";
    const allocations = payload.allocations ?? null;
    const payload_hash = await sha256Hex(raw);

    if (!external_ref) {
      return new Response(JSON.stringify({ error: "external_ref required" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data, error } = await supabase.rpc("mark_paynow_settled", {
      p_external_ref: external_ref,
      p_payload_hash: payload_hash,
      p_provider_ref: payload.provider_ref ?? payload.paynowreference ?? null,
      p_allocations: allocations,
      p_settlement_currency: payload.settlement_currency ?? null,
      p_settlement_amount: payload.settlement_amount ?? null,
      p_settlement_exchange_rate: payload.settlement_exchange_rate ?? null,
      p_success: success,
      p_failure_reason: payload.failure_reason ?? null,
    });

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(
      JSON.stringify({ ok: true, result: data, stub: true }),
      { headers: { "Content-Type": "application/json" } },
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
