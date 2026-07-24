/**
 * ContiPay edge initiate stub.
 * Secrets: CONTIPAY_API_KEY, CONTIPAY_MERCHANT_ID — Edge Function env only.
 * Requires staff JWT; uses anon + user JWT so create_contipay_intent staff gate applies.
 * Local stub without secrets: CONTIPAY_ALLOW_UNVERIFIED_LOCAL=1
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  corsHeaders,
  isLocalUnverifiedAllowed,
  jsonResponse,
  requireBearerJwt,
} from "../_shared/payment_edge.ts";

Deno.serve(async (req) => {
  const cors = corsHeaders(req);
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: cors });
  }

  try {
    const authHeader = requireBearerJwt(req);
    if (!authHeader) {
      return jsonResponse(
        { error: "Authorization Bearer JWT required" },
        401,
        cors,
      );
    }

    const apiKey = Deno.env.get("CONTIPAY_API_KEY");
    const merchantId = Deno.env.get("CONTIPAY_MERCHANT_ID");
    const localStub = isLocalUnverifiedAllowed("CONTIPAY_ALLOW_UNVERIFIED_LOCAL");
    if (!apiKey || !merchantId) {
      if (!localStub) {
        return jsonResponse(
          {
            error:
              "CONTIPAY_API_KEY / CONTIPAY_MERCHANT_ID required (set CONTIPAY_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)",
          },
          503,
          cors,
        );
      }
      console.warn(
        "contipay-initiate: secrets unset — local stub via CONTIPAY_ALLOW_UNVERIFIED_LOCAL=1",
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
      return jsonResponse(
        { error: "external_ref, method, amount required" },
        400,
        cors,
      );
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );

    const { data, error } = await supabase.rpc("create_contipay_intent", {
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
      return jsonResponse({ error: error.message }, 400, cors);
    }

    return jsonResponse(
      {
        intent_id: data,
        status: "pending",
        checkout_url: null,
        stub: true,
      },
      200,
      cors,
    );
  } catch (e) {
    return jsonResponse({ error: String(e) }, 500, corsHeaders(req));
  }
});
