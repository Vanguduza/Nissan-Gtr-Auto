/**
 * SQL / Edge fire-and-assign stub for FIFO dispatch (DIAL D-45).
 *
 * This bridge wires `runDeliveryDispatchCycle` to Postgres assign RPCs so Edge
 * (`delivery-dispatch-cycle`) and cron can run the same offer SM without a
 * Temporal worker host. It is **not** the full `DeliveryDispatchWorkflow`
 * worker — that binary is deferred (§H / DIAL E2+).
 *
 * Default: offers are **not** auto-accepted (`autoAcceptOffers` defaults false)
 * so reject/timeout → requeue can be evidenced via injectable `awaitDecision`.
 * Pass `autoAcceptOffers: true` only for cron / immediate SQL assign.
 *
 * No Fleetbase. AI never writes money / payable amounts.
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

export type SqlDispatchActivityOpts = {
  /**
   * Opt-in: first offered courier is auto-accepted (cron / immediate assign).
   * Default `false` — `awaitDecision` returns `"timeout"` unless overridden,
   * so offer SM reject/timeout → requeue can be unit-tested without a Temporal host.
   */
  autoAcceptOffers?: boolean;
  offerTimeoutSeconds?: number;
  /**
   * Test double / Edge hook: override offer wait. When omitted, decision is
   * `"accept"` iff `autoAcceptOffers === true`, else `"timeout"`.
   */
  awaitDecision?: DispatchActivities["awaitDecision"];
};

/**
 * Map suggest_delivery_assignees rows → CourierCandidate (rank by distance then load).
 */
export function candidatesFromSuggestRows(
  rows: SuggestAssigneeRow[],
): CourierCandidate[] {
  return rows
    .map((r) => {
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
    })
    .sort((a, b) => a.rankScore - b.rankScore);
}

/**
 * Build DispatchActivities backed by assign / suggest RPCs.
 *
 * SQL bridge = fire-and-assign stub (Edge-portable). Full Temporal
 * `DeliveryDispatchWorkflow` worker with durable timers = §H — not this module.
 */
export function createSqlDispatchActivities(
  client: AssignRpcClient,
  opts?: SqlDispatchActivityOpts,
): DispatchActivities {
  const autoAccept = opts?.autoAcceptOffers === true;
  const awaitDecisionOverride = opts?.awaitDecision;

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
      // Offer rows / push live in Temporal worker (§H); SQL path is fire-and-assign.
    },

    async awaitDecision(jobId, driverId, timeoutSeconds) {
      if (awaitDecisionOverride) {
        return awaitDecisionOverride(jobId, driverId, timeoutSeconds);
      }
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
 *
 * Same caveats as {@link createSqlDispatchActivities}: stub bridge, not §H worker.
 * Default does not auto-accept; pass `autoAcceptOffers: true` for cron assign.
 */
export async function runSqlDeliveryDispatchCycle(
  client: AssignRpcClient,
  input: DispatchWorkflowInput,
  opts?: SqlDispatchActivityOpts,
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
