/**
 * Temporal `DeliveryDispatchWorkflow` activity contracts + portable cycle (DIAL D-45 / B5).
 *
 * FIFO helpers (`selectNextCourierOffer`, `applyOfferDecision`) + this cycle are package SoR.
 * SQL/Edge bridge (`createSqlDispatchActivities`) is a fire-and-assign stub that reuses this
 * orchestration. Full Temporal worker binary with durable timers = §H — not required for Epic B DoD.
 */
import {
  applyOfferDecision,
  selectNextCourierOffer,
  type CourierCandidate,
} from "./dispatch.ts";
import {
  DELIVERY_DISPATCH_WORKFLOW,
  type DeliveryOfferDecision,
  type DeliveryOfferState,
  type DispatchWorkflowInput,
  type DispatchWorkflowResult,
} from "./types.ts";

export { DELIVERY_DISPATCH_WORKFLOW };

export type DispatchActivities = {
  listEligibleCouriers(jobId: string): Promise<CourierCandidate[]>;
  sendOffer(jobId: string, driverId: string, timeoutSeconds: number): Promise<void>;
  awaitDecision(
    jobId: string,
    driverId: string,
    timeoutSeconds: number,
  ): Promise<DeliveryOfferDecision>;
  assignJob(jobId: string, driverId: string): Promise<void>;
  enqueueFifo(jobId: string): Promise<void>;
};

/**
 * Pure orchestration of one offer→decision→assign|requeue|fifo cycle.
 * Portable to a Temporal worker; unit-testable via injectable `DispatchActivities`
 * (no fake Temporal host required for B5 SM evidence).
 */
export async function runDeliveryDispatchCycle(
  input: DispatchWorkflowInput,
  activities: DispatchActivities,
): Promise<DispatchWorkflowResult> {
  const tried: string[] = [];
  let candidates = await activities.listEligibleCouriers(input.deliveryJobId);

  if (input.preferredDriverId) {
    candidates = [
      ...candidates.filter((c) => c.driverId === input.preferredDriverId),
      ...candidates.filter((c) => c.driverId !== input.preferredDriverId),
    ];
  }

  for (;;) {
    const pick = selectNextCourierOffer({
      candidates,
      alreadyTriedDriverIds: tried,
    });

    if (pick.kind === "fifo_queued") {
      await activities.enqueueFifo(input.deliveryJobId);
      return {
        deliveryJobId: input.deliveryJobId,
        finalState: "fifo_queued",
        assigneeDriverId: null,
        etaSource: null,
      };
    }

    await activities.sendOffer(
      input.deliveryJobId,
      pick.driverId,
      input.offerTimeoutSeconds,
    );
    const decision = await activities.awaitDecision(
      input.deliveryJobId,
      pick.driverId,
      input.offerTimeoutSeconds,
    );

    const outcome = applyOfferDecision({
      decision,
      currentDriverId: pick.driverId,
      candidates,
      alreadyTriedDriverIds: tried,
    });

    if (outcome.kind === "accepted") {
      await activities.assignJob(input.deliveryJobId, outcome.driverId);
      return {
        deliveryJobId: input.deliveryJobId,
        finalState: "accepted" satisfies DeliveryOfferState,
        assigneeDriverId: outcome.driverId,
        etaSource: "osrm",
      };
    }

    if (outcome.kind === "fifo_queued") {
      await activities.enqueueFifo(input.deliveryJobId);
      return {
        deliveryJobId: input.deliveryJobId,
        finalState: "fifo_queued",
        assigneeDriverId: null,
        etaSource: null,
      };
    }

    tried.push(...outcome.triedDriverIds);
  }
}
