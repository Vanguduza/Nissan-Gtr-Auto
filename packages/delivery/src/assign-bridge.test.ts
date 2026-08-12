import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  candidatesFromSuggestRows,
  createSqlDispatchActivities,
  runSqlDeliveryDispatchCycle,
  type AssignRpcClient,
} from "./assign-bridge.ts";

describe("assign-bridge", () => {
  it("ranks suggest rows by distance then load", () => {
    const c = candidatesFromSuggestRows([
      {
        user_id: "b",
        distance_m: 500,
        open_jobs: 0,
        capacity: 3,
        status: "available",
      },
      {
        user_id: "a",
        distance_m: 100,
        open_jobs: 0,
        capacity: 3,
        status: "available",
      },
    ]);
    assert.equal(c[0]!.driverId, "a");
  });

  it("runs FIFO cycle and assigns via RPC", async () => {
    const calls: string[] = [];
    const client: AssignRpcClient = {
      async rpc(fn, args) {
        calls.push(fn);
        if (fn === "suggest_delivery_assignees") {
          return {
            data: [
              {
                user_id: "driver-1",
                distance_m: 50,
                open_jobs: 0,
                capacity: 2,
                status: "available",
              },
            ],
            error: null,
          };
        }
        if (fn === "assign_delivery_job") {
          assert.equal(args?.p_assignee_user_id, "driver-1");
          return { data: args?.p_delivery_job_id, error: null };
        }
        return { data: null, error: null };
      },
    };

    const result = await runSqlDeliveryDispatchCycle(client, {
      deliveryJobId: "job-1",
      offerTimeoutSeconds: 30,
    });

    assert.equal(result.finalState, "accepted");
    assert.equal(result.assigneeDriverId, "driver-1");
    assert.ok(calls.includes("assign_delivery_job"));
  });

  it("exposes createSqlDispatchActivities", () => {
    const a = createSqlDispatchActivities({
      async rpc() {
        return { data: [], error: null };
      },
    });
    assert.equal(typeof a.assignJob, "function");
  });
});
