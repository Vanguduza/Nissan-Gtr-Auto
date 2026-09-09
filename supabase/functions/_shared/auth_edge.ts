import {
  createClient,
  type Session,
  type SupabaseClient,
  type User,
} from "npm:@supabase/supabase-js@2.105.0";

const ALLOWED_ORIGINS = new Set([
  "https://nissangtrauto.co.zw",
  "https://www.nissangtrauto.co.zw",
  "http://localhost:3000",
  "http://127.0.0.1:3000",
  "http://localhost:5173",
  "http://127.0.0.1:5173",
]);

const DEFAULT_ORIGIN = "https://nissangtrauto.co.zw";

export type AuthEdgeOperation =
  | "signup_request"
  | "signup_verify"
  | "login"
  | "password_reset_request"
  | "password_reset_verify";

export class AuthEdgeError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string,
  ) {
    super(message);
    this.name = "AuthEdgeError";
  }
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new AuthEdgeError(`${name} is not configured`, 503, "AUTH_CONFIG_MISSING");
  return value;
}

export function corsHeaders(req: Request): Record<string, string> {
  const origin = req.headers.get("Origin");
  const allowed = origin && ALLOWED_ORIGINS.has(origin) ? origin : DEFAULT_ORIGIN;
  return {
    "Access-Control-Allow-Origin": allowed,
    "Access-Control-Allow-Headers":
      "authorization, x-client-info, apikey, content-type, x-device-id",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    Vary: "Origin",
  };
}

export function jsonResponse(
  req: Request,
  body: unknown,
  status = 200,
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
      ...corsHeaders(req),
    },
  });
}

export function normalizeEmail(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const value = raw.trim().toLowerCase();
  if (!value || value.length > 320 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    return null;
  }
  return value;
}

export function normalizeE164(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  let value = raw.trim().replace(/[\s\-()]/g, "");
  if (!value) return null;
  if (!/^\+?[0-9]{8,15}$/.test(value)) return null;
  if (!value.startsWith("+")) value = `+${value}`;
  return value;
}

export function normalizeCode(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const value = raw.trim();
  return /^[0-9]{6,10}$/.test(value) ? value : null;
}

export function normalizeDeviceId(req: Request, raw?: unknown): string | null {
  const supplied = typeof raw === "string" ? raw.trim() : "";
  const header = req.headers.get("x-device-id")?.trim() ?? "";
  const value = supplied || header;
  if (!value) return null;
  return value.slice(0, 256);
}

function requestIp(req: Request): string | null {
  const cf = req.headers.get("cf-connecting-ip")?.trim();
  if (cf) return cf.slice(0, 128);
  const real = req.headers.get("x-real-ip")?.trim();
  if (real) return real.slice(0, 128);
  const forwarded = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim();
  return forwarded ? forwarded.slice(0, 128) : null;
}

async function sha256Hex(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(`gtr-auth-edge-v1:${value}`);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export function serviceClient(): SupabaseClient {
  return createClient(requiredEnv("SUPABASE_URL"), requiredEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

export function anonClient(): SupabaseClient {
  return createClient(requiredEnv("SUPABASE_URL"), requiredEnv("SUPABASE_ANON_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
  });
}

export function userClient(): SupabaseClient {
  return createClient(requiredEnv("SUPABASE_URL"), requiredEnv("SUPABASE_ANON_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
  });
}

export async function enforceAuthRateLimit(
  service: SupabaseClient,
  req: Request,
  operation: AuthEdgeOperation,
  identifier: string,
  deviceId?: unknown,
): Promise<void> {
  const identifierHash = await sha256Hex(identifier.toLowerCase());
  const ip = requestIp(req);
  const ipHash = ip ? await sha256Hex(ip) : null;
  const device = normalizeDeviceId(req, deviceId);
  const deviceHash = device ? await sha256Hex(device) : null;

  const { error } = await service.rpc("enforce_auth_edge_rate_limit", {
    p_operation: operation,
    p_identifier_hash: identifierHash,
    p_ip_hash: ipHash,
    p_device_hash: deviceHash,
  });
  if (!error) return;

  const message = error.message ?? "authentication request rejected";
  if (message.includes("AUTH_RATE_LIMITED")) {
    const retry = /retry_after_seconds=(\d+)/.exec(message)?.[1];
    throw new AuthEdgeError(
      retry ? `Too many attempts. Retry in ${retry} seconds.` : "Too many attempts. Try again later.",
      429,
      "AUTH_RATE_LIMITED",
    );
  }
  throw new AuthEdgeError("Authentication rate-limit service unavailable", 503, "AUTH_RATE_LIMIT_ERROR");
}

export async function resolveAuthUserId(
  service: SupabaseClient,
  email: string | null,
  phone: string | null,
): Promise<string | null> {
  const { data, error } = await service.rpc("resolve_auth_user", {
    p_email: email,
    p_phone_e164: phone,
  });
  if (error) {
    if ((error.message ?? "").includes("AUTH_IDENTIFIER_CONFLICT")) {
      throw new AuthEdgeError("The email and phone number belong to different accounts", 409, "AUTH_IDENTIFIER_CONFLICT");
    }
    throw new AuthEdgeError("Unable to resolve account", 503, "AUTH_LOOKUP_FAILED");
  }
  return typeof data === "string" && data ? data : null;
}

export async function getAuthUser(
  service: SupabaseClient,
  userId: string,
): Promise<User> {
  const { data, error } = await service.auth.admin.getUserById(userId);
  if (error || !data.user) {
    throw new AuthEdgeError("Account is unavailable", 404, "AUTH_USER_NOT_FOUND");
  }
  return data.user;
}

export function isPendingSignup(user: User): boolean {
  return user.app_metadata?.gtr_signup_pending === true;
}

export function isEmailConfirmed(user: User): boolean {
  return Boolean(user.email_confirmed_at);
}

export function isPhoneConfirmed(user: User): boolean {
  return Boolean(user.phone_confirmed_at);
}

export function sessionPayload(session: Session, user: User) {
  return {
    user_id: user.id,
    access_token: session.access_token,
    refresh_token: session.refresh_token,
    expires_in: session.expires_in,
    expires_at: session.expires_at ?? null,
    token_type: session.token_type,
  };
}

export function authFailureStatus(error: { status?: number; code?: string; message?: string } | null): number {
  if (!error) return 400;
  if (error.status === 429) return 429;
  if ((error.status ?? 0) >= 500) return 503;
  return 400;
}

export function authFailureCode(error: { code?: string } | null, fallback: string): string {
  return error?.code?.trim() || fallback;
}
