import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { applyOfferDecision, selectNextCourierOffer } from "./dispatch.js";

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

  it("requeues after reject", () => {
    const r = applyOfferDecision({
      decision: "reject",
      currentDriverId: "a",
      candidates: [
        { driverId: "a", rankScore: 1, available: true },
        { driverId: "b", rankScore: 2, available: true },
      ],
    });
    assert.equal(r.kind, "requeued");
  });
});
