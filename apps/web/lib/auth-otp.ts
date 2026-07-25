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
  /** Short-lived server proof — required for complete_signup / complete_login. */
  proofToken: string;
  proofExpiresAt?: string;
};

export type AuthOtpSessionResult = {
  ok: true;
  userId: string;
  accessToken: string;
  refreshToken: string;
  expiresIn?: number;
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

function isFailClosedMessage(msg: string, status?: number): boolean {
  if (status === 503) return true;
  return /refus|unavail|gateway|not configured|AUTH_OTP|fail.?closed|misconfigured/i.test(
    msg,
  );
}

async function readFunctionsErrorBody(
  error: { context?: unknown; message?: string } | null,
): Promise<{ body: unknown; status?: number }> {
  if (!error) return { body: null };
  const ctx = error.context as
    | Response
    | { json?: () => Promise<unknown>; body?: unknown; status?: number }
    | null
    | undefined;
  if (!ctx) return { body: null };
  if (typeof Response !== "undefined" && ctx instanceof Response) {
    try {
      return { body: await ctx.clone().json(), status: ctx.status };
    } catch {
      return { status: ctx.status, body: null };
    }
  }
  if (typeof ctx.json === "function") {
    try {
      return {
        body: await ctx.json(),
        status: typeof ctx.status === "number" ? ctx.status : undefined,
      };
    } catch {
      return {
        body: null,
        status: typeof ctx.status === "number" ? ctx.status : undefined,
      };
    }
  }
  if ("body" in ctx && ctx.body != null) {
    return {
      body: ctx.body,
      status: typeof ctx.status === "number" ? ctx.status : undefined,
    };
  }
  return { body: null };
}

/**
 * Customer OTP via Edge `auth-otp` — signup and contact confirmation only.
 * Returning logins use {@link signInWithEmailOrPhone} (password; no OTP).
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
    const { body, status } = await readFunctionsErrorBody(error);
    const msg = edgeErrorMessage(body, error.message || "OTP request failed");
    return {
      ok: false,
      error: msg,
      status,
      failClosed: isFailClosedMessage(msg, status),
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
      failClosed: isFailClosedMessage(msg, status),
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
    const { body, status } = await readFunctionsErrorBody(error);
    const msg = edgeErrorMessage(body, error.message || "OTP verify failed");
    return {
      ok: false,
      error: msg,
      status,
      failClosed: isFailClosedMessage(msg, status),
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
  const proofToken =
    typeof body.proof_token === "string" ? body.proof_token.trim() : "";
  if (!proofToken) {
    return {
      ok: false,
      error: "OTP verify did not return proof_token — cannot complete auth.",
    };
  }

  return {
    ok: true,
    verified: true,
    email: typeof body.email === "string" ? body.email : email,
    phone_e164:
      typeof body.phone_e164 === "string" ? body.phone_e164 : phone,
    proofToken,
    proofExpiresAt:
      typeof body.proof_expires_at === "string"
        ? body.proof_expires_at
        : undefined,
  };
}

/**
 * Create Auth user + session via Edge after OTP proof (public signup disabled).
 */
export async function completeAuthSignup(
  client: SupabaseClient,
  args: {
    email: string;
    password: string;
    proofToken: string;
    fullName?: string | null;
    phoneE164?: string | null;
  },
): Promise<AuthOtpSessionResult | AuthOtpError> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  if (!email) return { ok: false, error: "Email is required." };
  if (!args.proofToken.trim()) {
    return { ok: false, error: "OTP proof missing — verify OTP again." };
  }
  if (args.password.length < 8) {
    return { ok: false, error: "Password must be at least 8 characters." };
  }

  const { data, error } = await client.functions.invoke("auth-otp", {
    body: {
      action: "complete_signup",
      email,
      password: args.password,
      proof_token: args.proofToken.trim(),
      ...(phone ? { phone_e164: phone } : {}),
      ...(args.fullName?.trim()
        ? { full_name: args.fullName.trim() }
        : {}),
    },
  });

  return readSessionResult(data, error, "Signup failed");
}

/**
 * Exchange OTP proof + password for a session (server-side gate).
 */
export async function completeAuthLogin(
  client: SupabaseClient,
  args: {
    email: string;
    password: string;
    proofToken: string;
    phoneE164?: string | null;
  },
): Promise<AuthOtpSessionResult | AuthOtpError> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  if (!email) return { ok: false, error: "Email is required." };
  if (!args.proofToken.trim()) {
    return { ok: false, error: "OTP proof missing — verify OTP again." };
  }
  if (!args.password) return { ok: false, error: "Password is required." };

  const { data, error } = await client.functions.invoke("auth-otp", {
    body: {
      action: "complete_login",
      email,
      password: args.password,
      proof_token: args.proofToken.trim(),
      ...(phone ? { phone_e164: phone } : {}),
    },
  });

  return readSessionResult(data, error, "Login failed");
}

async function readSessionResult(
  data: unknown,
  error: { context?: unknown; message?: string } | null,
  fallback: string,
): Promise<AuthOtpSessionResult | AuthOtpError> {
  if (error) {
    const { body, status } = await readFunctionsErrorBody(error);
    const msg = edgeErrorMessage(body, error.message || fallback);
    return {
      ok: false,
      error: msg,
      status,
      failClosed: isFailClosedMessage(msg, status),
    };
  }
  if (!data || typeof data !== "object") {
    return { ok: false, error: `Unexpected response: ${fallback}` };
  }
  const body = data as Record<string, unknown>;
  if (body.ok === false || (typeof body.error === "string" && body.error)) {
    const msg = edgeErrorMessage(body, fallback);
    const status = typeof body.status === "number" ? body.status : undefined;
    return {
      ok: false,
      error: msg,
      status,
      failClosed: isFailClosedMessage(msg, status),
    };
  }
  const accessToken =
    typeof body.access_token === "string" ? body.access_token : "";
  const refreshToken =
    typeof body.refresh_token === "string" ? body.refresh_token : "";
  const userId = typeof body.user_id === "string" ? body.user_id : "";
  if (!accessToken || !refreshToken || !userId) {
    return { ok: false, error: "Session tokens missing from Edge response." };
  }
  return {
    ok: true,
    userId,
    accessToken,
    refreshToken,
    expiresIn:
      typeof body.expires_in === "number" ? body.expires_in : undefined,
    email: typeof body.email === "string" ? body.email : null,
    phone_e164: typeof body.phone_e164 === "string" ? body.phone_e164 : null,
  };
}
