/**
 * Short-lived HMAC proof tokens minted after OTP verify.
 * complete_signup / complete_login must present a valid, unconsumed proof.
 *
 * Secret: AUTH_OTP_PROOF_SECRET, else SUPABASE_SERVICE_ROLE_KEY (Edge-only).
 */

export const AUTH_OTP_PROOF_TTL_SEC = 10 * 60;
export const AUTH_OTP_MAX_ATTEMPTS = 5;

async function hmacSha256Hex(rawBody: string, secret: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
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

export type AuthOtpProofPayload = {
  /** auth_otp_proofs.id */
  pid: string;
  email: string | null;
  phone_e164: string | null;
  exp: number;
};

function b64urlEncode(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function b64urlDecode(s: string): Uint8Array {
  const pad = "=".repeat((4 - (s.length % 4)) % 4);
  const b64 = (s + pad).replace(/-/g, "+").replace(/_/g, "/");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function timingSafeEqualStr(a: string, b: string): boolean {
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

export function otpProofSecret(): string | null {
  const dedicated = Deno.env.get("AUTH_OTP_PROOF_SECRET")?.trim();
  if (dedicated) return dedicated;
  const sr = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  return sr || null;
}

/** True when Edge must never use local OTP stub (production / hosted heuristic). */
export function isAuthOtpProductionEnv(): boolean {
  const env = (Deno.env.get("ENVIRONMENT") ?? Deno.env.get("ENV") ?? "")
    .trim()
    .toLowerCase();
  if (env === "production" || env === "prod") return true;
  const url = (Deno.env.get("SUPABASE_URL") ?? "").trim().toLowerCase();
  // Local stack uses localhost / 127.0.0.1 / kong; hosted projects use *.supabase.co
  if (
    url.includes("127.0.0.1") ||
    url.includes("localhost") ||
    url.includes(":54321")
  ) {
    return false;
  }
  if (url.includes(".supabase.co")) return true;
  return false;
}

export async function mintOtpProofToken(
  payload: AuthOtpProofPayload,
): Promise<string | null> {
  const secret = otpProofSecret();
  if (!secret) return null;
  const body = b64urlEncode(
    new TextEncoder().encode(JSON.stringify(payload)),
  );
  const sig = await hmacSha256Hex(body, secret);
  return `${body}.${sig}`;
}

export async function parseOtpProofToken(
  token: string,
): Promise<
  | { ok: true; payload: AuthOtpProofPayload }
  | { ok: false; error: string }
> {
  const secret = otpProofSecret();
  if (!secret) {
    return { ok: false, error: "OTP proof secret unavailable" };
  }
  const parts = token.trim().split(".");
  if (parts.length !== 2 || !parts[0] || !parts[1]) {
    return { ok: false, error: "malformed OTP proof token" };
  }
  const [body, sig] = parts;
  const expected = await hmacSha256Hex(body, secret);
  if (!timingSafeEqualStr(expected.toLowerCase(), sig.toLowerCase())) {
    return { ok: false, error: "invalid OTP proof signature" };
  }
  let raw: unknown;
  try {
    raw = JSON.parse(new TextDecoder().decode(b64urlDecode(body)));
  } catch {
    return { ok: false, error: "invalid OTP proof payload" };
  }
  if (!raw || typeof raw !== "object") {
    return { ok: false, error: "invalid OTP proof payload" };
  }
  const o = raw as Record<string, unknown>;
  const pid = typeof o.pid === "string" ? o.pid : "";
  const exp = typeof o.exp === "number" ? o.exp : NaN;
  if (!/^[0-9a-f-]{36}$/i.test(pid) || !Number.isFinite(exp)) {
    return { ok: false, error: "invalid OTP proof fields" };
  }
  if (Date.now() / 1000 > exp) {
    return { ok: false, error: "OTP proof expired" };
  }
  return {
    ok: true,
    payload: {
      pid,
      email: typeof o.email === "string" ? o.email : null,
      phone_e164: typeof o.phone_e164 === "string" ? o.phone_e164 : null,
      exp,
    },
  };
}
