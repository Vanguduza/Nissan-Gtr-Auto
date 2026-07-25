/**
 * Meta Cloud API webhook — WhatsApp parts-finder bot.
 *
 * GET  hub.verify_token / hub.challenge (WHATSAPP_VERIFY_TOKEN)
 * POST X-Hub-Signature-256 (WHATSAPP_APP_SECRET) → parse → rate-limit →
 *      search_catalog (service_role) → sendWhatsAppText + PDP deep-links
 *
 * AuthZ = Meta signature (verify_jwt = false). Do not GRANT search_catalog to anon.
 * Receipt PDF / outbox path is separate (process-customer-receipts).
 *
 * Env:
 *   WHATSAPP_VERIFY_TOKEN, WHATSAPP_APP_SECRET
 *   WHATSAPP_ACCESS_TOKEN, WHATSAPP_PHONE_NUMBER_ID
 *   WHATSAPP_HANDOFF_NUMBER, SITE_URL
 *   WHATSAPP_ALLOW_UNVERIFIED_LOCAL=1 — local only when app secret unset
 *   WHATSAPP_BOT_RATE_LIMIT_MAX (default 20), WHATSAPP_BOT_RATE_LIMIT_WINDOW_SEC (default 60)
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import {
  getWhatsAppCloudConfig,
  sendWhatsAppText,
} from "../_shared/whatsapp_cloud.ts";

function jsonResponse(
  body: unknown,
  status: number,
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
import {
  formatHandoffMessage,
  formatRateLimitMessage,
  formatSearchReply,
  siteOrigin,
} from "./format.ts";
import {
  allowWhatsAppUnverifiedLocal,
  extractInboundTextMessages,
  handleVerifyGet,
  verifyMetaSignature,
} from "./meta_auth.ts";
import { HELP_TEXT, parseInboundText } from "./parse.ts";

function serviceClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
}

function rateLimitMax(): number {
  const n = Number(Deno.env.get("WHATSAPP_BOT_RATE_LIMIT_MAX") ?? "20");
  return Number.isFinite(n) && n > 0 ? Math.min(200, Math.floor(n)) : 20;
}

function rateLimitWindowSec(): number {
  const n = Number(Deno.env.get("WHATSAPP_BOT_RATE_LIMIT_WINDOW_SEC") ?? "60");
  return Number.isFinite(n) && n > 0 ? Math.min(3600, Math.floor(n)) : 60;
}

async function replyText(
  to: string,
  body: string,
): Promise<{ stub: boolean; messageId: string | null }> {
  const cfg = getWhatsAppCloudConfig();
  if (!cfg) {
    if (allowWhatsAppUnverifiedLocal()) {
      console.warn(
        "whatsapp-webhook: stub reply (no Cloud API config)",
        { to: to.replace(/\d(?=\d{4})/g, "*"), len: body.length },
      );
      return { stub: true, messageId: null };
    }
    throw new Error(
      "WHATSAPP_ACCESS_TOKEN / WHATSAPP_PHONE_NUMBER_ID unset",
    );
  }
  const result = await sendWhatsAppText(cfg, to, body);
  return { stub: false, messageId: result.messageId };
}

async function checkRateLimit(
  supabase: SupabaseClient,
  waFrom: string,
): Promise<{ allowed: boolean; retryAfter: number }> {
  const { data, error } = await supabase.rpc("check_whatsapp_bot_rate_limit", {
    p_wa_from: waFrom,
    p_max_requests: rateLimitMax(),
    p_window_seconds: rateLimitWindowSec(),
  });
  if (error) {
    console.error("whatsapp-webhook: rate limit RPC failed", error.message);
    // Fail closed on rate-limit infrastructure errors
    return { allowed: false, retryAfter: 60 };
  }
  const row = data as {
    allowed?: boolean;
    retry_after_seconds?: number;
  } | null;
  return {
    allowed: row?.allowed === true,
    retryAfter: Number(row?.retry_after_seconds ?? 60) || 60,
  };
}

const SEARCH_QUERY_MAX = 256;

async function runSearch(
  supabase: SupabaseClient,
  mode: string,
  query: string,
): Promise<unknown> {
  const p_query = query.length > SEARCH_QUERY_MAX
    ? query.slice(0, SEARCH_QUERY_MAX)
    : query;
  const { data, error } = await supabase.rpc("search_catalog", {
    p_mode: mode,
    p_query,
  });
  if (error) throw new Error(error.message);
  return data;
}

async function handleInbound(
  supabase: SupabaseClient,
  from: string,
  body: string,
): Promise<{ action: string; stub?: boolean }> {
  const handoffNumber =
    Deno.env.get("WHATSAPP_HANDOFF_NUMBER")?.trim() || "";
  const origin = siteOrigin(
    Deno.env.get("SITE_URL") ?? Deno.env.get("NEXT_PUBLIC_SITE_URL"),
  );

  const rate = await checkRateLimit(supabase, from);
  if (!rate.allowed) {
    const r = await replyText(from, formatRateLimitMessage(rate.retryAfter));
    return { action: "rate_limited", stub: r.stub };
  }

  const parsed = parseInboundText(body);

  if (parsed.kind === "help") {
    const r = await replyText(from, HELP_TEXT);
    return { action: "help", stub: r.stub };
  }

  if (parsed.kind === "invalid") {
    const r = await replyText(from, parsed.message);
    return { action: "invalid", stub: r.stub };
  }

  if (parsed.kind === "handoff") {
    const msg = handoffNumber
      ? formatHandoffMessage(handoffNumber)
      : "A team member will assist you shortly. Please call our counter if urgent.";
    const r = await replyText(from, msg);
    return { action: "handoff_keyword", stub: r.stub };
  }

  // search
  const raw = await runSearch(supabase, parsed.mode, parsed.query);
  const reply = formatSearchReply(
    (raw && typeof raw === "object" ? raw : {}) as {
      mode?: string;
      query?: string;
      results?: Record<string, unknown>[];
    },
    origin,
  );

  if (!reply) {
    const msg = handoffNumber
      ? `No catalog matches for ${parsed.mode} "${parsed.query}".\n\n${formatHandoffMessage(handoffNumber)}`
      : `No catalog matches for ${parsed.mode} "${parsed.query}".\nReply: agent for help, or try another search.`;
    const r = await replyText(from, msg);
    return { action: "handoff_empty", stub: r.stub };
  }

  const r = await replyText(from, reply);
  return { action: "search_hits", stub: r.stub };
}

Deno.serve(async (req) => {
  try {
    if (req.method === "GET") {
      return handleVerifyGet(
        new URL(req.url),
        Deno.env.get("WHATSAPP_VERIFY_TOKEN") ?? undefined,
      );
    }

    if (req.method !== "POST") {
      return jsonResponse({ error: "method not allowed" }, 405);
    }

    const MAX_BODY_BYTES = 256 * 1024;
    const contentLength = Number(req.headers.get("content-length") ?? "");
    if (Number.isFinite(contentLength) && contentLength > MAX_BODY_BYTES) {
      return jsonResponse({ error: "payload too large" }, 413);
    }

    const raw = await req.text();
    if (new TextEncoder().encode(raw).byteLength > MAX_BODY_BYTES) {
      return jsonResponse({ error: "payload too large" }, 413);
    }

    const appSecret = Deno.env.get("WHATSAPP_APP_SECRET")?.trim() ?? "";
    const localUnverified =
      !appSecret && allowWhatsAppUnverifiedLocal();

    if (!appSecret) {
      if (!localUnverified) {
        return jsonResponse(
          {
            error:
              "WHATSAPP_APP_SECRET unset — refuse webhook (set WHATSAPP_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)",
          },
          401,
        );
      }
      console.warn(
        "whatsapp-webhook: unverified local stub (WHATSAPP_ALLOW_UNVERIFIED_LOCAL=1)",
      );
    } else {
      const sig = req.headers.get("X-Hub-Signature-256");
      const ok = await verifyMetaSignature(raw, sig, appSecret);
      if (!ok) {
        return jsonResponse({ error: "invalid signature" }, 401);
      }
    }

    let payload: unknown = {};
    try {
      payload = JSON.parse(raw || "{}");
    } catch {
      return jsonResponse({ error: "invalid json" }, 400);
    }

    const messages = extractInboundTextMessages(payload);
    if (messages.length === 0) {
      // Status callbacks / non-text — ack Meta
      return jsonResponse({ ok: true, handled: 0 });
    }

    const supabase = serviceClient();
    const results: { from: string; action: string; stub?: boolean }[] = [];

    for (const msg of messages) {
      try {
        const r = await handleInbound(supabase, msg.from, msg.body);
        results.push({
          from: msg.from.replace(/\d(?=\d{4})/g, "*"),
          ...r,
        });
      } catch (e) {
        console.error("whatsapp-webhook: message handler error", String(e));
        results.push({
          from: msg.from.replace(/\d(?=\d{4})/g, "*"),
          action: "error",
        });
      }
    }

    return jsonResponse({
      ok: true,
      handled: results.length,
      results,
      stub: localUnverified,
      unverified_local: localUnverified,
    });
  } catch (e) {
    console.error("whatsapp-webhook: unhandled error", e);
    return jsonResponse({ error: "internal error" }, 500);
  }
});
