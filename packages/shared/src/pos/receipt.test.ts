import { strict as assert } from "node:assert";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { RECEIPT_FORMAT_VERSION, RECEIPT_LABELS_EN, formatReceiptMoney, receiptRows, thermalLines, thermalText, type ReceiptInput, type ReceiptRow } from "./receipt.ts";

type Fixture = {
  version: number;
  thermalColumns: number;
  labels: typeof RECEIPT_LABELS_EN;
  cases: Array<{ name: string; receipt: ReceiptInput; rows: ReceiptRow[]; thermal42: string[] }>;
};
const fixture: Fixture = JSON.parse(readFileSync(new URL("../../fixtures/pos-receipt/v1.json", import.meta.url), "utf8"));

test("fixture is format v1 with the canonical labels", () => {
  assert.equal(fixture.version, RECEIPT_FORMAT_VERSION);
  assert.deepEqual(fixture.labels, RECEIPT_LABELS_EN);
});

for (const c of fixture.cases) {
  test(`conformance: ${c.name}`, () => {
    const rows = receiptRows(c.receipt, fixture.labels);
    assert.deepEqual(rows, c.rows);
    const text = thermalText(rows, fixture.thermalColumns);
    assert.deepEqual(text, c.thermal42);
    for (const line of text) assert.ok([...line].length <= fixture.thermalColumns, `over width: ${line}`);
  });
}

test("money keeps currency, thousands and two decimals", () => {
  assert.equal(formatReceiptMoney(125115, "USD"), "US$ 1,251.15");
  assert.equal(formatReceiptMoney(5, "ZIG"), "ZiG 0.05");
  assert.equal(formatReceiptMoney(123456789, "USD"), "US$ 1,234,567.89");
});

test("amount rows never push the amount out of its column", () => {
  const [line] = thermalLines("A very long label that cannot possibly fit beside it", "US$ 1,000.00", 42);
  assert.equal([...line].length, 42);
  assert.ok(line.endsWith(" US$ 1,000.00"));
});
