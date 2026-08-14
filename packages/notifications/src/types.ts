/**
 * Channel split (Dial-a-Spare / DIAL §4.6):
 * - Resend → critical transactional (OTP, receipts, password reset, report PDFs)
 * - Brevo → promo / CRM journeys
 *
 * Consent SoR remains Postgres (`marketing_opt_in`), not the ESP.
 */

export type EmailChannel = "transactional" | "promo";

export type EmailProviderId = "resend" | "brevo";

export type EmailMessage = {
  to: string;
  subject: string;
  text: string;
  html?: string;
  /** Optional idempotency / correlation for outbox workers. */
  idempotencyKey?: string;
};

export type EmailSendResult = {
  provider: EmailProviderId;
  messageId: string | null;
};

export type EmailAdapter = {
  readonly id: EmailProviderId;
  /** Returns null when env keys missing (callers fail closed or stub). */
  isConfigured(): boolean;
  send(message: EmailMessage): Promise<EmailSendResult>;
};

/** Resolve which provider owns a logical channel. */
export function providerForChannel(channel: EmailChannel): EmailProviderId {
  return channel === "promo" ? "brevo" : "resend";
}
