/**
 * Branded documents for Nissan GTR Auto ERP (Batch 1 §2.4 / §3.2).
 * Tax-agnostic — no ZIMRA / FDMS / fiscal QR.
 *
 * Edge render: `render-branded-doc` + `_shared/branded_docs_pdf.ts` (pdf-lib).
 * This package holds shared input shapes, mm page sizes, and payload builders.
 */

export type DocumentCurrency = "USD" | "ZIG";

/** Millimetres → PDF points (1 pt = 1/72 in). */
export function mmToPt(mm: number): number {
  return (mm * 72) / 25.4;
}

/** Physical card sizes (ISO ID-1 / CR80 and common business card). */
export const DOCUMENT_PAGE_MM = {
  /** CR80 / ISO ID-1 */
  idCard: { widthMm: 85.6, heightMm: 54 } as const,
  businessCard: { widthMm: 90, heightMm: 50 } as const,
  /** A4 for statements + payslips */
  a4: { widthMm: 210, heightMm: 297 } as const,
} as const;

export const DOCUMENT_PAGE_PT = {
  idCard: {
    width: mmToPt(DOCUMENT_PAGE_MM.idCard.widthMm),
    height: mmToPt(DOCUMENT_PAGE_MM.idCard.heightMm),
  },
  businessCard: {
    width: mmToPt(DOCUMENT_PAGE_MM.businessCard.widthMm),
    height: mmToPt(DOCUMENT_PAGE_MM.businessCard.heightMm),
  },
  a4: {
    width: mmToPt(DOCUMENT_PAGE_MM.a4.widthMm),
    height: mmToPt(DOCUMENT_PAGE_MM.a4.heightMm),
  },
} as const;

export type BrandedDocLine = {
  description: string;
  qty?: number;
  unitPrice?: number;
  lineTotal: number;
};

export type BrandedStatementInput = {
  storeName: string;
  storeCode?: string | null;
  /** e.g. "Customer statement" / "Trial balance" — never fiscal labels */
  documentLabel: string;
  currency: DocumentCurrency;
  exchangeRate?: number | null;
  asOf?: string | null;
  partyName?: string | null;
  openingBalance?: number;
  closingBalance?: number;
  lines: BrandedDocLine[];
};

export type BrandedPayslipInput = {
  storeName: string;
  employeeName: string;
  employeeCode: string;
  periodStart: string;
  periodEnd: string;
  currency: DocumentCurrency;
  /** Gross only — no PAYE/NSSA columns */
  grossPay: number;
  manualDeductions: Array<{ label: string; amount: number }>;
  netPay: number;
};

export type BrandedIdCardInput = {
  storeName: string;
  fullName: string;
  roleTitle: string;
  employeeCode: string;
  /** Opaque verify URL — name/photo/role only; never fiscal authority payload */
  verifyUrl?: string | null;
  photoStoragePath?: string | null;
};

export type BrandedBusinessCardInput = {
  storeName: string;
  fullName: string;
  roleTitle: string;
  employeeCode: string;
  phone?: string | null;
  email?: string | null;
  domain?: string | null;
};

export type BrandedDocKind =
  | "statement"
  | "payslip"
  | "id_card"
  | "business_card";

/** Hook: build a statement export payload for Edge PDF / CSV. */
export function buildStatementExportPayload(
  input: BrandedStatementInput,
): BrandedStatementInput {
  return {
    ...input,
    storeName: input.storeName || "Nissan GTR Auto",
    lines: input.lines ?? [],
  };
}

/** Hook: payslip payload — gross − manual deductions only. */
export function buildPayslipExportPayload(
  input: BrandedPayslipInput,
): BrandedPayslipInput {
  const deductions = input.manualDeductions ?? [];
  const deducted = deductions.reduce((s, d) => s + Number(d.amount || 0), 0);
  const net =
    input.netPay != null && Number.isFinite(input.netPay)
      ? input.netPay
      : input.grossPay - deducted;
  return { ...input, manualDeductions: deductions, netPay: net };
}

export function buildIdCardPayload(input: BrandedIdCardInput): BrandedIdCardInput {
  return {
    ...input,
    storeName: input.storeName || "Nissan GTR Auto",
  };
}

export function buildBusinessCardPayload(
  input: BrandedBusinessCardInput,
): BrandedBusinessCardInput {
  return {
    ...input,
    storeName: input.storeName || "Nissan GTR Auto",
    domain: input.domain || "nissangtrauto.co.zw",
  };
}

/** Body for Edge `render-branded-doc`. */
export function toRenderBrandedDocBody(
  kind: BrandedDocKind,
  payload:
    | BrandedStatementInput
    | BrandedPayslipInput
    | BrandedIdCardInput
    | BrandedBusinessCardInput,
): Record<string, unknown> {
  return { kind, ...payload };
}

/**
 * Brand kit — full render lives in Edge pdf-lib (branded_docs_pdf / receipt_pdf).
 * Callers must not invent fiscal QR fields here.
 */
export const DOCUMENT_BRAND = {
  storeName: "Nissan GTR Auto",
  domain: "nissangtrauto.co.zw",
  /** No ZIMRA fiscal QR — display QR only for opaque staff verify tokens. */
  allowFiscalQr: false as const,
  idCardMm: DOCUMENT_PAGE_MM.idCard,
  businessCardMm: DOCUMENT_PAGE_MM.businessCard,
  /** Hex mirrors packages/ui/brand-tokens.json — Edge pdf-lib uses same ratios. */
  colors: {
    primary: "#C8102E",
    steel: "#12151C",
    steelLift: "#1E2430",
    chalk: "#F4F5F7",
    mist: "#E8ECF1",
    silver: "#C0C5CE",
  } as const,
} as const;
