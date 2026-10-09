import { RECEIPT_LABELS_EN, receiptRows, type ReceiptInput, type ReceiptRow } from "@gtr/shared";
import type { ReceiptDocument } from "./types";

const minor = (major: number) => Math.round(major * 100);
const pad = (n: number) => String(n).padStart(2, "0");

/** Shop-local wall time for the receipt header (`YYYY-MM-DDTHH:mm`). */
function localStamp(iso: string | null): string {
  const d = iso ? new Date(iso) : new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** The web receipt in the shared counter-receipt format v1 (same rows the tablet prints). */
export function webReceiptInput(r: ReceiptDocument, cash?: { givenMinor: number; changeMinor: number } | null): ReceiptInput {
  return {
    invoiceId: r.invoiceId,
    documentNumber: r.documentNumber,
    issuedAtLocal: localStamp(r.postedAt),
    operatorName: r.operator || null,
    customerName: r.customerName,
    vehicleLabel: r.vehicleLabel,
    currency: r.currency,
    lines: r.lines.map((l) => ({
      name: l.name,
      oemPartNumber: l.oemPartNumber,
      qty: l.qty,
      unitPriceMinor: minor(l.unitPrice),
      lineTotalMinor: minor(l.lineTotal),
    })),
    subtotalMinor: minor(r.subtotal),
    discountMinor: Math.max(minor(r.subtotal) - minor(r.total), 0),
    totalMinor: minor(r.total),
    tenders: r.tenders.map((t) => ({ tender: t.tender, amountMinor: minor(t.amount) })),
    cashGivenMinor: cash?.givenMinor ?? null,
    changeMinor: cash?.changeMinor ?? null,
    offline: false,
  };
}

export function webReceiptRows(r: ReceiptDocument, cash?: { givenMinor: number; changeMinor: number } | null): ReceiptRow[] {
  return receiptRows(webReceiptInput(r, cash), RECEIPT_LABELS_EN);
}
