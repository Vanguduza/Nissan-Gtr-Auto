/** Warranty claim workflow (must match DB enums). */
export const WARRANTY_CLAIM_STATUSES = [
  "open",
  "approved",
  "rejected",
  "closed",
] as const;

export type WarrantyClaimStatus = (typeof WARRANTY_CLAIM_STATUSES)[number];

export const WARRANTY_CLAIM_RESOLUTIONS = [
  "replacement",
  "credit_note",
  "return_only",
  "reject_only",
] as const;

export type WarrantyClaimResolution = (typeof WARRANTY_CLAIM_RESOLUTIONS)[number];

export const WARRANTY_CLAIM_DOCUMENT_PREFIX = "WC-" as const;
