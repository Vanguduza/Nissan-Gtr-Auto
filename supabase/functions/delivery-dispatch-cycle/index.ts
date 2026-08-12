/**
 * Delivery FIFO / Temporal dispatch cycle bridge (mirrors `@gtr/delivery` assign-bridge).
 *
 * AuthZ: `assertWorkerSecret` (fail-closed). No Fleetbase. No ZIMRA. No client secrets.
 *
 * Deno cannot import the workspace package — this edge ports the same opt-in semantics
 * (not a second FIFO invent). Package SoR: `runDeliveryDispatchCycle` +
 * `createSqlDispatchActivities` (`autoAcceptOffers` defaults **false**).
 *
 * ## Modes
 *
 * | mode / flag | Behavior |
 * |---|---|
 * | `sql_auto` | Immediate SQL SoR: `_try_auto_assign_delivery_job` (cron / fire-and-assign). |
 * | `fifo_cycle` + `autoAcceptOffers: true` | Cron immediate assign: suggest → first eligible → `assign_delivery_job`. **Must opt in.** |
 * | `fifo_cycle` + `autoAcceptOffers: false` (default) | Offer SM: each decision is timeout (or body `offer_decisions` / awaitDecision stand-in) → reject/timeout requeue → eventually `_try_auto_assign_delivery_job` enqueue. |
 *
 * Body / query: `{ autoAcceptOffers: true|false }` matches package `SqlDispatchActivityOpts`.
 * Optional `offer_decisions: ("accept"|"reject"|"timeout")[]` = Edge stand-in for injectable
 * `awaitDecision` (Temporal worker = §H; not hosted here).
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { assertWorkerSecret } from "../_shared/worker_auth.ts";

type SuggestRow = {
  user_id: string;
  distance_m: number | null;
  open_jobs: number | null;
  capacity: number | null;
  status: string;
};

type OfferDecision = "accept" | "reject" | "timeout";

type CourierCandidate = {
  driverId: string;
  rankScore: number;
  available: boolean;
};

/** Mirror `candidatesFromSuggestRows` — rank by distance then load. */
function candidatesFromSuggestRows(rows: SuggestRow[]): CourierCandidate[] {
  return rows
    .map((r) => {
      const dist = r.distance_m ?? 1_000_000;
      const load = r.open_jobs ?? 0;
      return {
        driverId: r.user_id,
        rankScore: dist + load * 1000,
        available:
          r.status === "available" ||
          r.status === "on_duty" ||
          r.status === "busy",
      };
    })
    .sort((a, b) => a.rankScore - b.rankScore);
}

/** Mirror `selectNextCourierOffer`. */
function selectNextCourierOffer(input: {
  candidates: CourierCandidate[];
  alreadyTriedDriverIds?: string[];
}): { kind: "offered"; driverId: string } | { kind: "fifo_queued" } {
  const tried = new Set(input.alreadyTriedDriverIds ?? []);
  const eligible = input.candidates
    .filter((c) => c.available && !tried.has(c.driverId))
    .sort((a, b) => a.rankScore - b.rankScore);
  if (eligible.length === 0) return { kind: "fifo_queued" };
  return { kind: "offered", driverId: eligible[0]!.driverId };
}

/** Mirror `applyOfferDecision` — reject|timeout → requeue remaining. */
function applyOfferDecision(input: {
  decision: OfferDecision;
  currentDriverId: string;
  candidates: CourierCandidate[];
  alreadyTriedDriverIds?: string[];
}):
  | { kind: "accepted"; driverId: string }
  | { kind: "fifo_queued" }
  | { kind: "requeued"; triedDriverIds: string[] } {
  if (input.decision === "accept") {
    return { kind: "accepted", driverId: input.currentDriverId };
  }
  const tried = [...(input.alreadyTriedDriverIds ?? []), input.currentDriverId];
  const next = selectNextCourierOffer({
    candidates: input.candidates,
    alreadyTriedDriverIds: tried,
  });
  if (next.kind === "fifo_queued") return next;
  return { kind: "requeued", triedDriverIds: tried };
}

function parseAutoAcceptOffers(
  body: Record<string, unknown>,
  url: URL,
): boolean {
  const q = url.searchParams.get("autoAcceptOffers") ??
    url.searchParams.get("auto_accept_offers");
  if (q === "true" || q === "1") return true;
  if (q === "false" || q === "0") return false;
  // Opt-in only — package default is false.
  return body.autoAcceptOffers === true;
}

function parseOfferDecisions(
  body: Record<string, unknown>,
): OfferDecision[] | null {
  const raw = body.offer_decisions ?? body.offerDecisions;
  if (!Array.isArray(raw) || raw.length === 0) return null;
  const out: OfferDecision[] = [];
  for (const d of raw) {
    if (d === "accept" || d === "reject" || d === "timeout") out.push(d);
  }
  return out.length > 0 ? out : null;
}

/**
 * FIFO offer cycle — same orchestration as `runSqlDeliveryDispatchCycle`.
 * `autoAcceptOffers: true` → awaitDecision returns accept (cron immediate assign).
 * Else timeout, unless `offer_decisions` shifts (awaitDecision stand-in).
 */
async function runFifoOfferCycle(
  supabase: SupabaseClient,
  jobId: string,
  opts: {
    autoAcceptOffers: boolean;
    offerTimeoutSeconds: number;
    offerDecisions: OfferDecision[] | null;
    preferredDriverId?: string | null;
  },
): Promise<{
  assignee_user_id: string | null;
  final_state: string;
  autoAcceptOffers: boolean;
}> {
  const suggest = await supabase.rpc("suggest_delivery_assignees", {
    p_delivery_job_id: jobId,
    p_limit: 10,
  });
  if (suggest.error) throw new Error(suggest.error.message);

  const rows = (Array.isArray(suggest.data) ? suggest.data : []) as SuggestRow[];
  let candidates = candidatesFromSuggestRows(rows);

  if (opts.preferredDriverId) {
    const pref = opts.preferredDriverId;
    candidates = [
      ...candidates.filter((c) => c.driverId === pref),
      ...candidates.filter((c) => c.driverId !== pref),
    ];
  }

  const decisions = opts.offerDecisions ? [...opts.offerDecisions] : null;
  const autoAccept = opts.autoAcceptOffers === true;
  const tried: string[] = [];

  for (;;) {
    const pick = selectNextCourierOffer({
      candidates,
      alreadyTriedDriverIds: tried,
    });

    if (pick.kind === "fifo_queued") {
      // Soft FIFO: SQL `_try_auto_assign_delivery_job` retries later (package enqueueFifo).
      await supabase.rpc("_try_auto_assign_delivery_job", {
        p_delivery_job_id: jobId,
      });
      return {
        assignee_user_id: null,
        final_state: "fifo_queued",
        autoAcceptOffers: autoAccept,
      };
    }

    // sendOffer stub — offer rows / push live in Temporal worker (§H).
    let decision: OfferDecision;
    if (decisions && decisions.length > 0) {
      decision = decisions.shift()!;
    } else {
      decision = autoAccept ? "accept" : "timeout";
    }

    const outcome = applyOfferDecision({
      decision,
      currentDriverId: pick.driverId,
      candidates,
      alreadyTriedDriverIds: tried,
    });

    if (outcome.kind === "accepted") {
      const assign = await supabase.rpc("assign_delivery_job", {
        p_delivery_job_id: jobId,
        p_assignee_user_id: outcome.driverId,
        p_override: false,
      });
      if (assign.error) throw new Error(assign.error.message);
      return {
        assignee_user_id: outcome.driverId,
        final_state: "accepted",
        autoAcceptOffers: autoAccept,
      };
    }

    if (outcome.kind === "fifo_queued") {
      await supabase.rpc("_try_auto_assign_delivery_job", {
        p_delivery_job_id: jobId,
      });
      return {
        assignee_user_id: null,
        final_state: "fifo_queued",
        autoAcceptOffers: autoAccept,
      };
    }

    tried.push(...outcome.triedDriverIds);
  }
}

Deno.serve(async (req) => {
  try {
    const denied = assertWorkerSecret(req);
    if (denied) return denied;

    const url = new URL(req.url);
    const body = (await req.json().catch(() => ({}))) as Record<string, unknown>;

    const jobId =
      (typeof body.delivery_job_id === "string"
        ? body.delivery_job_id.trim()
        : "") ||
      (url.searchParams.get("delivery_job_id")?.trim() ?? "");
    if (!jobId) {
      return new Response(JSON.stringify({ error: "delivery_job_id required" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const modeRaw = body.mode ?? url.searchParams.get("mode") ?? "fifo_cycle";
    const mode = modeRaw === "sql_auto" ? "sql_auto" : "fifo_cycle";
    const autoAcceptOffers = parseAutoAcceptOffers(body, url);
    const offerTimeoutSeconds =
      typeof body.offer_timeout_seconds === "number"
        ? body.offer_timeout_seconds
        : 30;
    const preferredDriverId =
      typeof body.preferred_driver_id === "string"
        ? body.preferred_driver_id
        : null;
    const offerDecisions = parseOfferDecisions(body);

    // --- sql_auto: production SQL SoR immediate assign (cron path) ---
    if (mode === "sql_auto") {
      const { data, error } = await supabase.rpc("_try_auto_assign_delivery_job", {
        p_delivery_job_id: jobId,
      });
      if (error) {
        return new Response(JSON.stringify({ error: error.message }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response(
        JSON.stringify({
          delivery_job_id: jobId,
          mode,
          // sql_auto is always immediate assign SoR (implicit auto-accept at SQL layer).
          autoAcceptOffers: true,
          assignee_user_id: data ?? null,
          final_state: data ? "accepted" : "fifo_queued",
        }),
        { headers: { "Content-Type": "application/json" } },
      );
    }

    // --- fifo_cycle: package offer SM; cron must pass autoAcceptOffers: true ---
    const result = await runFifoOfferCycle(supabase, jobId, {
      autoAcceptOffers,
      offerTimeoutSeconds,
      offerDecisions,
      preferredDriverId,
    });

    return new Response(
      JSON.stringify({
        delivery_job_id: jobId,
        mode,
        autoAcceptOffers: result.autoAcceptOffers,
        assignee_user_id: result.assignee_user_id,
        final_state: result.final_state,
        eta_source: result.final_state === "accepted" ? "osrm" : null,
        workflow: "DeliveryDispatchWorkflow",
        offer_timeout_seconds: offerTimeoutSeconds,
      }),
      { headers: { "Content-Type": "application/json" } },
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
