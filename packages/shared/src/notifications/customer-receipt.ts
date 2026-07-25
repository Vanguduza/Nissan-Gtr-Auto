const RECEIPT_HOST = "https://nissangtrauto.co.zw";

/** Build SMS summary; Phase 13 appends PDF download URL at the bottom. */
export function buildCustomerReceiptSmsSummary(params: {
  docLabel: string;
  isCreditNote?: boolean;
  total: number;
  currency: string;
  pdfDownloadUrl?: string | null;
}): string {
  const kind = params.isCreditNote ? "Credit" : "Sale";
  const base = `GTR Auto ${kind} ${params.docLabel} Total ${params.total} ${params.currency}. Thank you.`;
  if (params.pdfDownloadUrl) {
    return `${base}\n${params.pdfDownloadUrl}`;
  }
  return base;
}

/** Email subject for customer receipt delivery (tax-agnostic; no fiscal wording). */
export function buildCustomerReceiptEmailSubject(params: {
  docLabel: string;
  isCreditNote?: boolean;
}): string {
  const kind = params.isCreditNote ? "credit note" : "receipt";
  return `Your Nissan GTR Auto ${kind} ${params.docLabel}`;
}

/** Brief email body; PDF attached by worker when available. */
export function buildCustomerReceiptEmailBody(params: {
  summary: string;
  pdfDownloadUrl?: string | null;
}): string {
  const link = params.pdfDownloadUrl?.trim();
  if (link) {
    return `${params.summary}\n\nDownload: ${link}`;
  }
  return params.summary;
}

/** Public receipt download URL on company domain (signed/token route). */
export function buildReceiptDownloadUrl(downloadToken: string): string {
  const token = downloadToken.trim();
  if (!token) {
    throw new Error("downloadToken required");
  }
  return `${RECEIPT_HOST}/receipts/${token}`;
}
