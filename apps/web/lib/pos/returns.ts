import type { ReplacementLine, ReturnCondition, ReturnResolution, WarrantyClaim, WarrantyDecision } from "@/lib/pos/types";

/** `{stock_item_id, uom_id, qty, replacement_serial_id?}`: a missing serial key is left out, never null. */
export function replacementJson(lines: ReplacementLine[]): Record<string, unknown>[] {
  return lines.map((l) => ({
    stock_item_id: l.stockItemId,
    uom_id: l.uomId,
    qty: l.qty,
    ...(l.replacementSerialId ? { replacement_serial_id: l.replacementSerialId } : {}),
  }));
}

/**
 * Arguments for approving a warranty claim, with [prefix] "p_" for the RPC or "" for the badge. Lines
 * are only sent when the decision needs them; a credit note is valued at the sold price on the server.
 */
export function warrantyApproveArgs(
  claim: WarrantyClaim,
  d: Extract<WarrantyDecision, { kind: "approve" }>,
  prefix: "p_" | "",
): Record<string, unknown> {
  const args: Record<string, unknown> = { [`${prefix}claim_id`]: claim.id, [`${prefix}resolution`]: d.resolution };
  if (d.resolution === "credit_note" && claim.stockItemId) args[`${prefix}lines`] = [{ stock_item_id: claim.stockItemId, qty: d.qty }];
  if (d.resolution === "replacement" && d.replacement) args[`${prefix}replacement_lines`] = replacementJson(d.replacement);
  return args;
}

export const RETURN_RESOLUTIONS: { value: ReturnResolution; label: string; hint: string }[] = [
  { value: "cash_refund", label: "Cash refund", hint: "Paid from this till. Only up to what the customer paid." },
  { value: "credit_note", label: "Credit to account", hint: "Lowers what the customer owes. Named customer only." },
  { value: "store_credit", label: "Store credit", hint: "Credit the customer spends on a later sale. Named customer only." },
  { value: "replacement", label: "Swap for the same part", hint: "The returned part goes to quarantine and the same part is handed over." },
  { value: "warranty", label: "Send for warranty", hint: "Opens a warranty claim for one part. A manager decides it later." },
];

export const RETURN_CONDITIONS: { value: ReturnCondition; label: string }[] = [
  { value: "sealed", label: "Sealed" },
  { value: "unopened", label: "Unopened" },
  { value: "opened", label: "Opened" },
  { value: "damaged", label: "Damaged" },
  { value: "defective", label: "Defective" },
];

export const WARRANTY_STATUS_LABEL: Record<string, string> = {
  open: "Waiting for a decision",
  approved: "Approved",
  rejected: "Rejected",
  closed: "Closed",
};

export const WARRANTY_RESOLUTION_LABEL: Record<string, string> = {
  replacement: "Replaced",
  credit_note: "Credited",
  return_only: "Taken back, no credit",
  reject_only: "Rejected",
};
