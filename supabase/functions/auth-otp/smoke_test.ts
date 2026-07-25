/**
 * Unit-style smoke for auth OTP fail-closed / local stub gate (no network).
 * Run: deno test --allow-env supabase/functions/auth-otp/smoke_test.ts
 */
import {
  assertOtpChannelAllowed,
  AUTH_OTP_STUB_CODE,
  allowAuthOtpLocalStub,
} from "../_shared/auth_otp_env.ts";

function clearOtpEnv() {
  Deno.env.delete("AUTH_OTP_ALLOW_UNVERIFIED_LOCAL");
  Deno.env.delete("SMS_GATEWAY_API_KEY");
  Deno.env.delete("EMAIL_API_KEY");
  Deno.env.delete("RESEND_API_KEY");
  Deno.env.delete("EMAIL_FROM");
  Deno.env.delete("RECEIPT_FROM_EMAIL");
  Deno.env.delete("AUTH_OTP_FROM_EMAIL");
}

Deno.test("OTP fail-closed without keys and without local flag", () => {
  clearOtpEnv();
  const phone = assertOtpChannelAllowed("phone");
  const email = assertOtpChannelAllowed("email");
  if (phone.ok || email.ok) {
    throw new Error("expected fail-closed when secrets unset");
  }
  if (!allowAuthOtpLocalStub()) {
    // ok
  } else {
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

Deno.test("OTP real path when SMS key present (no stub)", () => {
  clearOtpEnv();
  Deno.env.set("SMS_GATEWAY_API_KEY", "test-key-not-real");
  const phone = assertOtpChannelAllowed("phone");
  if (!phone.ok || phone.stub) {
    throw new Error("expected real (non-stub) when key set");
  }
});
