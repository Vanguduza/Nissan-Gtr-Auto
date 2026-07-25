/**
 * Staff interactive AI analytics — KPI aggregates + optional Gemini narrative.
 * AuthZ: caller JWT (admin|finance|sales via KPI RPC).
 * Fail closed for narrative when GEMINI_API_KEY missing (422 + KPIs still returned).
 * No ZIMRA / payroll tax / PII dumps.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { requireBearerJwt } from "../_shared/payment_edge.ts";
import {
  generateKpiNarrative,
  getGeminiApiKey,
} from "../_shared/gemini_narrative.ts";

function json(
  body: unknown,
  status = 200,
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function periodBounds(fromIso?: string, toIso?: string): {
  from: string;
  to: string;
} | null {
  const to = toIso ? new Date(toIso) : new Date();
  const from = fromIso
    ? new Date(fromIso)
    : new Date(to.getTime() - 24 * 60 * 60 * 1000);
  if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime())) return null;
  if (to <= from) return null;
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
    const kpiSet = (body?.kpi_set as string | undefined) ?? "ops_sales_v1";
    const includeNarrative = body?.include_narrative !== false;
    const bounds = periodBounds(body?.from, body?.to);
    if (!bounds) {
      return json({ error: "valid from/to ISO timestamps required" }, 400);
    }
    if (kpiSet !== "ops_sales_v1") {
      return json({ error: "unsupported kpi_set" }, 400);
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );

    const { data: kpis, error: kpiErr } = await supabase.rpc(
      "kpi_ops_sales_v1",
      {
        p_from: bounds.from,
        p_to: bounds.to,
        p_top_limit: 10,
      },
    );

    if (kpiErr) {
      const status = /role required/i.test(kpiErr.message) ? 403 : 400;
      return json({ error: kpiErr.message }, status);
    }

    if (!includeNarrative) {
      return json({
        kpis,
        narrative: null,
        gemini_used: false,
        error: null,
      });
    }

    if (!getGeminiApiKey()) {
      return json(
        {
          kpis,
          narrative: null,
          gemini_used: false,
          error: "gemini_unavailable",
        },
        422,
      );
    }

    const narrativeResult = await generateKpiNarrative(kpis);
    if (narrativeResult.error) {
      return json(
        {
          kpis,
          narrative: null,
          gemini_used: false,
          error: narrativeResult.error,
        },
        422,
      );
    }

    return json({
      kpis,
      narrative: narrativeResult.narrative,
      gemini_used: true,
      error: null,
    });
  } catch (e) {
    return json({ error: String(e) }, 500);
  }
});
