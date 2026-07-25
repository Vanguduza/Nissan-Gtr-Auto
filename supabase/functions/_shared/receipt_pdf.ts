/**
 * Tax-agnostic customer receipt PDF (pdf-lib).
 * No ZIMRA / FDMS / fiscal QR or tax-authority payloads.
 */
import { PDFDocument, StandardFonts, rgb } from "npm:pdf-lib@1.17.1";

export type ReceiptLineInput = {
  description: string;
  qty: number;
  unitPrice: number;
  lineTotal: number;
  isCoreCharge?: boolean;
};

export type ReceiptTenderInput = {
  tender: string;
  amount: number;
  currency: string;
};

export type ReceiptPdfInput = {
  storeName: string;
  storeCode?: string | null;
  documentLabel: string;
  docKind: "Sale" | "Credit";
  currency: string;
  exchangeRate?: number | null;
  postedAt?: string | null;
  subtotal: number;
  total: number;
  lines: ReceiptLineInput[];
  tenders?: ReceiptTenderInput[];
  customerContact?: string | null;
};

function money(n: number, currency: string): string {
  return `${currency} ${n.toFixed(2)}`;
}

/** Build a minimal multi-page receipt PDF; returns bytes + sha256 hex. */
export async function buildReceiptPdf(
  input: ReceiptPdfInput,
): Promise<{ bytes: Uint8Array; sha256Hex: string; byteSize: number }> {
  const doc = await PDFDocument.create();
  const font = await doc.embedFont(StandardFonts.Helvetica);
  const fontBold = await doc.embedFont(StandardFonts.HelveticaBold);
  const margin = 48;
  const pageWidth = 595.28; // A4
  const pageHeight = 841.89;
  let page = doc.addPage([pageWidth, pageHeight]);
  let y = pageHeight - margin;

  const draw = (
    text: string,
    opts: { bold?: boolean; size?: number; x?: number } = {},
  ) => {
    const size = opts.size ?? 10;
    const f = opts.bold ? fontBold : font;
    const x = opts.x ?? margin;
    if (y < margin + 24) {
      page = doc.addPage([pageWidth, pageHeight]);
      y = pageHeight - margin;
    }
    page.drawText(text.slice(0, 110), {
      x,
      y,
      size,
      font: f,
      color: rgb(0.1, 0.1, 0.1),
    });
    y -= size + 4;
  };

  draw(input.storeName || "Nissan GTR Auto", { bold: true, size: 16 });
  if (input.storeCode) draw(`Branch: ${input.storeCode}`, { size: 9 });
  draw("nissangtrauto.co.zw", { size: 9 });
  y -= 6;
  draw(`${input.docKind} receipt — ${input.documentLabel}`, {
    bold: true,
    size: 12,
  });
  if (input.postedAt) draw(`Date: ${input.postedAt}`, { size: 9 });
  if (input.customerContact) {
    draw(`Customer: ${input.customerContact}`, { size: 9 });
  }
  if (input.exchangeRate && input.exchangeRate !== 1) {
    draw(`Exchange rate applied: ${input.exchangeRate}`, { size: 9 });
  }
  y -= 8;
  draw("Qty   Description                         Unit      Total", {
    bold: true,
    size: 9,
  });
  page.drawLine({
    start: { x: margin, y: y + 2 },
    end: { x: pageWidth - margin, y: y + 2 },
    thickness: 0.5,
    color: rgb(0.6, 0.6, 0.6),
  });
  y -= 4;

  for (const line of input.lines) {
    const desc = `${line.isCoreCharge ? "[Core] " : ""}${line.description}`;
    const qty = String(line.qty);
    const unit = money(line.unitPrice, input.currency);
    const tot = money(line.lineTotal, input.currency);
    const row = `${qty.padEnd(5)} ${desc.slice(0, 36).padEnd(36)} ${unit.padStart(10)} ${tot.padStart(10)}`;
    draw(row, { size: 9 });
  }

  y -= 8;
  draw(`Subtotal: ${money(input.subtotal, input.currency)}`, { bold: true });
  draw(`Total:    ${money(input.total, input.currency)}`, {
    bold: true,
    size: 12,
  });

  if (input.tenders?.length) {
    y -= 6;
    draw("Payment", { bold: true, size: 10 });
    for (const t of input.tenders) {
      draw(
        `${t.tender}: ${money(t.amount, t.currency || input.currency)}`,
        { size: 9 },
      );
    }
  }

  y -= 16;
  draw("This is a tax-agnostic commercial receipt.", { size: 8 });
  draw("Thank you for your business.", { size: 9 });

  const bytes = await doc.save();
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  const sha256Hex = Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
  return { bytes, sha256Hex, byteSize: bytes.byteLength };
}
