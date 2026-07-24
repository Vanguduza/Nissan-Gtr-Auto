/**
 * Optional demand-forecast edge stub → generate_forecast_suggestions RPC.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

Deno.serve(async (req) => {
  try {
    const body = await req.json();
    const warehouseId = body.warehouse_id as string;
    const horizonDays = Number(body.horizon_days ?? 30);
    const defaultReorderQty = Number(body.default_reorder_qty ?? 10);

    if (!warehouseId) {
      return new Response(JSON.stringify({ error: "warehouse_id required" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data, error } = await supabase.rpc("generate_forecast_suggestions", {
      p_warehouse_id: warehouseId,
      p_horizon_days: horizonDays,
      p_default_reorder_qty: defaultReorderQty,
    });

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(
      JSON.stringify({ suggestions_upserted: data, stub: true }),
      { headers: { "Content-Type": "application/json" } },
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
