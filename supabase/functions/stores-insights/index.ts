/**
 * Staff stores insights — ABC + forecast suggestions + optional Gemini directives.
 * AuthZ: caller JWT (admin|warehouse|finance via KPI RPC).
 * Fail closed for directives when GEMINI_API_KEY missing.
 * No ZIMRA / payroll tax / Text-to-SQL.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { requireBearerJwt } from "../_shared/payment_edge.ts";
import {
  generateStoresDirectives,
  getGeminiApiKey,
} from "../_shared/gemini_narrative.ts";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function periodBounds(fromIso?: string, toIso?: string): {
  from: string;
  to: string;
} {
  const to = toIso ? new Date(toIso) : new Date();
  const from = fromIso
    ? new Date(fromIso)
    : new Date(to.getTime() - 90 * 24 * 60 * 60 * 1000);
  if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime()) || to <= from) {
    const fallbackTo = new Date();
    const fallbackFrom = new Date(
      fallbackTo.getTime() - 90 * 24 * 60 * 60 * 1000,
    );
    return {
      from: fallbackFrom.toISOString(),
      to: fallbackTo.toISOString(),
    };
  }
  return { from: from.toISOString(), to: to.toISOString() };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers":
          "authorization, x-client-info, apikey, content-type",
      },
    });
  }

  try {
    if (req.method !== "POST") {
      return json({ error: "POST required" }, 405);
    }

    const authHeader = requireBearerJwt(req);
    if (!authHeader) {
      return json({ error: "Authorization Bearer JWT required" }, 401);
    }

    const body = await req.json().catch(() => ({}));
    const warehouseId = body?.warehouse_id as string | undefined;
    const includeDirectives = body?.include_directives !== false;
    const runAbc = body?.run_abc !== false;
    const bounds = periodBounds(body?.from, body?.to);

    if (!warehouseId) {
      return json({ error: "warehouse_id required" }, 400);
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );

    const { data: kpis, error: kpiErr } = await supabase.rpc(
      "kpi_stores_forecast_v1",
      {
        p_warehouse_id: warehouseId,
        p_from: bounds.from,
        p_to: bounds.to,
        p_run_abc: runAbc,
      },
    );

    if (kpiErr) {
      const status = /role required/i.test(kpiErr.message) ? 403 : 400;
      return json({ error: kpiErr.message }, status);
    }

    if (!includeDirectives) {
      return json({
        kpis,
        directives: [],
        narrative: null,
        gemini_used: false,
        error: null,
        directive_id: null,
      });
    }

    if (!getGeminiApiKey()) {
      return json(
        {
          kpis,
          directives: [],
          narrative: null,
          gemini_used: false,
          error: "gemini_unavailable",
          directive_id: null,
        },
        422,
      );
    }

    const gen = await generateStoresDirectives(kpis);
    if (gen.error || !gen.gemini_used) {
      return json(
        {
          kpis,
          directives: gen.directives,
          narrative: gen.narrative,
          gemini_used: false,
          error: gen.error ?? "gemini_failed",
          directive_id: null,
        },
        422,
      );
    }

    const { data: directiveId, error: insErr } = await supabase.rpc(
      "insert_inventory_ai_directive",
      {
        p_warehouse_id: warehouseId,
        p_period_start: bounds.from,
        p_period_end: bounds.to,
        p_kpi_json: kpis,
        p_directives: gen.directives,
        p_narrative: gen.narrative,
        p_gemini_used: true,
        p_error: null,
      },
    );

    if (insErr) {
      return json(
        {
          kpis,
          directives: gen.directives,
          narrative: gen.narrative,
          gemini_used: true,
          error: insErr.message,
          directive_id: null,
        },
        200,
      );
    }

    return json({
      kpis,
      directives: gen.directives,
      narrative: gen.narrative,
      gemini_used: true,
      error: null,
      directive_id: directiveId,
    });
  } catch (e) {
    return json({ error: String(e) }, 500);
  }
});
