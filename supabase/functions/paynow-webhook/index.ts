/**
 * Paynow webhook / result settle.
 * Env: PAYNOW_INTEGRATION_KEY — verify inbound field hash (not stub prefix).
 * Local unverified settle: PAYNOW_ALLOW_UNVERIFIED_LOCAL=1 only when key unset.
 * Does not trust webhook allocations for AR — ledger uses DB intent amount.
 *
 * Paynow posts application/x-www-form-urlencoded to resulturl with hash in body.
 * https://developers.paynow.co.zw/docs/paynow/status_update/
 * https://developers.paynow.co.zw/docs/paynow/validating_hash/
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  isLocalUnverifiedAllowed,
  isPaynowFailureStatus,
  isPaynowSuccessStatus,
  jsonResponse,
  pollPaynowStatus,
  sha256Hex,
  timingSafeEqualStr,
  verifyPaynowMessageHash,
} from "../_shared/payment_edge.ts";

Deno.serve(async (req) => {
  try {
    const raw = await req.text();
    const key = Deno.env.get("PAYNOW_INTEGRATION_KEY")?.trim();
    const localUnverified =
      !key && isLocalUnverifiedAllowed("PAYNOW_ALLOW_UNVERIFIED_LOCAL");

    let fields: Record<string, string> = {};

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
      try {
        const parsed = JSON.parse(raw || "{}") as Record<string, unknown>;
        for (const [k, v] of Object.entries(parsed)) {
          fields[k.toLowerCase()] = v == null ? "" : String(v);
        }
      } catch {
        // form body without verification in local stub
        for (const part of (raw || "").split("&")) {
          const eq = part.indexOf("=");
          if (eq === -1) continue;
          const k = decodeURIComponent(part.slice(0, eq).replace(/\+/g, " "));
          const v = decodeURIComponent(part.slice(eq + 1).replace(/\+/g, " "));
          fields[k.toLowerCase()] = v;
        }
      }
    } else {
      const verified = await verifyPaynowMessageHash(raw, key);
      if (!verified.ok) {
        // Also accept legacy header-only local tests that mistakenly used x-paynow-hash
        // with body hash missing — still require real field hash when present.
        const hashHeader = (req.headers.get("x-paynow-hash") ?? "")
          .trim()
          .toUpperCase();
        if (hashHeader && !verified.fields.hash) {
          // No body hash: reject — real Paynow always includes hash in body.
          return jsonResponse({ error: "invalid or missing Paynow hash" }, 401);
        }
        if (!verified.ok) {
          return jsonResponse({ error: "invalid or missing Paynow hash" }, 401);
        }
      }
      fields = verified.fields;

      // Optional confirm via pollurl when present (recommended by Paynow docs).
      const pollurl = fields.pollurl?.trim();
      if (pollurl) {
        try {
          const polled = await pollPaynowStatus(pollurl, key);
          // Prefer poll status when hash-verified.
          if (polled.status) fields = { ...fields, ...polled };
        } catch (pollErr) {
          console.warn("paynow-webhook: poll confirm failed", String(pollErr));
          // Continue with verified webhook payload.
        }
      }
    }

    const external_ref =
      fields.reference?.trim() ||
      fields.external_ref?.trim() ||
      "";
    const status = fields.status ?? "";
    const success = localUnverified
      ? fields.success !== "false" && status.toLowerCase() !== "failed"
      : isPaynowSuccessStatus(status);
    const failed = !localUnverified && isPaynowFailureStatus(status);

    // Intermediate (Created/Sent) — ack without settling.
    if (!localUnverified && !success && !failed) {
      return jsonResponse({
        ok: true,
        ignored: true,
        status,
        reason: "non-terminal Paynow status",
      });
    }

    const payload_hash = await sha256Hex(raw);

    if (!external_ref) {
      return jsonResponse({ error: "reference (external_ref) required" }, 400);
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data, error } = await supabase.rpc("mark_paynow_settled", {
      p_external_ref: external_ref,
      p_payload_hash: payload_hash,
      p_provider_ref: fields.paynowreference ?? fields.provider_ref ?? null,
      p_allocations: null,
      p_settlement_currency: null,
      p_settlement_amount: null,
      p_settlement_exchange_rate: null,
      p_success: success,
      p_failure_reason: failed
        ? `Paynow status: ${status}`
        : fields.failure_reason ?? null,
    });

    if (error) {
      return jsonResponse({ error: error.message }, 400);
    }

    return jsonResponse({
      ok: true,
      result: data,
      stub: false,
      unverified_local: localUnverified,
    });
  } catch (e) {
    return jsonResponse({ error: String(e) }, 500);
  }
});
