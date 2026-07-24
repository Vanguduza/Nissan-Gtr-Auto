/** Logistics / pick-pack / DN (must match DB enums). */
export const FULFILLMENT_MODES = ["immediate", "dispatch"] as const;
export type FulfillmentMode = (typeof FULFILLMENT_MODES)[number];

export const PICK_LIST_STATUSES = ["draft", "done", "cancelled"] as const;
export type PickListStatus = (typeof PICK_LIST_STATUSES)[number];

export const DELIVERY_NOTE_STATUSES = ["draft", "submitted", "cancelled"] as const;
export type DeliveryNoteStatus = (typeof DELIVERY_NOTE_STATUSES)[number];

export const DELIVERY_JOB_STATUSES = [
  "pending",
  "dispatched",
  "completed",
  "failed",
] as const;
export type DeliveryJobStatus = (typeof DELIVERY_JOB_STATUSES)[number];

export const PICK_LIST_DOCUMENT_PREFIX = "PL-" as const;
export const DELIVERY_NOTE_DOCUMENT_PREFIX = "DN-" as const;
export const DELIVERY_JOB_DOCUMENT_PREFIX = "DJ-" as const;
