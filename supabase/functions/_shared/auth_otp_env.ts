/**
 * Auth OTP local-stub gate — same pattern as ContiPay/Paynow/worker stubs.
 *
 * Stub ONLY when:
 *   AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1 AND the relevant gateway secret is unset.
 * Never default-on in production.
 */
export const AUTH_OTP_STUB_CODE = "000000";

export function allowAuthOtpLocalStub(): boolean {
  return Deno.env.get("AUTH_OTP_ALLOW_UNVERIFIED_LOCAL") === "1";
}

export function smsOtpConfigured(): boolean {
  return Boolean(Deno.env.get("SMS_GATEWAY_API_KEY")?.trim());
}

export function emailOtpConfigured(): boolean {
  // Match getEmailSendConfig() — same keys resolve a real sender.
  const apiKey =
    Deno.env.get("EMAIL_API_KEY")?.trim() ||
    Deno.env.get("RESEND_API_KEY")?.trim() ||
    "";
  const from =
    Deno.env.get("REPORT_FROM_EMAIL")?.trim() ||
    Deno.env.get("EMAIL_FROM")?.trim() ||
    Deno.env.get("RECEIPT_FROM_EMAIL")?.trim() ||
    Deno.env.get("AUTH_OTP_FROM_EMAIL")?.trim() ||
    "";
  return Boolean(apiKey && from);
}

/** Fail-closed: refuse channel when secret unset unless local stub flag. */
export function assertOtpChannelAllowed(
  channel: "email" | "phone",
): { ok: true; stub: boolean } | { ok: false; error: string; status: number } {
  const local = allowAuthOtpLocalStub();
  if (channel === "phone") {
    if (smsOtpConfigured()) return { ok: true, stub: false };
    if (local) return { ok: true, stub: true };
    return {
      ok: false,
      status: 503,
      error:
        "SMS_GATEWAY_API_KEY unset — OTP SMS refuse (set AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)",
    };
  }
  if (emailOtpConfigured()) return { ok: true, stub: false };
  if (local) return { ok: true, stub: true };
  return {
    ok: false,
    status: 503,
    error:
      "EMAIL_API_KEY/EMAIL_FROM unset — OTP email refuse (set AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)",
  };
}
