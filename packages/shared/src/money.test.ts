import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  cartLineMoneyDto,
  displayLineTotalMajor,
  displayMajorFromDual,
  displayUnitPriceMajor,
  dualWriteFromMinor,
  dualWriteMoney,
  dualWriteMoneyRpcFields,
  dualWriteUnitPriceRpcFields,
  fromAmountMinor,
  moneyMinorFromJson,
  moneyMinorToJson,
  moneyToMinor,
  preferAmountMinor,
  requireApiMoney,
  settlementMoneyRpcFields,
  sumPreferAmountMinor,
  toAmountMinor,
} from "./money.ts";
import { toAllocatePaymentArgs } from "./payments/types.ts";
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

  it("dualWriteMoney pairs amount + amountMinor", () => {
    const d = dualWriteMoney(10.5, "USD");
    assert.equal(d.amount, 10.5);
    assert.equal(d.amountMinor, 1050n);
    assert.equal(d.currency, "USD");
  });

  it("preferAmountMinor dual-reads minor first", () => {
    const m = preferAmountMinor({
      amountMinor: 1999,
      amountMajor: 99.99,
      currency: "USD",
    });
    assert.equal(m.amountMinor, 1999n);
    assert.equal(
      displayMajorFromDual({
        amountMinor: 1999,
        amountMajor: 1,
        currency: "USD",
      }),
      19.99,
    );
  });

  it("preferAmountMinor falls back to major when minor null", () => {
    const m = preferAmountMinor({
      amountMinor: null,
      amountMajor: 12.34,
      currency: "USD",
    });
    assert.equal(m.amountMinor, 1234n);
  });

  it("sumPreferAmountMinor prefers line minors when present", () => {
    const sum = sumPreferAmountMinor(
      [
        { amountMinor: 1000, amountMajor: 9.99 },
        { amountMinor: null, amountMajor: 2.5 },
      ],
      "USD",
    );
    assert.equal(sum.amountMinor, 1250n);
    assert.equal(fromAmountMinor(sum.amountMinor, "USD"), 12.5);
  });

  it("displayLineTotalMajor / displayUnitPriceMajor dual-read", () => {
    assert.equal(
      displayLineTotalMajor(
        { line_total: 1, line_total_minor: 1999 },
        "USD",
      ),
      19.99,
    );
    assert.equal(
      displayUnitPriceMajor(
        { unit_price: 12.34, unit_price_minor: null },
        "USD",
      ),
      12.34,
    );
  });

  it("dualWriteFromMinor derives major from amountMinor", () => {
    const d = dualWriteFromMinor({ amountMinor: 1999n, currency: "USD" });
    assert.equal(d.amount, 19.99);
    assert.equal(d.amountMinor, 1999n);
  });

  it("dualWriteMoneyRpcFields / dualWriteUnitPriceRpcFields snake_case", () => {
    const a = dualWriteMoneyRpcFields(10.5, "USD");
    assert.equal(a.amount, 10.5);
    assert.equal(a.amount_minor, 1050);
    const u = dualWriteUnitPriceRpcFields(12.34, "ZIG");
    assert.equal(u.unit_price, 12.34);
    assert.equal(u.unit_price_minor, 1234);
    assert.equal(u.currency, "ZIG");
  });

  it("requireApiMoney prefers minor; cartLineMoneyDto derives majors", () => {
    const m = requireApiMoney({
      amountMinor: 500n,
      amountMajor: 1,
      currency: "USD",
    });
    assert.equal(m.amountMinor, 500n);
    const dto = cartLineMoneyDto(
      {
        unit_price: 1,
        unit_price_minor: 250,
        line_total: 2,
        line_total_minor: 500,
      },
      "USD",
    );
    assert.equal(dto.unitPrice.amountMinor, 250n);
    assert.equal(dto.lineTotal.amountMinor, 500n);
    assert.equal(dto.unitPriceMajor, 2.5);
    assert.equal(dto.lineTotalMajor, 5);
  });

  it("settlementMoneyRpcFields dual-writes settlement_amount_minor", () => {
    const s = settlementMoneyRpcFields({
      currency: "ZIG",
      amountMinor: 27500n,
      exchangeRate: 27.5,
      fxRateId: "fx-1",
    });
    assert.equal(s.settlement_amount, 275);
    assert.equal(s.settlement_amount_minor, 27500);
    assert.equal(s.settlement_currency, "ZIG");
    assert.equal(s.fx_rate_id, "fx-1");
  });
});

describe("PaymentAllocationInput H4 cutover", () => {
  it("toAllocatePaymentArgs prefers amountMinor and dual-writes amount_minor", () => {
    const args = toAllocatePaymentArgs("pe-1", [
      { salesInvoiceId: "inv-1", amountMinor: 1999n, currency: "USD" },
      { salesInvoiceId: "inv-2", amount: 5.5, currency: "USD" },
    ]);
    assert.equal(args.p_payment_entry_id, "pe-1");
    assert.equal(args.p_allocations[0].amount, 19.99);
    assert.equal(args.p_allocations[0].amount_minor, 1999);
    assert.equal(args.p_allocations[1].amount, 5.5);
    assert.equal(args.p_allocations[1].amount_minor, 550);
  });
});