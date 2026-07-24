/**
 * Shared AuthZ for cron / outbox worker edge functions.
 *
 * Env: WORKER_SHARED_SECRET — set in Edge secrets / local .env; never commit real values.
 * Callers must send header: x-worker-secret: <same value>
 *
 * Refuse 401 when secret unset (except WORKER_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)
 * or when header missing/wrong. Uses constant-time compare.
 */
import { timingSafeEqualStr } from "./payment_edge.ts";

export function assertWorkerSecret(req: Request): Response | null {
  const expected = Deno.env.get("WORKER_SHARED_SECRET")?.trim() ?? "";
  const localUnverified =
    !expected && Deno.env.get("WORKER_ALLOW_UNVERIFIED_LOCAL") === "1";

  if (!expected) {
    if (!localUnverified) {
      return new Response(
        JSON.stringify({
          error:
            "WORKER_SHARED_SECRET unset — refuse (set WORKER_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)",
        }),
        { status: 401, headers: { "Content-Type": "application/json" } },
      );
    }
    console.warn(
      "worker: unverified local stub (WORKER_ALLOW_UNVERIFIED_LOCAL=1)",
    );
    return null;
  }

  const provided = (req.headers.get("x-worker-secret") ?? "").trim();
  if (!provided || !timingSafeEqualStr(expected, provided)) {
    return new Response(JSON.stringify({ error: "unauthorized" }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    });
  }

  return null;
}
