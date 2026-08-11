import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  completedTrackerCount,
  resolveProcurementProgress,
} from "./types.js";

describe("procurement progress", () => {
  it("maps approved without funds to approved", () => {
    assert.equal(
      resolveProcurementProgress({ status: "approved" }),
      "approved",
    );
  });

  it("maps funds released", () => {
    assert.equal(
      resolveProcurementProgress({
        status: "approved",
        fundsReleasedAt: "2026-08-12T00:00:00Z",
      }),
      "funds_released",
    );
  });

  it("maps partial receive", () => {
    assert.equal(
      resolveProcurementProgress({
        status: "approved",
        fundsReleasedAt: "2026-08-12T00:00:00Z",
        qtyOrdered: 10,
        qtyReceived: 4,
      }),
      "partially_received",
    );
  });

  it("counts tracker steps", () => {
    assert.equal(completedTrackerCount("submitted"), 2);
  });
});
