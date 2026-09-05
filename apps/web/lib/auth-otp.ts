import type { SupabaseClient } from "@gtr/supabase-client";
import { normalizeE164, normalizeReceiptEmail } from "@gtr/shared";

export type AuthOtpRequestResult = {
  ok: true;
  userId?: string;
  channels: Record<string, { sent?: boolean; error?: string }>;
  verificationRequired: { email: boolean; phone: boolean };
};

export type AuthOtpVerifyResult = {
  ok: true;
  verified: { email: boolean; phone: boolean | null };
  signupReady: boolean;
};

export type AuthOtpSessionResult = {
  ok: true;
  userId: string;
  accessToken: string;
  refreshToken: string;
  expiresIn?: number;
  email: string | null;
  phone_e164: string | null;
  customerId?: string;
};

export type AuthOtpError = {
  ok: false;
  error: string;
  status?: number;
  code?: string;
  failClosed?: boolean;
};

function edgeErrorMessage(body: unknown, fallback: string): string {
  if (!body || typeof body !== "object") return fallback;
  const o = body as Record<string, unknown>;
  if (typeof o.error === "string" && o.error.trim()) return o.error;
  if (typeof o.message === "string" && o.message.trim()) return o.message;
  return fallback;
}

function edgeErrorCode(body: unknown): string | undefined {
  if (!body || typeof body !== "object") return undefined;
  const code = (body as Record<string, unknown>).code;
  return typeof code === "string" && code.trim() ? code.trim() : undefined;
}

function isFailClosedMessage(msg: string, status?: number): boolean {
  if (status === 503) return true;
  return /unavail|not configured|misconfigured|delivery|provider|service error|fail.?closed/i.test(msg);
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

function authEdgeError(body: unknown, fallback: string, status?: number): AuthOtpError {
  const msg = edgeErrorMessage(body, fallback);
  return {
    ok: false,
    error: msg,
    status,
    code: edgeErrorCode(body),
    failClosed: isFailClosedMessage(msg, status),
  };
}

/**
 * Starts or resumes customer signup and asks Supabase Auth to send exactly one
 * verification challenge. Email and phone are intentionally independent.
 */
export async function requestAuthOtp(
  client: SupabaseClient,
  args: {
    email: string;
    phoneE164?: string | null;
    channel?: "email" | "phone";
    fullName?: string | null;
    deviceId?: string | null;
  },
): Promise<AuthOtpRequestResult | AuthOtpError> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  const channel = args.channel ?? "email";
  if (!email) return { ok: false, error: "Enter a valid email address." };
  if (channel === "phone" && !phone) {
    return { ok: false, error: "Enter a valid phone number (E.164)." };
  }

  const { data, error } = await client.functions.invoke("auth-otp", {
    body: {
      action: "request",
      channel,
      email,
      ...(phone ? { phone_e164: phone } : {}),
      ...(args.fullName?.trim() ? { full_name: args.fullName.trim() } : {}),
      ...(args.deviceId?.trim() ? { device_id: args.deviceId.trim() } : {}),
    },
  });

  if (error) {
    const parsed = await readFunctionsErrorBody(error);
    return authEdgeError(parsed.body, error.message || "Verification request failed", parsed.status);
  }
  if (!data || typeof data !== "object") {
    return { ok: false, error: "Unexpected verification response." };
  }
  const body = data as Record<string, unknown>;
  if (body.ok !== true) return authEdgeError(body, "Verification request refused");
  const required = body.verification_required as Record<string, unknown> | undefined;
  return {
    ok: true,
    userId: typeof body.user_id === "string" ? body.user_id : undefined,
    channels:
      body.channels && typeof body.channels === "object"
        ? (body.channels as Record<string, { sent?: boolean; error?: string }>)
        : {},
    verificationRequired: {
      email: required?.email !== false,
      phone: required?.phone === true,
    },
  };
}

/** Verify one Supabase Auth-generated signup code at a time. */
export async function verifyAuthOtp(
  client: SupabaseClient,
  args: {
    email: string;
    phoneE164?: string | null;
    channel: "email" | "phone";
    code: string;
    deviceId?: string | null;
  },
): Promise<AuthOtpVerifyResult | AuthOtpError> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  const code = args.code.trim();
  if (!email) return { ok: false, error: "Enter a valid email address." };
  if (args.channel === "phone" && !phone) {
    return { ok: false, error: "Enter a valid phone number (E.164)." };
  }
  if (!/^[0-9]{6,10}$/.test(code)) {
    return { ok: false, error: "Enter the verification code." };
  }

  const { data, error } = await client.functions.invoke("auth-otp", {
    body: {
      action: "verify",
      channel: args.channel,
      code,
      email,
      ...(phone ? { phone_e164: phone } : {}),
      ...(args.deviceId?.trim() ? { device_id: args.deviceId.trim() } : {}),
    },
  });

  if (error) {
    const parsed = await readFunctionsErrorBody(error);
    return authEdgeError(parsed.body, error.message || "Verification failed", parsed.status);
  }
  if (!data || typeof data !== "object") {
    return { ok: false, error: "Unexpected verification response." };
  }
  const body = data as Record<string, unknown>;
  if (body.ok !== true) return authEdgeError(body, "Verification failed");
  const verified = body.verified as Record<string, unknown> | undefined;
  return {
    ok: true,
    verified: {
      email: verified?.email === true,
      phone: verified?.phone === null ? null : verified?.phone === true,
    },
    signupReady: body.signup_ready === true,
  };
}

/** Finish a verified pending Supabase Auth signup and mint its normal session. */
export async function completeAuthSignup(
  client: SupabaseClient,
  args: {
    email: string;
    password: string;
    fullName?: string | null;
    phoneE164?: string | null;
    deviceId?: string | null;
  },
): Promise<AuthOtpSessionResult | AuthOtpError> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  if (!email) return { ok: false, error: "Email is required." };
  if (args.password.length < 8) {
    return { ok: false, error: "Password must be at least 8 characters." };
  }

  const { data, error } = await client.functions.invoke("auth-otp", {
    body: {
      action: "complete_signup",
      email,
      password: args.password,
      ...(phone ? { phone_e164: phone } : {}),
      ...(args.fullName?.trim() ? { full_name: args.fullName.trim() } : {}),
      ...(args.deviceId?.trim() ? { device_id: args.deviceId.trim() } : {}),
    },
  });
  return readSessionResult(data, error, "Signup failed");
}

/**
 * Returning login always goes through the Auth Edge so project-level
 * identifier/IP/device rate limits are applied before Supabase Auth's password
 * grant. Supabase Auth remains the only session authority.
 */
export async function signInWithEmailOrPhone(
  client: SupabaseClient,
  args: {
    email?: string | null;
    phoneE164?: string | null;
    password: string;
    deviceId?: string | null;
  },
): Promise<AuthOtpSessionResult | AuthOtpError> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  if (!email && !phone) return { ok: false, error: "Enter email and/or phone (E.164)." };
  if (!args.password) return { ok: false, error: "Password is required." };

  const { data, error } = await client.functions.invoke("auth-otp", {
    body: {
      action: "complete_login",
      password: args.password,
      ...(email ? { email } : {}),
      ...(phone ? { phone_e164: phone } : {}),
      ...(args.deviceId?.trim() ? { device_id: args.deviceId.trim() } : {}),
    },
  });
  const session = await readSessionResult(data, error, "Login failed");
  if (!session.ok) return session;

  const { error: sessionErr } = await client.auth.setSession({
    access_token: session.accessToken,
    refresh_token: session.refreshToken,
  });
  if (sessionErr) return { ok: false, error: sessionErr.message };
  return session;
}

/** @deprecated Use signInWithEmailOrPhone. */
export async function completeAuthLogin(
  client: SupabaseClient,
  args: { email: string; password: string; phoneE164?: string | null },
): Promise<AuthOtpSessionResult | AuthOtpError> {
  return signInWithEmailOrPhone(client, args);
}

async function readSessionResult(
  data: unknown,
  error: { context?: unknown; message?: string } | null,
  fallback: string,
): Promise<AuthOtpSessionResult | AuthOtpError> {
  if (error) {
    const parsed = await readFunctionsErrorBody(error);
    return authEdgeError(parsed.body, error.message || fallback, parsed.status);
  }
  if (!data || typeof data !== "object") {
    return { ok: false, error: `Unexpected response: ${fallback}` };
  }
  const body = data as Record<string, unknown>;
  if (body.ok !== true) return authEdgeError(body, fallback);
  const accessToken = typeof body.access_token === "string" ? body.access_token : "";
  const refreshToken = typeof body.refresh_token === "string" ? body.refresh_token : "";
  const userId = typeof body.user_id === "string" ? body.user_id : "";
  if (!accessToken || !refreshToken || !userId) {
    return { ok: false, error: "Session tokens missing from Auth Edge response." };
  }
  return {
    ok: true,
    userId,
    accessToken,
    refreshToken,
    expiresIn: typeof body.expires_in === "number" ? body.expires_in : undefined,
    email: typeof body.email === "string" ? body.email : null,
    phone_e164: typeof body.phone_e164 === "string" ? body.phone_e164 : null,
    customerId: typeof body.customer_id === "string" ? body.customer_id : undefined,
  };
}
