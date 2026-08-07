/**
 * Catalog search proxy — Meilisearch with Postgres FTS fallback.
 * Auth: caller Bearer JWT (authenticated catalog search).
 * Secrets: MEILI_HOST + MEILI_SEARCH_KEY (preferred) or MEILI_MASTER_KEY (dev only).
 * Feature flag: CATALOG_SEARCH_BACKEND=meili|fts (default meili).
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { corsHeaders, jsonResponse, requireBearerJwt } from "../_shared/payment_edge.ts";
import {
  catalogSearchBackend,
  enrichMeiliResultsWithFtsFitments,
  isSearchMode,
  meiliConfig,
  searchCatalogFtsFallback,
  searchMeiliCatalog,
  type SearchMode,
} from "../_shared/meili_search.ts";

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
      return jsonResponse(
        { error: "Authorization Bearer JWT required" },
        401,
        corsHeaders(req),
      );
    }

    const body = await req.json().catch(() => ({}));
    const modeRaw = typeof body?.mode === "string" ? body.mode : "part";
    const query = typeof body?.query === "string" ? body.query.trim() : "";
    const limit = typeof body?.limit === "number" ? body.limit : 20;
    const facets = Array.isArray(body?.facets)
      ? body.facets.filter((f: unknown) => typeof f === "string")
      : undefined;

    if (!query) {
      return jsonResponse(
        {
          mode: isSearchMode(modeRaw) ? modeRaw : "part",
          query: "",
          results: [],
          backend: catalogSearchBackend(),
        },
        200,
        corsHeaders(req),
      );
    }

    if (!isSearchMode(modeRaw)) {
      return jsonResponse(
        { error: "mode must be part|vin|model|pnc" },
        400,
        corsHeaders(req),
      );
    }

    const mode = modeRaw as SearchMode;
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );

    const useFts =
      catalogSearchBackend() === "fts" || meiliConfig() === null;

    if (useFts) {
      const data = await searchCatalogFtsFallback(supabase, mode, query);
      return jsonResponse(data, 200, corsHeaders(req));
    }

    try {
      const data = await searchMeiliCatalog(mode, query, { limit, facets });
      if (mode === "vin" || mode === "pnc") {
        data.results = await enrichMeiliResultsWithFtsFitments(
          supabase,
          mode,
          query,
          data.results,
        );
      }
      return jsonResponse(data, 200, corsHeaders(req));
    } catch (meiliErr) {
      console.warn("Meili search failed; falling back to FTS:", meiliErr);
      const data = await searchCatalogFtsFallback(supabase, mode, query);
      return jsonResponse(data, 200, corsHeaders(req));
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : "catalog search failed";
    return jsonResponse({ error: message }, 500, corsHeaders(req));
  }
});
