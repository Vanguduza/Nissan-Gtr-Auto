/**
 * Temporal `DeliveryDispatchWorkflow` (§H H1).
 *
 * Workflow sandbox: only `@temporalio/workflow` + local orchestration.
 * Offer SM matches `@gtr/delivery` `runDeliveryDispatchCycle` (pure helpers inlined
 * via activities). Durable wait uses workflow `sleep` raced with activity decision.
 * No Fleetbase. AI never writes money.
 */
import { proxyActivities, sleep } from "@temporalio/workflow";
import type {
  DeliveryOfferDecision,
  DispatchWorkflowInput,
  DispatchWorkflowResult,
} from "../workflow-types.ts";

export { DELIVERY_DISPATCH_WORKFLOW } from "../workflow-types.ts";

const acts = proxyActivities<{
  listEligibleCouriers(
    jobId: string,
  ): Promise<Array<{ driverId: string; rankScore: number; available: boolean }>>;
  sendOffer(
    jobId: string,
    driverId: string,
    timeoutSeconds: number,
  ): Promise<void>;
  awaitDecision(
    jobId: string,
    driverId: string,
    timeoutSeconds: number,
  ): Promise<DeliveryOfferDecision>;
  assignJob(jobId: string, driverId: string): Promise<void>;
  enqueueFifo(jobId: string): Promise<void>;
}>({
  startToCloseTimeout: "5 minutes",
  retry: { maximumAttempts: 3 },
});

function selectNext(args: {
  candidates: Array<{ driverId: string; rankScore: number; available: boolean }>;
  alreadyTriedDriverIds: string[];
}):
  | { kind: "offer"; driverId: string }
  | { kind: "fifo_queued" } {
  const tried = new Set(args.alreadyTriedDriverIds);
  const next = args.candidates.find(
    (c) => c.available && !tried.has(c.driverId),
  );
  if (!next) return { kind: "fifo_queued" };
  return { kind: "offer", driverId: next.driverId };
}

function applyDecision(args: {
  decision: DeliveryOfferDecision;
  currentDriverId: string;
  alreadyTriedDriverIds: string[];
}):
  | { kind: "accepted"; driverId: string }
  | { kind: "requeue"; triedDriverIds: string[] } {
  if (args.decision === "accept") {
    return { kind: "accepted", driverId: args.currentDriverId };
  }
  return {
    kind: "requeue",
    triedDriverIds: [...args.alreadyTriedDriverIds, args.currentDriverId],
  };
}

/**
 * Named workflow — must stay `DeliveryDispatchWorkflow` / DELIVERY_DISPATCH_WORKFLOW.
 */
export async function DeliveryDispatchWorkflow(
  input: DispatchWorkflowInput,
): Promise<DispatchWorkflowResult> {
  const tried: string[] = [];
  let candidates = await acts.listEligibleCouriers(input.deliveryJobId);

  if (input.preferredDriverId) {
    const pref = input.preferredDriverId;
    candidates = [
      ...candidates.filter((c) => c.driverId === pref),
      ...candidates.filter((c) => c.driverId !== pref),
    ];
  }

  for (;;) {
    const pick = selectNext({ candidates, alreadyTriedDriverIds: tried });
    if (pick.kind === "fifo_queued") {
      await acts.enqueueFifo(input.deliveryJobId);
      return {
        deliveryJobId: input.deliveryJobId,
        finalState: "fifo_queued",
        assigneeDriverId: null,
        etaSource: null,
      };
    }

    await acts.sendOffer(
      input.deliveryJobId,
      pick.driverId,
      input.offerTimeoutSeconds,
    );

    const decision = await Promise.race([
      acts.awaitDecision(
        input.deliveryJobId,
        pick.driverId,
        input.offerTimeoutSeconds,
      ),
      sleep(`${input.offerTimeoutSeconds} seconds`).then(
        () => "timeout" as DeliveryOfferDecision,
      ),
    ]);

    const outcome = applyDecision({
      decision,
      currentDriverId: pick.driverId,
      alreadyTriedDriverIds: tried,
    });

    if (outcome.kind === "accepted") {
      await acts.assignJob(input.deliveryJobId, outcome.driverId);
      return {
        deliveryJobId: input.deliveryJobId,
        finalState: "accepted",
        assigneeDriverId: outcome.driverId,
        etaSource: "osrm",
      };
    }

    tried.length = 0;
    tried.push(...outcome.triedDriverIds);
    candidates = await acts.listEligibleCouriers(input.deliveryJobId);
  }
}
