/**
 * request-password-reset — send reset OTP via email and/or SMS.
 * Verify + set password: verify-password-reset Edge.
 * Mirrors auth-otp fail-closed channel gates. No ZIMRA.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { jsonErr, jsonOk } from "../_shared/channel_env.ts";
import {
  AUTH_OTP_STUB_CODE,
  assertOtpChannelAllowed,
} from "../_shared/auth_otp_env.ts";
import { getSmsGatewayConfig, sendSms } from "../_shared/sms_gateway.ts";
import { getEmailSendConfig, sendEmail } from "../_shared/email_send.ts";

type Body = {
  email?: string;
  phone_e164?: string;
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

function randomOtp(): string {
  const n = crypto.getRandomValues(new Uint32Array(1))[0]! % 1_000_000;
  return n.toString().padStart(6, "0");
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

    const supabase = serviceClient();
    const channels: ("email" | "phone")[] = [];
    if (email) channels.push("email");
    if (phone) channels.push("phone");

    const results: Record<string, unknown> = {};
    let anyStub = false;

    for (const ch of channels) {
      const gate = assertOtpChannelAllowed(ch);
      if (!gate.ok) return jsonErr(gate.error, gate.status);

      if (gate.stub) {
        anyStub = true;
        results[ch] = {
          stub: true,
          stub_code: AUTH_OTP_STUB_CODE,
          message: "local stub OTP — not for production",
        };
        continue;
      }

      const code = randomOtp();
      const codeHash = await hashCode(code);
      const identifier = ch === "email" ? email! : phone!;
      const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

      const { error: insErr } = await supabase
        .from("password_reset_challenges")
        .insert({
          channel: ch,
          identifier,
          code_hash: codeHash,
          expires_at: expiresAt,
          attempt_count: 0,
        });
      if (insErr) return jsonErr(insErr.message, 400);

      if (ch === "phone") {
        const smsCfg = getSmsGatewayConfig();
        if (!smsCfg) return jsonErr("SMS gateway misconfigured", 503);
        await sendSms(
          smsCfg,
          phone!,
          `GTR Auto password reset code: ${code}. Valid 10 minutes.`,
        );
      } else {
        const emailCfg = getEmailSendConfig();
        if (!emailCfg) return jsonErr("Email gateway misconfigured", 503);
        await sendEmail(emailCfg, {
          to: email!,
          subject: "GTR Auto password reset",
          text: `Your password reset code is ${code}. Valid 10 minutes.`,
        });
      }
      results[ch] = { stub: false, sent: true };
    }

    return jsonOk({
      ok: true,
      stub: anyStub,
      channels: results,
      ...(anyStub ? { stub_code: AUTH_OTP_STUB_CODE } : {}),
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : "password reset request failed";
    return jsonErr(msg, 500);
  }
});
