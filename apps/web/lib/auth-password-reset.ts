import type { SupabaseClient } from "@gtr/supabase-client";
import { normalizeE164, normalizeReceiptEmail } from "@gtr/shared";

export type PasswordResetRequestResult = {
  ok: true;
  stub: boolean;
  stubCode?: string;
  channels: Record<string, unknown>;
};

export type PasswordResetVerifyResult = {
  ok: true;
  userId: string;
};

export type PasswordResetError = {
  ok: false;
  error: string;
  status?: number;
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

/** Request password-reset OTP (email and/or phone) via Edge `request-password-reset`. */
export async function requestPasswordReset(
  client: SupabaseClient,
  args: { email?: string | null; phoneE164?: string | null },
): Promise<PasswordResetRequestResult | PasswordResetError> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  if (!email && !phone) {
    return { ok: false, error: "Enter email and/or phone (E.164)." };
  }

  const { data, error } = await client.functions.invoke("request-password-reset", {
    body: {
      ...(email ? { email } : {}),
      ...(phone ? { phone_e164: phone } : {}),
    },
  });

  if (error) {
    const { body, status } = await readFunctionsErrorBody(error);
    const msg = edgeErrorMessage(body, error.message || "Reset request failed");
    return {
      ok: false,
      error: msg,
      status,
      failClosed: isFailClosedMessage(msg, status),
    };
  }

  if (!data || typeof data !== "object") {
    return { ok: false, error: "Unexpected reset response." };
  }
  const body = data as Record<string, unknown>;
  if (body.ok === false || (typeof body.error === "string" && body.error)) {
    const msg = edgeErrorMessage(body, "Reset request refused");
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

/** Verify reset OTP + set new password via Edge `verify-password-reset`. */
export async function verifyPasswordReset(
  client: SupabaseClient,
  args: {
    email?: string | null;
    phoneE164?: string | null;
    code: string;
    newPassword: string;
  },
): Promise<PasswordResetVerifyResult | PasswordResetError> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  const code = args.code.trim();
  if (!email && !phone) {
    return { ok: false, error: "Enter email and/or phone (E.164)." };
  }
  if (!/^[0-9]{6}$/.test(code)) {
    return { ok: false, error: "Enter the 6-digit code." };
  }
  if (args.newPassword.length < 8) {
    return { ok: false, error: "Password must be at least 8 characters." };
  }

  const { data, error } = await client.functions.invoke("verify-password-reset", {
    body: {
      ...(email ? { email } : {}),
      ...(phone ? { phone_e164: phone } : {}),
      code,
      new_password: args.newPassword,
    },
  });

  if (error) {
    const { body, status } = await readFunctionsErrorBody(error);
    const msg = edgeErrorMessage(body, error.message || "Reset verify failed");
    return {
      ok: false,
      error: msg,
      status,
      failClosed: isFailClosedMessage(msg, status),
    };
  }

  if (!data || typeof data !== "object") {
    return { ok: false, error: "Unexpected verify response." };
  }
  const body = data as Record<string, unknown>;
  if (body.ok === false || (typeof body.error === "string" && body.error)) {
    return {
      ok: false,
      error: edgeErrorMessage(body, "Could not reset password"),
    };
  }

  const userId =
    typeof body.user_id === "string" && body.user_id.trim()
      ? body.user_id.trim()
      : "";
  if (!userId) {
    return { ok: false, error: "Unexpected verify response." };
  }
  return { ok: true, userId };
}
