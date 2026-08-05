/**
 * EcoCash webhook / settle — calls mark_ecocash_settled (idempotent).
 * Optional ECOCASH_WEBHOOK_SECRET header check when set.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  corsHeaders,
  isLocalUnverifiedAllowed,
  jsonResponse,
} from "../_shared/payment_edge.ts";

async function sha256Hex(text: string): Promise<string> {
  const data = new TextEncoder().encode(text);
  const hash = await crypto.subtle.digest("SHA-256", data);
  return [...new Uint8Array(hash)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

Deno.serve(async (req) => {
  const cors = corsHeaders(req);
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: cors });
  }

  try {
    const raw = await req.text();
    const secret = Deno.env.get("ECOCASH_WEBHOOK_SECRET")?.trim();
    if (secret) {
      const sig =
        req.headers.get("x-ecocash-signature") ||
        req.headers.get("X-EcoCash-Signature") ||
        "";
      if (sig !== secret) {
        return jsonResponse({ error: "invalid signature" }, 401, cors);
      }
    } else if (!isLocalUnverifiedAllowed("ECOCASH_ALLOW_UNVERIFIED_LOCAL")) {
      return jsonResponse(
        {
          error:
            "ECOCASH_WEBHOOK_SECRET required (or ECOCASH_ALLOW_UNVERIFIED_LOCAL=1)",
        },
        503,
        cors,
      );
    }

    let body: Record<string, unknown> = {};
    try {
      body = raw ? JSON.parse(raw) : {};
    } catch {
      return jsonResponse({ error: "JSON body required" }, 422, cors);
    }

    const externalRef = String(
      body.sourceReference ||
        body.source_reference ||
        body.external_ref ||
        body.merchantReference ||
        "",
    ).trim();
    const status = String(
      body.transactionStatus || body.status || body.paymentStatus || "",
    ).toLowerCase();
    const success = ["paid", "success", "successful", "completed", "approved", "ok"]
      .some((t) => status.includes(t));
    const providerRef = String(
      body.ecocashReference ||
        body.transactionReference ||
        body.reference ||
        "",
    ) || null;

    if (!externalRef) {
      return jsonResponse({ error: "sourceReference / external_ref required" }, 422, cors);
    }

    const payloadHash = await sha256Hex(raw || externalRef + status);
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data, error } = await supabase.rpc("mark_ecocash_settled", {
      p_external_ref: externalRef,
      p_payload_hash: payloadHash,
      p_provider_ref: providerRef,
      p_allocations: body.allocations ?? null,
      p_success: success,
      p_failure_reason: success ? null : status || "failed",
    });

    if (error) {
      return jsonResponse({ error: error.message }, 400, cors);
    }

    return jsonResponse(
      { ok: true, intent_or_payment_id: data, status: success ? "settled" : "failed" },
      200,
      cors,
    );
  } catch (e) {
    console.error("ecocash-webhook", e);
    return jsonResponse(
      { error: e instanceof Error ? e.message : "webhook failed" },
      500,
      cors,
    );
  }
});
