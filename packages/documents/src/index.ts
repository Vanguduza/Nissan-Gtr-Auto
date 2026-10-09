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

/** Coarse AuthZ roles mirrored from `public.staff_role`. */
export type StaffRoleLabel =
  | "admin"
  | "finance"
  | "warehouse"
  | "sales"
  | "dispatcher"
  | "hr"
  | "driver";

export type BrandedIdCardInput = {
  storeName: string;
  fullName: string;
  /** Organogram / job title (e.g. "Delivery Driver"). */
  roleTitle: string;
  /**
   * Coarse `staff_roles` enum shown on the card (e.g. "driver").
   * Distinct from roleTitle — AuthZ surface, not job title alone.
   */
  staffRole?: StaffRoleLabel | string | null;
  employeeCode: string;
  /** Opaque verify URL — name/photo/role only; never fiscal authority payload */
  verifyUrl?: string | null;
  photoStoragePath?: string | null;
};

/** Human label for ID card / UI (Title Case). */
export function formatStaffRoleLabel(
  role: StaffRoleLabel | string | null | undefined,
): string {
  const raw = String(role ?? "")
    .trim()
    .toLowerCase();
  if (!raw) return "";
  const known: Record<string, string> = {
    admin: "Admin",
    finance: "Finance",
    warehouse: "Warehouse",
    sales: "Sales",
    dispatcher: "Dispatcher",
    hr: "HR",
    driver: "Driver",
  };
  return known[raw] ?? raw.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

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
  const staffRole = input.staffRole
    ? String(input.staffRole).trim().toLowerCase()
    : null;
  return {
    ...input,
    storeName: input.storeName || "Nissan GTR Auto",
    staffRole: staffRole || null,
  };
}

/**
 * CR80 HTML preview matching Edge pdf-lib layout (brand tokens).
 * Tax-agnostic — no fiscal QR. Open in a browser for smoke / docs previews.
 */
export function renderIdCardHtml(input: BrandedIdCardInput): string {
  const payload = buildIdCardPayload(input);
  const staffLabel = formatStaffRoleLabel(payload.staffRole);
  const roleLine = [payload.roleTitle, staffLabel ? `Staff: ${staffLabel}` : ""]
    .filter(Boolean)
    .join(" · ");
  const verify = payload.verifyUrl
    ? `<div class="verify"><span>VERIFY</span><small>staff token</small></div>`
    : "";
  const { colors } = DOCUMENT_BRAND;
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>ID card · ${escapeHtml(payload.employeeCode || "staff")}</title>
<style>
  :root {
    --primary: ${colors.primary};
    --steel: ${colors.steel};
    --steel-lift: ${colors.steelLift};
    --chalk: ${colors.chalk};
    --mist: ${colors.mist};
    --silver: ${colors.silver};
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    min-height: 100vh;
    display: grid;
    place-items: center;
    background: linear-gradient(160deg, #1a1f2a 0%, #0d1016 100%);
    font-family: "Segoe UI", Helvetica, Arial, sans-serif;
    color: var(--steel);
  }
  .frame {
    padding: 2rem;
    text-align: center;
  }
  .meta {
    color: var(--silver);
    font-size: 0.75rem;
    margin-bottom: 1rem;
    letter-spacing: 0.04em;
  }
  .card {
    width: 85.6mm;
    height: 54mm;
    margin: 0 auto;
    background: var(--chalk);
    border-radius: 2mm;
    overflow: hidden;
    box-shadow: 0 12px 40px rgba(0,0,0,0.45);
    display: grid;
    grid-template-rows: 22px 1fr 22px;
    text-align: left;
  }
  .header {
    background: var(--steel);
    color: #fff;
    font-size: 8pt;
    font-weight: 700;
    padding: 5px 8px 0;
    border-top: 3px solid var(--primary);
  }
  .body {
    display: grid;
    grid-template-columns: 48px 1fr auto;
    gap: 8px;
    padding: 8px;
    align-items: start;
  }
  .photo {
    width: 48px;
    height: 58px;
    background: var(--mist);
    border: 0.75px solid var(--silver);
    display: grid;
    place-items: center;
    font-size: 6pt;
    color: #595e66;
  }
  .name {
    font-size: 11pt;
    font-weight: 700;
    color: var(--steel);
    line-height: 1.2;
  }
  .role {
    margin-top: 4px;
    font-size: 8pt;
    color: #595e66;
  }
  .staff-badge {
    display: inline-block;
    margin-top: 6px;
    padding: 2px 6px;
    font-size: 7pt;
    font-weight: 700;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: #fff;
    background: var(--primary);
  }
  .verify {
    width: 36px;
    height: 36px;
    border: 1px solid var(--steel);
    background: #fff;
    display: grid;
    place-content: center;
    text-align: center;
    font-size: 5pt;
    font-weight: 700;
    color: var(--steel);
  }
  .verify small {
    display: block;
    font-weight: 400;
    color: #595e66;
    font-size: 4pt;
  }
  .footer {
    background: var(--steel-lift);
    color: #fff;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 8px;
    font-size: 9pt;
    font-weight: 700;
  }
  .footer span:last-child {
    font-size: 5pt;
    font-weight: 400;
    color: var(--silver);
  }
</style>
</head>
<body>
  <div class="frame">
    <p class="meta">Simulated HR onboarding · CR80 ID card (no fiscal QR)</p>
    <article class="card" aria-label="Staff ID card">
      <div class="header">${escapeHtml(payload.storeName)}</div>
      <div class="body">
        <div class="photo">PHOTO</div>
        <div>
          <div class="name">${escapeHtml(payload.fullName)}</div>
          <div class="role">${escapeHtml(payload.roleTitle || "Staff")}</div>
          ${
            staffLabel
              ? `<div class="staff-badge">${escapeHtml(staffLabel)}</div>`
              : ""
          }
        </div>
        ${verify}
      </div>
      <div class="footer">
        <span>${escapeHtml(payload.employeeCode)}</span>
        <span>85.6×54 mm · no fiscal QR</span>
      </div>
    </article>
    <p class="meta" style="margin-top:1rem">${escapeHtml(roleLine)}</p>
  </div>
</body>
</html>`;
}

function escapeHtml(value: string): string {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
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
