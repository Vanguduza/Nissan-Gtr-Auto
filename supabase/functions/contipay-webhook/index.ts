/**
 * ContiPay webhook settle stub.
 * Env: CONTIPAY_WEBHOOK_HMAC_SECRET — verify signature; never commit.
 * Local unverified settle: CONTIPAY_ALLOW_UNVERIFIED_LOCAL=1 only when secret unset.
 * Does not trust webhook allocations for AR — ledger uses DB intent amount.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  isLocalUnverifiedAllowed,
  jsonResponse,
  sha256Hex,
  stubContipayExpectedSig,
  timingSafeEqualStr,
} from "../_shared/payment_edge.ts";

Deno.serve(async (req) => {
  try {
    const raw = await req.text();
    const hmacSecret = Deno.env.get("CONTIPAY_WEBHOOK_HMAC_SECRET");
    const sig = (req.headers.get("x-contipay-signature") ?? "").trim().toLowerCase();
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
      if (!sig) {
        return jsonResponse({ error: "missing signature" }, 401);
      }
      // STUB HMAC — replace with ContiPay-documented scheme when secrets arrive.
      const expected = (await stubContipayExpectedSig(raw, hmacSecret)).toLowerCase();
      if (!timingSafeEqualStr(expected, sig)) {
        return jsonResponse({ error: "invalid signature" }, 401);
      }
    }

    const payload = JSON.parse(raw || "{}");
    const external_ref = payload.external_ref ?? payload.reference;
    const success = payload.success !== false && payload.status !== "failed";
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
      p_provider_ref: payload.provider_ref ?? null,
      p_allocations: null,
      p_settlement_currency: null,
      p_settlement_amount: null,
      p_settlement_exchange_rate: null,
      p_success: success,
      p_failure_reason: payload.failure_reason ?? null,
    });

    if (error) {
      return jsonResponse({ error: error.message }, 400);
    }

    return jsonResponse({
      ok: true,
      result: data,
      stub: true,
      unverified_local: localUnverified,
    });
  } catch (e) {
    return jsonResponse({ error: String(e) }, 500);
  }
});
