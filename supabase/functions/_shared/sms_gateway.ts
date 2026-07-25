/**
 * Generic SMS HTTP gateway client.
 *
 * Assumed provider shape (document in README / .env.example):
 *   POST {SMS_GATEWAY_BASE_URL}/messages
 *   Authorization: Bearer {SMS_GATEWAY_API_KEY}
 *   Content-Type: application/json
 *   Body: { "to": "+263…", "from": "<SMS_GATEWAY_SENDER>", "text": "…" }
 *   2xx JSON: { "id" } or { "message_id" } or { "messageId" }
 *
 * Env:
 *   SMS_GATEWAY_API_KEY     — required for real send
 *   SMS_GATEWAY_BASE_URL    — default https://sms.nissangtrauto.co.zw/v1
 *   SMS_GATEWAY_SENDER      — optional alphanumeric / short code
 */

export type SmsGatewayConfig = {
  apiKey: string;
  baseUrl: string;
  sender: string | null;
};

export type SmsSendResult = {
  messageId: string | null;
  raw: unknown;
};

export class SmsGatewayError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
    public readonly body?: string,
  ) {
    super(message);
    this.name = "SmsGatewayError";
  }
}

export function getSmsGatewayConfig(): SmsGatewayConfig | null {
  const apiKey = Deno.env.get("SMS_GATEWAY_API_KEY")?.trim() ?? "";
  if (!apiKey) return null;
  const baseUrl = (
    Deno.env.get("SMS_GATEWAY_BASE_URL")?.trim() ||
    "https://sms.nissangtrauto.co.zw/v1"
  ).replace(/\/$/, "");
  const sender = Deno.env.get("SMS_GATEWAY_SENDER")?.trim() || null;
  return { apiKey, baseUrl, sender };
}

function extractMessageId(raw: unknown): string | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  for (const k of ["id", "message_id", "messageId", "sid"]) {
    const v = o[k];
    if (typeof v === "string" && v) return v;
  }
  return null;
}

/** Send one SMS. `to` should be E.164 (with or without +). */
export async function sendSms(
  cfg: SmsGatewayConfig,
  to: string,
  text: string,
): Promise<SmsSendResult> {
  const recipient = to.trim();
  if (!recipient) throw new SmsGatewayError("SMS recipient required");
  if (!text.trim()) throw new SmsGatewayError("SMS body required");

  const payload: Record<string, string> = {
    to: recipient,
    text: text.slice(0, 1600),
  };
  if (cfg.sender) payload.from = cfg.sender;

  const res = await fetch(`${cfg.baseUrl}/messages`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${cfg.apiKey}`,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(payload),
  });
  const bodyText = await res.text();
  let raw: unknown = bodyText;
  try {
    raw = JSON.parse(bodyText);
  } catch {
    /* keep text */
  }
  if (!res.ok) {
    throw new SmsGatewayError(
      `SMS gateway ${res.status}: ${bodyText.slice(0, 400)}`,
      res.status,
      bodyText,
    );
  }
  return { messageId: extractMessageId(raw), raw };
}
