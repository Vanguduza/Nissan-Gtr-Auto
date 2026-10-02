import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { GetObjectCommand, S3Client } from "npm:@aws-sdk/client-s3@3";
import { getSignedUrl } from "npm:@aws-sdk/s3-request-presigner@3";

// Source of the deployed `catalog-live-r2` (v6 contract). The hierarchy is vehicle-routed: families
// and variants come from `catalog_r2_vehicle_master` (variant_id = vehicle-master id = R2 scope),
// sections and diagram lists from that vehicle's `vehicle_search` shard. The action is read from
// the POST body, `?action=`, or (older clients) the last path segment.
//
// A serving object may be split over several R2 pages (`metadata.pages`, read in order), and a
// `diagram_parts` object may point at its section's shard (`metadata.filter` = "diagram_id"), in
// which case only that diagram's rows are returned.

const MAX_BYTES = 16 * 1024 * 1024;
const REQUIRED_CORE_KINDS = ["vehicle_search", "vehicle_fitment", "section_parts", "diagram_parts"] as const;
const REQUIRED_FULL_KINDS = [...REQUIRED_CORE_KINDS, "diagram_image"] as const;
/** PostgREST page size (project max_rows); larger reads are paged. */
const PAGE = 1000;
/** The vehicle master is read on most requests; a warm instance reuses it briefly. */
const VEHICLE_CACHE_MS = 5 * 60 * 1000;
let vehicleCache: { at: number; release: string; rows: Vehicle[] } | null = null;
const ORIGINS = new Set(["https://nissangtrauto.co.zw", "https://www.nissangtrauto.co.zw", "http://localhost:3000", "http://127.0.0.1:3000"]);
const ACTIONS = new Set(["health", "vehicle-master", "customer-stock", "customer-search", "fitment-check", "staff-families", "staff-variants", "staff-sections", "staff-diagrams", "staff-section-parts", "staff-diagram-parts", "diagram-image", "offline-bundle"]);
type Row = Record<string, unknown>;
type ServingObject = { object_key: string; sha256: string | null; row_count: number; bytes: number; content_encoding: string | null; metadata: Record<string, unknown> | null };
type Vehicle = { r2_scope_key: string; maker_slug: string; model: string; chassis_code: string; engine_code: string; year_start: number | null; year_end: number | null; sales_region: string | null; source_release_version: string };
type R2 = { bucket: string; client: S3Client };

function cors(req: Request) {
  const origin = req.headers.get("Origin");
  return {
    "Access-Control-Allow-Origin": origin && ORIGINS.has(origin) ? origin : "https://nissangtrauto.co.zw",
    "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    Vary: "Origin",
  };
}
function reply(req: Request, status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { ...cors(req), "Content-Type": "application/json", "Cache-Control": "no-store" } });
}
function normalizePart(value: unknown) { return String(value ?? "").toUpperCase().replace(/[^A-Z0-9]/g, ""); }
function normalizeCategory(value: unknown) { return String(value ?? "").trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, ""); }
function slug(value: unknown) { return String(value ?? "").trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, ""); }
function displayVehicle(v: Vehicle) {
  return [v.chassis_code, v.engine_code, v.year_start || v.year_end ? `${v.year_start ?? ""}–${v.year_end ?? ""}` : null, v.sales_region].filter(Boolean).join(" · ");
}
/** Most frequent label wins (ties alphabetical). */
function topLabel(labels: Map<string, number>, fallback: string) {
  return [...labels.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))[0]?.[0] ?? fallback;
}
// Edge secrets first; otherwise the Vault-backed `catalog_r2_runtime_config` (service role only).
async function r2Config(admin: SupabaseClient): Promise<R2 | null> {
  let accountId = Deno.env.get("CLOUDFLARE_ACCOUNT_ID")?.trim() ?? "";
  let accessKeyId = Deno.env.get("CLOUDFLARE_R2_ACCESS_KEY_ID")?.trim() ?? "";
  let secretAccessKey = Deno.env.get("CLOUDFLARE_R2_SECRET_ACCESS_KEY")?.trim() ?? "";
  let bucket = Deno.env.get("CLOUDFLARE_R2_BUCKET")?.trim() ?? "";
  let endpoint = Deno.env.get("CLOUDFLARE_R2_ENDPOINT")?.trim() ?? "";
  if (!accountId || !accessKeyId || !secretAccessKey || !bucket) {
    const { data, error } = await admin.rpc("catalog_r2_runtime_config");
    if (!error && data && typeof data === "object") {
      const cfg = data as Record<string, unknown>;
      accountId = String(cfg.account_id ?? "").trim();
      accessKeyId = String(cfg.access_key_id ?? "").trim();
      secretAccessKey = String(cfg.secret_access_key ?? "").trim();
      bucket = String(cfg.bucket ?? "").trim();
      endpoint = String(cfg.endpoint ?? "").trim();
    }
  }
  if (!accountId || !accessKeyId || !secretAccessKey || !bucket) return null;
  return {
    bucket,
    client: new S3Client({ region: "auto", endpoint: endpoint || `https://${accountId}.r2.cloudflarestorage.com`, credentials: { accessKeyId, secretAccessKey } }),
  };
}
async function bodyText(config: R2, key: string) {
  const signed = await getSignedUrl(config.client, new GetObjectCommand({ Bucket: config.bucket, Key: key }), { expiresIn: 180 });
  const response = await fetch(signed, { headers: { Accept: "application/octet-stream" } });
  if (!response.ok) throw new Error(`R2 object unavailable (${response.status})`);
  const bytes = new Uint8Array(await response.arrayBuffer());
  if (bytes.byteLength > MAX_BYTES) throw new Error("R2 serving shard too large");
  const isGzip = bytes.length >= 2 && bytes[0] === 0x1f && bytes[1] === 0x8b;
  if (!isGzip) return new TextDecoder().decode(bytes);
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"));
  return await new Response(stream).text();
}
function parseRows(text: string): Row[] {
  const t = text.trim();
  if (!t) return [];
  if (t.startsWith("[")) { const parsed = JSON.parse(t); return Array.isArray(parsed) ? parsed : []; }
  return t.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line) as Row);
}
function searchable(row: Row) {
  return [row.search_text, row.name, row.description, row.category_name, row.subcategory_name, row.pnc_code, row.display_oem_number, ...(Array.isArray(row.aliases) ? row.aliases : [])].filter(Boolean).join(" ").toLowerCase();
}
function queryMatch(row: Row, q: string) { const hay = searchable(row); return q.toLowerCase().split(/\s+/).filter(Boolean).every((term) => hay.includes(term)); }

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors(req) });
  if (req.method !== "GET" && req.method !== "POST") return reply(req, 405, { error: "GET or POST required" });
  const auth = req.headers.get("Authorization");
  if (!auth?.startsWith("Bearer ")) return reply(req, 401, { error: "sign in required" });
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!supabaseUrl || !serviceKey) return reply(req, 503, { error: "catalog service misconfigured" });
  const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const token = auth.replace(/^Bearer\s+/i, "").trim();
  const serviceCaller = token === serviceKey;
  let userId: string | null = null;
  if (!serviceCaller) {
    const { data: userData, error: userError } = await admin.auth.getUser(token);
    userId = userData.user?.id ?? null;
    if (userError || !userId) return reply(req, 401, { error: "invalid session" });
  }

  const url = new URL(req.url);
  const input = req.method === "POST" ? await req.json().catch(() => ({})) as Record<string, unknown> : {};
  const param = (key: string) => input[key] == null ? url.searchParams.get(key) : String(input[key]);
  const lastSegment = url.pathname.split("/").filter(Boolean).at(-1) ?? "";
  const action = String(input.action ?? url.searchParams.get("action") ?? (ACTIONS.has(lastSegment) ? lastSegment : "")).trim();
  const maker = (param("maker") ?? "nissan").trim().toLowerCase();
  if (maker !== "nissan") return reply(req, 404, { error: "maker not published" });
  const { data: release } = await admin.from("catalog_releases").select("id,version,bucket_name,published_at").eq("maker_slug", maker).eq("is_current", true).not("published_at", "is", null).maybeSingle();
  if (!release?.id) return reply(req, 503, { error: "no current catalog release" });

  const isStaff = async () => {
    if (serviceCaller) return true;
    if (!userId) return false;
    const [{ data: profile }, { data: roles }] = await Promise.all([
      admin.from("profiles").select("is_staff").eq("id", userId).maybeSingle(),
      admin.from("staff_roles").select("role").eq("user_id", userId).limit(1),
    ]);
    return profile?.is_staff === true || (roles?.length ?? 0) > 0;
  };
  const vehicles = async (): Promise<Vehicle[]> => {
    if (vehicleCache && vehicleCache.release === release.id && Date.now() - vehicleCache.at < VEHICLE_CACHE_MS) return vehicleCache.rows;
    const rows: Vehicle[] = [];
    for (let from = 0; ; from += PAGE) {
      const { data, error } = await admin.from("catalog_r2_vehicle_master").select("r2_scope_key,maker_slug,model,chassis_code,engine_code,year_start,year_end,sales_region,source_release_version").eq("maker_slug", maker).order("model").order("year_start").order("chassis_code").order("engine_code").order("r2_scope_key").range(from, from + PAGE - 1);
      if (error) throw new Error("vehicle routing unavailable");
      rows.push(...((data ?? []) as Vehicle[]));
      if ((data ?? []).length < PAGE) break;
    }
    vehicleCache = { at: Date.now(), release: release.id, rows };
    return rows;
  };
  const serving = async (kind: string, scope: string): Promise<ServingObject | null> => {
    const { data } = await admin.from("catalog_r2_serving_objects").select("object_key,sha256,row_count,bytes,content_encoding,metadata").eq("release_id", release.id).eq("maker_slug", maker).eq("object_kind", kind).eq("scope_key", scope).maybeSingle();
    return data as ServingObject | null;
  };

  if (action === "vehicle-master") {
    const rows = await vehicles();
    return reply(req, 200, {
      release: release.version,
      vehicles: rows.map((v) => ({ id: v.r2_scope_key, vehicle_master_id: v.r2_scope_key, make: "Nissan", model_family: v.model, model_variant: `Nissan ${v.model}`, chassis_code: v.chassis_code, engine_code: v.engine_code, production_year: v.year_start, year_start: v.year_start, year_end: v.year_end, sales_region: v.sales_region, display_name: `Nissan ${v.model} · ${displayVehicle(v)}`, vin_prefix: null })),
      full_catalog_download_required: false,
    });
  }
  if (action === "staff-families") {
    if (!(await isStaff())) return reply(req, 403, { error: "staff access required" });
    const groups = new Map<string, Vehicle[]>();
    for (const v of await vehicles()) { const k = slug(v.model); groups.set(k, [...(groups.get(k) ?? []), v]); }
    const families = [...groups.entries()].map(([familySlug, vs]) => ({ family_id: familySlug, family_slug: familySlug, display_name: `Nissan ${vs[0].model}`, generation_label: null, body_type: null, year_start: Math.min(...vs.map((v) => v.year_start ?? 9999)), year_end: Math.max(...vs.map((v) => v.year_end ?? 0)), variant_count: vs.length })).sort((a, b) => a.display_name.localeCompare(b.display_name));
    return reply(req, 200, { release: release.version, families, full_catalog_download_required: false });
  }
  if (action === "staff-variants") {
    if (!(await isStaff())) return reply(req, 403, { error: "staff access required" });
    const familyId = slug(param("family_id"));
    if (!familyId) return reply(req, 400, { error: "family_id required" });
    const rows = (await vehicles()).filter((v) => slug(v.model) === familyId);
    if (!rows.length) return reply(req, 404, { error: "catalog family not found" });
    const variants = rows.map((v) => ({ variant_id: v.r2_scope_key, variant_slug: v.r2_scope_key, display_name: displayVehicle(v), chassis_code: v.chassis_code, engine_code: v.engine_code, year_start: v.year_start, year_end: v.year_end, body_style: null, drive_type: null, transmission_code: null, market: v.sales_region }));
    return reply(req, 200, { release: release.version, variants, full_catalog_download_required: false });
  }

  const r2 = await r2Config(admin);
  if (action === "health") {
    const counts = Object.fromEntries(await Promise.all(REQUIRED_FULL_KINDS.map(async (kind) => {
      const { count } = await admin.from("catalog_r2_serving_objects").select("id", { head: true, count: "exact" }).eq("release_id", release.id).eq("maker_slug", maker).eq("object_kind", kind);
      return [kind, count ?? 0];
    })));
    const missingCore = REQUIRED_CORE_KINDS.filter((kind) => Number(counts[kind] ?? 0) <= 0);
    const missingFull = REQUIRED_FULL_KINDS.filter((kind) => Number(counts[kind] ?? 0) <= 0);
    const vehicleCount = (await vehicles()).length;
    return reply(req, 200, { release: release.version, object_counts: counts, vehicle_master_count: vehicleCount, r2_serving_objects: Object.values(counts).reduce((sum, value) => sum + Number(value ?? 0), 0), missing_core_kinds: missingCore, missing_required_kinds: missingFull, r2_configured: Boolean(r2), catalog_data_ready: Boolean(r2) && vehicleCount > 0 && Number(counts.vehicle_search ?? 0) >= vehicleCount && missingCore.length === 0, live_browsing_ready: Boolean(r2) && vehicleCount > 0 && Number(counts.vehicle_search ?? 0) >= vehicleCount && missingFull.length === 0, diagram_images_ready: Number(counts.diagram_image ?? 0) > 0, full_catalog_download_required: false });
  }
  if (!r2) return reply(req, 503, { error: "R2 is not configured" });
  r2.bucket = release.bucket_name || r2.bucket;
  const load = async (object: ServingObject) => {
    const pages = Array.isArray(object.metadata?.pages) ? (object.metadata.pages as unknown[]).map(String) : [object.object_key];
    const rows = (await Promise.all(pages.map(async (key) => parseRows(await bodyText(r2, key))))).flat();
    return rows.length > 200_000 ? rows.slice(0, 200_000) : rows;
  };
  const vehicleRows = async (vehicleId: string) => {
    if (!(await vehicles()).some((v) => v.r2_scope_key === vehicleId)) throw new Error("vehicle is not present in the published Nissan master");
    const object = await serving("vehicle_search", vehicleId);
    if (!object) throw new Error("R2 vehicle serving shard not published");
    return await load(object);
  };
  const republish = (e: unknown) => reply(req, 409, { error: e instanceof Error ? e.message : String(e), status: "CATALOG_REPUBLISH_REQUIRED" });

  if (action === "staff-sections") {
    if (!(await isStaff())) return reply(req, 403, { error: "staff access required" });
    const variantId = (param("variant_id") ?? "").trim();
    if (!variantId) return reply(req, 400, { error: "variant_id required" });
    let rows: Row[];
    try { rows = await vehicleRows(variantId); } catch (e) { return republish(e); }
    const map = new Map<string, { section_id: string; section_slug: string; labels: Map<string, number>; diagrams: Set<string> }>();
    for (const row of rows) {
      const id = String(row.section_id ?? "").trim();
      if (!id) continue;
      const current = map.get(id) ?? { section_id: id, section_slug: String(row.section_slug ?? "").trim(), labels: new Map(), diagrams: new Set() };
      const label = String(row.subcategory_name ?? row.section_slug ?? id).trim();
      if (label) current.labels.set(label, (current.labels.get(label) ?? 0) + 1);
      const did = String(row.diagram_id ?? "").trim();
      if (did) current.diagrams.add(did);
      if (!current.section_slug && row.section_slug) current.section_slug = String(row.section_slug);
      map.set(id, current);
    }
    const sections = [...map.values()].map((s) => ({ section_id: s.section_id, section_slug: s.section_slug || s.section_id, display_name: topLabel(s.labels, s.section_slug || s.section_id), sort_order: 0, diagram_count: s.diagrams.size })).sort((a, b) => a.display_name.localeCompare(b.display_name));
    return reply(req, 200, { release: release.version, variant_id: variantId, sections, full_catalog_download_required: false });
  }
  if (action === "staff-diagrams") {
    if (!(await isStaff())) return reply(req, 403, { error: "staff access required" });
    const variantId = (param("variant_id") ?? param("variant_slug") ?? "").trim();
    const sectionId = (param("section_id") ?? "").trim();
    const sectionSlug = (param("section_slug") ?? "").trim();
    if (!variantId || (!sectionId && !sectionSlug)) return reply(req, 400, { error: "variant_id/variant_slug and section_id/section_slug required" });
    let rows: Row[];
    try { rows = await vehicleRows(variantId); } catch (e) { return republish(e); }
    rows = rows.filter((row) => sectionId ? String(row.section_id ?? "") === sectionId : String(row.section_slug ?? "") === sectionSlug);
    const map = new Map<string, { id: string; titles: Map<string, number>; count: number }>();
    for (const row of rows) {
      const id = String(row.diagram_id ?? "").trim();
      if (!id) continue;
      const current = map.get(id) ?? { id, titles: new Map(), count: 0 };
      const title = String(row.category_name ?? row.subcategory_name ?? id).trim();
      if (title) current.titles.set(title, (current.titles.get(title) ?? 0) + 1);
      current.count += 1;
      map.set(id, current);
    }
    const ids = [...map.keys()];
    const ready = new Set<string>();
    if (ids.length) {
      const { data: imageRows } = await admin.from("catalog_r2_serving_objects").select("scope_key").eq("release_id", release.id).eq("maker_slug", maker).eq("object_kind", "diagram_image").in("scope_key", ids);
      for (const row of imageRows ?? []) ready.add(String(row.scope_key));
    }
    const all = [...map.values()].map((d) => ({ diagram_id: d.id, title: topLabel(d.titles, d.id), name_en: topLabel(d.titles, d.id), diagram_kind: null, applicability: null, expected_part_count: d.count, expected_hotspot_count: null, image_ready: ready.has(d.id), image_sha256: null })).sort((a, b) => a.title.localeCompare(b.title));
    const limit = Math.max(1, Math.min(Number(param("limit") ?? "200") || 200, 500));
    const offset = Math.max(0, Number(param("offset") ?? "0") || 0);
    return reply(req, 200, { release: release.version, diagrams: all.slice(offset, offset + limit), total: all.length, full_catalog_download_required: false });
  }

  const commerce = async (rows: Row[], limit: number, vehicleId: string | null) => {
    const byPart = new Map<string, Row>();
    for (const row of rows) { const key = normalizePart(row.normalized_oem_number ?? row.display_oem_number); if (key && !byPart.has(key)) byPart.set(key, row); }
    const keys = [...byPart.keys()].slice(0, 1200);
    if (!keys.length) return [];
    const { data, error } = await admin.rpc("catalog_commerce_stock_for_oems", { p_oems: keys });
    if (error) throw new Error("commerce lookup failed");
    const result = [];
    for (const stock of data ?? []) {
      const epc = byPart.get(String(stock.normalized_oem ?? "")); if (!epc) continue;
      const qty = Number(stock.saleable_qty ?? 0); const reorder = stock.reorder_point == null ? null : Number(stock.reorder_point);
      result.push({ stock_item_id: stock.stock_item_id, name: stock.description || epc.name || epc.description || "Nissan part", category: epc.category_name ?? null, subcategory: epc.subcategory_name ?? null, stock: { state: qty <= 0 ? "backorder" : reorder != null && qty <= reorder ? "low" : "in_stock", qty }, price: stock.unit_price == null ? null : { amount: Number(stock.unit_price), currency: String(stock.currency ?? "USD") }, fitment_status: vehicleId ? "VERIFIED_FIT" : "FITMENT_UNRESOLVED", vehicle_id: vehicleId, internal_catalog_ref: epc.display_oem_number ?? epc.normalized_oem_number ?? null });
      if (result.length >= limit) break;
    }
    return result;
  };
  if (action === "customer-stock" || action === "customer-search") {
    const vehicleId = (param("vehicle_id") ?? "").trim();
    if (!vehicleId) return reply(req, 400, { error: "vehicle_id required" });
    let rows: Row[];
    try { rows = await vehicleRows(vehicleId); } catch (e) { return republish(e); }
    const category = normalizeCategory(param("category"));
    if (category) rows = rows.filter((row) => [row.category_name, row.subcategory_name].filter(Boolean).some((value) => normalizeCategory(value).includes(category)));
    if (action === "customer-search") { const q = (param("q") ?? "").trim(); if (!q) return reply(req, 400, { error: "q required" }); rows = rows.filter((row) => queryMatch(row, q)); }
    const limit = Math.max(1, Math.min(Number(param("limit") ?? "100") || 100, 100));
    try {
      return reply(req, 200, { source: "r2_epc+supabase_commerce", release: release.version, vehicle_id: vehicleId, results: await commerce(rows, limit, vehicleId), full_catalog_download_required: false });
    } catch {
      return reply(req, 503, { error: "catalog commerce lookup unavailable" });
    }
  }
  if (action === "fitment-check") {
    const vehicleId = (param("vehicle_id") ?? "").trim(); const oem = normalizePart(param("oem"));
    if (!vehicleId || !oem) return reply(req, 400, { error: "vehicle_id and oem required" });
    const object = await serving("vehicle_fitment", vehicleId);
    if (!object) return reply(req, 200, { source: "r2_epc", fitment: { status: "FITMENT_UNRESOLVED" } });
    const rows = await load(object); const hit = rows.find((row) => normalizePart(row.normalized_oem_number ?? row.display_oem_number) === oem);
    if (hit) return reply(req, 200, { source: "r2_epc", fitment: { status: "VERIFIED_FIT", release: release.version, section_id: hit.section_id ?? null, diagram_id: hit.diagram_id ?? null } });
    return reply(req, 200, { source: "r2_epc", fitment: { status: object.metadata?.complete === false ? "FITMENT_UNRESOLVED" : "VERIFIED_NOT_FIT", release: release.version } });
  }
  if (action === "staff-section-parts" || action === "staff-diagram-parts") {
    if (!(await isStaff())) return reply(req, 403, { error: "staff access required" });
    const diagram = action === "staff-diagram-parts"; const scope = (param(diagram ? "diagram_id" : "section_id") ?? "").trim();
    if (!scope) return reply(req, 400, { error: `${diagram ? "diagram_id" : "section_id"} required` });
    const object = await serving(diagram ? "diagram_parts" : "section_parts", scope);
    if (!object) return reply(req, 409, { error: "R2 serving shard not published", status: "CATALOG_REPUBLISH_REQUIRED", full_catalog_download_required: false });
    let parts = await load(object);
    if (diagram && object.metadata?.filter === "diagram_id") parts = parts.filter((row) => String(row.diagram_id ?? "") === scope);
    return reply(req, 200, { source: "r2_live", release: release.version, row_count: parts.length, parts, full_catalog_download_required: false });
  }
  if (action === "diagram-image") {
    if (!(await isStaff())) return reply(req, 403, { error: "staff access required" });
    const diagramId = (param("diagram_id") ?? "").trim();
    if (!diagramId) return reply(req, 400, { error: "diagram_id required" });
    const object = await serving("diagram_image", diagramId);
    if (!object) return reply(req, 409, { error: "authoritative diagram-to-R2 mapping is not published for this diagram", status: "CATALOG_REPUBLISH_REQUIRED", diagram_id: diagramId, full_catalog_download_required: false });
    const signed = await getSignedUrl(r2.client, new GetObjectCommand({ Bucket: r2.bucket, Key: object.object_key }), { expiresIn: 600 });
    return reply(req, 200, { signed_url: signed, expires_in: 600, diagram_id: diagramId, sha256: object.sha256, object_key: object.object_key, full_catalog_download_required: false });
  }
  // POS offline bundle (data-pipeline/scripts/build_pos_offline_bundle.py): the published manifest
  // with a signed link per file, so a till can download the whole catalogue for offline use.
  if (action === "offline-bundle") {
    if (!(await isStaff())) return reply(req, 403, { error: "staff access required" });
    const prefix = `bundles/${maker}/${release.version}/pos-offline`;
    let manifest: { build?: string; files?: Array<{ path: string }> } & Record<string, unknown>;
    try {
      manifest = JSON.parse(await bodyText(r2, `${prefix}/manifest.json`));
    } catch {
      return reply(req, 404, { error: "offline bundle is not published for this release", status: "OFFLINE_BUNDLE_NOT_PUBLISHED", release: release.version });
    }
    if (!manifest.build || !Array.isArray(manifest.files)) return reply(req, 409, { error: "offline bundle manifest is invalid", status: "CATALOG_REPUBLISH_REQUIRED" });
    const expiresIn = 6 * 60 * 60;
    const files = await Promise.all(manifest.files.map(async (f) => ({
      ...f,
      url: await getSignedUrl(r2.client, new GetObjectCommand({ Bucket: r2.bucket, Key: `${prefix}/${manifest.build}/${f.path}` }), { expiresIn }),
    })));
    return reply(req, 200, { ...manifest, files, expires_in: expiresIn });
  }
  return reply(req, 404, { error: "unknown action", actions: ["health", "vehicle-master", "customer-stock", "customer-search", "fitment-check", "staff-families", "staff-variants", "staff-sections", "staff-diagrams", "staff-section-parts", "staff-diagram-parts", "diagram-image", "offline-bundle"] });
});
