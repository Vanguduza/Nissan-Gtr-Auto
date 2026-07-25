/**
 * Local-only channel stub gate — same rule as worker_auth:
 * WORKER_ALLOW_UNVERIFIED_LOCAL=1 AND WORKER_SHARED_SECRET unset.
 * Never use for production fake-success.
 */
export function allowLocalChannelStub(): boolean {
  const secret = Deno.env.get("WORKER_SHARED_SECRET")?.trim() ?? "";
  return !secret && Deno.env.get("WORKER_ALLOW_UNVERIFIED_LOCAL") === "1";
}

export function jsonOk(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function jsonErr(error: string, status: number): Response {
  return new Response(JSON.stringify({ error }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
