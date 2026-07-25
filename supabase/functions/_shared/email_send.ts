/**
 * Transactional email via Resend HTTP API (default) or compatible providers.
 *
 * Env:
 *   EMAIL_API_KEY   — Resend (or provider) API key; required for real send
 *   EMAIL_FROM      — e.g. receipts@nissangtrauto.co.zw
 *   REPORT_FROM_EMAIL / RECEIPT_FROM_EMAIL — optional From fallbacks
 *   EMAIL_API_BASE  — optional; default https://api.resend.com
 *
 * Assumed shape (Resend):
 *   POST {base}/emails
 *   Authorization: Bearer {EMAIL_API_KEY}
 *   { from, to, subject, text, attachments?: [{ filename, content: base64 }] }
 */

export type EmailSendConfig = {
  apiKey: string;
  from: string;
  baseUrl: string;
};

export type EmailAttachment = {
  filename: string;
  contentBase64: string;
  contentType?: string;
};

export type EmailSendResult = {
  messageId: string | null;
  raw: unknown;
};

export class EmailSendError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
    public readonly body?: string,
  ) {
    super(message);
    this.name = "EmailSendError";
  }
}

export function getEmailSendConfig(): EmailSendConfig | null {
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
  if (!apiKey || !from) return null;
  const baseUrl = (
    Deno.env.get("EMAIL_API_BASE")?.trim() || "https://api.resend.com"
  ).replace(/\/$/, "");
  return { apiKey, from, baseUrl };
}

export async function sendEmail(
  cfg: EmailSendConfig,
  opts: {
    to: string;
    subject: string;
    text: string;
    attachments?: EmailAttachment[];
  },
): Promise<EmailSendResult> {
  const to = opts.to.trim();
  if (!to) throw new EmailSendError("email recipient required");
  if (!opts.subject.trim()) throw new EmailSendError("email subject required");

  const body: Record<string, unknown> = {
    from: cfg.from,
    to: [to],
    subject: opts.subject,
    text: opts.text,
  };
  if (opts.attachments?.length) {
    body.attachments = opts.attachments.map((a) => ({
      filename: a.filename,
      content: a.contentBase64,
      ...(a.contentType ? { content_type: a.contentType } : {}),
    }));
  }

  const res = await fetch(`${cfg.baseUrl}/emails`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${cfg.apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  let raw: unknown = text;
  try {
    raw = JSON.parse(text);
  } catch {
    /* keep */
  }
  if (!res.ok) {
    throw new EmailSendError(
      `Email API ${res.status}: ${text.slice(0, 400)}`,
      res.status,
      text,
    );
  }
  const messageId =
    raw && typeof raw === "object" && typeof (raw as { id?: string }).id === "string"
      ? (raw as { id: string }).id
      : null;
  return { messageId, raw };
}

/** Uint8Array → base64 for Resend attachments. */
export function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}
