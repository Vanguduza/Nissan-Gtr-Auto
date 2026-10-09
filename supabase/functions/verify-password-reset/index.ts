import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  anonClient,
  AuthEdgeError,
  enforceAuthRateLimit,
  isPendingSignup,
  jsonResponse,
  normalizeCode,
  normalizeE164,
  normalizeEmail,
  serviceClient,
  sessionPayload,
  userClient,
} from "../_shared/auth_edge.ts";

type Body = {
  email?: string;
  phone_e164?: string;
  channel?: "email" | "phone";
  code?: string;
  new_password?: string;
  device_id?: string;
};

Deno.serve(async (req) => {
  try {
    if (req.method === "OPTIONS") return jsonResponse(req, { ok: true });
    if (req.method !== "POST") {
      return jsonResponse(req, { error: "POST required", code: "METHOD_NOT_ALLOWED" }, 405);
    }

    const body = (await req.json().catch(() => ({}))) as Body;
    const email = normalizeEmail(body.email);
    const phone = normalizeE164(body.phone_e164);
    const channel = body.channel ?? (email ? "email" : phone ? "phone" : null);
    const code = normalizeCode(body.code);
    const newPassword = typeof body.new_password === "string" ? body.new_password : "";

    if (!channel || (channel === "email" && !email) || (channel === "phone" && !phone)) {
      return jsonResponse(req, {
        error: "Choose email or phone and provide a valid identifier",
        code: "IDENTIFIER_REQUIRED",
      }, 400);
    }
    if (!code) {
      return jsonResponse(req, { error: "Valid verification code required", code: "CODE_REQUIRED" }, 400);
    }
    if (newPassword.length < 8) {
      return jsonResponse(req, {
        error: "New password must be at least 8 characters",
        code: "PASSWORD_TOO_SHORT",
      }, 400);
    }

    const service = serviceClient();
    const identifier = channel === "email" ? email! : phone!;
    await enforceAuthRateLimit(
      service,
      req,
      "password_reset_verify",
      `${channel}:${identifier}`,
      body.device_id,
    );

    const auth = anonClient();
    const verified = channel === "email"
      ? await auth.auth.verifyOtp({ email: email!, token: code, type: "recovery" })
      : await auth.auth.verifyOtp({ phone: phone!, token: code, type: "sms" });

    if (verified.error || !verified.data.session || !verified.data.user) {
      return jsonResponse(req, {
        error: "Invalid or expired reset code",
        code: "RESET_CODE_INVALID_OR_EXPIRED",
      }, 401);
    }

    if (isPendingSignup(verified.data.user)) {
      await auth.auth.signOut({ scope: "local" }).catch(() => undefined);
      return jsonResponse(req, {
        error: "This account has not completed signup",
        code: "SIGNUP_INCOMPLETE",
      }, 409);
    }

    // Use the Supabase recovery session itself to authorize the password change.
    // No service-role password mutation is performed.
    const scoped = userClient();
    const set = await scoped.auth.setSession({
      access_token: verified.data.session.access_token,
      refresh_token: verified.data.session.refresh_token,
    });
    if (set.error || !set.data.session) {
      return jsonResponse(req, {
        error: "Recovery session could not be established",
        code: "RECOVERY_SESSION_FAILED",
      }, 401);
    }

    const changed = await scoped.auth.updateUser({ password: newPassword });
    if (changed.error || !changed.data.user) {
      console.error("verify-password-reset updateUser", changed.error?.code, changed.error?.message);
      return jsonResponse(req, {
        error: "Password update failed",
        code: changed.error?.code ?? "PASSWORD_UPDATE_FAILED",
      }, changed.error?.status && changed.error.status >= 500 ? 503 : 400);
    }

    // Keep the just-recovered session, revoke other refresh-token sessions.
    await scoped.auth.signOut({ scope: "others" }).catch((error) =>
      console.warn("verify-password-reset revoke others", String(error))
    );

    await service.from("profiles").update({
      must_change_password: false,
      updated_at: new Date().toISOString(),
    }).eq("id", changed.data.user.id);

    return jsonResponse(req, {
      ok: true,
      password_updated: true,
      ...sessionPayload(set.data.session, changed.data.user),
    });
  } catch (error) {
    if (error instanceof AuthEdgeError) {
      return jsonResponse(req, { error: error.message, code: error.code }, error.status);
    }
    console.error("verify-password-reset", error);
    return jsonResponse(req, { error: "Password reset service error", code: "RESET_SERVICE_ERROR" }, 500);
  }
});
