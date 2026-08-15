/**
 * Branded document PDFs (pdf-lib) — statements, payslips, CR80 ID, business card.
 * Tax-agnostic: no ZIMRA / FDMS / fiscal QR. Staff verify QR is opaque URL only.
 *
 * Visual polish mirrors packages/ui brand tokens:
 * steel `#12151C`, primary `#C8102E`, chalk `#F4F5F7`, silver `#C0C5CE`.
 *
 * Physical sizes match `@gtr/documents` DOCUMENT_PAGE_MM:
 * ID CR80 85.6×54 mm · business 90×50 mm · A4 statements/payslips.
 */
import { PDFDocument, StandardFonts, rgb, type PDFPage, type PDFFont } from "npm:pdf-lib@1.17.1";
import { encode as encodeQr } from "npm:uqr@0.1.3";

export type DocCurrency = "USD" | "ZIG" | string;

export type BrandedStatementPdfInput = {
  storeName: string;
  storeCode?: string | null;
  documentLabel: string;
  currency: DocCurrency;
  exchangeRate?: number | null;
  asOf?: string | null;
  partyName?: string | null;
  openingBalance?: number;
  closingBalance?: number;
  lines: Array<{
    description: string;
    qty?: number;
    unitPrice?: number;
    lineTotal: number;
  }>;
};

export type BrandedPayslipPdfInput = {
  storeName: string;
  employeeName: string;
  employeeCode: string;
  periodStart: string;
  periodEnd: string;
  currency: DocCurrency;
  grossPay: number;
  manualDeductions: Array<{ label: string; amount: number }>;
  netPay: number;
};

export type BrandedIdCardPdfInput = {
  storeName: string;
  fullName: string;
  roleTitle: string;
  /** Coarse staff_roles enum label (e.g. driver). */
  staffRole?: string | null;
  employeeCode: string;
  verifyUrl?: string | null;
};

export type BrandedBusinessCardPdfInput = {
  storeName: string;
  fullName: string;
  roleTitle: string;
  employeeCode: string;
  phone?: string | null;
  email?: string | null;
  domain?: string | null;
};

/** mm → PDF points (72 dpi). Keep in sync with packages/documents mmToPt. */
export function mmToPt(mm: number): number {
  return (mm * 72) / 25.4;
}

export const PAGE_PT = {
  idCard: { width: mmToPt(85.6), height: mmToPt(54) },
  businessCard: { width: mmToPt(90), height: mmToPt(50) },
  a4: { width: mmToPt(210), height: mmToPt(297) },
} as const;

/** Brand palette (sRGB 0–1). */
const Brand = {
  primary: rgb(0xc8 / 255, 0x10 / 255, 0x2e / 255),
  steel: rgb(0x12 / 255, 0x15 / 255, 0x1c / 255),
  steelLift: rgb(0x1e / 255, 0x24 / 255, 0x30 / 255),
  chalk: rgb(0xf4 / 255, 0xf5 / 255, 0xf7 / 255),
  mist: rgb(0xe8 / 255, 0xec / 255, 0xf1 / 255),
  silver: rgb(0xc0 / 255, 0xc5 / 255, 0xce / 255),
  ink: rgb(0.1, 0.1, 0.12),
  muted: rgb(0.35, 0.37, 0.4),
  white: rgb(1, 1, 1),
} as const;

function money(n: number, currency: string): string {
  return `${currency} ${Number(n).toFixed(2)}`;
}

function drawSteelHeader(
  page: PDFPage,
  opts: {
    width: number;
    height: number;
    storeName: string;
    subtitle?: string;
    fontBold: PDFFont;
    font: PDFFont;
  },
): number {
  const headerH = 52;
  page.drawRectangle({
    x: 0,
    y: opts.height - headerH,
    width: opts.width,
    height: headerH,
    color: Brand.steel,
  });
  page.drawRectangle({
    x: 0,
    y: opts.height - 4,
    width: opts.width,
    height: 4,
    color: Brand.primary,
  });
  page.drawText((opts.storeName || "Nissan GTR Auto").slice(0, 40), {
    x: 48,
    y: opts.height - 28,
    size: 14,
    font: opts.fontBold,
    color: Brand.white,
  });
  if (opts.subtitle) {
    page.drawText(opts.subtitle.slice(0, 60), {
      x: 48,
      y: opts.height - 42,
      size: 8,
      font: opts.font,
      color: Brand.silver,
    });
  }
  return opts.height - headerH - 20;
}

/** A4 branded statement / register export. */
export async function buildStatementPdf(
  input: BrandedStatementPdfInput,
): Promise<Uint8Array> {
  const doc = await PDFDocument.create();
  const font = await doc.embedFont(StandardFonts.Helvetica);
  const fontBold = await doc.embedFont(StandardFonts.HelveticaBold);
  const margin = 48;
  const pageWidth = PAGE_PT.a4.width;
  const pageHeight = PAGE_PT.a4.height;
  let page = doc.addPage([pageWidth, pageHeight]);
  page.drawRectangle({
    x: 0,
    y: 0,
    width: pageWidth,
    height: pageHeight,
    color: Brand.chalk,
  });
  let y = drawSteelHeader(page, {
    width: pageWidth,
    height: pageHeight,
    storeName: input.storeName || "Nissan GTR Auto",
    subtitle: "nissangtrauto.co.zw · tax-agnostic statement",
    fontBold,
    font,
  });

  const draw = (
    text: string,
    opts: { bold?: boolean; size?: number; color?: ReturnType<typeof rgb> } = {},
  ) => {
    const size = opts.size ?? 10;
    const f = opts.bold ? fontBold : font;
    if (y < margin + 24) {
      page = doc.addPage([pageWidth, pageHeight]);
      page.drawRectangle({
        x: 0,
        y: 0,
        width: pageWidth,
        height: pageHeight,
        color: Brand.chalk,
      });
      y = pageHeight - margin;
    }
    page.drawText(String(text).slice(0, 110), {
      x: margin,
      y,
      size,
      font: f,
      color: opts.color ?? Brand.ink,
    });
    y -= size + 4;
  };

  if (input.storeCode) draw(`Branch: ${input.storeCode}`, { size: 9, color: Brand.muted });
  draw(input.documentLabel, { bold: true, size: 13 });
  if (input.asOf) draw(`As of: ${input.asOf}`, { size: 9, color: Brand.muted });
  if (input.partyName) draw(`Party: ${input.partyName}`, { size: 9 });
  if (input.exchangeRate && input.exchangeRate !== 1) {
    draw(`Exchange rate applied: ${input.exchangeRate}`, { size: 9, color: Brand.muted });
  }
  if (input.openingBalance != null) {
    draw(`Opening: ${money(input.openingBalance, input.currency)}`, { size: 9 });
  }
  y -= 6;
  page.drawRectangle({
    x: margin,
    y: y - 2,
    width: pageWidth - margin * 2,
    height: 16,
    color: Brand.mist,
  });
  draw("Description                                      Amount", {
    bold: true,
    size: 9,
  });
  for (const line of input.lines) {
    const amt = money(line.lineTotal, input.currency);
    draw(
      `${line.description.slice(0, 48).padEnd(48)} ${amt.padStart(14)}`,
      { size: 9 },
    );
  }
  y -= 8;
  if (input.closingBalance != null) {
    draw(`Closing: ${money(input.closingBalance, input.currency)}`, {
      bold: true,
      size: 11,
    });
  }
  y -= 12;
  draw("Tax-agnostic statement — no fiscal authority QR.", {
    size: 8,
    color: Brand.muted,
  });

  return doc.save();
}

/** A4 payslip — gross − manual deductions only (no PAYE/NSSA columns). */
export async function buildPayslipPdf(
  input: BrandedPayslipPdfInput,
): Promise<Uint8Array> {
  const doc = await PDFDocument.create();
  const font = await doc.embedFont(StandardFonts.Helvetica);
  const fontBold = await doc.embedFont(StandardFonts.HelveticaBold);
  const pageWidth = PAGE_PT.a4.width;
  const pageHeight = PAGE_PT.a4.height;
  const page = doc.addPage([pageWidth, pageHeight]);
  page.drawRectangle({
    x: 0,
    y: 0,
    width: pageWidth,
    height: pageHeight,
    color: Brand.chalk,
  });
  let y = drawSteelHeader(page, {
    width: pageWidth,
    height: pageHeight,
    storeName: input.storeName || "Nissan GTR Auto",
    subtitle: "Payslip · gross − manual deductions only",
    fontBold,
    font,
  });
  const margin = 48;
  const contentW = pageWidth - margin * 2;

  const draw = (
    text: string,
    opts: { bold?: boolean; size?: number; color?: ReturnType<typeof rgb>; x?: number } = {},
  ) => {
    const size = opts.size ?? 10;
    page.drawText(String(text).slice(0, 100), {
      x: opts.x ?? margin,
      y,
      size,
      font: opts.bold ? fontBold : font,
      color: opts.color ?? Brand.ink,
    });
    y -= size + 5;
  };

  draw(`${input.employeeName} · ${input.employeeCode}`, { bold: true, size: 12 });
  draw(`Period: ${input.periodStart} → ${input.periodEnd}`, {
    size: 9,
    color: Brand.muted,
  });
  y -= 10;

  page.drawRectangle({
    x: margin,
    y: y - 4,
    width: contentW,
    height: 18,
    color: Brand.mist,
  });
  draw("Earnings / deductions", { bold: true, size: 9 });
  draw(`Gross pay`, { size: 10 });
  page.drawText(money(input.grossPay, input.currency), {
    x: margin + contentW - 100,
    y: y + 15,
    size: 10,
    font: fontBold,
    color: Brand.ink,
  });

  const deductions = input.manualDeductions ?? [];
  if (deductions.length === 0) {
    draw("Manual deductions: none", { size: 9, color: Brand.muted });
  } else {
    draw("Manual deductions", { bold: true, size: 9, color: Brand.muted });
    for (const d of deductions) {
      draw(`  − ${d.label}`, { size: 9, color: Brand.muted });
      page.drawText(money(d.amount, input.currency), {
        x: margin + contentW - 100,
        y: y + 14,
        size: 9,
        font,
        color: Brand.muted,
      });
    }
  }

  y -= 6;
  page.drawRectangle({
    x: margin,
    y: y - 6,
    width: contentW,
    height: 28,
    color: Brand.steelLift,
  });
  page.drawText(`Net pay`, {
    x: margin + 8,
    y: y + 4,
    size: 12,
    font: fontBold,
    color: Brand.white,
  });
  page.drawText(money(input.netPay, input.currency), {
    x: margin + contentW - 110,
    y: y + 4,
    size: 12,
    font: fontBold,
    color: Brand.white,
  });
  y -= 36;
  draw("No statutory tax columns (PAYE/NSSA excluded by policy).", {
    size: 8,
    color: Brand.muted,
  });
  draw("Tax-agnostic document — no fiscal authority QR.", {
    size: 8,
    color: Brand.muted,
  });

  return doc.save();
}

/** CR80 ID card — 85.6 × 54 mm · page 1 front, page 2 back (employee QR). */
export async function buildIdCardPdf(
  input: BrandedIdCardPdfInput,
): Promise<Uint8Array> {
  const w = PAGE_PT.idCard.width;
  const h = PAGE_PT.idCard.height;
  const doc = await PDFDocument.create();
  const font = await doc.embedFont(StandardFonts.Helvetica);
  const fontBold = await doc.embedFont(StandardFonts.HelveticaBold);

  // —— Front ——
  const front = doc.addPage([w, h]);
  front.drawRectangle({
    x: 0,
    y: 0,
    width: w,
    height: h,
    color: Brand.chalk,
  });
  // Mist wash (right) for brand accent without purple/cream defaults.
  front.drawRectangle({
    x: w * 0.45,
    y: 22,
    width: w * 0.55,
    height: h - 44,
    color: Brand.mist,
  });
  // Primary accent rail (left).
  front.drawRectangle({
    x: 0,
    y: 0,
    width: 5,
    height: h,
    color: Brand.primary,
  });
  // Steel header + red underline.
  front.drawRectangle({
    x: 0,
    y: h - 20,
    width: w,
    height: 20,
    color: Brand.steel,
  });
  front.drawRectangle({
    x: 0,
    y: h - 21.5,
    width: w,
    height: 1.5,
    color: Brand.primary,
  });
  front.drawText((input.storeName || "Nissan GTR Auto").slice(0, 28), {
    x: 10,
    y: h - 14,
    size: 8,
    font: fontBold,
    color: Brand.white,
  });

  // Photo LEFT — Bridge upload path; no browser camera here.
  const photoW = 42;
  const photoH = 52;
  const photoX = 12;
  const photoY = 30;
  front.drawRectangle({
    x: photoX,
    y: photoY,
    width: photoW,
    height: photoH,
    color: Brand.white,
  });
  front.drawRectangle({
    x: photoX,
    y: photoY,
    width: photoW,
    height: photoH,
    borderColor: Brand.silver,
    borderWidth: 0.75,
  });
  front.drawText("PHOTO", {
    x: photoX + 8,
    y: photoY + photoH / 2 - 3,
    size: 6,
    font: fontBold,
    color: Brand.muted,
  });

  // Logo mark RIGHT (opposite photo).
  const logoSize = 40;
  const logoX = w - logoSize - 10;
  const logoY = photoY + (photoH - logoSize) / 2;
  front.drawRectangle({
    x: logoX,
    y: logoY,
    width: logoSize,
    height: logoSize,
    color: Brand.steel,
  });
  front.drawRectangle({
    x: logoX,
    y: logoY,
    width: logoSize,
    height: 2,
    color: Brand.primary,
  });
  front.drawText("GTR", {
    x: logoX + 9,
    y: logoY + logoSize / 2 - 4,
    size: 11,
    font: fontBold,
    color: Brand.white,
  });

  // Centered identity between photo and logo: name · position/staff role · employee #.
  const midLeft = photoX + photoW;
  const midRight = logoX;
  const midCenter = (midLeft + midRight) / 2;
  const staffRoleRaw = String(input.staffRole ?? "").trim();
  const staffLabel = staffRoleRaw
    ? staffRoleRaw
      .toLowerCase()
      .replace(/_/g, " ")
      .replace(/\b\w/g, (c) => c.toUpperCase())
    : "";
  const positionLine = [input.roleTitle, staffLabel].filter(Boolean).join(" · ");
  const name = input.fullName.slice(0, 28);
  const nameW = fontBold.widthOfTextAtSize(name, 11);
  front.drawText(name, {
    x: midCenter - nameW / 2,
    y: h - 40,
    size: 11,
    font: fontBold,
    color: Brand.steel,
  });
  const pos = (positionLine || "Staff").slice(0, 36);
  const posW = font.widthOfTextAtSize(pos, 7.5);
  front.drawText(pos, {
    x: midCenter - posW / 2,
    y: h - 54,
    size: 7.5,
    font,
    color: Brand.muted,
  });
  const emp = input.employeeCode.slice(0, 16);
  const empW = fontBold.widthOfTextAtSize(emp, 10);
  front.drawText(emp, {
    x: midCenter - empW / 2,
    y: h - 70,
    size: 10,
    font: fontBold,
    color: Brand.primary,
  });

  front.drawRectangle({
    x: 0,
    y: 0,
    width: w,
    height: 18,
    color: Brand.steel,
  });
  front.drawText("STAFF ID · no fiscal QR", {
    x: 10,
    y: 6,
    size: 5.5,
    font,
    color: Brand.silver,
  });
  front.drawText("85.6×54 mm", {
    x: w - 52,
    y: 6,
    size: 5.5,
    font,
    color: Brand.silver,
  });

  // —— Back (employee QR) ——
  const back = doc.addPage([w, h]);
  back.drawRectangle({
    x: 0,
    y: 0,
    width: w,
    height: h,
    color: Brand.steel,
  });
  back.drawRectangle({
    x: 0,
    y: h - 2,
    width: w,
    height: 2,
    color: Brand.primary,
  });
  back.drawText("EMPLOYEE QR", {
    x: w / 2 - 34,
    y: h - 16,
    size: 8,
    font: fontBold,
    color: Brand.silver,
  });

  const qrPayload =
    String(input.verifyUrl ?? "").trim() ||
    (input.employeeCode
      ? `gtr://employee/${encodeURIComponent(input.employeeCode.trim())}`
      : "");

  if (qrPayload) {
    const qr = encodeQr(qrPayload, { ecc: "M", border: 1 });
    const qrBox = 92;
    const module = qrBox / qr.size;
    const qrX = (w - qrBox) / 2;
    const qrY = (h - qrBox) / 2 - 4;
    back.drawRectangle({
      x: qrX - 4,
      y: qrY - 4,
      width: qrBox + 8,
      height: qrBox + 8,
      color: Brand.chalk,
    });
    for (let r = 0; r < qr.size; r++) {
      for (let c = 0; c < qr.size; c++) {
        if (!qr.data[r][c]) continue;
        back.drawRectangle({
          x: qrX + c * module,
          y: qrY + (qr.size - 1 - r) * module,
          width: module,
          height: module,
          color: Brand.steel,
        });
      }
    }
  } else {
    back.drawText("NO QR PAYLOAD", {
      x: w / 2 - 36,
      y: h / 2,
      size: 8,
      font: fontBold,
      color: Brand.silver,
    });
  }

  back.drawRectangle({
    x: 0,
    y: 0,
    width: w,
    height: 18,
    color: Brand.steelLift,
  });
  back.drawText("Bridge scan", {
    x: 10,
    y: 6,
    size: 5.5,
    font,
    color: Brand.silver,
  });
  back.drawText(input.employeeCode.slice(0, 16), {
    x: w / 2 - 20,
    y: 6,
    size: 7,
    font: fontBold,
    color: Brand.white,
  });

  return doc.save();
}

/** Business card — 90 × 50 mm. */
export async function buildBusinessCardPdf(
  input: BrandedBusinessCardPdfInput,
): Promise<Uint8Array> {
  const w = PAGE_PT.businessCard.width;
  const h = PAGE_PT.businessCard.height;
  const doc = await PDFDocument.create();
  const font = await doc.embedFont(StandardFonts.Helvetica);
  const fontBold = await doc.embedFont(StandardFonts.HelveticaBold);
  const page = doc.addPage([w, h]);

  page.drawRectangle({
    x: 0,
    y: 0,
    width: w,
    height: h,
    color: Brand.white,
  });
  page.drawRectangle({
    x: 0,
    y: 0,
    width: 6,
    height: h,
    color: Brand.primary,
  });
  page.drawRectangle({
    x: 6,
    y: h - 20,
    width: w - 6,
    height: 20,
    color: Brand.steel,
  });
  page.drawText((input.storeName || "Nissan GTR Auto").slice(0, 26), {
    x: 14,
    y: h - 13,
    size: 9,
    font: fontBold,
    color: Brand.white,
  });
  page.drawText(input.fullName.slice(0, 28), {
    x: 14,
    y: h - 36,
    size: 12,
    font: fontBold,
    color: Brand.steel,
  });
  page.drawText(input.roleTitle.slice(0, 32), {
    x: 14,
    y: h - 48,
    size: 8,
    font,
    color: Brand.muted,
  });
  let y = 28;
  if (input.phone) {
    page.drawText(input.phone.slice(0, 24), {
      x: 14,
      y,
      size: 7,
      font,
      color: Brand.ink,
    });
    y -= 10;
  }
  if (input.email) {
    page.drawText(input.email.slice(0, 36), {
      x: 14,
      y,
      size: 7,
      font,
      color: Brand.ink,
    });
  }
  page.drawText(
    (input.domain || "nissangtrauto.co.zw").slice(0, 28),
    {
      x: 14,
      y: 8,
      size: 6,
      font,
      color: Brand.muted,
    },
  );
  page.drawText(input.employeeCode.slice(0, 14), {
    x: w - 72,
    y: 8,
    size: 6,
    font: fontBold,
    color: Brand.primary,
  });
  page.drawText("90×50 mm", {
    x: w - 42,
    y: h - 13,
    size: 5,
    font,
    color: Brand.silver,
  });

  return doc.save();
}
