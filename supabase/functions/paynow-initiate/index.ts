/**
 * Paynow initiate — real provider when PAYNOW_INTEGRATION_ID + KEY set.
 * Staff JWT → create_paynow_intent; customer JWT + sales_invoice_id →
 * create_customer_paynow_intent. Settle stays webhook.
 * Local stub without secrets: PAYNOW_ALLOW_UNVERIFIED_LOCAL=1
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  corsHeaders,
  defaultWebhookUrl,
  formatMoney2,
  initiatePaynowTransaction,
  isLocalUnverifiedAllowed,
  jsonResponse,
  mergeRedirectMetadata,
  requireBearerJwt,
  stubCheckoutUrl,
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

    const integrationId = Deno.env.get("PAYNOW_INTEGRATION_ID")?.trim();
    const integrationKey = Deno.env.get("PAYNOW_INTEGRATION_KEY")?.trim();
    const secretsMissing = !integrationId || !integrationKey;
    const localStub = isLocalUnverifiedAllowed("PAYNOW_ALLOW_UNVERIFIED_LOCAL");
    if (secretsMissing) {
      if (!localStub) {
        return jsonResponse(
          {
            error:
              "PAYNOW_INTEGRATION_ID / PAYNOW_INTEGRATION_KEY required (set PAYNOW_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)",
          },
          503,
          cors,
        );
      }
      console.warn(
        "paynow-initiate: secrets unset — local stub via PAYNOW_ALLOW_UNVERIFIED_LOCAL=1",
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
      additionalinfo,
      authemail,
      authphone,
      authname,
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

    // User-scoped client — never service_role on initiate.
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );

    const { data, error } = sales_invoice_id
      ? await supabase.rpc("create_customer_paynow_intent", {
          p_sales_invoice_id: sales_invoice_id,
          p_method: method,
          p_external_ref: external_ref ?? null,
          p_amount: amount ?? null,
          p_settlement_currency: settlement_currency ?? null,
          p_settlement_amount: settlement_amount ?? null,
          p_settlement_exchange_rate: settlement_exchange_rate ?? null,
          p_metadata: pMetadata,
        })
      : await supabase.rpc("create_paynow_intent", {
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
          ? stubCheckoutUrl(return_url.trim(), "paynow", intentId)
          : null;
      return jsonResponse(
        {
          intent_id: data,
          status: "pending",
          checkout_url: checkoutUrl,
          poll_url: null,
          return_url: typeof return_url === "string" ? return_url : null,
          cancel_url: typeof cancel_url === "string" ? cancel_url : null,
          result_url: typeof result_url === "string" ? result_url : null,
          stub: true,
        },
        200,
        cors,
      );
    }

    // Load intent for reference + amount (customer path may auto-generate both).
    const { data: intent, error: intentErr } = await supabase
      .from("paynow_payment_intents")
      .select("external_ref, amount, currency")
      .eq("id", intentId)
      .maybeSingle();

    if (intentErr || !intent) {
      return jsonResponse(
        {
          error: intentErr?.message ??
            "intent created but not readable for Paynow initiate",
        },
        500,
        cors,
      );
    }

    const browserReturn =
      typeof return_url === "string" && return_url.trim()
        ? return_url.trim()
        : null;
    if (!browserReturn) {
      return jsonResponse(
        { error: "return_url required for Paynow hosted checkout" },
        400,
        cors,
      );
    }

    // Never trust client result_url for PSP webhook registration.
    const resultUrl = defaultWebhookUrl("paynow-webhook");

    try {
      const initiated = await initiatePaynowTransaction({
        integrationId: integrationId!,
        integrationKey: integrationKey!,
        reference: intent.external_ref,
        amount: formatMoney2(intent.amount),
        additionalinfo:
          typeof additionalinfo === "string" && additionalinfo.trim()
            ? additionalinfo.trim()
            : `Invoice payment ${intent.external_ref}`,
        returnurl: browserReturn,
        resulturl: resultUrl,
        authemail:
          typeof authemail === "string" && authemail.trim()
            ? authemail.trim()
            : undefined,
        authphone:
          typeof authphone === "string" && authphone.trim()
            ? authphone.trim()
            : undefined,
        authname:
          typeof authname === "string" && authname.trim()
            ? authname.trim()
            : undefined,
      });

      return jsonResponse(
        {
          intent_id: data,
          status: "pending",
          checkout_url: initiated.browserurl,
          // poll_url stays server-side only (webhook poll confirm).
          return_url: browserReturn,
          cancel_url: typeof cancel_url === "string" ? cancel_url : null,
          result_url: resultUrl,
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
