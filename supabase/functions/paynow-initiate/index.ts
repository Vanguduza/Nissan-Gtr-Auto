/**
 * Paynow initiate stub.
 * Secrets: PAYNOW_INTEGRATION_ID, PAYNOW_INTEGRATION_KEY — Edge Function env only.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: cors });
  }

  try {
    const integrationId = Deno.env.get("PAYNOW_INTEGRATION_ID");
    const integrationKey = Deno.env.get("PAYNOW_INTEGRATION_KEY");
    if (!integrationId || !integrationKey) {
      console.warn(
        "paynow-initiate: PAYNOW_INTEGRATION_ID / PAYNOW_INTEGRATION_KEY not set (stub mode)",
      );
    }

    const body = await req.json();
    const {
      external_ref,
      method,
      amount,
      currency = "USD",
      exchange_rate = 1,
      customer_id,
      settlement_currency,
      settlement_amount,
      settlement_exchange_rate,
      metadata,
    } = body ?? {};

    if (!external_ref || !method || !amount) {
      return new Response(JSON.stringify({ error: "external_ref, method, amount required" }), {
        status: 400,
        headers: { ...cors, "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data, error } = await supabase.rpc("create_paynow_intent", {
      p_external_ref: external_ref,
      p_method: method,
      p_amount: amount,
      p_currency: currency,
      p_exchange_rate: exchange_rate,
      p_customer_id: customer_id ?? null,
      p_settlement_currency: settlement_currency ?? null,
      p_settlement_amount: settlement_amount ?? null,
      p_settlement_exchange_rate: settlement_exchange_rate ?? null,
      p_metadata: metadata ?? {},
    });

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 400,
        headers: { ...cors, "Content-Type": "application/json" },
      });
    }

    return new Response(
      JSON.stringify({
        intent_id: data,
        status: "pending",
        poll_url: null,
        return_url_hint: "https://nissangtrauto.co.zw",
        stub: true,
      }),
      { headers: { ...cors, "Content-Type": "application/json" } },
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), {
      status: 500,
      headers: { ...cors, "Content-Type": "application/json" },
    });
  }
});
