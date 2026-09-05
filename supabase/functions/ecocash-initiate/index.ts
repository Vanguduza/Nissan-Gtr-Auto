/**
 * EcoCash direct C2B initiate — cross-platform (web / POS / iOS / Android / WhatsApp adapter).
 * Customer JWT + commerce_order_id (legacy sales_invoice_id also accepted) → create_customer_ecocash_intent.
 * Staff JWT → create_ecocash_intent.
 * Then push C2B PIN request when ECOCASH_API_KEY is set (stub otherwise).
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  corsHeaders,
  initiateEcocashC2b,
  isLocalUnverifiedAllowed,
  jsonResponse,
  normalizeEcocashMsisdn,
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

    const apiKey = Deno.env.get("ECOCASH_API_KEY")?.trim();
    const secretsMissing = !apiKey;
    const localStub = isLocalUnverifiedAllowed("ECOCASH_ALLOW_UNVERIFIED_LOCAL");
    if (secretsMissing && !localStub) {
      return jsonResponse(
        {
          error:
            "ECOCASH_API_KEY required (set ECOCASH_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)",
        },
        503,
        cors,
      );
    }

    const body = await req.json();
    const {
      external_ref,
      payer_msisdn,
      payer_mode = "other",
      channel = "web",
      amount,
      currency = "USD",
      exchange_rate = 1,
      customer_id,
      commerce_order_id,
      sales_invoice_id,
      whatsapp_flow_order_id,
      settlement_currency,
      settlement_amount,
      settlement_exchange_rate,
      metadata,
    } = body ?? {};
    const customerOrderRef = commerce_order_id ?? sales_invoice_id ?? null;

    const msisdn = normalizeEcocashMsisdn(String(payer_msisdn || ""));
    if (!msisdn) {
      return jsonResponse(
        {
          error:
            "payer_msisdn required (263… / 07…). Use saved/profile number or enter another EcoCash wallet.",
        },
        400,
        cors,
      );
    }

    if (!customerOrderRef && (external_ref == null || amount == null)) {
      return jsonResponse(
        {
          error:
            "commerce_order_id (customer) or external_ref + amount (staff) required",
        },
        400,
        cors,
      );
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );

    const pMetadata = {
      ...(metadata && typeof metadata === "object" ? metadata : {}),
      channel,
    };

    const { data: intentIdRaw, error } = customerOrderRef
      ? await supabase.rpc("create_customer_ecocash_intent", {
          p_sales_invoice_id: customerOrderRef,
          p_payer_msisdn: msisdn,
          p_payer_mode: payer_mode,
          p_external_ref: external_ref ?? null,
          p_amount: amount ?? null,
          p_channel: channel ?? "web",
          p_settlement_currency: settlement_currency ?? null,
          p_settlement_amount: settlement_amount ?? null,
          p_settlement_exchange_rate: settlement_exchange_rate ?? null,
          p_metadata: pMetadata,
        })
      : await supabase.rpc("create_ecocash_intent", {
          p_external_ref: external_ref,
          p_payer_msisdn: msisdn,
          p_amount: amount,
          p_currency: currency,
          p_exchange_rate: exchange_rate,
          p_payer_mode: payer_mode,
          p_channel: channel ?? "pos",
          p_customer_id: customer_id ?? null,
          p_sales_invoice_id: null,
          p_whatsapp_flow_order_id: whatsapp_flow_order_id ?? null,
          p_settlement_currency: settlement_currency ?? null,
          p_settlement_amount: settlement_amount ?? null,
          p_settlement_exchange_rate: settlement_exchange_rate ?? null,
          p_metadata: pMetadata,
        });

    if (error) {
      return jsonResponse({ error: error.message }, 400, cors);
    }

    const intentId = String(intentIdRaw);
    const { data: intent, error: intentErr } = await supabase
      .from("ecocash_payment_intents")
      .select("external_ref, amount, currency, payer_msisdn")
      .eq("id", intentId)
      .maybeSingle();

    if (intentErr || !intent) {
      return jsonResponse(
        {
          error: intentErr?.message ??
            "intent created but not readable for EcoCash C2B",
        },
        500,
        cors,
      );
    }

    if (secretsMissing) {
      return jsonResponse(
        {
          intent_id: intentId,
          external_ref: intent.external_ref,
          payer_msisdn: intent.payer_msisdn,
          status: "pending",
          stub: true,
          message:
            "Intent created (stub). Approve PIN when ECOCASH_API_KEY is set for live C2B.",
        },
        200,
        cors,
      );
    }

    const environment =
      (Deno.env.get("ECOCASH_ENVIRONMENT") ?? "sandbox").toLowerCase() === "live"
        ? "live"
        : "sandbox";

    try {
      const c2b = await initiateEcocashC2b({
        apiKey: apiKey!,
        msisdn: String(intent.payer_msisdn),
        amount: Number(intent.amount),
        currency: String(intent.currency || "USD"),
        reason: `GTR ${String(intent.external_ref).slice(0, 24)}`,
        sourceReference: String(intent.external_ref),
        environment,
      });

      if (c2b.providerReference) {
        await supabase
          .from("ecocash_payment_intents")
          .update({ provider_ref: c2b.providerReference })
          .eq("id", intentId);
      }

      return jsonResponse(
        {
          intent_id: intentId,
          external_ref: intent.external_ref,
          payer_msisdn: intent.payer_msisdn,
          status: c2b.status,
          provider_reference: c2b.providerReference,
          stub: false,
          message:
            "EcoCash PIN request sent to the payer handset. Settlement via webhook/lookup.",
        },
        200,
        cors,
      );
    } catch (c2bErr) {
      console.error("ecocash-initiate C2B", c2bErr);
      return jsonResponse(
        {
          intent_id: intentId,
          external_ref: intent.external_ref,
          payer_msisdn: intent.payer_msisdn,
          status: "pending",
          stub: false,
          error: c2bErr instanceof Error ? c2bErr.message : "C2B push failed",
          message:
            "Intent saved but EcoCash C2B push failed — retry or check portal credentials.",
        },
        502,
        cors,
      );
    }
  } catch (e) {
    console.error("ecocash-initiate", e);
    return jsonResponse(
      { error: e instanceof Error ? e.message : "initiate failed" },
      500,
      cors,
    );
  }
});
