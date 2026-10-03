/**
 * GTR counter receipt, format v1 — the one layout both POS clients print (web `ReceiptView`,
 * tablet `receiptRows` in :feature:pos-domain). Rows are left/right pairs; the 80 mm thermal text
 * is those rows laid out in 42 columns (ESC/POS Font A). Conformance fixture:
 * `packages/shared/fixtures/pos-receipt/v1.json` — both clients' tests must reproduce it exactly.
 *
 * Amounts are integer minor units with an explicit currency (D-006). No tax rows (no fiscal tax).
 */

export const RECEIPT_FORMAT_VERSION = 1;
export const THERMAL_COLUMNS = 42;

export type ReceiptCurrency = "USD" | "ZIG";
export type ReceiptTender = "cash" | "bank" | "ecocash" | "store_credit" | "paynow" | "contipay" | "account";

export type ReceiptInput = {
  invoiceId: string;
  documentNumber: string | null;
  /** Shop-local wall time, `YYYY-MM-DDTHH:mm[...]`. */
  issuedAtLocal: string;
  operatorName: string | null;
  customerName: string | null;
  vehicleLabel: string | null;
  currency: ReceiptCurrency;
  lines: Array<{ name: string; oemPartNumber: string; qty: number; unitPriceMinor: number; lineTotalMinor: number }>;
  subtotalMinor: number;
  discountMinor: number;
  totalMinor: number;
  tenders: Array<{ tender: ReceiptTender; amountMinor: number }>;
  cashGivenMinor: number | null;
  changeMinor: number | null;
  /** Queued offline: the invoice number is issued on sync. */
  offline: boolean;
};

export type ReceiptLabels = {
  brand: string;
  strap: string;
  invoice: string; // "{0}" = document number
  servedBy: string;
  customer: string;
  vehicle: string;
  subtotal: string;
  discount: string;
  total: string;
  cashGiven: string;
  change: string;
  offline: string;
  thanks: string;
  tenders: Record<ReceiptTender, string>;
};

export const RECEIPT_LABELS_EN: ReceiptLabels = {
  brand: "NISSAN GTR AUTO",
  strap: "Genuine Parts · Real Performance",
  invoice: "Invoice {0}",
  servedBy: "Served by {0}",
  customer: "Customer: {0}",
  vehicle: "Vehicle: {0}",
  subtotal: "Subtotal",
  discount: "Discount",
  total: "TOTAL",
  cashGiven: "Cash handed over",
  change: "Change due",
  offline: "OFFLINE SALE · invoice no. issued on sync",
  thanks: "Thank you for your business.",
  tenders: { cash: "Cash", bank: "Card / bank", ecocash: "EcoCash", store_credit: "Store credit", paynow: "Paynow", contipay: "ContiPay", account: "On account" },
};

export type ReceiptRow = { left: string; right: string; strong: boolean };

const row = (left: string, right = "", strong = false): ReceiptRow => ({ left, right, strong });
const fill = (template: string, value: string) => template.replace("{0}", value);

/** `US$ 1,250.00` / `ZiG 3,400.50` — same as both POS screens. */
export function formatReceiptMoney(minor: number, currency: ReceiptCurrency): string {
  const symbol = currency === "USD" ? "US$" : "ZiG";
  const negative = minor < 0;
  const abs = Math.abs(Math.round(minor));
  const whole = Math.floor(abs / 100)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  const cents = (abs % 100).toString().padStart(2, "0");
  return `${symbol} ${negative ? "-" : ""}${whole}.${cents}`;
}

export function formatReceiptQty(qty: number): string {
  return Number.isInteger(qty) ? String(qty) : String(qty);
}

export function receiptRows(r: ReceiptInput, labels: ReceiptLabels = RECEIPT_LABELS_EN): ReceiptRow[] {
  const money = (minor: number) => formatReceiptMoney(minor, r.currency);
  const rows: ReceiptRow[] = [row(labels.brand, "", true), row(labels.strap), row("")];
  rows.push(row(fill(labels.invoice, r.documentNumber ?? r.invoiceId.slice(0, 8)), "", true));
  rows.push(row(r.issuedAtLocal.replace("T", " ").slice(0, 16)));
  if (r.operatorName) rows.push(row(fill(labels.servedBy, r.operatorName)));
  if (r.customerName) rows.push(row(fill(labels.customer, r.customerName)));
  if (r.vehicleLabel) rows.push(row(fill(labels.vehicle, r.vehicleLabel)));
  rows.push(row(""));
  for (const l of r.lines) {
    rows.push(row(l.name));
    rows.push(row(`  ${l.oemPartNumber}`));
    rows.push(row(`  ${formatReceiptQty(l.qty)} × ${money(l.unitPriceMinor)}`, money(l.lineTotalMinor)));
  }
  rows.push(row(""));
  rows.push(row(labels.subtotal, money(r.subtotalMinor)));
  rows.push(row(labels.discount, money(r.discountMinor)));
  rows.push(row(labels.total, money(r.totalMinor), true));
  for (const t of r.tenders) rows.push(row(labels.tenders[t.tender], money(t.amountMinor)));
  if (r.cashGivenMinor != null) rows.push(row(labels.cashGiven, money(r.cashGivenMinor)));
  if (r.changeMinor != null) rows.push(row(labels.change, money(r.changeMinor), true));
  rows.push(row(""));
  if (r.offline) rows.push(row(labels.offline, "", true));
  rows.push(row(labels.thanks));
  return rows;
}

/**
 * Lays a row out in [width] columns. Text-only rows word-wrap (a long part name is never lost);
 * rows with an amount keep the amount flush right and cut the label to fit, so amounts always
 * line up in the right-hand column.
 */
export function thermalLines(left: string, right: string, width = THERMAL_COLUMNS): string[] {
  const chars = (t: string) => [...t];
  if (!right) {
    if (chars(left).length <= width) return [left];
    const indent = left.match(/^ */)?.[0] ?? "";
    const out: string[] = [];
    let line = "";
    for (const word of left.trim().split(/\s+/)) {
      const next = line ? `${line} ${word}` : `${indent}${word}`;
      if (chars(next).length <= width) {
        line = next;
      } else {
        if (line) out.push(line);
        // A single word longer than the paper is hard-broken.
        let rest = `${indent}${word}`;
        while (chars(rest).length > width) {
          out.push(chars(rest).slice(0, width).join(""));
          rest = indent + chars(rest).slice(width).join("");
        }
        line = rest;
      }
    }
    if (line) out.push(line);
    return out;
  }
  const room = Math.max(width - chars(right).length - 1, 1);
  return [chars(left).slice(0, room).join("").padEnd(room) + " " + right];
}

export function thermalText(rows: ReceiptRow[], width = THERMAL_COLUMNS): string[] {
  return rows.flatMap((r) => thermalLines(r.left, r.right, width));
}
