import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { GetObjectCommand, S3Client } from "npm:@aws-sdk/client-s3@3";
import { getSignedUrl } from "npm:@aws-sdk/s3-request-presigner@3";

const MAX_BYTES = 16 * 1024 * 1024;
const REQUIRED_SERVING_KINDS = [
  "vehicle_search",
  "vehicle_fitment",
  "section_parts",
  "diagram_parts",
  "diagram_image",
] as const;
const ORIGINS = new Set([
  "https://nissangtrauto.co.zw",
  "https://www.nissangtrauto.co.zw",
  "http://localhost:3000",
  "http://127.0.0.1:3000",
]);

type Row = Record<string, unknown>;
type ServingObject = {
  object_key: string;
  sha256: string | null;
  row_count: number;
  bytes: number;
  content_encoding: string | null;
  metadata: Record<string, unknown> | null;
};

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
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors(req), "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

function normalizePart(value: unknown) {
  return String(value ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
}

function normalizeCategory(value: unknown) {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function r2Config() {
  const accountId = Deno.env.get("CLOUDFLARE_ACCOUNT_ID")?.trim() ?? "";
  const accessKeyId = Deno.env.get("CLOUDFLARE_R2_ACCESS_KEY_ID")?.trim() ?? "";
  const secretAccessKey = Deno.env.get("CLOUDFLARE_R2_SECRET_ACCESS_KEY")?.trim() ?? "";
  const bucket = Deno.env.get("CLOUDFLARE_R2_BUCKET")?.trim() ?? "";
  if (!accountId || !accessKeyId || !secretAccessKey || !bucket) return null;
  return {
    bucket,
    client: new S3Client({
      region: "auto",
      endpoint: Deno.env.get("CLOUDFLARE_R2_ENDPOINT")?.trim() || `https://${accountId}.r2.cloudflarestorage.com`,
      credentials: { accessKeyId, secretAccessKey },
    }),
  };
}

async function bodyText(config: NonNullable<ReturnType<typeof r2Config>>, key: string, gzip: boolean) {
  const out = await config.client.send(new GetObjectCommand({ Bucket: config.bucket, Key: key }));
  if (!out.Body) throw new Error("R2 object body missing");
  const bytes = await out.Body.transformToByteArray();
  if (bytes.byteLength > MAX_BYTES) throw new Error("R2 serving shard too large");
  if (!gzip) return new TextDecoder().decode(bytes);
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"));
  return await new Response(stream).text();
}

function parseRows(text: string): Row[] {
  const t = text.trim();
  if (!t) return [];
  if (t.startsWith("[")) {
    const parsed = JSON.parse(t);
    return Array.isArray(parsed) ? parsed : [];
  }
  return t.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line) as Row);
}

function searchable(row: Row) {
  return [
    row.search_text,
    row.name,
    row.description,
    row.category_name,
    row.subcategory_name,
    row.pnc_code,
    row.display_oem_number,
    ...(Array.isArray(row.aliases) ? row.aliases : []),
  ].filter(Boolean).join(" ").toLowerCase();
}

function queryMatch(row: Row, q: string) {
  const hay = searchable(row);
  return q.toLowerCase().split(/\s+/).filter(Boolean).every((term) => hay.includes(term));
}

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
  const { data: userData, error: userError } = await admin.auth.getUser(token);
  const userId = userData.user?.id;
  if (userError || !userId) return reply(req, 401, { error: "invalid session" });

  const url = new URL(req.url);
  const input = req.method === "POST" ? await req.json().catch(() => ({})) as Record<string, unknown> : {};
  const param = (key: string) => input[key] == null ? url.searchParams.get(key) : String(input[key]);
  const action = String(input.action ?? url.pathname.split("/").filter(Boolean).at(-1) ?? "").trim();
  const maker = (param("maker") ?? "nissan").trim().toLowerCase();

  const { data: release } = await admin
    .from("catalog_releases")
    .select("id,version,bucket_name,published_at")
    .eq("maker_slug", maker)
    .eq("is_current", true)
    .not("published_at", "is", null)
    .maybeSingle();
  if (!release?.id) return reply(req, 503, { error: "no current catalog release" });

  const r2 = r2Config();
  if (!r2) return reply(req, 503, { error: "R2 is not configured" });
  r2.bucket = release.bucket_name || r2.bucket;

  const serving = async (kind: string, scope: string): Promise<ServingObject | null> => {
    const { data } = await admin
      .from("catalog_r2_serving_objects")
      .select("object_key,sha256,row_count,bytes,content_encoding,metadata")
      .eq("release_id", release.id)
      .eq("maker_slug", maker)
      .eq("object_kind", kind)
      .eq("scope_key", scope)
      .maybeSingle();
    return data as ServingObject | null;
  };

  const load = async (object: ServingObject) => parseRows(
    await bodyText(r2, object.object_key, object.content_encoding === "gzip" || object.object_key.endsWith(".gz")),
  );

  const isStaff = async () => {
    const { data } = await admin.from("profiles").select("is_staff").eq("id", userId).maybeSingle();
    return data?.is_staff === true;
  };

  if (action === "health") {
    const counts = Object.fromEntries(await Promise.all(REQUIRED_SERVING_KINDS.map(async (kind) => {
      const { count, error } = await admin
        .from("catalog_r2_serving_objects")
        .select("id", { head: true, count: "exact" })
        .eq("release_id", release.id)
        .eq("maker_slug", maker)
        .eq("object_kind", kind);
      if (error) throw new Error(error.message);
      return [kind, count ?? 0];
    })));
    const missing = REQUIRED_SERVING_KINDS.filter((kind) => Number(counts[kind] ?? 0) <= 0);
    const total = Object.values(counts).reduce((sum, value) => sum + Number(value ?? 0), 0);
    return reply(req, 200, {
      release: release.version,
      object_counts: counts,
      r2_serving_objects: total,
      missing_required_kinds: missing,
      live_browsing_ready: missing.length === 0,
      full_catalog_download_required: false,
    });
  }

  const commerce = async (rows: Row[], limit: number, vehicleId: string | null) => {
    const byPart = new Map<string, Row>();
    for (const row of rows) {
      const key = normalizePart(row.normalized_oem_number ?? row.display_oem_number);
      if (key && !byPart.has(key)) byPart.set(key, row);
    }
    const keys = [...byPart.keys()].slice(0, 1200);
    const { data, error } = await admin.rpc("catalog_commerce_stock_for_oems", { p_oems: keys });
    if (error) throw new Error(error.message);
    const result = [];
    for (const stock of data ?? []) {
      const epc = byPart.get(String(stock.normalized_oem ?? ""));
      if (!epc) continue;
      const qty = Number(stock.saleable_qty ?? 0);
      const reorder = stock.reorder_point == null ? null : Number(stock.reorder_point);
      result.push({
        stock_item_id: stock.stock_item_id,
        name: stock.description || epc.name || epc.description || "Nissan part",
        category: epc.category_name ?? null,
        subcategory: epc.subcategory_name ?? null,
        stock: { state: qty <= 0 ? "backorder" : reorder != null && qty <= reorder ? "low" : "in_stock", qty },
        price: stock.unit_price == null ? null : { amount: Number(stock.unit_price), currency: String(stock.currency ?? "USD") },
        fitment_status: vehicleId ? "VERIFIED_FIT" : "FITMENT_UNRESOLVED",
        vehicle_id: vehicleId,
        internal_catalog_ref: epc.display_oem_number ?? epc.normalized_oem_number ?? null,
      });
      if (result.length >= limit) break;
    }
    return result;
  };

  if (action === "customer-stock" || action === "customer-search") {
    const vehicleId = (param("vehicle_id") ?? "").trim();
    if (!vehicleId) return reply(req, 400, { error: "vehicle_id required" });
    const object = await serving("vehicle_search", vehicleId);
    if (!object) return reply(req, 409, { error: "R2 vehicle serving shard not published", status: "CATALOG_REPUBLISH_REQUIRED" });
    let rows = await load(object);
    const category = normalizeCategory(param("category"));
    if (category) {
      rows = rows.filter((row) => [row.category_name, row.subcategory_name]
        .filter(Boolean)
        .some((value) => normalizeCategory(value).includes(category)));
    }
    if (action === "customer-search") {
      const q = (param("q") ?? "").trim();
      if (!q) return reply(req, 400, { error: "q required" });
      rows = rows.filter((row) => queryMatch(row, q));
    }
    const limit = Math.max(1, Math.min(Number(param("limit") ?? "100") || 100, 100));
    const results = await commerce(rows, limit, vehicleId);
    return reply(req, 200, { source: "r2_epc+supabase_commerce", release: release.version, vehicle_id: vehicleId, results, full_catalog_download_required: false });
  }

  if (action === "fitment-check") {
    const vehicleId = (param("vehicle_id") ?? "").trim();
    const oem = normalizePart(param("oem"));
    if (!vehicleId || !oem) return reply(req, 400, { error: "vehicle_id and oem required" });
    const object = await serving("vehicle_fitment", vehicleId);
    if (!object) return reply(req, 200, { source: "r2_epc", fitment: { status: "FITMENT_UNRESOLVED" } });
    const rows = await load(object);
    const hit = rows.find((row) => normalizePart(row.normalized_oem_number ?? row.display_oem_number) === oem);
    if (hit) return reply(req, 200, { source: "r2_epc", fitment: { status: "VERIFIED_FIT", release: release.version, section_id: hit.section_id ?? null, diagram_id: hit.diagram_id ?? null } });
    return reply(req, 200, { source: "r2_epc", fitment: { status: object.metadata?.complete === false ? "FITMENT_UNRESOLVED" : "VERIFIED_NOT_FIT", release: release.version } });
  }

  if (action === "staff-section-parts" || action === "staff-diagram-parts") {
    if (!(await isStaff())) return reply(req, 403, { error: "staff access required" });
    const diagram = action === "staff-diagram-parts";
    const scope = (param(diagram ? "diagram_id" : "section_id") ?? "").trim();
    if (!scope) return reply(req, 400, { error: `${diagram ? "diagram_id" : "section_id"} required` });
    const object = await serving(diagram ? "diagram_parts" : "section_parts", scope);
    if (!object) return reply(req, 409, { error: "R2 serving shard not published", status: "CATALOG_REPUBLISH_REQUIRED", full_catalog_download_required: false });
    const parts = await load(object);
    return reply(req, 200, { source: "r2_live", release: release.version, row_count: parts.length, parts, full_catalog_download_required: false });
  }

  if (action === "diagram-image") {
    if (!(await isStaff())) return reply(req, 403, { error: "staff access required" });
    const diagramId = (param("diagram_id") ?? "").trim();
    if (!diagramId) return reply(req, 400, { error: "diagram_id required" });
    const object = await serving("diagram_image", diagramId);
    if (!object) {
      return reply(req, 409, {
        error: "authoritative diagram-to-R2 mapping is not published for this diagram",
        status: "CATALOG_REPUBLISH_REQUIRED",
        diagram_id: diagramId,
        full_catalog_download_required: false,
      });
    }
    const signed = await getSignedUrl(
      r2.client,
      new GetObjectCommand({ Bucket: r2.bucket, Key: object.object_key }),
      { expiresIn: 600 },
    );
    return reply(req, 200, {
      signed_url: signed,
      expires_in: 600,
      diagram_id: diagramId,
      sha256: object.sha256,
      object_key: object.object_key,
      full_catalog_download_required: false,
    });
  }

  return reply(req, 404, { error: "unknown action", actions: ["health", "customer-stock", "customer-search", "fitment-check", "staff-section-parts", "staff-diagram-parts", "diagram-image"] });
});
