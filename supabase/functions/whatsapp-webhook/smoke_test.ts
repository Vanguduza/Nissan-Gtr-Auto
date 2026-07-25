/**
 * Smoke tests for WhatsApp parts-finder bot (no live Meta / DB required).
 *
 * Run from repo root (Deno 1.x+):
 *   deno test --allow-env supabase/functions/whatsapp-webhook/smoke_test.ts
 *
 * Covers: verify challenge, Meta signature, mode parse, mock catalog format,
 * rate-limit deny message. SQL rate-limit RPC covered in
 * supabase/tests/whatsapp_bot_rate_limit_smoke.sql (apply migration first).
 */
import {
  assertEquals,
  assertStringIncludes,
} from "https://deno.land/std@0.224.0/asserts.ts";
import {
  formatHandoffMessage,
  formatRateLimitMessage,
  formatSearchReply,
  partDeepLink,
  siteOrigin,
} from "./format.ts";
import {
  extractInboundTextMessages,
  handleVerifyGet,
  hmacSha256Hex,
  metaSignatureHex,
  verifyMetaSignature,
} from "./meta_auth.ts";
import { HELP_TEXT, parseInboundText } from "./parse.ts";

Deno.test("GET verify challenge succeeds with matching token", () => {
  const url = new URL(
    "https://example.test/functions/v1/whatsapp-webhook" +
      "?hub.mode=subscribe&hub.verify_token=secret-token&hub.challenge=12345",
  );
  const res = handleVerifyGet(url, "secret-token");
  assertEquals(res.status, 200);
});

Deno.test("GET verify rejects wrong token", async () => {
  const url = new URL(
    "https://example.test/functions/v1/whatsapp-webhook" +
      "?hub.mode=subscribe&hub.verify_token=wrong&hub.challenge=12345",
  );
  const res = handleVerifyGet(url, "secret-token");
  assertEquals(res.status, 403);
  const body = await res.json();
  assertEquals(body.error, "verification failed");
});

Deno.test("Meta signature verifies X-Hub-Signature-256", async () => {
  const secret = "app-secret-test";
  const raw = '{"object":"whatsapp_business_account"}';
  const hex = await hmacSha256Hex(raw, secret);
  const ok = await verifyMetaSignature(raw, `sha256=${hex}`, secret);
  assertEquals(ok, true);
  assertEquals(metaSignatureHex(`sha256=${hex}`), hex);
  const bad = await verifyMetaSignature(raw, `sha256=${"0".repeat(64)}`, secret);
  assertEquals(bad, false);
});

Deno.test("parseInboundText modes and handoff", () => {
  assertEquals(parseInboundText("part 16546-EA00A"), {
    kind: "search",
    mode: "part",
    query: "16546-EA00A",
  });
  assertEquals(parseInboundText("vin:JN1TANT31U0"), {
    kind: "search",
    mode: "vin",
    query: "JN1TANT31U0",
  });
  assertEquals(parseInboundText("model Navara D40").kind, "search");
  assertEquals(parseInboundText("pnc 21456").kind, "search");
  assertEquals(parseInboundText("agent").kind, "handoff");
  assertEquals(parseInboundText("human").kind, "handoff");
  assertEquals(parseInboundText("help").kind, "handoff");
  assertEquals(parseInboundText("").kind, "help");
  assertEquals(parseInboundText("16546-EA00A"), {
    kind: "search",
    mode: "part",
    query: "16546-EA00A",
  });
  assertStringIncludes(HELP_TEXT, "part <OEM");
});

Deno.test("formatSearchReply mock catalog + PDP deep-link", () => {
  const origin = siteOrigin("https://nissangtrauto.co.zw/");
  assertEquals(origin, "https://nissangtrauto.co.zw");
  assertEquals(
    partDeepLink(origin, "16546-EA00A"),
    "https://nissangtrauto.co.zw/parts/16546-EA00A",
  );

  const reply = formatSearchReply(
    {
      mode: "part",
      query: "16546",
      results: [
        {
          type: "part",
          oem_part_number: "16546-EA00A",
          category_name: "Filter",
        },
        {
          type: "part",
          oem_part_number: "16546-EA01A",
          category_name: "Filter",
        },
      ],
    },
    origin,
  );
  assertEquals(reply !== null, true);
  assertStringIncludes(reply!, "16546-EA00A");
  assertStringIncludes(
    reply!,
    "https://nissangtrauto.co.zw/parts/16546-EA00A",
  );
  assertEquals(formatSearchReply({ mode: "part", query: "x", results: [] }, origin), null);
});

Deno.test("rate-limit deny message and handoff copy", () => {
  assertStringIncludes(formatRateLimitMessage(30), "30s");
  assertStringIncludes(formatHandoffMessage("+263771234567"), "+263771234567");
});

Deno.test("extractInboundTextMessages from Meta payload", () => {
  const msgs = extractInboundTextMessages({
    object: "whatsapp_business_account",
    entry: [
      {
        changes: [
          {
            field: "messages",
            value: {
              messages: [
                {
                  from: "263771234567",
                  id: "wamid.1",
                  type: "text",
                  text: { body: "part ABC" },
                },
                {
                  from: "263771234567",
                  id: "wamid.2",
                  type: "image",
                },
              ],
            },
          },
        ],
      },
    ],
  });
  assertEquals(msgs.length, 1);
  assertEquals(msgs[0]!.body, "part ABC");
});
