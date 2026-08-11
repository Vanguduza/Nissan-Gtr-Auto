import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  fromAmountMinor,
  moneyMinorFromJson,
  moneyMinorToJson,
  moneyToMinor,
  toAmountMinor,
} from "./money.ts";

describe("amountMinor helpers", () => {
  it("converts USD majors to cents", () => {
    assert.equal(toAmountMinor(12.34, "USD"), 1234n);
    assert.equal(toAmountMinor(0, "USD"), 0n);
    assert.equal(toAmountMinor(-1.5, "ZIG"), -150n);
  });

  it("round-trips via Money bridge", () => {
    const minor = moneyToMinor({ amount: 99.99, currency: "USD" });
    assert.equal(minor.amountMinor, 9999n);
    assert.equal(fromAmountMinor(minor.amountMinor, "USD"), 99.99);
  });

  it("JSON uses string minor units", () => {
    const json = moneyMinorToJson({
      amountMinor: 250n,
      currency: "ZIG",
      fxRateId: "rate-1",
    });
    assert.equal(json.amountMinor, "250");
    const back = moneyMinorFromJson(json);
    assert.equal(back.amountMinor, 250n);
    assert.equal(back.fxRateId, "rate-1");
  });

  it("rejects non-finite majors", () => {
    assert.throws(() => toAmountMinor(Number.NaN, "USD"));
  });
});
