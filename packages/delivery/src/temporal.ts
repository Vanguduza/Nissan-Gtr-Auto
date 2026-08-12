/**
 * Temporal DeliveryDispatchWorkflow activity contracts (DIAL D-45).
 * Worker deployment is separate; SQL `_try_auto_assign_delivery_job` remains live SoR until worker cutover.
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
 * Pure orchestration of one dispatch cycle — portable to Temporal worker.
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
