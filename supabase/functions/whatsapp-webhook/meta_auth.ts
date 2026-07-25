/**
 * Meta Cloud API webhook AuthZ helpers (verify challenge + X-Hub-Signature-256).
 * Fail closed when WHATSAPP_APP_SECRET unset (unless local unverified flag).
 */

import {
  isLocalUnverifiedAllowed,
  timingSafeEqualStr,
} from "../_shared/payment_edge.ts";

export function allowWhatsAppUnverifiedLocal(): boolean {
  return isLocalUnverifiedAllowed("WHATSAPP_ALLOW_UNVERIFIED_LOCAL");
}

/** Extract hex digest from X-Hub-Signature-256 (sha256=<hex>). */
export function metaSignatureHex(headerValue: string | null): string {
  if (!headerValue) return "";
  const v = headerValue.trim();
  const m = /^sha256=(.+)$/i.exec(v);
  return (m ? m[1]! : v).trim().toLowerCase();
}

export async function hmacSha256Hex(
  rawBody: string,
  secret: string,
): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(rawBody),
  );
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export async function verifyMetaSignature(
  rawBody: string,
  signatureHeader: string | null,
  appSecret: string,
): Promise<boolean> {
  const provided = metaSignatureHex(signatureHeader);
  if (!provided) return false;
  const expected = (await hmacSha256Hex(rawBody, appSecret)).toLowerCase();
  return timingSafeEqualStr(expected, provided);
}

/**
 * GET hub.challenge verify.
 * Returns challenge Response on success, or error Response.
 */
export function handleVerifyGet(
  url: URL,
  verifyToken: string | undefined,
): Response {
  const mode = url.searchParams.get("hub.mode");
  const token = url.searchParams.get("hub.verify_token");
  const challenge = url.searchParams.get("hub.challenge");

  if (!verifyToken?.trim()) {
    return new Response(
      JSON.stringify({
        error:
          "WHATSAPP_VERIFY_TOKEN unset — cannot complete webhook verification",
      }),
      { status: 503, headers: { "Content-Type": "application/json" } },
    );
  }

  if (mode === "subscribe" && token === verifyToken && challenge != null) {
    return new Response(challenge, {
      status: 200,
      headers: { "Content-Type": "text/plain" },
    });
  }

  return new Response(JSON.stringify({ error: "verification failed" }), {
    status: 403,
    headers: { "Content-Type": "application/json" },
  });
}

export type InboundTextMessage = {
  from: string;
  messageId: string;
  body: string;
};

/** Pull text messages from Meta WhatsApp Cloud webhook JSON. */
export function extractInboundTextMessages(
  payload: unknown,
): InboundTextMessage[] {
  const out: InboundTextMessage[] = [];
  if (!payload || typeof payload !== "object") return out;
  const entry = (payload as { entry?: unknown }).entry;
  if (!Array.isArray(entry)) return out;

  for (const e of entry) {
    if (!e || typeof e !== "object") continue;
    const changes = (e as { changes?: unknown }).changes;
    if (!Array.isArray(changes)) continue;
    for (const ch of changes) {
      if (!ch || typeof ch !== "object") continue;
      const value = (ch as { value?: unknown }).value;
      if (!value || typeof value !== "object") continue;
      const messages = (value as { messages?: unknown }).messages;
      if (!Array.isArray(messages)) continue;
      for (const msg of messages) {
        if (!msg || typeof msg !== "object") continue;
        const m = msg as {
          from?: unknown;
          id?: unknown;
          type?: unknown;
          text?: { body?: unknown };
        };
        if (m.type !== "text") continue;
        const from = typeof m.from === "string" ? m.from : "";
        const body =
          m.text && typeof m.text.body === "string" ? m.text.body : "";
        const messageId = typeof m.id === "string" ? m.id : "";
        if (!from || !body.trim()) continue;
        out.push({ from, messageId, body });
      }
    }
  }
  return out;
}
