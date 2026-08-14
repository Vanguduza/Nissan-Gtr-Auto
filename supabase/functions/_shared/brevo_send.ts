/**
 * Brevo (Sendinblue) SMTP email API — promo / CRM channel only.
 *
 * Env:
 *   BREVO_API_KEY          — required for real send
 *   BREVO_FROM_EMAIL       — sender address
 *   BREVO_FROM_NAME        — optional display name
 *   BREVO_API_BASE         — optional; default https://api.brevo.com
 *
 * Transactional mail stays on Resend (`email_send.ts`). Dial-a-Spare §4.6 split.
 */

export type BrevoSendConfig = {
  apiKey: string;
  fromEmail: string;
  fromName: string | null;
  baseUrl: string;
};

export type BrevoSendResult = {
  messageId: string | null;
  raw: unknown;
};

export class BrevoSendError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
    public readonly body?: string,
  ) {
    super(message);
    this.name = "BrevoSendError";
  }
}

export function getBrevoSendConfig(): BrevoSendConfig | null {
  const apiKey = Deno.env.get("BREVO_API_KEY")?.trim() || "";
  const fromEmail =
    Deno.env.get("BREVO_FROM_EMAIL")?.trim() ||
    Deno.env.get("CRM_FROM_EMAIL")?.trim() ||
    "";
  if (!apiKey || !fromEmail) return null;
  const fromName = Deno.env.get("BREVO_FROM_NAME")?.trim() || null;
  const baseUrl = (
    Deno.env.get("BREVO_API_BASE")?.trim() || "https://api.brevo.com"
  ).replace(/\/$/, "");
  return { apiKey, fromEmail, fromName, baseUrl };
}

export async function sendBrevoEmail(
  cfg: BrevoSendConfig,
  opts: {
    to: string;
    subject: string;
    text: string;
    html?: string;
  },
): Promise<BrevoSendResult> {
  const to = opts.to.trim();
  if (!to) throw new BrevoSendError("email recipient required");
  if (!opts.subject.trim()) throw new BrevoSendError("email subject required");

  const body: Record<string, unknown> = {
    sender: {
      email: cfg.fromEmail,
      ...(cfg.fromName ? { name: cfg.fromName } : {}),
    },
    to: [{ email: to }],
    subject: opts.subject,
    textContent: opts.text,
  };
  if (opts.html?.trim()) body.htmlContent = opts.html;

  const res = await fetch(`${cfg.baseUrl}/v3/smtp/email`, {
    method: "POST",
    headers: {
      "api-key": cfg.apiKey,
      "Content-Type": "application/json",
      Accept: "application/json",
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
    throw new BrevoSendError(
      `Brevo API ${res.status}: ${text.slice(0, 400)}`,
      res.status,
      text,
    );
  }
  const messageId =
    raw &&
      typeof raw === "object" &&
      typeof (raw as { messageId?: string }).messageId === "string"
      ? (raw as { messageId: string }).messageId
      : null;
  return { messageId, raw };
}
