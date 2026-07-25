/**
 * ContiPay webhook settle.
 * Env: CONTIPAY_WEBHOOK_HMAC_SECRET — HMAC-SHA256(raw body) hex verify.
 * Local unverified settle: CONTIPAY_ALLOW_UNVERIFIED_LOCAL=1 only when secret unset.
 * Does not trust webhook allocations for AR — ledger uses DB intent amount.
 *
 * Webhook signature: no public ContiPay merchant doc found for header name.
 * Implemented as HMAC-SHA256 over raw body; compare to x-contipay-signature
 * (also accepts x-signature / signature, optional sha256= prefix).
 * Confirm with ContiPay when merchant keys arrive.
 * Source note: https://github.com/njzw/contipay-js-client (acquire auth only).
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  contipayHmacSha256Hex,
  contipaySignatureFromHeaders,
  isLocalUnverifiedAllowed,
  jsonResponse,
  sha256Hex,
  timingSafeEqualStr,
} from "../_shared/payment_edge.ts";

Deno.serve(async (req) => {
  try {
    const raw = await req.text();
    const hmacSecret = Deno.env.get("CONTIPAY_WEBHOOK_HMAC_SECRET")?.trim();
    const localUnverified =
      !hmacSecret && isLocalUnverifiedAllowed("CONTIPAY_ALLOW_UNVERIFIED_LOCAL");

    if (!hmacSecret) {
      if (!localUnverified) {
        return jsonResponse(
          {
            error:
              "CONTIPAY_WEBHOOK_HMAC_SECRET unset — refuse settle (set CONTIPAY_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)",
          },
          401,
        );
      }
      console.warn(
        "contipay-webhook: unverified local stub (CONTIPAY_ALLOW_UNVERIFIED_LOCAL=1)",
      );
    } else {
      const sig = contipaySignatureFromHeaders(req).toLowerCase();
      if (!sig) {
        return jsonResponse({ error: "missing signature" }, 401);
      }
      const expected = (await contipayHmacSha256Hex(raw, hmacSecret)).toLowerCase();
      if (!timingSafeEqualStr(expected, sig)) {
        return jsonResponse({ error: "invalid signature" }, 401);
      }
    }

    const payload = JSON.parse(raw || "{}") as Record<string, unknown>;
    const external_ref = String(
      payload.external_ref ??
        payload.reference ??
        (payload.transaction &&
          typeof payload.transaction === "object" &&
          (payload.transaction as { reference?: unknown }).reference) ??
        "",
    ).trim();
    const status = String(payload.status ?? "").toLowerCase();
    // Allowlist settle success — do not treat unknown statuses as paid.
    const success =
      status === "paid" || status === "success" || status === "completed";
    const payload_hash = await sha256Hex(raw);

    if (!external_ref) {
      return jsonResponse({ error: "external_ref required" }, 400);
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data, error } = await supabase.rpc("mark_contipay_settled", {
      p_external_ref: external_ref,
      p_payload_hash: payload_hash,
      p_provider_ref:
        payload.provider_ref != null
          ? String(payload.provider_ref)
          : payload.id != null
          ? String(payload.id)
          : null,
      p_allocations: null,
      p_settlement_currency: null,
      p_settlement_amount: null,
      p_settlement_exchange_rate: null,
      p_success: success,
      p_failure_reason:
        payload.failure_reason != null
          ? String(payload.failure_reason)
          : !success
          ? `ContiPay status: ${status || "failed"}`
          : null,
    });

    if (error) {
      return jsonResponse({ error: error.message }, 400);
    }

    return jsonResponse({
      ok: true,
      result: data,
      stub: localUnverified,
      unverified_local: localUnverified,
    });
  } catch (e) {
    return jsonResponse({ error: String(e) }, 500);
  }
});
