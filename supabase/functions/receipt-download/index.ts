/**
 * Public receipt PDF download by token.
 * POST { token } → short-lived Storage signed URL for bucket `customer-receipts`.
 *
 * Fail closed: missing/invalid token, missing path, or Storage error → 404/400.
 * Uses service_role only inside the Edge runtime — never exposed to the browser.
 * Tax-agnostic; no ZIMRA / FDMS / fiscal payloads.
 *
 * Called by apps/web `GET /receipts/[token]` with anon Bearer (verify_jwt = true).
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { corsHeaders, jsonResponse } from "../_shared/payment_edge.ts";

const BUCKET = "customer-receipts";
/** Short-lived redirect target for company-domain receipt links. */
const SIGNED_URL_TTL_SEC = 300;

function normalizeToken(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const token = raw.trim();
  if (token.length < 8 || token.length > 128) return null;
  if (/[^a-zA-Z0-9_-]/.test(token)) return null;
  return token;
}

function storageObjectPath(stored: string): string | null {
  const p = stored.trim();
  if (!p || p.includes("..")) return null;
  if (/zimra|fdms|fiscal/i.test(p)) return null;
  // Paths may have been stored with or without bucket prefix
  if (p.startsWith(`${BUCKET}/`)) return p.slice(BUCKET.length + 1);
  return p;
}

Deno.serve(async (req) => {
  const cors = corsHeaders(req);
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: cors });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "POST required" }, 405, cors);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!supabaseUrl || !serviceKey) {
    return jsonResponse({ error: "receipt service misconfigured" }, 503, cors);
  }

  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return jsonResponse({ error: "invalid JSON body" }, 400, cors);
  }

  const token = normalizeToken(
    body && typeof body === "object"
      ? (body as { token?: unknown }).token
      : null,
  );
  if (!token) {
    return jsonResponse({ error: "token required" }, 400, cors);
  }

  const admin = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: artifact, error: lookupErr } = await admin
    .from("receipt_pdf_artifacts")
    .select("id, pdf_storage_path, download_token")
    .eq("download_token", token)
    .maybeSingle();

  if (lookupErr) {
    console.error("receipt-download lookup:", lookupErr.message);
    return jsonResponse({ error: "receipt not found" }, 404, cors);
  }

  if (!artifact?.pdf_storage_path) {
    return jsonResponse({ error: "receipt not found" }, 404, cors);
  }

  const objectPath = storageObjectPath(artifact.pdf_storage_path);
  if (!objectPath) {
    return jsonResponse({ error: "receipt not found" }, 404, cors);
  }

  const { data: signed, error: signErr } = await admin.storage
    .from(BUCKET)
    .createSignedUrl(objectPath, SIGNED_URL_TTL_SEC);

  if (signErr || !signed?.signedUrl) {
    console.error("receipt-download sign:", signErr?.message ?? "no url");
    return jsonResponse({ error: "receipt not found" }, 404, cors);
  }

  return jsonResponse(
    {
      signed_url: signed.signedUrl,
      expires_in: SIGNED_URL_TTL_SEC,
    },
    200,
    cors,
  );
});
