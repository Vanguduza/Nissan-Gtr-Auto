/**
 * Customer auth OTP (email and/or phone) — fail-closed without gateway secrets.
 *
 * POST JSON:
 *   { "action": "request" | "verify", "email"?: string, "phone_e164"?: string, "code"?: string }
 *
 * Local stub: AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1 + keys unset → stub code 000000.
 * Production: never enable the local flag; set SMS_GATEWAY_* / EMAIL_* secrets.
 *
 * No ZIMRA / payroll-tax identity flows.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { jsonErr, jsonOk } from "../_shared/channel_env.ts";
import {
  AUTH_OTP_STUB_CODE,
  assertOtpChannelAllowed,
} from "../_shared/auth_otp_env.ts";
import { getSmsGatewayConfig, sendSms } from "../_shared/sms_gateway.ts";
import { getEmailSendConfig, sendEmail } from "../_shared/email_send.ts";

type Body = {
  action?: string;
  email?: string;
  phone_e164?: string;
  code?: string;
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

Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") {
      return jsonErr("POST required", 405);
    }

    const body = (await req.json().catch(() => ({}))) as Body;
    const action = (body.action ?? "").trim().toLowerCase();
    const email = normalizeEmail(body.email);
    const phone = normalizeE164(body.phone_e164);

    if (!email && !phone) {
      return jsonErr("email and/or phone_e164 required", 400);
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    if (action === "request") {
      const channels: ("email" | "phone")[] = [];
      if (email) channels.push("email");
      if (phone) channels.push("phone");

      const results: Record<string, unknown> = {};
      let anyStub = false;

      for (const ch of channels) {
        const gate = assertOtpChannelAllowed(ch);
        if (!gate.ok) {
          return jsonErr(gate.error, gate.status);
        }

        if (gate.stub) {
          anyStub = true;
          results[ch] = {
            stub: true,
            stub_code: AUTH_OTP_STUB_CODE,
            message: "local stub OTP — not for production",
          };
          console.warn(
            `auth-otp: stub request channel=${ch} (AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1)`,
          );
          continue;
        }

        const code = randomOtp();
        const codeHash = await hashCode(code);
        const identifier = ch === "email" ? email! : phone!;
        const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

        const { error: insErr } = await supabase.from("auth_otp_challenges").insert({
          channel: ch,
          identifier,
          code_hash: codeHash,
          expires_at: expiresAt,
        });
        if (insErr) return jsonErr(insErr.message, 400);

        if (ch === "phone") {
          const smsCfg = getSmsGatewayConfig();
          if (!smsCfg) {
            return jsonErr("SMS gateway misconfigured", 503);
          }
          await sendSms(
            smsCfg,
            phone!,
            `GTR Auto login code: ${code}. Valid 10 minutes.`,
          );
        } else {
          const emailCfg = getEmailSendConfig();
          if (!emailCfg) {
            return jsonErr("Email gateway misconfigured", 503);
          }
          await sendEmail(emailCfg, {
            to: email!,
            subject: "GTR Auto login code",
            text: `Your one-time code is ${code}. Valid 10 minutes.`,
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
    }

    if (action === "verify") {
      const code = typeof body.code === "string" ? body.code.trim() : "";
      if (!/^[0-9]{6}$/.test(code)) {
        return jsonErr("6-digit code required", 400);
      }

      const channels: ("email" | "phone")[] = [];
      if (email) channels.push("email");
      if (phone) channels.push("phone");

      for (const ch of channels) {
        const gate = assertOtpChannelAllowed(ch);
        if (!gate.ok) {
          return jsonErr(gate.error, gate.status);
        }

        if (gate.stub) {
          if (code !== AUTH_OTP_STUB_CODE) {
            return jsonErr("invalid stub OTP code", 401);
          }
          continue;
        }

        const identifier = ch === "email" ? email! : phone!;
        const codeHash = await hashCode(code);
        const { data: rows, error: selErr } = await supabase
          .from("auth_otp_challenges")
          .select("id")
          .eq("channel", ch)
          .eq("identifier", identifier)
          .eq("code_hash", codeHash)
          .is("consumed_at", null)
          .gt("expires_at", new Date().toISOString())
          .order("created_at", { ascending: false })
          .limit(1);
        if (selErr) return jsonErr(selErr.message, 400);
        if (!rows?.length) {
          return jsonErr("invalid or expired OTP", 401);
        }
        await supabase
          .from("auth_otp_challenges")
          .update({ consumed_at: new Date().toISOString() })
          .eq("id", rows[0].id);
      }

      // Persist verified phone onto the caller's profile when JWT present.
      const authHeader = req.headers.get("Authorization") ?? "";
      const jwt = authHeader.replace(/^Bearer\s+/i, "").trim();
      if (phone && jwt) {
        const userClient = createClient(
          Deno.env.get("SUPABASE_URL")!,
          Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
          { global: { headers: { Authorization: `Bearer ${jwt}` } } },
        );
        const { data: userData } = await userClient.auth.getUser();
        if (userData?.user?.id) {
          await supabase
            .from("profiles")
            .update({ phone_e164: phone, updated_at: new Date().toISOString() })
            .eq("id", userData.user.id);
        }
      }

      return jsonOk({ ok: true, verified: true, email: email ?? null, phone_e164: phone ?? null });
    }

    return jsonErr("action must be request or verify", 400);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error("auth-otp error:", msg);
    return jsonErr(msg, 500);
  }
});
