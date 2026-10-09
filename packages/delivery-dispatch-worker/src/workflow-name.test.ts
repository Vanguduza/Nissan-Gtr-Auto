import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { DELIVERY_DISPATCH_WORKFLOW as PKG_NAME } from "../../delivery/src/types.ts";
import type { AssignRpcClient } from "../../delivery/src/assign-bridge.ts";
import { createDispatchActivities } from "./activities.ts";
import { DELIVERY_DISPATCH_WORKFLOW } from "./workflow-types.ts";

describe("H1 DeliveryDispatchWorkflow worker contracts", () => {
  it("workflow name matches @gtr/delivery DELIVERY_DISPATCH_WORKFLOW", () => {
    assert.equal(DELIVERY_DISPATCH_WORKFLOW, "DeliveryDispatchWorkflow");
    assert.equal(DELIVERY_DISPATCH_WORKFLOW, PKG_NAME);
  });

  it("createDispatchActivities wires SQL bridge activities", async () => {
    const calls: string[] = [];
    const client: AssignRpcClient = {
      async rpc(fn) {
        calls.push(fn);
        if (fn === "suggest_delivery_assignees") {
          return {
            data: [
              {
                user_id: "d1",
                distance_m: 100,
                open_jobs: 0,
                capacity: 3,
                status: "available",
              },
            ],
            error: null,
          };
        }
        return { data: null, error: null };
      },
    };
    const acts = createDispatchActivities(client, { autoAcceptOffers: true });
    const couriers = await acts.listEligibleCouriers("job-1");
    assert.equal(couriers[0]?.driverId, "d1");
    assert.equal(await acts.awaitDecision("job-1", "d1", 30), "accept");
    assert.ok(calls.includes("suggest_delivery_assignees"));
  });
});
