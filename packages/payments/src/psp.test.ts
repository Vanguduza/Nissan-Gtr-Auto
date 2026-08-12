import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { describe, it } from "node:test";
import {
  AI_NEVER_WRITES_MONEY,
  buildCheckoutDisplay,
  createStubPspAdapter,
  defaultPspRegistry,
} from "./psp.ts";

describe("PspRegistry", () => {
  it("registers stub adapters", () => {
    const r = defaultPspRegistry();
    assert.ok(r.get("paynow"));
    assert.equal(r.list().includes("contipay"), true);
  });
});

describe("stub initiate idempotencyKey", () => {
  it("is deterministic on the same idempotencyKey", async () => {
    const adapter = createStubPspAdapter("paynow");
    const a = await adapter.initiate({
      method: "paynow",
      amount: { amountMinor: 1000n, currency: "USD" },
      idempotencyKey: "order-42",
    });
    const b = await adapter.initiate({
      method: "paynow",
      amount: { amountMinor: 9999n, currency: "USD" },
      idempotencyKey: "order-42",
    });
    assert.equal(a.ok && a.intentId, "stub_paynow_order-42");
    assert.equal(b.ok && b.intentId, "stub_paynow_order-42");
    assert.equal(a.ok && a.intentId, b.ok && b.intentId);
  });

  it("differs across idempotencyKeys", async () => {
    const adapter = createStubPspAdapter("contipay");
    const a = await adapter.initiate({
      method: "contipay",
      amount: { amountMinor: 100n, currency: "USD" },
      idempotencyKey: "k1",
    });
    const b = await adapter.initiate({
      method: "contipay",
      amount: { amountMinor: 100n, currency: "USD" },
      idempotencyKey: "k2",
    });
    assert.notEqual(a.ok && a.intentId, b.ok && b.intentId);
  });
});

describe("stub handleWebhook replay", () => {
  it("first call duplicate:false; replay same intent duplicate:true", async () => {
    const adapter = createStubPspAdapter("ecocash");
    const body = JSON.stringify({ intentId: "stub_ecocash_order-7" });
    const first = await adapter.handleWebhook(body, {});
    const second = await adapter.handleWebhook(body, {});
    assert.equal(first.ok, true);
    assert.equal(second.ok, true);
    if (first.ok && second.ok) {
      assert.equal(first.duplicate, false);
      assert.equal(second.duplicate, true);
      assert.equal(first.intentId, second.intentId);
      assert.equal(first.status, "captured");
    }
  });

  it("resolves logical intent from idempotencyKey in body", async () => {
    const adapter = createStubPspAdapter("cod");
    const body = JSON.stringify({ idempotencyKey: "cod-99" });
    const first = await adapter.handleWebhook(body, {});
    const second = await adapter.handleWebhook(body, {});
    assert.equal(first.ok && first.intentId, "stub_cod_cod-99");
    assert.equal(first.ok && first.duplicate, false);
    assert.equal(second.ok && second.duplicate, true);
  });

  it("treats distinct intents as independent (not duplicates of each other)", async () => {
    const adapter = createStubPspAdapter("paynow");
    const a = await adapter.handleWebhook(JSON.stringify({ intentId: "a" }), {});
    const b = await adapter.handleWebhook(JSON.stringify({ intentId: "b" }), {});
    assert.equal(a.ok && a.duplicate, false);
    assert.equal(b.ok && b.duplicate, false);
  });
});

describe("D-57 checkout display", () => {
  it("keeps USD for Paynow", () => {
    const d = buildCheckoutDisplay({
      usdMinor: 10000n,
      payMethod: "paynow",
      zigRatePerUsd: 27.5,
    });
    assert.equal(d.payCurrency, "USD");
    assert.equal(d.payable.amountMinor, 10000n);
    assert.ok(d.indicativeZigMinor != null);
  });

  it("converts EcoCash to ZiG with fx", () => {
    const d = buildCheckoutDisplay({
      usdMinor: 10000n,
      payMethod: "ecocash",
      zigRatePerUsd: 25,
      fxRateId: "rate-1",
    });
    assert.equal(d.payCurrency, "ZIG");
    assert.equal(d.payable.amountMinor, 250000n);
    assert.equal(d.fxRateId, "rate-1");
    assert.equal(d.payable.fxRateId, "rate-1");
  });

  it("requires zigRatePerUsd for EcoCash", () => {
    assert.throws(
      () =>
        buildCheckoutDisplay({
          usdMinor: 10000n,
          payMethod: "ecocash",
        }),
      /Daily ZiG rate required/,
    );
  });
});

describe("Finance C6 — AI never writes money", () => {
  it("exports package-side evidence of the AI money ban", () => {
    assert.match(AI_NEVER_WRITES_MONEY.rule, /AI must never invent payable amounts/i);
    assert.ok(AI_NEVER_WRITES_MONEY.forbidden.includes("AI auto-created purchase orders"));
    assert.ok(
      AI_NEVER_WRITES_MONEY.payableSourcesOfTruth.some((s) =>
        s.toLowerCase().includes("webhook"),
      ),
    );
  });

  it("Semgrep-style: AI workers omit payable / amount_minor / PO money writes", () => {
    const root = path.resolve(import.meta.dirname, "../../..");
    const forbidden =
      /\b(amount_minor|amountMinor|payable|purchase_orders?|create_purchase_order)\b/i;
    for (const rel of AI_NEVER_WRITES_MONEY.greppedWorkerPaths) {
      const full = path.join(root, rel);
      assert.ok(fs.existsSync(full), `missing worker: ${rel}`);
      const src = fs.readFileSync(full, "utf8");
      assert.equal(
        forbidden.test(src),
        false,
        `${rel} must not write payable / amount_minor / PO money`,
      );
    }
  });
});
