import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { buildCheckoutDisplay, defaultPspRegistry } from "./psp.ts";

describe("PspRegistry", () => {
  it("registers stub adapters", () => {
    const r = defaultPspRegistry();
    assert.ok(r.get("paynow"));
    assert.equal(r.list().includes("contipay"), true);
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
  });
});
