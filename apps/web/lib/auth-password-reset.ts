import type { SupabaseClient } from "@gtr/supabase-client";
import { normalizeE164, normalizeReceiptEmail } from "@gtr/shared";

export type PasswordResetChannel = "email" | "phone";

export type PasswordResetResult =
  | { ok: true; message: string; channel: PasswordResetChannel }
  | { ok: false; error: string; status?: number; code?: string };

export type PasswordResetVerifyResult =
  | {
      ok: true;
      accessToken: string;
      refreshToken: string;
      userId: string;
      expiresIn?: number;
    }
  | { ok: false; error: string; status?: number; code?: string };

async function parseFunctionError(
  error: { context?: unknown; message?: string } | null,
  fallback: string,
): Promise<{ error: string; status?: number; code?: string }> {
  if (!error) return { error: fallback };
  const ctx = error.context as Response | null | undefined;
  if (typeof Response !== "undefined" && ctx instanceof Response) {
    try {
      const body = (await ctx.clone().json()) as Record<string, unknown>;
      return {
        error:
          typeof body.error === "string" && body.error.trim()
            ? body.error
            : error.message || fallback,
        status: ctx.status,
        code: typeof body.code === "string" ? body.code : undefined,
      };
    } catch {
      return { error: error.message || fallback, status: ctx.status };
    }
  }
  return { error: error.message || fallback };
}

export async function requestPasswordReset(
  client: SupabaseClient,
  args: {
    email?: string | null;
    phoneE164?: string | null;
    channel?: PasswordResetChannel;
    redirectTo?: string | null;
    deviceId?: string | null;
  },
): Promise<PasswordResetResult> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  const channel = args.channel ?? (email ? "email" : "phone");
  if (channel === "email" && !email) return { ok: false, error: "Enter a valid email address." };
  if (channel === "phone" && !phone) return { ok: false, error: "Enter a valid phone number (E.164)." };

  const { data, error } = await client.functions.invoke("request-password-reset", {
    body: {
      channel,
      ...(email ? { email } : {}),
      ...(phone ? { phone_e164: phone } : {}),
      ...(args.redirectTo ? { redirect_to: args.redirectTo } : {}),
      ...(args.deviceId?.trim() ? { device_id: args.deviceId.trim() } : {}),
    },
  });
  if (error) {
    const parsed = await parseFunctionError(error, "Password reset request failed");
    return { ok: false, ...parsed };
  }
  if (!data || typeof data !== "object") return { ok: false, error: "Unexpected password reset response." };
  const body = data as Record<string, unknown>;
  if (body.ok !== true) {
    return {
      ok: false,
      error: typeof body.error === "string" ? body.error : "Password reset request failed",
      code: typeof body.code === "string" ? body.code : undefined,
    };
  }
  return {
    ok: true,
    channel: body.channel === "phone" ? "phone" : "email",
    message:
      typeof body.message === "string"
        ? body.message
        : "If an account matches those details, recovery instructions have been sent.",
  };
}

export async function verifyPasswordReset(
  client: SupabaseClient,
  args: {
    email?: string | null;
    phoneE164?: string | null;
    channel: PasswordResetChannel;
    code: string;
    newPassword: string;
    deviceId?: string | null;
  },
): Promise<PasswordResetVerifyResult> {
  const email = normalizeReceiptEmail(args.email);
  const phone = normalizeE164(args.phoneE164);
  const code = args.code.trim();
  if (args.channel === "email" && !email) return { ok: false, error: "Enter a valid email address." };
  if (args.channel === "phone" && !phone) return { ok: false, error: "Enter a valid phone number (E.164)." };
  if (!/^[0-9]{6,10}$/.test(code)) return { ok: false, error: "Enter the recovery code." };
  if (args.newPassword.length < 8) return { ok: false, error: "Password must be at least 8 characters." };

  const { data, error } = await client.functions.invoke("verify-password-reset", {
    body: {
      channel: args.channel,
      code,
      new_password: args.newPassword,
      ...(email ? { email } : {}),
      ...(phone ? { phone_e164: phone } : {}),
      ...(args.deviceId?.trim() ? { device_id: args.deviceId.trim() } : {}),
    },
  });
  if (error) {
    const parsed = await parseFunctionError(error, "Password reset verification failed");
    return { ok: false, ...parsed };
  }
  if (!data || typeof data !== "object") return { ok: false, error: "Unexpected password reset response." };
  const body = data as Record<string, unknown>;
  if (body.ok !== true || body.password_updated !== true) {
    return {
      ok: false,
      error: typeof body.error === "string" ? body.error : "Password reset failed",
      code: typeof body.code === "string" ? body.code : undefined,
    };
  }
  const accessToken = typeof body.access_token === "string" ? body.access_token : "";
  const refreshToken = typeof body.refresh_token === "string" ? body.refresh_token : "";
  const userId = typeof body.user_id === "string" ? body.user_id : "";
  if (!accessToken || !refreshToken || !userId) {
    return { ok: false, error: "Recovery session tokens are missing." };
  }
  return {
    ok: true,
    accessToken,
    refreshToken,
    userId,
    expiresIn: typeof body.expires_in === "number" ? body.expires_in : undefined,
  };
}
