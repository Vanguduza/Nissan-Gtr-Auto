import type { SupabaseClient } from "@gtr/supabase-client";
import { normalizeE164, normalizeReceiptEmail } from "@gtr/shared";

export type AuthOtpRequestResult = {
  ok: true;
  stub: boolean;
  /** Present only when Edge local stub is enabled — never hardcode in the client. */
  stubCode?: string;
  channels: Record<string, unknown>;
};

export type AuthOtpVerifyResult = {
  ok: true;
  verified: true;
  email: string | null;
  phone_e164: string | null;
};

export type AuthOtpError = {
  ok: false;
  error: string;
  status?: number;
  /** True when Edge refused send (missing gateway keys, no local stub). */
  failClosed?: boolean;
};

function edgeErrorMessage(body: unknown, fallback: string): string {
  if (!body || typeof body !== "object") return fallback;
  const o = body as Record<string, unknown>;
  if (typeof o.error === "string" && o.error.trim()) return o.error;
  if (typeof o.message === "string" && o.message.trim()) return o.message;
  return fallback;
}

/**
 * Customer signup/login OTP via Edge `auth-otp`.
 * Fail-closed when server returns 503 (no gateway keys / no local stub flag).
 * Never treat stub code as a client-side production default.
 */
export async function requestAuthOtp(
  client: SupabaseClient,
  args: { email?: string | null; phoneE164?: string | null },
): Promise<AuthOtpRequestResult | AuthOtpError> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  if (!email && !phone) {
    return { ok: false, error: "Enter email and/or phone (E.164)." };
  }

  const { data, error } = await client.functions.invoke("auth-otp", {
    body: {
      action: "request",
      ...(email ? { email } : {}),
      ...(phone ? { phone_e164: phone } : {}),
    },
  });

  if (error) {
    const status =
      typeof (error as { context?: { status?: number } }).context?.status ===
      "number"
        ? (error as { context: { status: number } }).context.status
        : undefined;
    const msg = error.message || "OTP request failed";
    return {
      ok: false,
      error: msg,
      status,
      failClosed: status === 503 || /refus|unavail|gateway|not configured|fail.?closed/i.test(msg),
    };
  }

  if (!data || typeof data !== "object") {
    return { ok: false, error: "Unexpected OTP response." };
  }
  const body = data as Record<string, unknown>;
  if (body.ok === false || (typeof body.error === "string" && body.error)) {
    const msg = edgeErrorMessage(body, "OTP request refused");
    const status = typeof body.status === "number" ? body.status : undefined;
    return {
      ok: false,
      error: msg,
      status,
      failClosed:
        status === 503 ||
        /refus|unavail|gateway|not configured|AUTH_OTP|fail.?closed/i.test(msg),
    };
  }

  const stub = Boolean(body.stub);
  const stubCode =
    typeof body.stub_code === "string" && body.stub_code.trim()
      ? body.stub_code.trim()
      : undefined;

  return {
    ok: true,
    stub,
    stubCode,
    channels:
      body.channels && typeof body.channels === "object"
        ? (body.channels as Record<string, unknown>)
        : {},
  };
}

export async function verifyAuthOtp(
  client: SupabaseClient,
  args: {
    email?: string | null;
    phoneE164?: string | null;
    code: string;
  },
): Promise<AuthOtpVerifyResult | AuthOtpError> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  const code = args.code.trim();
  if (!email && !phone) {
    return { ok: false, error: "Enter email and/or phone (E.164)." };
  }
  if (!/^[0-9]{6}$/.test(code)) {
    return { ok: false, error: "Enter the 6-digit code." };
  }

  const { data, error } = await client.functions.invoke("auth-otp", {
    body: {
      action: "verify",
      code,
      ...(email ? { email } : {}),
      ...(phone ? { phone_e164: phone } : {}),
    },
  });

  if (error) {
    const status =
      typeof (error as { context?: { status?: number } }).context?.status ===
      "number"
        ? (error as { context: { status: number } }).context.status
        : undefined;
    const msg = error.message || "OTP verify failed";
    return {
      ok: false,
      error: msg,
      status,
      failClosed: status === 503,
    };
  }

  if (!data || typeof data !== "object") {
    return { ok: false, error: "Unexpected OTP verify response." };
  }
  const body = data as Record<string, unknown>;
  if (body.ok === false || body.verified !== true) {
    return {
      ok: false,
      error: edgeErrorMessage(body, "OTP verification failed"),
    };
  }

  return {
    ok: true,
    verified: true,
    email: typeof body.email === "string" ? body.email : email,
    phone_e164:
      typeof body.phone_e164 === "string" ? body.phone_e164 : phone,
  };
}
