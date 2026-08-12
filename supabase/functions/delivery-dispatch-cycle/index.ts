/**
 * Delivery FIFO / Temporal dispatch cycle bridge.
 *
 * Worker secret AuthZ. Runs `@gtr/delivery` SQL-backed FIFO cycle:
 * suggest_delivery_assignees → assign_delivery_job (auto-accept stub),
 * or falls back to `_try_auto_assign_delivery_job`.
 *
 * Full Temporal worker is separate; this edge is the live assign bridge.
 * No Fleetbase. No ZIMRA.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { assertWorkerSecret } from "../_shared/worker_auth.ts";

type SuggestRow = {
  user_id: string;
  distance_m: number | null;
  open_jobs: number | null;
  capacity: number | null;
  status: string;
};

Deno.serve(async (req) => {
  try {
    const denied = assertWorkerSecret(req);
    if (denied) return denied;

    const body = (await req.json()) as {
      delivery_job_id?: string;
      mode?: "fifo_cycle" | "sql_auto";
      offer_timeout_seconds?: number;
    };

    const jobId = body.delivery_job_id?.trim();
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

    const mode = body.mode ?? "fifo_cycle";

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
          assignee_user_id: data ?? null,
          final_state: data ? "accepted" : "fifo_queued",
        }),
        { headers: { "Content-Type": "application/json" } },
      );
    }

    // FIFO cycle (mirrors packages/delivery assign-bridge — Deno cannot import workspace pkgs)
    const suggest = await supabase.rpc("suggest_delivery_assignees", {
      p_delivery_job_id: jobId,
      p_limit: 10,
    });
    if (suggest.error) {
      return new Response(JSON.stringify({ error: suggest.error.message }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const rows = (Array.isArray(suggest.data) ? suggest.data : []) as SuggestRow[];
    const eligible = rows
      .filter(
        (r) =>
          r.status === "available" ||
          r.status === "on_duty" ||
          r.status === "busy",
      )
      .sort((a, b) => {
        const da = (a.distance_m ?? 1_000_000) + (a.open_jobs ?? 0) * 1000;
        const db = (b.distance_m ?? 1_000_000) + (b.open_jobs ?? 0) * 1000;
        return da - db;
      });

    if (eligible.length === 0) {
      await supabase.rpc("_try_auto_assign_delivery_job", {
        p_delivery_job_id: jobId,
      });
      return new Response(
        JSON.stringify({
          delivery_job_id: jobId,
          mode,
          assignee_user_id: null,
          final_state: "fifo_queued",
          workflow: "DeliveryDispatchWorkflow",
        }),
        { headers: { "Content-Type": "application/json" } },
      );
    }

    const driverId = eligible[0]!.user_id;
    const assign = await supabase.rpc("assign_delivery_job", {
      p_delivery_job_id: jobId,
      p_assignee_user_id: driverId,
      p_override: false,
    });
    if (assign.error) {
      return new Response(JSON.stringify({ error: assign.error.message }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(
      JSON.stringify({
        delivery_job_id: jobId,
        mode,
        assignee_user_id: driverId,
        final_state: "accepted",
        eta_source: "osrm",
        workflow: "DeliveryDispatchWorkflow",
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
