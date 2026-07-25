/**
 * ContiPay initiate — real provider when CONTIPAY_API_KEY + MERCHANT_ID + secret set.
 * Staff JWT → create_contipay_intent; customer JWT + sales_invoice_id →
 * create_customer_contipay_intent. Settle stays webhook.
 * Local stub without secrets: CONTIPAY_ALLOW_UNVERIFIED_LOCAL=1
 *
 * Acquire API: Basic Auth PUT /acquire/payment (redirect).
 * Source: https://github.com/njzw/contipay-js-client
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  corsHeaders,
  defaultWebhookUrl,
  initiateContipayRedirect,
  isLocalUnverifiedAllowed,
  jsonResponse,
  mergeRedirectMetadata,
  requireBearerJwt,
  stubCheckoutUrl,
} from "../_shared/payment_edge.ts";

function contipayApiSecret(): string | undefined {
  return (
    Deno.env.get("CONTIPAY_API_SECRET")?.trim() ||
    Deno.env.get("CONTIPAY_WEBHOOK_HMAC_SECRET")?.trim() ||
    undefined
  );
}

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

    const apiKey = Deno.env.get("CONTIPAY_API_KEY")?.trim();
    const merchantId = Deno.env.get("CONTIPAY_MERCHANT_ID")?.trim();
    const apiSecret = contipayApiSecret();
    const secretsMissing = !apiKey || !merchantId || !apiSecret;
    const localStub = isLocalUnverifiedAllowed("CONTIPAY_ALLOW_UNVERIFIED_LOCAL");
    if (secretsMissing) {
      if (!localStub) {
        return jsonResponse(
          {
            error:
              "CONTIPAY_API_KEY / CONTIPAY_MERCHANT_ID / CONTIPAY_API_SECRET (or CONTIPAY_WEBHOOK_HMAC_SECRET) required (set CONTIPAY_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)",
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
      sales_invoice_id,
      settlement_currency,
      settlement_amount,
      settlement_exchange_rate,
      metadata,
      return_url,
      cancel_url,
      result_url,
      phone,
      email,
      customer_first_name,
      customer_surname,
    } = body ?? {};

    if (!method) {
      return jsonResponse({ error: "method required" }, 400, cors);
    }
    if (!sales_invoice_id && (!external_ref || !amount)) {
      return jsonResponse(
        {
          error:
            "external_ref, method, amount required (or sales_invoice_id for customer self-pay)",
        },
        400,
        cors,
      );
    }

    const pMetadata = mergeRedirectMetadata(metadata, {
      return_url,
      cancel_url,
      result_url,
    });

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );

    const { data, error } = sales_invoice_id
      ? await supabase.rpc("create_customer_contipay_intent", {
          p_sales_invoice_id: sales_invoice_id,
          p_method: method,
          p_external_ref: external_ref ?? null,
          p_amount: amount ?? null,
          p_settlement_currency: settlement_currency ?? null,
          p_settlement_amount: settlement_amount ?? null,
          p_settlement_exchange_rate: settlement_exchange_rate ?? null,
          p_metadata: pMetadata,
        })
      : await supabase.rpc("create_contipay_intent", {
          p_external_ref: external_ref,
          p_method: method,
          p_amount: amount,
          p_currency: currency,
          p_exchange_rate: exchange_rate,
          p_customer_id: customer_id ?? null,
          p_settlement_currency: settlement_currency ?? null,
          p_settlement_amount: settlement_amount ?? null,
          p_settlement_exchange_rate: settlement_exchange_rate ?? null,
          p_metadata: pMetadata,
        });

    if (error) {
      return jsonResponse({ error: error.message }, 400, cors);
    }

    const intentId = String(data);

    if (secretsMissing) {
      const checkoutUrl =
        typeof return_url === "string" && return_url.trim()
          ? stubCheckoutUrl(return_url.trim(), "contipay", intentId)
          : null;
      return jsonResponse(
        {
          intent_id: data,
          status: "pending",
          checkout_url: checkoutUrl,
          return_url: typeof return_url === "string" ? return_url : null,
          cancel_url: typeof cancel_url === "string" ? cancel_url : null,
          result_url: typeof result_url === "string" ? result_url : null,
          stub: true,
        },
        200,
        cors,
      );
    }

    const { data: intent, error: intentErr } = await supabase
      .from("contipay_payment_intents")
      .select("external_ref, amount, currency, metadata")
      .eq("id", intentId)
      .maybeSingle();

    if (intentErr || !intent) {
      return jsonResponse(
        {
          error: intentErr?.message ??
            "intent created but not readable for ContiPay initiate",
        },
        500,
        cors,
      );
    }

    const successUrl =
      typeof return_url === "string" && return_url.trim()
        ? return_url.trim()
        : null;
    const cancelUrl =
      typeof cancel_url === "string" && cancel_url.trim()
        ? cancel_url.trim()
        : successUrl;
    if (!successUrl) {
      return jsonResponse(
        { error: "return_url required for ContiPay hosted checkout" },
        400,
        cors,
      );
    }

    const meta =
      intent.metadata && typeof intent.metadata === "object"
        ? (intent.metadata as Record<string, unknown>)
        : {};
    const cell =
      (typeof phone === "string" && phone.trim()) ||
      (typeof meta.phone === "string" && meta.phone.trim()) ||
      (typeof meta.cell === "string" && meta.cell.trim()) ||
      "";
    if (!cell) {
      return jsonResponse(
        {
          error:
            "phone (or metadata.phone/cell) required for ContiPay redirect initiate",
        },
        400,
        cors,
      );
    }

    // Never trust client result_url for PSP webhook registration.
    const webhookUrl = defaultWebhookUrl("contipay-webhook");

    try {
      const initiated = await initiateContipayRedirect({
        apiKey: apiKey!,
        apiSecret: apiSecret!,
        merchantId: merchantId!,
        reference: intent.external_ref,
        amount: intent.amount,
        currencyCode: intent.currency ?? currency ?? "USD",
        webhookUrl,
        successUrl,
        cancelUrl: cancelUrl!,
        description: `Payment ${intent.external_ref}`,
        customer: {
          cell,
          email:
            typeof email === "string" && email.trim()
              ? email.trim()
              : typeof meta.email === "string"
              ? meta.email
              : undefined,
          firstName:
            typeof customer_first_name === "string"
              ? customer_first_name
              : undefined,
          surname:
            typeof customer_surname === "string" ? customer_surname : undefined,
        },
      });

      return jsonResponse(
        {
          intent_id: data,
          status: "pending",
          checkout_url: initiated.checkoutUrl,
          return_url: successUrl,
          cancel_url: cancelUrl,
          result_url: webhookUrl,
          stub: false,
        },
        200,
        cors,
      );
    } catch (providerErr) {
      return jsonResponse(
        { error: String(providerErr), intent_id: data },
        502,
        cors,
      );
    }
  } catch (e) {
    return jsonResponse({ error: String(e) }, 500, corsHeaders(req));
  }
});
