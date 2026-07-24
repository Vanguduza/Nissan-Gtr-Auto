/**
 * ContiPay webhook settle stub.
 * Env: CONTIPAY_WEBHOOK_HMAC_SECRET — verify signature in production; never commit.
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
    const hmacSecret = Deno.env.get("CONTIPAY_WEBHOOK_HMAC_SECRET");
    const sig = req.headers.get("x-contipay-signature") ?? "";

    // Stub verify: if secret configured, require non-empty signature header (real HMAC TBD).
    if (hmacSecret && !sig) {
      return new Response(JSON.stringify({ error: "missing signature" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    const payload = JSON.parse(raw || "{}");
    const external_ref = payload.external_ref ?? payload.reference;
    const success = payload.success !== false && payload.status !== "failed";
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

    const { data, error } = await supabase.rpc("mark_contipay_settled", {
      p_external_ref: external_ref,
      p_payload_hash: payload_hash,
      p_provider_ref: payload.provider_ref ?? null,
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
