import { assertEquals } from "jsr:@std/assert@1";
import { allowLocalChannelStub } from "./channel_env.ts";
import { allowAuthOtpLocalStub } from "./auth_otp_env.ts";
import { isLocalDevelopmentEnvironment } from "./local_env.ts";
import {
  contipayHmacSha256Hex,
  contipaySignatureFromHeaders,
  contipayWebhookContract,
  isEcocashSuccessStatus,
  isLocalUnverifiedAllowed,
  timingSafeEqualStr,
} from "./payment_edge.ts";
import { assertWorkerSecret } from "./worker_auth.ts";
import { allowWhatsAppUnverifiedLocal } from "../whatsapp-webhook/meta_auth.ts";

const flags = [
  "PAYNOW_ALLOW_UNVERIFIED_LOCAL",
  "CONTIPAY_ALLOW_UNVERIFIED_LOCAL",
  "ECOCASH_ALLOW_UNVERIFIED_LOCAL",
  "WORKER_ALLOW_UNVERIFIED_LOCAL",
  "WHATSAPP_ALLOW_UNVERIFIED_LOCAL",
  "AUTH_OTP_ALLOW_UNVERIFIED_LOCAL",
];
function reset() {
  for (
    const name of [
      "ENVIRONMENT",
      "ENV",
      "NODE_ENV",
      "DENO_ENV",
      "DENO_DEPLOYMENT_ID",
      "WORKER_SHARED_SECRET",
      "CONTIPAY_WEBHOOK_CONTRACT",
      "CONTIPAY_WEBHOOK_SIGNATURE_HEADER",
      ...flags,
    ]
  ) Deno.env.delete(name);
  Deno.env.set("SUPABASE_URL", "http://127.0.0.1:54321");
}

Deno.test("EcoCash exact allowlist cannot settle negative substring statuses", () => {
  for (
    const status of [
      "paid",
      "SUCCESS",
      "successful",
      " completed ",
      "complete",
      "approved",
      "ok",
      "00",
    ]
  ) {
    assertEquals(isEcocashSuccessStatus(status), true, status);
  }
  for (
    const status of [
      undefined,
      null,
      "",
      "FAILED",
      "unpaid",
      "unsuccessful",
      "not_ok",
      "not_paid",
      "not_approved",
      "payment successful",
    ]
  ) {
    assertEquals(isEcocashSuccessStatus(status), false, String(status));
  }
});

Deno.test("local flags require an exact local host, never a hostname substring or port", () => {
  reset();
  Deno.env.set("PAYNOW_ALLOW_UNVERIFIED_LOCAL", "1");
  for (
    const url of [
      "",
      "invalid",
      "https://project.supabase.co",
      "https://production.example:54321",
      "https://localhost.example",
      "https://example.test/?localhost=1",
    ]
  ) {
    Deno.env.set("SUPABASE_URL", url);
    assertEquals(
      isLocalUnverifiedAllowed("PAYNOW_ALLOW_UNVERIFIED_LOCAL"),
      false,
      url,
    );
  }
  for (
    const url of [
      "http://localhost:54321",
      "http://127.0.0.1:54321",
      "http://[::1]:54321",
      "http://kong:8000",
    ]
  ) {
    Deno.env.set("SUPABASE_URL", url);
    assertEquals(
      isLocalUnverifiedAllowed("PAYNOW_ALLOW_UNVERIFIED_LOCAL"),
      true,
      url,
    );
  }
});

Deno.test("every explicit production marker overrides even local URLs and enabled flags", () => {
  for (
    const [name, value] of [
      ["ENVIRONMENT", "production"],
      ["ENV", "prod"],
      ["NODE_ENV", "production"],
      ["DENO_ENV", "staging"],
      ["DENO_DEPLOYMENT_ID", "hosted-deployment"],
    ]
  ) {
    reset();
    for (const flag of flags) Deno.env.set(flag, "1");
    Deno.env.set(name, value);
    assertEquals(isLocalDevelopmentEnvironment(), false, name);
    for (const flag of flags) {
      assertEquals(isLocalUnverifiedAllowed(flag), false, flag);
    }
    assertEquals(allowLocalChannelStub(), false);
    assertEquals(allowWhatsAppUnverifiedLocal(), false);
    assertEquals(allowAuthOtpLocalStub(), false);
    assertEquals(
      assertWorkerSecret(new Request("https://example.test/worker"))?.status,
      401,
    );
  }
});

Deno.test("hosted backend refuses worker/channel/Meta/OTP stubs while local development still works", () => {
  reset();
  for (const flag of flags) Deno.env.set(flag, "1");
  assertEquals(allowLocalChannelStub(), true);
  assertEquals(allowWhatsAppUnverifiedLocal(), true);
  assertEquals(allowAuthOtpLocalStub(), true);
  assertEquals(
    assertWorkerSecret(new Request("http://localhost/worker")),
    null,
  );
  Deno.env.set("SUPABASE_URL", "https://project.supabase.co");
  assertEquals(allowLocalChannelStub(), false);
  assertEquals(allowWhatsAppUnverifiedLocal(), false);
  assertEquals(allowAuthOtpLocalStub(), false);
  assertEquals(
    assertWorkerSecret(new Request("https://example.test/worker"))?.status,
    401,
  );
});

Deno.test("configured worker secret always needs the matching header", () => {
  reset();
  Deno.env.set("WORKER_ALLOW_UNVERIFIED_LOCAL", "1");
  Deno.env.set("WORKER_SHARED_SECRET", "test-worker-secret");
  assertEquals(allowLocalChannelStub(), false);
  for (const header of ["", "incorrect"]) {
    assertEquals(
      assertWorkerSecret(
        new Request("http://localhost/worker", {
          headers: { "x-worker-secret": header },
        }),
      )?.status,
      401,
    );
  }
  assertEquals(
    assertWorkerSecret(
      new Request("http://localhost/worker", {
        headers: { "x-worker-secret": "test-worker-secret" },
      }),
    ),
    null,
  );
});

Deno.test("ContiPay requires an explicit supported contract and exact configured header", async () => {
  reset();
  const raw = '{"external_ref":"test-ref","status":"paid"}';
  const signature = await contipayHmacSha256Hex(raw, "test-hmac-secret");
  assertEquals(contipayWebhookContract(), null);
  Deno.env.set("CONTIPAY_WEBHOOK_CONTRACT", "hmac-sha256-raw-body-v1");
  assertEquals(contipayWebhookContract(), null);
  Deno.env.set("CONTIPAY_WEBHOOK_SIGNATURE_HEADER", "x-merchant-proof");
  assertEquals(
    contipaySignatureFromHeaders(
      new Request("https://example.test/", {
        headers: { "x-signature": signature },
      }),
    ),
    "",
  );
  const provided = contipaySignatureFromHeaders(
    new Request("https://example.test/", {
      headers: { "x-merchant-proof": `sha256=${signature}` },
    }),
  );
  assertEquals(timingSafeEqualStr(signature, provided), true);
  assertEquals(
    timingSafeEqualStr(
      await contipayHmacSha256Hex(raw + " ", "test-hmac-secret"),
      provided,
    ),
    false,
  );
  Deno.env.set("CONTIPAY_WEBHOOK_CONTRACT", "unknown-provider-format");
  assertEquals(contipayWebhookContract(), null);
});
