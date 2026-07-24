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

/** Public receipt download URL on company domain (signed/token route). */
export function buildReceiptDownloadUrl(downloadToken: string): string {
  const token = downloadToken.trim();
  if (!token) {
    throw new Error("downloadToken required");
  }
  return `${RECEIPT_HOST}/receipts/${token}`;
}
