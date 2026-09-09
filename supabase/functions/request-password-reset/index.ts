import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  anonClient,
  AuthEdgeError,
  authFailureCode,
  enforceAuthRateLimit,
  jsonResponse,
  normalizeE164,
  normalizeEmail,
  serviceClient,
} from "../_shared/auth_edge.ts";

type Body = {
  email?: string;
  phone_e164?: string;
  channel?: "email" | "phone";
  redirect_to?: string;
  device_id?: string;
};

function safeRedirect(raw: unknown): string | null {
  if (typeof raw !== "string" || !raw.trim()) return null;
  try {
    const url = new URL(raw.trim());
    const allowed = new Set([
      "https://nissangtrauto.co.zw",
      "https://www.nissangtrauto.co.zw",
      "http://localhost:3000",
      "http://127.0.0.1:3000",
      "http://localhost:5173",
      "http://127.0.0.1:5173",
    ]);
    if (!allowed.has(url.origin)) return null;
    return url.toString();
  } catch {
    return null;
  }
}

function isOperationalAuthError(error: { status?: number; code?: string; message?: string } | null): boolean {
  if (!error) return false;
  if (error.status === 429) return true;
  if ((error.status ?? 0) >= 500) return true;
  const code = (error.code ?? "").toLowerCase();
  return code.includes("hook") || code.includes("smtp") || code.includes("sms") || code.includes("provider") || code.includes("disabled");
}

Deno.serve(async (req) => {
  try {
    if (req.method === "OPTIONS") {
      return jsonResponse(req, { ok: true }, 200);
    }
    if (req.method !== "POST") {
      return jsonResponse(req, { error: "POST required", code: "METHOD_NOT_ALLOWED" }, 405);
    }

    const body = (await req.json().catch(() => ({}))) as Body;
    const email = normalizeEmail(body.email);
    const phone = normalizeE164(body.phone_e164);
    const channel = body.channel ?? (email ? "email" : phone ? "phone" : null);
    if (!channel || (channel === "email" && !email) || (channel === "phone" && !phone)) {
      return jsonResponse(req, {
        error: "Choose email or phone and provide a valid identifier",
        code: "IDENTIFIER_REQUIRED",
      }, 400);
    }

    const service = serviceClient();
    const auth = anonClient();
    const identifier = channel === "email" ? email! : phone!;
    await enforceAuthRateLimit(
      service,
      req,
      "password_reset_request",
      `${channel}:${identifier}`,
      body.device_id,
    );

    let error: { status?: number; code?: string; message?: string } | null = null;
    if (channel === "email") {
      const redirectTo = safeRedirect(body.redirect_to);
      const result = await auth.auth.resetPasswordForEmail(
        email!,
        redirectTo ? { redirectTo } : undefined,
      );
      error = result.error;
    } else {
      const result = await auth.auth.signInWithOtp({
        phone: phone!,
        options: { shouldCreateUser: false, channel: "sms" },
      });
      error = result.error;
    }

    // Account existence is deliberately not disclosed. Infrastructure/provider
    // failures and rate limiting are operational conditions and remain visible.
    if (error && isOperationalAuthError(error)) {
      if (error.status === 429) {
        return jsonResponse(req, {
          error: "Too many reset requests. Try again later.",
          code: "AUTH_RATE_LIMITED",
        }, 429);
      }
      console.error("request-password-reset provider failure", error.code, error.message);
      return jsonResponse(req, {
        error: "Password reset delivery service is unavailable",
        code: authFailureCode(error, "RESET_DELIVERY_UNAVAILABLE"),
      }, 503);
    }

    return jsonResponse(req, {
      ok: true,
      channel,
      message: channel === "email"
        ? "If an account matches that email, a Supabase Auth recovery code has been sent."
        : "If an account matches that phone number, a Supabase Auth verification code has been sent.",
    });
  } catch (error) {
    if (error instanceof AuthEdgeError) {
      return jsonResponse(req, { error: error.message, code: error.code }, error.status);
    }
    console.error("request-password-reset", error);
    return jsonResponse(req, { error: "Password reset service error", code: "RESET_SERVICE_ERROR" }, 500);
  }
});
