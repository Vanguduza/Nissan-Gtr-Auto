/**
 * Workflow-safe contracts (no Node / Supabase imports).
 * Keep in sync with `@gtr/delivery` `DELIVERY_DISPATCH_WORKFLOW` + dispatch types.
 */
export const DELIVERY_DISPATCH_WORKFLOW = "DeliveryDispatchWorkflow" as const;

export type DeliveryOfferDecision = "accept" | "reject" | "timeout";

export type DeliveryOfferState =
  | "pending"
  | "offered"
  | "accepted"
  | "rejected"
  | "timed_out"
  | "requeued"
  | "fifo_queued"
  | "in_run"
  | "pod_submitted"
  | "completed"
  | "failed";

export type DeliveryEtaSource = "haversine" | "osrm" | "vroom" | "manual";

export type DispatchWorkflowInput = {
  deliveryJobId: string;
  preferredDriverId?: string | null;
  offerTimeoutSeconds: number;
};

export type DispatchWorkflowResult = {
  deliveryJobId: string;
  finalState: DeliveryOfferState;
  assigneeDriverId: string | null;
  etaSource: DeliveryEtaSource | null;
};
