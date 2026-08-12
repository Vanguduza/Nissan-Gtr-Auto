import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { applyOfferDecision, selectNextCourierOffer } from "./dispatch.ts";
import {
  runDeliveryDispatchCycle,
  type DispatchActivities,
} from "./temporal.ts";
import type { DeliveryOfferDecision } from "./types.ts";

const twoCouriers = [
  { driverId: "a", rankScore: 1, available: true },
  { driverId: "b", rankScore: 2, available: true },
];

describe("delivery auto-assign FIFO", () => {
  it("offers lowest rank available courier", () => {
    const r = selectNextCourierOffer({
      candidates: [
        { driverId: "b", rankScore: 20, available: true },
        { driverId: "a", rankScore: 5, available: true },
      ],
    });
    assert.equal(r.kind, "offered");
    if (r.kind === "offered") assert.equal(r.driverId, "a");
  });

  it("fifo queues when none available", () => {
    const r = selectNextCourierOffer({
      candidates: [{ driverId: "a", rankScore: 1, available: false }],
    });
    assert.equal(r.kind, "fifo_queued");
  });

  it("accepts offered courier", () => {
    const r = applyOfferDecision({
      decision: "accept",
      currentDriverId: "a",
      candidates: twoCouriers,
    });
    assert.equal(r.kind, "accepted");
    if (r.kind === "accepted") {
      assert.equal(r.driverId, "a");
      assert.equal(r.nextState, "accepted");
    }
  });

  it("requeues after reject", () => {
    const r = applyOfferDecision({
      decision: "reject",
      currentDriverId: "a",
      candidates: twoCouriers,
    });
    assert.equal(r.kind, "requeued");
    if (r.kind === "requeued") {
      assert.deepEqual(r.triedDriverIds, ["a"]);
      assert.equal(r.nextState, "requeued");
    }
  });

  it("requeues after timeout", () => {
    const r = applyOfferDecision({
      decision: "timeout",
      currentDriverId: "a",
      candidates: twoCouriers,
    });
    assert.equal(r.kind, "requeued");
    if (r.kind === "requeued") {
      assert.deepEqual(r.triedDriverIds, ["a"]);
      assert.equal(r.nextState, "requeued");
    }
  });

  it("fifo queues when timeout exhausts candidates", () => {
    const r = applyOfferDecision({
      decision: "timeout",
      currentDriverId: "a",
      candidates: [{ driverId: "a", rankScore: 1, available: true }],
    });
    assert.equal(r.kind, "fifo_queued");
  });
});

describe("runDeliveryDispatchCycle offer SM", () => {
  function mockActivities(opts: {
    candidates?: typeof twoCouriers;
    decisions: DeliveryOfferDecision[];
  }): {
    activities: DispatchActivities;
    assigned: string[];
    enqueued: string[];
    offered: string[];
  } {
    const decisions = [...opts.decisions];
    const assigned: string[] = [];
    const enqueued: string[] = [];
    const offered: string[] = [];
    const activities: DispatchActivities = {
      async listEligibleCouriers() {
        return opts.candidates ?? twoCouriers;
      },
      async sendOffer(_jobId, driverId) {
        offered.push(driverId);
      },
      async awaitDecision() {
        const d = decisions.shift();
        assert.ok(d !== undefined, "unexpected awaitDecision call");
        return d;
      },
      async assignJob(_jobId, driverId) {
        assigned.push(driverId);
      },
      async enqueueFifo(jobId) {
        enqueued.push(jobId);
      },
    };
    return { activities, assigned, enqueued, offered };
  }

  it("accept path assigns first courier", async () => {
    const { activities, assigned, offered } = mockActivities({
      decisions: ["accept"],
    });
    const result = await runDeliveryDispatchCycle(
      { deliveryJobId: "job-1", offerTimeoutSeconds: 30 },
      activities,
    );
    assert.equal(result.finalState, "accepted");
    assert.equal(result.assigneeDriverId, "a");
    assert.deepEqual(offered, ["a"]);
    assert.deepEqual(assigned, ["a"]);
  });

  it("timeout → requeue → next courier accept", async () => {
    const { activities, assigned, offered, enqueued } = mockActivities({
      decisions: ["timeout", "accept"],
    });
    const result = await runDeliveryDispatchCycle(
      { deliveryJobId: "job-1", offerTimeoutSeconds: 30 },
      activities,
    );
    assert.equal(result.finalState, "accepted");
    assert.equal(result.assigneeDriverId, "b");
    assert.deepEqual(offered, ["a", "b"]);
    assert.deepEqual(assigned, ["b"]);
    assert.deepEqual(enqueued, []);
  });

  it("timeout exhausts pool → fifo_queued", async () => {
    const { activities, assigned, enqueued, offered } = mockActivities({
      candidates: [{ driverId: "a", rankScore: 1, available: true }],
      decisions: ["timeout"],
    });
    const result = await runDeliveryDispatchCycle(
      { deliveryJobId: "job-1", offerTimeoutSeconds: 30 },
      activities,
    );
    assert.equal(result.finalState, "fifo_queued");
    assert.equal(result.assigneeDriverId, null);
    assert.deepEqual(offered, ["a"]);
    assert.deepEqual(assigned, []);
    assert.deepEqual(enqueued, ["job-1"]);
  });

  it("reject → requeue → next courier accept", async () => {
    const { activities, assigned, offered } = mockActivities({
      decisions: ["reject", "accept"],
    });
    const result = await runDeliveryDispatchCycle(
      { deliveryJobId: "job-1", offerTimeoutSeconds: 30 },
      activities,
    );
    assert.equal(result.finalState, "accepted");
    assert.equal(result.assigneeDriverId, "b");
    assert.deepEqual(offered, ["a", "b"]);
    assert.deepEqual(assigned, ["b"]);
  });
});
