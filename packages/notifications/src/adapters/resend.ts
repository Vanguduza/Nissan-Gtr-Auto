import type { EmailAdapter, EmailMessage, EmailSendResult } from "./types.ts";

export type ResendConfig = {
  apiKey: string;
  from: string;
  baseUrl: string;
};

/**
 * Resend HTTP adapter (transactional). Mirrors Edge `_shared/email_send.ts`.
 * Node/browser callers supply fetch + config; Edge keeps Deno env helper.
 */
export function createResendAdapter(
  cfg: ResendConfig,
  fetchImpl: typeof fetch = fetch,
): EmailAdapter {
  return {
    id: "resend",
    isConfigured: () => Boolean(cfg.apiKey && cfg.from),
    async send(message: EmailMessage): Promise<EmailSendResult> {
      const res = await fetchImpl(`${cfg.baseUrl.replace(/\/$/, "")}/emails`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${cfg.apiKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          from: cfg.from,
          to: [message.to.trim()],
          subject: message.subject,
          text: message.text,
          ...(message.html ? { html: message.html } : {}),
        }),
      });
      const text = await res.text();
      let raw: unknown = text;
      try {
        raw = JSON.parse(text);
      } catch {
        /* keep */
      }
      if (!res.ok) {
        throw new Error(`Resend ${res.status}: ${text.slice(0, 400)}`);
      }
      const messageId =
        raw &&
        typeof raw === "object" &&
        typeof (raw as { id?: string }).id === "string"
          ? (raw as { id: string }).id
          : null;
      return { provider: "resend", messageId };
    },
  };
}
