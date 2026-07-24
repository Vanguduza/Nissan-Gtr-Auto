/**
 * Shared helpers for ContiPay / Paynow initiate + webhook stubs.
 * Privileged paths: never Access-Control-Allow-Origin: *; never reflect arbitrary Origin.
 */

const ALLOWED_ORIGINS = new Set([
  "https://nissangtrauto.co.zw",
  "https://www.nissangtrauto.co.zw",
  "http://localhost:3000",
  "http://127.0.0.1:3000",
  "http://localhost:5173",
  "http://127.0.0.1:5173",
]);

/** Default ACAO when Origin absent or not allowlisted (no reflection of arbitrary origins). */
const DEFAULT_ALLOWED_ORIGIN = "https://nissangtrauto.co.zw";

export function corsHeaders(req: Request): Record<string, string> {
  const origin = req.headers.get("Origin");
  const allow =
    origin && ALLOWED_ORIGINS.has(origin) ? origin : DEFAULT_ALLOWED_ORIGIN;
  return {
    "Access-Control-Allow-Origin": allow,
    "Access-Control-Allow-Headers":
      "authorization, x-client-info, apikey, content-type",
    Vary: "Origin",
  };
}

export function jsonResponse(
  body: unknown,
  status: number,
  extraHeaders: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...extraHeaders },
  });
}

/** Constant-time string compare (length mismatch always false; no early exit on content). */
export function timingSafeEqualStr(a: string, b: string): boolean {
  const enc = new TextEncoder();
  const bufA = enc.encode(a);
  const bufB = enc.encode(b);
  const len = Math.max(bufA.length, bufB.length);
  let diff = bufA.length ^ bufB.length;
  for (let i = 0; i < len; i++) {
    diff |= (bufA[i] ?? 0) ^ (bufB[i] ?? 0);
  }
  return diff === 0;
}

export async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/**
 * STUB expected Paynow hash — NOT the real Paynow algorithm.
 * Replace with provider field concatenation + integration-key hash when merchant keys/docs arrive.
 */
export async function stubPaynowExpectedHash(
  rawBody: string,
  integrationKey: string,
): Promise<string> {
  return sha256Hex(`paynow-stub:${integrationKey}:${rawBody}`);
}

/**
 * STUB ContiPay HMAC (hex) — replace with provider-documented scheme when secrets arrive.
 */
export async function stubContipayExpectedSig(
  rawBody: string,
  hmacSecret: string,
): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(hmacSecret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(rawBody),
  );
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export function requireBearerJwt(req: Request): string | null {
  const auth = req.headers.get("Authorization");
  if (!auth || !auth.startsWith("Bearer ") || auth.length < 16) {
    return null;
  }
  return auth;
}

export function isLocalUnverifiedAllowed(envFlag: string): boolean {
  return Deno.env.get(envFlag) === "1";
}
