/**
 * Customer auth OTP (email and/or phone) — fail-closed without gateway secrets.
 *
 * POST JSON actions:
 *   request | verify | complete_signup | complete_login
 *
 * Server-side gate (ADR 2026-07-25-auth-otp-fail-closed):
 *   verify → short-lived HMAC proof_token (DB-backed, one-time)
 *   complete_signup / complete_login → require proof; mint session via service_role
 * Public GoTrue signup is disabled (config enable_signup=false).
 *
 * Local stub: AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1 + keys unset + non-prod → code 000000.
 * Production: stub refused even if flag set.
 *
 * No ZIMRA / payroll-tax identity flows.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { jsonErr, jsonOk } from "../_shared/channel_env.ts";
import {
  AUTH_OTP_STUB_CODE,
  assertOtpChannelAllowed,
} from "../_shared/auth_otp_env.ts";
import {
  AUTH_OTP_MAX_ATTEMPTS,
  AUTH_OTP_PROOF_TTL_SEC,
  mintOtpProofToken,
  parseOtpProofToken,
} from "../_shared/auth_otp_proof.ts";
import { getSmsGatewayConfig, sendSms } from "../_shared/sms_gateway.ts";
import { getEmailSendConfig, sendEmail } from "../_shared/email_send.ts";

type Body = {
  action?: string;
  email?: string;
  phone_e164?: string;
  code?: string;
  proof_token?: string;
  password?: string;
  full_name?: string;
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

function anonClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
}

async function mintProofRow(
  supabase: SupabaseClient,
  email: string | null,
  phone: string | null,
): Promise<{ proof_token: string; expires_at: string } | { error: string; status: number }> {
  const expiresAt = new Date(Date.now() + AUTH_OTP_PROOF_TTL_SEC * 1000);
  const { data: row, error } = await supabase
    .from("auth_otp_proofs")
    .insert({
      email,
      phone_e164: phone,
      expires_at: expiresAt.toISOString(),
    })
    .select("id")
    .single();
  if (error || !row?.id) {
    return { error: error?.message ?? "failed to mint OTP proof", status: 500 };
  }
  const token = await mintOtpProofToken({
    pid: row.id,
    email,
    phone_e164: phone,
    exp: Math.floor(expiresAt.getTime() / 1000),
  });
  if (!token) {
    return { error: "OTP proof secret unavailable", status: 503 };
  }
  return { proof_token: token, expires_at: expiresAt.toISOString() };
}

async function consumeProof(
  supabase: SupabaseClient,
  proofToken: string,
  expectEmail: string | null,
): Promise<
  | { ok: true; email: string | null; phone_e164: string | null }
  | { ok: false; error: string; status: number }
> {
  const parsed = await parseOtpProofToken(proofToken);
  if (!parsed.ok) {
    return { ok: false, error: parsed.error, status: 401 };
  }
  const { pid, email: proofEmail, phone_e164: proofPhone } = parsed.payload;

  if (expectEmail && proofEmail && proofEmail !== expectEmail) {
    return { ok: false, error: "OTP proof email mismatch", status: 401 };
  }
  if (expectEmail && !proofEmail) {
    return {
      ok: false,
      error: "OTP proof missing email — verify email OTP before password auth",
      status: 400,
    };
  }

  const { data: row, error } = await supabase
    .from("auth_otp_proofs")
    .select("id, email, phone_e164, expires_at, consumed_at")
    .eq("id", pid)
    .maybeSingle();
  if (error) return { ok: false, error: error.message, status: 400 };
  if (!row) return { ok: false, error: "OTP proof not found", status: 401 };
  if (row.consumed_at) {
    return { ok: false, error: "OTP proof already used", status: 401 };
  }
  if (new Date(row.expires_at).getTime() <= Date.now()) {
    return { ok: false, error: "OTP proof expired", status: 401 };
  }

  const { data: updated, error: updErr } = await supabase
    .from("auth_otp_proofs")
    .update({ consumed_at: new Date().toISOString() })
    .eq("id", pid)
    .is("consumed_at", null)
    .select("id")
    .maybeSingle();
  if (updErr) return { ok: false, error: updErr.message, status: 400 };
  if (!updated) {
    return { ok: false, error: "OTP proof already used", status: 401 };
  }

  return {
    ok: true,
    email: (row.email as string | null) ?? proofEmail,
    phone_e164: (row.phone_e164 as string | null) ?? proofPhone,
  };
}

async function persistPhoneIfNeeded(
  supabase: SupabaseClient,
  userId: string,
  phone: string | null,
): Promise<void> {
  if (!phone) return;
  await supabase
    .from("profiles")
    .update({ phone_e164: phone, updated_at: new Date().toISOString() })
    .eq("id", userId);
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

    const supabase = serviceClient();

    if (action === "request") {
      if (!email && !phone) {
        return jsonErr("email and/or phone_e164 required", 400);
      }

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
          attempt_count: 0,
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
      if (!email && !phone) {
        return jsonErr("email and/or phone_e164 required", 400);
      }
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
        const { data: openRows, error: openErr } = await supabase
          .from("auth_otp_challenges")
          .select("id, code_hash, attempt_count")
          .eq("channel", ch)
          .eq("identifier", identifier)
          .is("consumed_at", null)
          .gt("expires_at", new Date().toISOString())
          .order("created_at", { ascending: false })
          .limit(1);
        if (openErr) return jsonErr(openErr.message, 400);
        if (!openRows?.length) {
          return jsonErr("invalid or expired OTP", 401);
        }
        const challenge = openRows[0]!;
        const attempts = Number(challenge.attempt_count ?? 0);
        if (attempts >= AUTH_OTP_MAX_ATTEMPTS) {
          return jsonErr("OTP attempts exceeded — request a new code", 429);
        }

        const codeHash = await hashCode(code);
        if (challenge.code_hash !== codeHash) {
          await supabase
            .from("auth_otp_challenges")
            .update({ attempt_count: attempts + 1 })
            .eq("id", challenge.id);
          return jsonErr("invalid or expired OTP", 401);
        }

        await supabase
          .from("auth_otp_challenges")
          .update({ consumed_at: new Date().toISOString() })
          .eq("id", challenge.id);
      }

      const minted = await mintProofRow(supabase, email, phone);
      if ("error" in minted) {
        return jsonErr(minted.error, minted.status);
      }

      return jsonOk({
        ok: true,
        verified: true,
        email: email ?? null,
        phone_e164: phone ?? null,
        proof_token: minted.proof_token,
        proof_expires_at: minted.expires_at,
      });
    }

    if (action === "complete_signup") {
      const password = typeof body.password === "string" ? body.password : "";
      const proofToken =
        typeof body.proof_token === "string" ? body.proof_token.trim() : "";
      const fullName =
        typeof body.full_name === "string" ? body.full_name.trim() : "";

      if (!proofToken) return jsonErr("proof_token required", 400);
      if (password.length < 8) {
        return jsonErr("password must be at least 8 characters", 400);
      }

      const expectEmail = normalizeEmail(body.email);
      if (!expectEmail) {
        return jsonErr("email required for signup", 400);
      }

      const consumed = await consumeProof(supabase, proofToken, expectEmail);
      if (!consumed.ok) {
        return jsonErr(consumed.error, consumed.status);
      }

      const phoneE164 = consumed.phone_e164 ?? normalizeE164(body.phone_e164);

      const { data: created, error: createErr } = await supabase.auth.admin
        .createUser({
          email: expectEmail,
          password,
          email_confirm: true,
          user_metadata: fullName ? { full_name: fullName } : undefined,
        });
      if (createErr || !created.user) {
        return jsonErr(createErr?.message ?? "signup failed", 400);
      }

      await persistPhoneIfNeeded(supabase, created.user.id, phoneE164);

      const { data: sessionData, error: signErr } = await anonClient().auth
        .signInWithPassword({ email: expectEmail, password });
      if (signErr || !sessionData.session) {
        return jsonErr(
          signErr?.message ??
            "account created but session mint failed — sign in with OTP again",
          400,
        );
      }

      return jsonOk({
        ok: true,
        user_id: created.user.id,
        access_token: sessionData.session.access_token,
        refresh_token: sessionData.session.refresh_token,
        expires_in: sessionData.session.expires_in,
        email: expectEmail,
        phone_e164: phoneE164,
      });
    }

    if (action === "complete_login") {
      const password = typeof body.password === "string" ? body.password : "";
      const proofToken =
        typeof body.proof_token === "string" ? body.proof_token.trim() : "";

      if (!proofToken) return jsonErr("proof_token required", 400);
      if (!password) return jsonErr("password required", 400);

      const expectEmail = normalizeEmail(body.email);
      if (!expectEmail) {
        return jsonErr("email required for login", 400);
      }

      const consumed = await consumeProof(supabase, proofToken, expectEmail);
      if (!consumed.ok) {
        return jsonErr(consumed.error, consumed.status);
      }

      const phoneE164 = consumed.phone_e164 ?? normalizeE164(body.phone_e164);

      const { data: sessionData, error: signErr } = await anonClient().auth
        .signInWithPassword({ email: expectEmail, password });
      if (signErr || !sessionData.session || !sessionData.user) {
        return jsonErr(signErr?.message ?? "invalid credentials", 401);
      }

      await persistPhoneIfNeeded(supabase, sessionData.user.id, phoneE164);

      return jsonOk({
        ok: true,
        user_id: sessionData.user.id,
        access_token: sessionData.session.access_token,
        refresh_token: sessionData.session.refresh_token,
        expires_in: sessionData.session.expires_in,
        email: expectEmail,
        phone_e164: phoneE164,
      });
    }

    return jsonErr(
      "action must be request, verify, complete_signup, or complete_login",
      400,
    );
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error("auth-otp error:", msg);
    return jsonErr(msg, 500);
  }
});
