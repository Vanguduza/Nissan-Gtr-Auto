/**
 * Paynow webhook / result settle stub.
 * Env: PAYNOW_INTEGRATION_KEY (hash verify) — never commit real values.
 * Local unverified settle: PAYNOW_ALLOW_UNVERIFIED_LOCAL=1 only when key unset.
 * Does not trust webhook allocations for AR — ledger uses DB intent amount.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  isLocalUnverifiedAllowed,
  jsonResponse,
  sha256Hex,
  stubPaynowExpectedHash,
  timingSafeEqualStr,
} from "../_shared/payment_edge.ts";

Deno.serve(async (req) => {
  try {
    const raw = await req.text();
    const key = Deno.env.get("PAYNOW_INTEGRATION_KEY");
    const hashHeader = (req.headers.get("x-paynow-hash") ?? "").trim().toLowerCase();
    const localUnverified =
      !key && isLocalUnverifiedAllowed("PAYNOW_ALLOW_UNVERIFIED_LOCAL");

    if (!key) {
      if (!localUnverified) {
        return jsonResponse(
          {
            error:
              "PAYNOW_INTEGRATION_KEY unset — refuse settle (set PAYNOW_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)",
          },
          401,
        );
      }
      console.warn(
        "paynow-webhook: unverified local stub (PAYNOW_ALLOW_UNVERIFIED_LOCAL=1)",
      );
    } else {
      if (!hashHeader) {
        return jsonResponse({ error: "missing hash" }, 401);
      }
      // STUB algorithm — replace with real Paynow hash when merchant keys/docs arrive.
      const expected = (await stubPaynowExpectedHash(raw, key)).toLowerCase();
      if (!timingSafeEqualStr(expected, hashHeader)) {
        return jsonResponse({ error: "invalid hash" }, 401);
      }
    }

    const payload = JSON.parse(raw || "{}");
    const external_ref = payload.external_ref ?? payload.reference ?? payload.pollurl;
    const success = payload.success !== false && payload.status !== "failed";
    // Never trust webhook allocations for AR — allocate server-side later if needed.
    const payload_hash = await sha256Hex(raw);

    if (!external_ref) {
      return jsonResponse({ error: "external_ref required" }, 400);
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data, error } = await supabase.rpc("mark_paynow_settled", {
      p_external_ref: external_ref,
      p_payload_hash: payload_hash,
      p_provider_ref: payload.provider_ref ?? payload.paynowreference ?? null,
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
