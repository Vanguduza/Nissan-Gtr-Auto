import type { EmailAdapter, EmailMessage, EmailSendResult } from "../types";

export type BrevoConfig = {
  apiKey: string;
  fromEmail: string;
  fromName?: string;
  baseUrl: string;
};

/**
 * Brevo (Sendinblue) transactional HTTP API — used for **promo/CRM** only.
 * Docs shape: POST /v3/smtp/email with api-key header.
 */
export function createBrevoAdapter(
  cfg: BrevoConfig,
  fetchImpl: typeof fetch = fetch,
): EmailAdapter {
  return {
    id: "brevo",
    isConfigured: () => Boolean(cfg.apiKey && cfg.fromEmail),
    async send(message: EmailMessage): Promise<EmailSendResult> {
      const payload: Record<string, unknown> = {
        sender: {
          email: cfg.fromEmail,
          ...(cfg.fromName ? { name: cfg.fromName } : {}),
        },
        to: [{ email: message.to.trim() }],
        subject: message.subject,
        textContent: message.text,
      };
      if (message.html) payload.htmlContent = message.html;
      if (message.idempotencyKey) {
        payload.headers = { "X-GTR-Idempotency-Key": message.idempotencyKey };
      }

      const res = await fetchImpl(
        `${cfg.baseUrl.replace(/\/$/, "")}/v3/smtp/email`,
        {
          method: "POST",
          headers: {
            "api-key": cfg.apiKey,
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify(payload),
        },
      );
      const text = await res.text();
      let raw: unknown = text;
      try {
        raw = JSON.parse(text);
      } catch {
        /* keep */
      }
      if (!res.ok) {
        throw new Error(`Brevo ${res.status}: ${text.slice(0, 400)}`);
      }
      const messageId =
        raw &&
        typeof raw === "object" &&
        typeof (raw as { messageId?: string }).messageId === "string"
          ? (raw as { messageId: string }).messageId
          : null;
      return { provider: "brevo", messageId };
    },
  };
}
