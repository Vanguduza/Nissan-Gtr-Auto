/**
 * Unit-style smoke for auth OTP fail-closed / local stub / proof gate (no network).
 * Run: deno test --allow-env supabase/functions/auth-otp/smoke_test.ts
 */
import {
  assertOtpChannelAllowed,
  AUTH_OTP_STUB_CODE,
  allowAuthOtpLocalStub,
} from "../_shared/auth_otp_env.ts";
import {
  AUTH_OTP_MAX_ATTEMPTS,
  isAuthOtpProductionEnv,
  mintOtpProofToken,
  parseOtpProofToken,
} from "../_shared/auth_otp_proof.ts";

function clearOtpEnv() {
  Deno.env.delete("AUTH_OTP_ALLOW_UNVERIFIED_LOCAL");
  Deno.env.delete("SMS_GATEWAY_API_KEY");
  Deno.env.delete("EMAIL_API_KEY");
  Deno.env.delete("RESEND_API_KEY");
  Deno.env.delete("EMAIL_FROM");
  Deno.env.delete("RECEIPT_FROM_EMAIL");
  Deno.env.delete("AUTH_OTP_FROM_EMAIL");
  Deno.env.delete("ENVIRONMENT");
  Deno.env.delete("ENV");
  Deno.env.delete("AUTH_OTP_PROOF_SECRET");
  // Keep local URL heuristic off production
  Deno.env.set("SUPABASE_URL", "http://127.0.0.1:54321");
  Deno.env.set(
    "SUPABASE_SERVICE_ROLE_KEY",
    "test-service-role-key-for-hmac-only",
  );
}

Deno.test("OTP fail-closed without keys and without local flag", () => {
  clearOtpEnv();
  const phone = assertOtpChannelAllowed("phone");
  const email = assertOtpChannelAllowed("email");
  if (phone.ok || email.ok) {
    throw new Error("expected fail-closed when secrets unset");
  }
  if (allowAuthOtpLocalStub()) {
    throw new Error("flag should be off");
  }
});

Deno.test("OTP local stub when flag set and keys unset", () => {
  clearOtpEnv();
  Deno.env.set("AUTH_OTP_ALLOW_UNVERIFIED_LOCAL", "1");
  const phone = assertOtpChannelAllowed("phone");
  const email = assertOtpChannelAllowed("email");
  if (!phone.ok || !phone.stub || !email.ok || !email.stub) {
    throw new Error("expected stub path");
  }
  if (AUTH_OTP_STUB_CODE !== "000000") {
    throw new Error("documented stub code mismatch");
  }
});

Deno.test("OTP stub refused when ENVIRONMENT=production even with flag", () => {
  clearOtpEnv();
  Deno.env.set("AUTH_OTP_ALLOW_UNVERIFIED_LOCAL", "1");
  Deno.env.set("ENVIRONMENT", "production");
  if (!isAuthOtpProductionEnv()) {
    throw new Error("expected production heuristic");
  }
  if (allowAuthOtpLocalStub()) {
    throw new Error("stub must be refused in production");
  }
  const phone = assertOtpChannelAllowed("phone");
  if (phone.ok) {
    throw new Error("expected fail-closed stub refuse in production");
  }
});

Deno.test("OTP stub refused on hosted supabase.co URL even with flag", () => {
  clearOtpEnv();
  Deno.env.set("AUTH_OTP_ALLOW_UNVERIFIED_LOCAL", "1");
  Deno.env.set("SUPABASE_URL", "https://abcdefgh.supabase.co");
  if (!isAuthOtpProductionEnv()) {
    throw new Error("expected hosted URL production heuristic");
  }
  const email = assertOtpChannelAllowed("email");
  if (email.ok) {
    throw new Error("expected fail-closed on hosted project without keys");
  }
});

Deno.test("OTP real path when SMS key present (no stub)", () => {
  clearOtpEnv();
  Deno.env.set("SMS_GATEWAY_API_KEY", "test-key-not-real");
  const phone = assertOtpChannelAllowed("phone");
  if (!phone.ok || phone.stub) {
    throw new Error("expected real (non-stub) when key set");
  }
});

Deno.test("OTP proof token mint + parse round-trip (server gate)", async () => {
  clearOtpEnv();
  Deno.env.set("AUTH_OTP_PROOF_SECRET", "unit-test-proof-secret");
  const exp = Math.floor(Date.now() / 1000) + 600;
  const token = await mintOtpProofToken({
    pid: "11111111-2222-4333-8444-555555555555",
    email: "a@example.com",
    phone_e164: "+263771234567",
    exp,
  });
  if (!token) throw new Error("mint failed");
  const parsed = await parseOtpProofToken(token);
  if (!parsed.ok) throw new Error(parsed.error);
  if (parsed.payload.email !== "a@example.com") {
    throw new Error("email mismatch");
  }
  if (parsed.payload.phone_e164 !== "+263771234567") {
    throw new Error("phone mismatch");
  }
});

Deno.test("OTP proof rejects tampered token", async () => {
  clearOtpEnv();
  Deno.env.set("AUTH_OTP_PROOF_SECRET", "unit-test-proof-secret");
  const token = await mintOtpProofToken({
    pid: "11111111-2222-4333-8444-555555555555",
    email: "a@example.com",
    phone_e164: null,
    exp: Math.floor(Date.now() / 1000) + 600,
  });
  if (!token) throw new Error("mint failed");
  const tampered = token.slice(0, -4) + "dead";
  const parsed = await parseOtpProofToken(tampered);
  if (parsed.ok) throw new Error("tampered token must fail");
});

Deno.test("OTP max attempts constant documented", () => {
  if (AUTH_OTP_MAX_ATTEMPTS < 3 || AUTH_OTP_MAX_ATTEMPTS > 20) {
    throw new Error("unexpected AUTH_OTP_MAX_ATTEMPTS");
  }
});
