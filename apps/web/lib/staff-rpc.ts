import type { SupabaseClient } from "@gtr/supabase-client";

/** Boundary for additive staff RPCs while generated database types catch up. */
export function staffRpc(client: SupabaseClient, name: string, args?: Record<string, unknown>) {
  return (client as unknown as {
    rpc: (name: string, params?: Record<string, unknown>) => PromiseLike<{
      data: unknown;
      error: { message: string } | null;
    }>;
  }).rpc(name, args);
}
