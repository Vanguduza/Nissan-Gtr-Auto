/**
 * verify-password-reset — consume reset OTP + set new password (service_role).
 * Pair with request-password-reset for OTP send. Mirrors auth-otp fail-closed gates.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { jsonErr, jsonOk } from "../_shared/channel_env.ts";
import {
  AUTH_OTP_STUB_CODE,
  assertOtpChannelAllowed,
} from "../_shared/auth_otp_env.ts";

type Body = {
  email?: string;
  phone_e164?: string;
  code?: string;
  new_password?: string;
};

function normalizeEmail(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const v = raw.trim().toLowerCase();
  if (!v || !v.includes("@")) return null;
  return v;
}

function normalizeE164(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  let v = raw.trim().replace(/[\s\-()]/g, "");
  if (!v) return null;
  if (!/^\+?[0-9]{8,15}$/.test(v)) return null;
  if (!v.startsWith("+")) v = `+${v}`;
  return v;
}

function toHex(buf: ArrayBuffer): string {
  return [...new Uint8Array(buf)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

async function hashCode(code: string): Promise<string> {
  const data = new TextEncoder().encode(code.trim());
  const digest = await crypto.subtle.digest("SHA-256", data);
  return toHex(digest);
}

function serviceClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
}

Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") return jsonErr("POST required", 405);

    const body = (await req.json().catch(() => ({}))) as Body;
    const email = normalizeEmail(body.email);
    const phone = normalizeE164(body.phone_e164);
    if (!email && !phone) {
      return jsonErr("email and/or phone_e164 required", 400);
    }
    const code = typeof body.code === "string" ? body.code.trim() : "";
    if (!/^[0-9]{6}$/.test(code)) {
      return jsonErr("6-digit code required", 400);
    }
    const newPassword =
      typeof body.new_password === "string" ? body.new_password : "";
    if (newPassword.length < 8) {
      return jsonErr("new_password must be at least 8 characters", 400);
    }

    const supabase = serviceClient();
    const gateEmail = email ? assertOtpChannelAllowed("email") : null;
    const gatePhone = phone ? assertOtpChannelAllowed("phone") : null;
    const stubOk =
      (gateEmail?.ok && gateEmail.stub && code === AUTH_OTP_STUB_CODE) ||
      (gatePhone?.ok && gatePhone.stub && code === AUTH_OTP_STUB_CODE);

    if (!stubOk) {
      const channel = email ? "email" : "phone";
      const identifier = email ?? phone!;
      const codeHash = await hashCode(code);
      const { data: row, error } = await supabase
        .from("password_reset_challenges")
        .select("id, code_hash, expires_at, attempt_count, consumed_at")
        .eq("channel", channel)
        .eq("identifier", identifier)
        .is("consumed_at", null)
        .order("expires_at", { ascending: false })
        .limit(1)
        .maybeSingle();
      if (error) return jsonErr(error.message, 400);
      if (!row) return jsonErr("reset code not found", 401);
      if (new Date(row.expires_at).getTime() <= Date.now()) {
        return jsonErr("reset code expired", 401);
      }
      if ((row.attempt_count as number) >= 5) {
        return jsonErr("too many attempts", 429);
      }
      if (row.code_hash !== codeHash) {
        await supabase
          .from("password_reset_challenges")
          .update({ attempt_count: (row.attempt_count as number) + 1 })
          .eq("id", row.id);
        return jsonErr("invalid reset code", 401);
      }
      await supabase
        .from("password_reset_challenges")
        .update({ consumed_at: new Date().toISOString() })
        .eq("id", row.id);
    }

    let userId: string | null = null;
    if (email) {
      const { data: users } = await supabase.auth.admin.listUsers({
        page: 1,
        perPage: 200,
      });
      const match = users?.users?.find((u) => u.email?.toLowerCase() === email);
      userId = match?.id ?? null;
    }
    if (!userId && phone) {
      const { data: prof } = await supabase
        .from("profiles")
        .select("id")
        .eq("phone_e164", phone)
        .maybeSingle();
      userId = prof?.id ?? null;
    }
    if (!userId) return jsonErr("account not found", 404);

    const { error: updErr } = await supabase.auth.admin.updateUserById(userId, {
      password: newPassword,
    });
    if (updErr) return jsonErr(updErr.message, 400);

    await supabase
      .from("profiles")
      .update({
        must_change_password: false,
        updated_at: new Date().toISOString(),
      })
      .eq("id", userId);

    return jsonOk({ ok: true, user_id: userId });
  } catch (e) {
    const msg = e instanceof Error ? e.message : "password reset verify failed";
    return jsonErr(msg, 500);
  }
});
