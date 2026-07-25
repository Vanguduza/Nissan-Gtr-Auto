/**
 * WhatsApp Cloud API client (Meta Graph).
 * Reused by process-customer-receipts (outbound PDF/text) and whatsapp-webhook
 * parts-finder bot (text only — do not send receipt PDFs from bot turns).
 *
 * Env:
 *   WHATSAPP_ACCESS_TOKEN       — permanent / system user token
 *   WHATSAPP_PHONE_NUMBER_ID    — Cloud API phone number id
 *   WHATSAPP_API_VERSION        — optional, default v21.0
 *
 * Fail closed when token or phone id missing (callers may allow local stub separately).
 */

export type WhatsAppCloudConfig = {
  accessToken: string;
  phoneNumberId: string;
  apiVersion: string;
};

export type WhatsAppSendResult = {
  messageId: string | null;
  raw: unknown;
};

export class WhatsAppCloudError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
    public readonly body?: string,
  ) {
    super(message);
    this.name = "WhatsAppCloudError";
  }
}

/** Read Cloud API config from env. Returns null if required secrets missing. */
export function getWhatsAppCloudConfig(): WhatsAppCloudConfig | null {
  const accessToken = Deno.env.get("WHATSAPP_ACCESS_TOKEN")?.trim() ?? "";
  const phoneNumberId = Deno.env.get("WHATSAPP_PHONE_NUMBER_ID")?.trim() ?? "";
  if (!accessToken || !phoneNumberId) return null;
  const apiVersion =
    Deno.env.get("WHATSAPP_API_VERSION")?.trim() || "v21.0";
  return { accessToken, phoneNumberId, apiVersion };
}

/** Digits-only MSISDN for Cloud API `to` (no leading +). */
export function normalizeWhatsAppTo(e164OrDigits: string): string {
  return e164OrDigits.replace(/\D/g, "");
}

function messagesUrl(cfg: WhatsAppCloudConfig): string {
  return `https://graph.facebook.com/${cfg.apiVersion}/${cfg.phoneNumberId}/messages`;
}

async function postMessages(
  cfg: WhatsAppCloudConfig,
  payload: Record<string, unknown>,
): Promise<WhatsAppSendResult> {
  const res = await fetch(messagesUrl(cfg), {
    method: "POST",
    headers: {
      Authorization: `Bearer ${cfg.accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      messaging_product: "whatsapp",
      ...payload,
    }),
  });
  const text = await res.text();
  let raw: unknown = text;
  try {
    raw = JSON.parse(text);
  } catch {
    /* keep text */
  }
  if (!res.ok) {
    throw new WhatsAppCloudError(
      `WhatsApp Cloud API ${res.status}: ${text.slice(0, 400)}`,
      res.status,
      text,
    );
  }
  const messageId =
    raw &&
    typeof raw === "object" &&
    Array.isArray((raw as { messages?: { id?: string }[] }).messages)
      ? ((raw as { messages: { id?: string }[] }).messages[0]?.id ?? null)
      : null;
  return { messageId, raw };
}

/** Send a plain text message. */
export async function sendWhatsAppText(
  cfg: WhatsAppCloudConfig,
  toE164: string,
  body: string,
): Promise<WhatsAppSendResult> {
  const to = normalizeWhatsAppTo(toE164);
  if (!to) throw new WhatsAppCloudError("WhatsApp recipient required");
  if (!body.trim()) throw new WhatsAppCloudError("WhatsApp text body required");
  return postMessages(cfg, {
    to,
    type: "text",
    text: { preview_url: false, body: body.slice(0, 4096) },
  });
}

/**
 * Send a document. Prefer HTTPS `link` (public/signed URL Meta can fetch).
 * Alternatively pass `mediaId` from a prior /media upload.
 */
export async function sendWhatsAppDocument(
  cfg: WhatsAppCloudConfig,
  toE164: string,
  opts: {
    link?: string;
    mediaId?: string;
    filename?: string;
    caption?: string;
  },
): Promise<WhatsAppSendResult> {
  const to = normalizeWhatsAppTo(toE164);
  if (!to) throw new WhatsAppCloudError("WhatsApp recipient required");
  if (!opts.link && !opts.mediaId) {
    throw new WhatsAppCloudError("WhatsApp document requires link or mediaId");
  }
  const document: Record<string, string> = {};
  if (opts.mediaId) document.id = opts.mediaId;
  if (opts.link) document.link = opts.link;
  if (opts.filename) document.filename = opts.filename;
  if (opts.caption) document.caption = opts.caption.slice(0, 1024);
  return postMessages(cfg, { to, type: "document", document });
}

/**
 * Upload PDF bytes to Meta media, return media id for sendWhatsAppDocument.
 * https://developers.facebook.com/docs/whatsapp/cloud-api/reference/media
 */
export async function uploadWhatsAppMediaPdf(
  cfg: WhatsAppCloudConfig,
  bytes: Uint8Array,
  filename = "receipt.pdf",
): Promise<string> {
  const url =
    `https://graph.facebook.com/${cfg.apiVersion}/${cfg.phoneNumberId}/media`;
  const form = new FormData();
  form.append("messaging_product", "whatsapp");
  form.append("type", "application/pdf");
  form.append(
    "file",
    new Blob([bytes as BlobPart], { type: "application/pdf" }),
    filename,
  );
  const res = await fetch(url, {
    method: "POST",
    headers: { Authorization: `Bearer ${cfg.accessToken}` },
    body: form,
  });
  const text = await res.text();
  let raw: { id?: string } = {};
  try {
    raw = JSON.parse(text) as { id?: string };
  } catch {
    /* ignore */
  }
  if (!res.ok || !raw.id) {
    throw new WhatsAppCloudError(
      `WhatsApp media upload ${res.status}: ${text.slice(0, 400)}`,
      res.status,
      text,
    );
  }
  return raw.id;
}
