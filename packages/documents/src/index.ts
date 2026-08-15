/**
 * Branded documents for Nissan GTR Auto ERP (Batch 1 §2.4 / §3.2).
 * Tax-agnostic — no ZIMRA / FDMS / fiscal QR.
 *
 * Edge render: `render-branded-doc` + `_shared/branded_docs_pdf.ts` (pdf-lib).
 * This package holds shared input shapes, mm page sizes, and payload builders.
 */
import { renderSVG as renderQrSvg } from "uqr";

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
  /**
   * Opaque staff verify URL (preferred QR payload) — name/photo/role only;
   * never fiscal authority. Fallback when omitted: `gtr://employee/{code}`.
   */
  verifyUrl?: string | null;
  photoStoragePath?: string | null;
  /** Optional logo data URL for HTML previews (e.g. `apps/web/public/brand/logo.png`). */
  logoDataUrl?: string | null;
};

/**
 * Employee ID-card QR payload (Bridge-scanned; not fiscal).
 * Prefer opaque `…/staff/verify/{token}`; else `gtr://employee/{emp#}`.
 */
export function buildEmployeeQrPayload(opts: {
  employeeCode: string;
  verifyUrl?: string | null;
}): string {
  const url = String(opts.verifyUrl ?? "").trim();
  if (url) return url;
  const code = String(opts.employeeCode ?? "").trim();
  if (!code) return "";
  return `gtr://employee/${encodeURIComponent(code)}`;
}

/** SVG string for employee QR (uqr). Steel modules on chalk. */
export function renderEmployeeQrSvg(payload: string, pixelSize = 4): string {
  if (!payload) return "";
  return renderQrSvg(payload, {
    ecc: "M",
    border: 2,
    pixelSize,
    blackColor: "#12151C",
    whiteColor: "#F4F5F7",
  });
}

/** Data-URL wrapper for `<img src>` embeds in HTML previews. */
export function renderEmployeeQrDataUrl(payload: string, pixelSize = 4): string {
  const svg = renderEmployeeQrSvg(payload, pixelSize);
  if (!svg) return "";
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

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
 * CR80 HTML preview — front + back faces matching Edge pdf-lib layout.
 * Brand: steel / chalk / primary red. Tax-agnostic — no fiscal QR.
 * Front: logo opposite photo; name / role / employee # centered.
 * Back: employee QR (`staff/verify` or `gtr://employee/{code}`).
 */
export function renderIdCardHtml(input: BrandedIdCardInput): string {
  const payload = buildIdCardPayload(input);
  const staffLabel = formatStaffRoleLabel(payload.staffRole);
  const positionLine = [payload.roleTitle, staffLabel].filter(Boolean).join(" · ");
  const qrPayload = buildEmployeeQrPayload({
    employeeCode: payload.employeeCode,
    verifyUrl: payload.verifyUrl,
  });
  const qrDataUrl = qrPayload ? renderEmployeeQrDataUrl(qrPayload, 3) : "";
  const logo = payload.logoDataUrl
    ? `<img class="logo" src="${escapeHtml(payload.logoDataUrl)}" alt="${escapeHtml(payload.storeName)}"/>`
    : `<div class="logo-mark" aria-hidden="true"><span>GTR</span></div>`;
  const { colors } = DOCUMENT_BRAND;
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>ID card · ${escapeHtml(payload.employeeCode || "staff")}</title>
<link rel="preconnect" href="https://fonts.googleapis.com"/>
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin/>
<link href="https://fonts.googleapis.com/css2?family=Source+Sans+3:wght@400;600;700&family=Titillium+Web:wght@600;700&display=swap" rel="stylesheet"/>
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
    background:
      radial-gradient(ellipse at 20% 0%, rgba(200,16,46,0.18), transparent 45%),
      linear-gradient(165deg, #1a1f2a 0%, #0d1016 55%, #12151C 100%);
    font-family: "Source Sans 3", "Segoe UI", Helvetica, Arial, sans-serif;
    color: var(--steel);
  }
  .frame {
    padding: 2rem 1.25rem 2.5rem;
    text-align: center;
    max-width: 42rem;
  }
  .meta {
    color: var(--silver);
    font-size: 0.75rem;
    margin: 0 0 1rem;
    letter-spacing: 0.06em;
    text-transform: uppercase;
  }
  .faces {
    display: flex;
    flex-wrap: wrap;
    gap: 1.5rem;
    justify-content: center;
    align-items: flex-start;
  }
  .face-wrap {
    display: flex;
    flex-direction: column;
    gap: 0.45rem;
  }
  .face-label {
    color: var(--silver);
    font-size: 0.65rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }
  .card {
    width: 85.6mm;
    height: 54mm;
    margin: 0 auto;
    border-radius: 2.2mm;
    overflow: hidden;
    box-shadow: 0 14px 42px rgba(0,0,0,0.5);
    position: relative;
  }
  /* —— Front —— */
  .card-front {
    background:
      linear-gradient(135deg, var(--chalk) 0%, var(--mist) 55%, #e2e6ed 100%);
    display: grid;
    grid-template-rows: 7mm 1fr 6.5mm;
  }
  .card-front::before {
    content: "";
    position: absolute;
    inset: 0 auto 0 0;
    width: 2.2mm;
    background: var(--primary);
    z-index: 2;
  }
  .front-top {
    background: linear-gradient(90deg, var(--steel) 0%, var(--steel-lift) 100%);
    border-bottom: 1.6px solid var(--primary);
    display: flex;
    align-items: center;
    padding: 0 3.5mm 0 4.5mm;
    color: #fff;
    font-family: "Titillium Web", "Source Sans 3", sans-serif;
    font-size: 7.5pt;
    font-weight: 700;
    letter-spacing: 0.04em;
  }
  .front-body {
    display: grid;
    grid-template-columns: 16mm 1fr 16mm;
    align-items: center;
    gap: 2mm;
    padding: 2.5mm 3.5mm 2mm 5mm;
    position: relative;
    z-index: 1;
  }
  .photo {
    width: 14.5mm;
    height: 18mm;
    background: #fff;
    border: 0.7px solid var(--silver);
    box-shadow: 0 1px 0 rgba(18,21,28,0.08);
    display: grid;
    place-items: center;
    font-size: 5.5pt;
    font-weight: 600;
    letter-spacing: 0.08em;
    color: #6b7280;
    justify-self: start;
  }
  .identity {
    text-align: center;
    padding: 0 1mm;
    min-width: 0;
  }
  .name {
    font-family: "Titillium Web", "Source Sans 3", sans-serif;
    font-size: 11pt;
    font-weight: 700;
    color: var(--steel);
    line-height: 1.15;
    letter-spacing: 0.01em;
  }
  .position {
    margin-top: 1.8mm;
    font-size: 7.5pt;
    font-weight: 600;
    color: #3d4450;
    line-height: 1.25;
  }
  .emp-no {
    margin-top: 2.2mm;
    font-family: "Titillium Web", "Source Sans 3", sans-serif;
    font-size: 9pt;
    font-weight: 700;
    color: var(--primary);
    letter-spacing: 0.08em;
  }
  .logo, .logo-mark {
    width: 14mm;
    height: 14mm;
    justify-self: end;
    object-fit: contain;
  }
  .logo-mark {
    display: grid;
    place-items: center;
    background: var(--steel);
    border: 1px solid var(--primary);
    color: #fff;
    font-family: "Titillium Web", sans-serif;
    font-weight: 700;
    font-size: 8pt;
    letter-spacing: 0.06em;
  }
  .front-foot {
    background: var(--steel);
    color: var(--silver);
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 3.5mm 0 5mm;
    font-size: 5.5pt;
    letter-spacing: 0.04em;
  }
  .front-foot strong {
    color: #fff;
    font-weight: 700;
  }
  /* —— Back —— */
  .card-back {
    background: linear-gradient(160deg, var(--steel) 0%, var(--steel-lift) 70%, #161a22 100%);
    display: grid;
    grid-template-rows: 7mm 1fr 6.5mm;
    color: #fff;
  }
  .card-back::after {
    content: "";
    position: absolute;
    inset: 0 0 auto 0;
    height: 1.6px;
    background: var(--primary);
  }
  .back-top {
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: "Titillium Web", sans-serif;
    font-size: 7pt;
    font-weight: 700;
    letter-spacing: 0.16em;
    text-transform: uppercase;
    color: var(--silver);
  }
  .back-body {
    display: grid;
    place-items: center;
    padding: 1mm 3mm;
  }
  .qr-frame {
    background: var(--chalk);
    padding: 1.6mm;
    border-radius: 1mm;
    box-shadow: 0 0 0 1px rgba(192,197,206,0.25);
  }
  .qr-frame img {
    display: block;
    width: 26mm;
    height: 26mm;
  }
  .qr-missing {
    width: 26mm;
    height: 26mm;
    display: grid;
    place-items: center;
    font-size: 6pt;
    color: var(--steel);
  }
  .back-foot {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 2mm;
    font-size: 5.5pt;
    color: var(--silver);
    letter-spacing: 0.05em;
  }
  .back-foot code {
    color: #fff;
    font-family: "Source Sans 3", monospace;
    font-weight: 700;
    letter-spacing: 0.06em;
  }
</style>
</head>
<body>
  <div class="frame">
    <p class="meta">Simulated HR onboarding · CR80 ID card · front + back</p>
    <div class="faces">
      <div class="face-wrap">
        <span class="face-label">Front</span>
        <article class="card card-front" aria-label="Staff ID card front">
          <div class="front-top">${escapeHtml(payload.storeName)}</div>
          <div class="front-body">
            <div class="photo">PHOTO</div>
            <div class="identity">
              <div class="name">${escapeHtml(payload.fullName)}</div>
              <div class="position">${escapeHtml(positionLine || "Staff")}</div>
              <div class="emp-no">${escapeHtml(payload.employeeCode)}</div>
            </div>
            ${logo}
          </div>
          <div class="front-foot">
            <span><strong>STAFF ID</strong> · no fiscal QR</span>
            <span>85.6×54 mm</span>
          </div>
        </article>
      </div>
      <div class="face-wrap">
        <span class="face-label">Back</span>
        <article class="card card-back" aria-label="Staff ID card back">
          <div class="back-top">Employee QR</div>
          <div class="back-body">
            <div class="qr-frame">
              ${
                qrDataUrl
                  ? `<img src="${qrDataUrl}" alt="Employee QR"/>`
                  : `<div class="qr-missing">NO QR</div>`
              }
            </div>
          </div>
          <div class="back-foot">
            <span>Bridge scan</span>
            <code>${escapeHtml(payload.employeeCode)}</code>
          </div>
        </article>
      </div>
    </div>
    <p class="meta" style="margin-top:1.25rem;text-transform:none;letter-spacing:0.02em">
      QR payload: ${escapeHtml(qrPayload || "(none)")}
    </p>
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
