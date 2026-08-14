/**
 * Activity implementations for `DeliveryDispatchWorkflow`.
 * Wraps `@gtr/delivery` SQL assign-bridge (same RPCs as Edge `delivery-dispatch-cycle`).
 * Imports use relative `../delivery/src/*.ts` so Node `--experimental-strip-types`
 * resolves (package exports are extensionless / bundler-oriented). Workspace dep
 * `@gtr/delivery` remains declared for pnpm lockfile hygiene.
 */
import {
  createSqlDispatchActivities,
  type AssignRpcClient,
  type DispatchActivities,
  type SqlDispatchActivityOpts,
} from "../../delivery/src/assign-bridge.ts";
import { createClient } from "@supabase/supabase-js";

export type WorkerActivityOpts = SqlDispatchActivityOpts & {
  /** When true, first offer auto-accepts (parity with Edge cron). Default false. */
  autoAcceptOffers?: boolean;
};

export function createDispatchActivities(
  client: AssignRpcClient,
  opts?: WorkerActivityOpts,
): DispatchActivities {
  return createSqlDispatchActivities(client, {
    autoAcceptOffers: opts?.autoAcceptOffers === true,
    offerTimeoutSeconds: opts?.offerTimeoutSeconds,
    awaitDecision: opts?.awaitDecision,
  });
}

/** Build Supabase RPC client from env (service role for worker host only). */
export function createSupabaseRpcClientFromEnv(
  env: NodeJS.ProcessEnv = process.env,
): AssignRpcClient {
  const url = env.SUPABASE_URL?.trim();
  const key = env.SUPABASE_SERVICE_ROLE_KEY?.trim();
  if (!url || !key) {
    throw new Error(
      "SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY required for dispatch worker activities (anon not accepted)",
    );
  }
  const sb = createClient(url, key, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  return {
    async rpc(fn: string, args?: Record<string, unknown>) {
      const { data, error } = await sb.rpc(fn, args ?? {});
      return {
        data,
        error: error ? { message: error.message } : null,
      };
    },
  };
}
