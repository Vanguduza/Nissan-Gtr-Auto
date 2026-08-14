/**
 * Temporal worker host for `DeliveryDispatchWorkflow` (§H H1).
 *
 * Edge `delivery-dispatch-cycle` remains the fire-and-assign bridge.
 * This process hosts the named Temporal workflow + SQL activities.
 * No Fleetbase. Requires TEMPORAL_ADDRESS + Supabase env for live run.
 */
import { NativeConnection, Worker } from "@temporalio/worker";
import { fileURLToPath } from "node:url";
import path from "node:path";
import {
  createDispatchActivities,
  createSupabaseRpcClientFromEnv,
} from "./activities.ts";
import { DELIVERY_DISPATCH_WORKFLOW } from "./workflow-types.ts";

const TASK_QUEUE = process.env.TEMPORAL_TASK_QUEUE?.trim() || "gtr-delivery-dispatch";

async function main(): Promise<void> {
  const address = process.env.TEMPORAL_ADDRESS?.trim();
  if (!address) {
    throw new Error(
      "TEMPORAL_ADDRESS required — refuse silent localhost (set env or use package .env.example)",
    );
  }
  const namespace = process.env.TEMPORAL_NAMESPACE?.trim() || "default";
  const autoAccept = process.env.DISPATCH_AUTO_ACCEPT_OFFERS === "1";

  const connection = await NativeConnection.connect({ address });
  const client = createSupabaseRpcClientFromEnv();
  const activities = createDispatchActivities(client, {
    autoAcceptOffers: autoAccept,
  });

  const workflowsPath = path.join(
    path.dirname(fileURLToPath(import.meta.url)),
    "workflows",
  );

  const worker = await Worker.create({
    connection,
    namespace,
    taskQueue: TASK_QUEUE,
    workflowsPath,
    activities,
  });

  console.log(
    JSON.stringify({
      msg: "delivery-dispatch-worker listening",
      workflow: DELIVERY_DISPATCH_WORKFLOW,
      taskQueue: TASK_QUEUE,
      address,
      namespace,
      autoAcceptOffers: autoAccept,
    }),
  );

  await worker.run();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
