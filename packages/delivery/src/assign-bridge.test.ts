import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  candidatesFromSuggestRows,
  createSqlDispatchActivities,
  runSqlDeliveryDispatchCycle,
  type AssignRpcClient,
} from "./assign-bridge.ts";
import type { DeliveryOfferDecision } from "./types.ts";

function suggestClient(rows: unknown[]): AssignRpcClient {
  const calls: { fn: string; args?: Record<string, unknown> }[] = [];
  return {
    async rpc(fn, args) {
      calls.push({ fn, args });
      if (fn === "suggest_delivery_assignees") {
        return { data: rows, error: null };
      }
      if (fn === "assign_delivery_job") {
        return { data: args?.p_delivery_job_id, error: null };
      }
      if (fn === "_try_auto_assign_delivery_job") {
        return { data: null, error: null };
      }
      return { data: null, error: null };
    },
  };
}

const oneDriver = [
  {
    user_id: "driver-1",
    distance_m: 50,
    open_jobs: 0,
    capacity: 2,
    status: "available",
  },
];

const twoDrivers = [
  {
    user_id: "driver-1",
    distance_m: 50,
    open_jobs: 0,
    capacity: 2,
    status: "available",
  },
  {
    user_id: "driver-2",
    distance_m: 80,
    open_jobs: 0,
    capacity: 2,
    status: "available",
  },
];

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

  it("opt-in autoAcceptOffers assigns via RPC", async () => {
    const calls: string[] = [];
    const client: AssignRpcClient = {
      async rpc(fn, args) {
        calls.push(fn);
        if (fn === "suggest_delivery_assignees") {
          return { data: oneDriver, error: null };
        }
        if (fn === "assign_delivery_job") {
          assert.equal(args?.p_assignee_user_id, "driver-1");
          return { data: args?.p_delivery_job_id, error: null };
        }
        return { data: null, error: null };
      },
    };

    const result = await runSqlDeliveryDispatchCycle(
      client,
      { deliveryJobId: "job-1", offerTimeoutSeconds: 30 },
      { autoAcceptOffers: true },
    );

    assert.equal(result.finalState, "accepted");
    assert.equal(result.assigneeDriverId, "driver-1");
    assert.ok(calls.includes("assign_delivery_job"));
  });

  it("default does not auto-accept (timeout → fifo when sole courier)", async () => {
    const client = suggestClient(oneDriver);
    const result = await runSqlDeliveryDispatchCycle(client, {
      deliveryJobId: "job-1",
      offerTimeoutSeconds: 30,
    });
    assert.equal(result.finalState, "fifo_queued");
    assert.equal(result.assigneeDriverId, null);
  });

  it("injectable awaitDecision evidences timeout → requeue → accept", async () => {
    const decisions: DeliveryOfferDecision[] = ["timeout", "accept"];
    const assigned: string[] = [];
    const client: AssignRpcClient = {
      async rpc(fn, args) {
        if (fn === "suggest_delivery_assignees") {
          return { data: twoDrivers, error: null };
        }
        if (fn === "assign_delivery_job") {
          assigned.push(String(args?.p_assignee_user_id));
          return { data: args?.p_delivery_job_id, error: null };
        }
        return { data: null, error: null };
      },
    };

    const result = await runSqlDeliveryDispatchCycle(
      client,
      { deliveryJobId: "job-1", offerTimeoutSeconds: 30 },
      {
        autoAcceptOffers: false,
        async awaitDecision(_jobId, _driverId, _timeoutSeconds) {
          const d = decisions.shift();
          assert.ok(d !== undefined);
          return d;
        },
      },
    );

    assert.equal(result.finalState, "accepted");
    assert.equal(result.assigneeDriverId, "driver-2");
    assert.deepEqual(assigned, ["driver-2"]);
  });

  it("exposes createSqlDispatchActivities with default timeout decision", async () => {
    const a = createSqlDispatchActivities({
      async rpc() {
        return { data: [], error: null };
      },
    });
    assert.equal(typeof a.assignJob, "function");
    assert.equal(await a.awaitDecision("j", "d", 30), "timeout");
  });

  it("createSqlDispatchActivities autoAcceptOffers opt-in returns accept", async () => {
    const a = createSqlDispatchActivities(
      {
        async rpc() {
          return { data: [], error: null };
        },
      },
      { autoAcceptOffers: true },
    );
    assert.equal(await a.awaitDecision("j", "d", 30), "accept");
  });
});
