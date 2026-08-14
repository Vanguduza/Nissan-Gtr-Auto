/**
 * Catalog hierarchy import for Catalog APK — JWT only (no service role in the APK).
 *
 * Auth: Authorization Bearer <user JWT>
 * Body: {
 *   job_id, maker, complete_only, strict_gate,
 *   selected_variants: [{model_slug, variant_slug}],
 *   bundle: { catalog_* / vehicle_master / part_fitment / ... }
 * }
 *
 * Server uses service role for upserts after verifying the caller JWT.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { corsHeaders, jsonResponse, requireBearerJwt } from "../_shared/payment_edge.ts";

type VariantKey = { model_slug: string; variant_slug: string };

function isStaffEmail(email: string | undefined): boolean {
  if (!email) return false;
  const allow = (Deno.env.get("CATALOG_IMPORT_ALLOW_EMAILS") || "")
    .split(",")
    .map((s) => s.trim().toLowerCase())
    .filter(Boolean);
  if (allow.length === 0) return true; // open to authenticated users until allow-list is set
  return allow.includes(email.toLowerCase());
}

function filterBundle(
  bundle: Record<string, unknown>,
  selected: VariantKey[],
): Record<string, unknown> {
  if (!selected.length) return bundle;
  const keys = new Set(selected.map((s) => `${s.model_slug}::${s.variant_slug}`));
  const filterRows = (rows: unknown, modelKey = "model_slug", variantKey = "variant_slug") => {
    if (!Array.isArray(rows)) return rows;
    return rows.filter((row) => {
      if (!row || typeof row !== "object") return false;
      const r = row as Record<string, unknown>;
      const k = `${r[modelKey] ?? ""}::${r[variantKey] ?? ""}`;
      return keys.has(k);
    });
  };

  return {
    ...bundle,
    catalog_variants: filterRows(bundle.catalog_variants),
    catalog_sections: filterRows(bundle.catalog_sections),
    catalog_diagrams: filterRows(bundle.catalog_diagrams),
    catalog_diagram_parts: filterRows(bundle.catalog_diagram_parts),
    part_fitment: filterRows(bundle.part_fitment),
    vehicle_master: filterRows(bundle.vehicle_master),
    diagram_assets: Array.isArray(bundle.diagram_assets)
      ? (bundle.diagram_assets as unknown[]).filter((row) => {
        if (!row || typeof row !== "object") return true;
        const r = row as Record<string, unknown>;
        if (!r.model_slug || !r.variant_slug) return true;
        return keys.has(`${r.model_slug}::${r.variant_slug}`);
      })
      : bundle.diagram_assets,
  };
}

async function upsertTable(
  admin: ReturnType<typeof createClient>,
  table: string,
  rows: unknown,
  onConflict: string,
): Promise<number> {
  if (!Array.isArray(rows) || rows.length === 0) return 0;
  const chunk = 200;
  let count = 0;
  for (let i = 0; i < rows.length; i += chunk) {
    const slice = rows.slice(i, i + chunk);
    const { error } = await admin.from(table).upsert(slice, { onConflict });
    if (error) throw new Error(`${table}: ${error.message}`);
    count += slice.length;
  }
  return count;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders(req) });
  }

  try {
    if (req.method !== "POST") {
      return jsonResponse({ error: "POST required" }, 405, corsHeaders(req));
    }

    const authHeader = requireBearerJwt(req);
    if (!authHeader) {
      return jsonResponse({ error: "Authorization Bearer JWT required" }, 401, corsHeaders(req));
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !anonKey || !serviceKey) {
      return jsonResponse({ error: "Server misconfigured" }, 500, corsHeaders(req));
    }

    const userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: userData, error: userErr } = await userClient.auth.getUser();
    if (userErr || !userData.user) {
      return jsonResponse({ error: "Invalid JWT" }, 401, corsHeaders(req));
    }
    if (!isStaffEmail(userData.user.email)) {
      return jsonResponse({ error: "Not authorized for catalog import" }, 403, corsHeaders(req));
    }

    const body = await req.json().catch(() => ({}));
    const selected = Array.isArray(body?.selected_variants)
      ? (body.selected_variants as VariantKey[]).filter(
        (v) => v && typeof v.model_slug === "string" && typeof v.variant_slug === "string",
      )
      : [];
    const rawBundle = (body?.bundle && typeof body.bundle === "object")
      ? body.bundle as Record<string, unknown>
      : null;
    if (!rawBundle) {
      return jsonResponse({ error: "bundle object required" }, 400, corsHeaders(req));
    }

    const strict = body?.strict_gate !== false;
    if (strict && selected.length === 0) {
      return jsonResponse(
        { error: "strict_gate requires selected_variants" },
        400,
        corsHeaders(req),
      );
    }

    const bundle = filterBundle(rawBundle, selected);
    const admin = createClient(supabaseUrl, serviceKey);

    const counts: Record<string, number> = {};
    counts.catalog_makers = await upsertTable(
      admin,
      "catalog_makers",
      bundle.catalog_makers,
      "slug",
    );
    counts.catalog_models = await upsertTable(
      admin,
      "catalog_models",
      bundle.catalog_models,
      "maker_slug,slug",
    );
    counts.catalog_variants = await upsertTable(
      admin,
      "catalog_variants",
      bundle.catalog_variants,
      "maker_slug,model_slug,slug",
    );
    counts.catalog_sections = await upsertTable(
      admin,
      "catalog_sections",
      bundle.catalog_sections,
      "maker_slug,model_slug,variant_slug,slug",
    );
    counts.catalog_diagrams = await upsertTable(
      admin,
      "catalog_diagrams",
      bundle.catalog_diagrams,
      "maker_slug,model_slug,variant_slug,section_slug,slug",
    );
    counts.catalog_diagram_parts = await upsertTable(
      admin,
      "catalog_diagram_parts",
      bundle.catalog_diagram_parts,
      "maker_slug,model_slug,variant_slug,section_slug,itemslist_id",
    );
    counts.vehicle_master = await upsertTable(
      admin,
      "vehicle_master",
      bundle.vehicle_master,
      "id",
    );
    counts.part_fitment = await upsertTable(
      admin,
      "part_fitment",
      bundle.part_fitment,
      "id",
    );

    return jsonResponse(
      {
        ok: true,
        job_id: body?.job_id ?? null,
        maker: body?.maker ?? null,
        imported_by: userData.user.email,
        selected_variants: selected.length,
        counts,
      },
      200,
      corsHeaders(req),
    );
  } catch (err) {
    return jsonResponse(
      { error: err instanceof Error ? err.message : String(err) },
      500,
      corsHeaders(req),
    );
  }
});
