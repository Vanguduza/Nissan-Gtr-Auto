// Recovered from the deployed bundle (card-terminal-result v1, 2026-10-07): the source was never
// committed. Type annotations were stripped by the deploy bundler.
import { createClient } from "npm:@supabase/supabase-js@2";
import { corsHeaders, jsonResponse, requireBearerJwt } from "../_shared/payment_edge.ts";
function bytesFromBase64(value) {
  const normalized = value.replace(/\s+/g, "");
  const raw = atob(normalized);
  return Uint8Array.from(raw, (ch)=>ch.charCodeAt(0));
}
function normalizedPayload(input) {
  const p = input ?? {};
  const nullable = (key)=>{
    const value = p[key];
    return typeof value === "string" && value.trim() !== "" ? value.trim() : null;
  };
  const outcome = String(p.outcome ?? "");
  if (![
    "approved",
    "declined",
    "cancelled",
    "unknown",
    "failed"
  ].includes(outcome)) {
    throw new Error("invalid terminal outcome");
  }
  const payload = {
    version: "gtr-card-terminal-evidence-v1",
    attempt_id: String(p.attempt_id ?? "").trim(),
    device_id: String(p.device_id ?? "").trim(),
    observed_at: String(p.observed_at ?? "").trim(),
    outcome: outcome,
    terminal_transaction_id: nullable("terminal_transaction_id"),
    rrn: nullable("rrn"),
    authorization_code: nullable("authorization_code"),
    card_last4: nullable("card_last4"),
    card_scheme: nullable("card_scheme"),
    response_code: nullable("response_code"),
    response_message: nullable("response_message")
  };
  if (!payload.attempt_id || !payload.device_id || !payload.observed_at) {
    throw new Error("attempt_id, device_id and observed_at required");
  }
  if (payload.card_last4 && !/^\d{4}$/.test(payload.card_last4)) {
    throw new Error("card_last4 must contain four digits");
  }
  if (payload.outcome === "approved" && (!payload.terminal_transaction_id || !payload.rrn && !payload.authorization_code)) {
    throw new Error("approved result requires transaction id plus RRN or authorization code");
  }
  return payload;
}
Deno.serve(async (req)=>{
  const cors = corsHeaders(req);
  if (req.method === "OPTIONS") return new Response("ok", {
    headers: cors
  });
  if (req.method !== "POST") return jsonResponse({
    error: "POST required"
  }, 405, cors);
  try {
    const authHeader = requireBearerJwt(req);
    if (!authHeader) return jsonResponse({
      error: "Authorization Bearer JWT required"
    }, 401, cors);
    const url = Deno.env.get("SUPABASE_URL");
    const anon = Deno.env.get("SUPABASE_ANON_KEY");
    const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
    if (!serviceRole) return jsonResponse({
      error: "card terminal verifier is not configured"
    }, 503, cors);
    const body = await req.json();
    const payload = normalizedPayload(body?.payload);
    const signatureBase64 = String(body?.signature_base64 ?? "").trim();
    if (!signatureBase64) return jsonResponse({
      error: "signature_base64 required"
    }, 400, cors);
    const observedMs = Date.parse(payload.observed_at);
    if (!Number.isFinite(observedMs) || Math.abs(Date.now() - observedMs) > 10 * 60 * 1000) {
      return jsonResponse({
        error: "terminal evidence timestamp expired; re-query terminal status"
      }, 409, cors);
    }
    const userClient = createClient(url, anon, {
      global: {
        headers: {
          Authorization: authHeader
        }
      }
    });
    const { data: userData, error: userError } = await userClient.auth.getUser();
    if (userError || !userData.user) return jsonResponse({
      error: "invalid staff session"
    }, 401, cors);
    const actorId = userData.user.id;
    const admin = createClient(url, serviceRole, {
      auth: {
        persistSession: false,
        autoRefreshToken: false
      }
    });
    const { data: attempt, error: attemptError } = await admin.from("pos_card_terminal_attempts").select("id,terminal_id,created_by,status").eq("id", payload.attempt_id).maybeSingle();
    if (attemptError || !attempt) return jsonResponse({
      error: "card terminal attempt not found"
    }, 404, cors);
    if (attempt.created_by !== actorId) {
      const { data: roles } = await admin.from("staff_roles").select("role").eq("user_id", actorId);
      const elevated = (roles ?? []).some((row)=>row.role === "admin" || row.role === "finance");
      if (!elevated) return jsonResponse({
        error: "card terminal attempt access denied"
      }, 403, cors);
    }
    const { data: verifier, error: keyError } = await admin.from("pos_card_terminal_device_keys").select("public_key_spki_base64,key_sha256").eq("terminal_id", attempt.terminal_id).eq("device_id", payload.device_id).eq("is_active", true).is("revoked_at", null).maybeSingle();
    if (keyError || !verifier) return jsonResponse({
      error: "terminal device is not securely paired"
    }, 403, cors);
    let signature;
    let spki;
    try {
      signature = bytesFromBase64(signatureBase64);
      spki = bytesFromBase64(verifier.public_key_spki_base64);
    } catch  {
      return jsonResponse({
        error: "invalid terminal evidence encoding"
      }, 400, cors);
    }
    const publicKey = await crypto.subtle.importKey("spki", spki, {
      name: "RSASSA-PKCS1-v1_5",
      hash: "SHA-256"
    }, false, [
      "verify"
    ]);
    const canonical = JSON.stringify(payload);
    const verified = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, signature, new TextEncoder().encode(canonical));
    if (!verified) return jsonResponse({
      error: "terminal evidence signature rejected"
    }, 403, cors);
    const { data, error } = await admin.rpc("record_pos_card_terminal_result", {
      p_actor_user_id: actorId,
      p_attempt_id: payload.attempt_id,
      p_outcome: payload.outcome,
      p_terminal_transaction_id: payload.terminal_transaction_id,
      p_rrn: payload.rrn,
      p_authorization_code: payload.authorization_code,
      p_card_last4: payload.card_last4,
      p_card_scheme: payload.card_scheme,
      p_response_code: payload.response_code,
      p_response_message: payload.response_message
    });
    if (error) return jsonResponse({
      error: error.message
    }, 400, cors);
    return jsonResponse({
      verified: true,
      attempt: data
    }, 200, cors);
  } catch (error) {
    return jsonResponse({
      error: String(error instanceof Error ? error.message : error)
    }, 400, cors);
  }
});
