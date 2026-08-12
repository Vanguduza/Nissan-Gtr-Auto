/**
 * Bridge: Temporal FIFO dispatch cycle → SQL assign RPCs (DIAL D-45).
 * Edge worker / Temporal activities call these; Postgres remains assign SoR until
 * full Temporal worker cutover. No Fleetbase.
 */
import {
  runDeliveryDispatchCycle,
  type DispatchActivities,
} from "./temporal.ts";
import type { CourierCandidate } from "./dispatch.ts";
import type {
  DeliveryOfferDecision,
  DispatchWorkflowInput,
  DispatchWorkflowResult,
} from "./types.ts";

/** Minimal PostgREST-shaped client used by the bridge (Supabase JS compatible). */
export type AssignRpcClient = {
  rpc(
    fn: string,
    args?: Record<string, unknown>,
  ): Promise<{ data: unknown; error: { message: string } | null }>;
};

export type SuggestAssigneeRow = {
  user_id: string;
  distance_m: number | null;
  open_jobs: number | null;
  capacity: number | null;
  status: string;
};

/**
 * Map suggest_delivery_assignees rows → CourierCandidate (rank by distance then load).
 */
export function candidatesFromSuggestRows(
  rows: SuggestAssigneeRow[],
): CourierCandidate[] {
  return rows.map((r) => {
    const dist = r.distance_m ?? 1_000_000;
    const load = r.open_jobs ?? 0;
    return {
      driverId: r.user_id,
      rankScore: dist + load * 1000,
      available:
        r.status === "available" ||
        r.status === "on_duty" ||
        r.status === "busy",
    };
  });
}

/**
 * Build DispatchActivities backed by assign / suggest RPCs.
 * Offer wait is short-circuit accept for immediate SQL assign path (worker stub).
 */
export function createSqlDispatchActivities(
  client: AssignRpcClient,
  opts?: {
    /** When true, first offered courier is auto-accepted (cron / immediate assign). */
    autoAcceptOffers?: boolean;
    offerTimeoutSeconds?: number;
  },
): DispatchActivities {
  const autoAccept = opts?.autoAcceptOffers !== false;

  return {
    async listEligibleCouriers(jobId) {
      const { data, error } = await client.rpc("suggest_delivery_assignees", {
        p_delivery_job_id: jobId,
        p_limit: 10,
      });
      if (error) throw new Error(error.message);
      const rows = (Array.isArray(data) ? data : []) as SuggestAssigneeRow[];
      return candidatesFromSuggestRows(rows);
    },

    async sendOffer(_jobId, _driverId, _timeoutSeconds) {
      // Offer rows live in Temporal worker; SQL path is fire-and-assign.
    },

    async awaitDecision(_jobId, _driverId, _timeoutSeconds) {
      return (autoAccept ? "accept" : "timeout") satisfies DeliveryOfferDecision;
    },

    async assignJob(jobId, driverId) {
      const { error } = await client.rpc("assign_delivery_job", {
        p_delivery_job_id: jobId,
        p_assignee_user_id: driverId,
        p_override: false,
      });
      if (error) throw new Error(error.message);
    },

    async enqueueFifo(jobId) {
      // Soft FIFO: leave unassigned; SQL `_try_auto_assign_delivery_job` retries later.
      const { error } = await client.rpc("_try_auto_assign_delivery_job", {
        p_delivery_job_id: jobId,
      });
      // Ignore missing grant / soft-fail — unassigned job stays in queue.
      if (error) {
        console.warn("enqueueFifo/_try_auto_assign soft-fail", error.message);
      }
    },
  };
}

/**
 * Run one FIFO offer→assign cycle using SQL RPCs (Temporal activity portable).
 */
export async function runSqlDeliveryDispatchCycle(
  client: AssignRpcClient,
  input: DispatchWorkflowInput,
  opts?: { autoAcceptOffers?: boolean },
): Promise<DispatchWorkflowResult> {
  const activities = createSqlDispatchActivities(client, opts);
  return runDeliveryDispatchCycle(input, activities);
}

/**
 * Fast path: call SQL auto-assign only (existing production SoR).
 */
export async function trySqlAutoAssign(
  client: AssignRpcClient,
  deliveryJobId: string,
): Promise<{ assigneeUserId: string | null }> {
  const { data, error } = await client.rpc("_try_auto_assign_delivery_job", {
    p_delivery_job_id: deliveryJobId,
  });
  if (error) throw new Error(error.message);
  return { assigneeUserId: (data as string | null) ?? null };
}
