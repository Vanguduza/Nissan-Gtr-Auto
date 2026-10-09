import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { canOverrideAssignDeliveryJob } from "./delivery.ts";

describe("canOverrideAssignDeliveryJob — exception override ≠ happy-path assign", () => {
  it("allows unassigned non-terminal jobs", () => {
    assert.equal(canOverrideAssignDeliveryJob(null, "pending"), true);
    assert.equal(canOverrideAssignDeliveryJob("", "pending"), true);
    assert.equal(canOverrideAssignDeliveryJob("  ", "dispatched"), true);
  });

  it("blocks happily assigned jobs", () => {
    assert.equal(
      canOverrideAssignDeliveryJob("00000000-0000-4000-8000-0000000000d1", "pending"),
      false,
    );
    assert.equal(
      canOverrideAssignDeliveryJob("00000000-0000-4000-8000-0000000000d1", "dispatched"),
      false,
    );
  });

  it("blocks terminal jobs even when unassigned", () => {
    assert.equal(canOverrideAssignDeliveryJob(null, "completed"), false);
    assert.equal(canOverrideAssignDeliveryJob(null, "failed"), false);
    assert.equal(canOverrideAssignDeliveryJob(null, "COMPLETED"), false);
  });
});
