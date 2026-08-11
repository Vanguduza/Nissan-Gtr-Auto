/**
 * Auto-assign / FIFO queue helpers (DIAL D-45 / AWS Last Mile MIT-0 patterns).
 * Pure functions — Temporal worker + SQL `_try_auto_assign_delivery_job` call these rules.
 */

import type { DeliveryOfferDecision, DeliveryOfferState } from "./types";

export type CourierCandidate = {
  driverId: string;
  /** Lower is better (distance, load, score). */
  rankScore: number;
  available: boolean;
};

export type OfferCycleResult =
  | { kind: "offered"; driverId: string; nextState: DeliveryOfferState }
  | { kind: "fifo_queued"; nextState: "fifo_queued" }
  | { kind: "requeued"; nextState: "requeued"; triedDriverIds: string[] };

/**
 * Pick next courier: eligible → rank ascending. Empty → FIFO waiting queue.
 */
export function selectNextCourierOffer(input: {
  candidates: CourierCandidate[];
  alreadyTriedDriverIds?: string[];
}): OfferCycleResult {
  const tried = new Set(input.alreadyTriedDriverIds ?? []);
  const eligible = input.candidates
    .filter((c) => c.available && !tried.has(c.driverId))
    .sort((a, b) => a.rankScore - b.rankScore);

  if (eligible.length === 0) {
    return { kind: "fifo_queued", nextState: "fifo_queued" };
  }

  return {
    kind: "offered",
    driverId: eligible[0]!.driverId,
    nextState: "offered",
  };
}

/**
 * Apply courier decision: accept → in_run; reject/timeout → requeue remaining.
 */
export function applyOfferDecision(input: {
  decision: DeliveryOfferDecision;
  currentDriverId: string;
  candidates: CourierCandidate[];
  alreadyTriedDriverIds?: string[];
}): OfferCycleResult | { kind: "accepted"; nextState: "accepted"; driverId: string } {
  if (input.decision === "accept") {
    return {
      kind: "accepted",
      nextState: "accepted",
      driverId: input.currentDriverId,
    };
  }

  const tried = [...(input.alreadyTriedDriverIds ?? []), input.currentDriverId];
  const next = selectNextCourierOffer({
    candidates: input.candidates,
    alreadyTriedDriverIds: tried,
  });

  if (next.kind === "fifo_queued") {
    return next;
  }

  return {
    kind: "requeued",
    nextState: "requeued",
    triedDriverIds: tried,
  };
}
