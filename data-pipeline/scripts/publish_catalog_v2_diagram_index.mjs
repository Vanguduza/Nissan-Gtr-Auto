#!/usr/bin/env node
/**
 * Publish catalog_v2 diagram-id -> Cloudflare R2 object mappings.
 *
 * This runs in the catalog release pipeline. It consumes the compact diagram manifest produced
 * while R2 images are uploaded; it never downloads image bytes and never downloads the full EPC
 * bundle. Staff EPC browsing then asks the control plane for a diagram id and receives a short-
 * lived signed R2 URL from `catalog-v2-serve`.
 *
 * NDJSON row contract:
 * {
 *   "diagram_id": "...catalog_v2 diagram id...",
 *   "release_id": "CAT-...",
 *   "maker_slug": "nissan",
 *   "sha256": "64 lowercase hex",
 *   "r2_key": "diagrams/nissan/ab/<sha>.png",
 *   "bytes": 123456,
 *   "width": 1200,
 *   "height": 900,
 *   "content_type": "image/png",
 *   "source_hash": "optional"
 * }
 *
 * Usage:
 *   SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... \
 *   node data-pipeline/scripts/publish_catalog_v2_diagram_index.mjs ./out/diagram-r2-index.ndjson
 */
import { createReadStream } from "node:fs";
import { createInterface } from "node:readline";

const args = process.argv.slice(2);
const input = args.find((a) => !a.startsWith("--"));
const batchArg = args.find((a) => a.startsWith("--batch="));
const dryRun = args.includes("--dry-run");
const batchSize = Math.max(50, Math.min(Number(batchArg?.split("=")[1] ?? 500) || 500, 1000));

if (!input) {
  console.error("Usage: publish_catalog_v2_diagram_index.mjs <diagram-index.ndjson> [--batch=500] [--dry-run]");
  process.exit(2);
}
const supabaseUrl = process.env.SUPABASE_URL?.replace(/\/$/, "");
const serviceRole = process.env.SUPABASE_SERVICE_ROLE_KEY;
if (!supabaseUrl || !serviceRole) {
  console.error("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required");
  process.exit(2);
}
const headers = {
  apikey: serviceRole,
  Authorization: `Bearer ${serviceRole}`,
  "Content-Type": "application/json",
};

function clean(value) {
  return value == null ? "" : String(value).trim();
}
function normalize(raw, lineNo) {
  const diagramId = clean(raw.diagram_id);
  const releaseId = clean(raw.release_id);
  const sha256 = clean(raw.sha256).toLowerCase();
  const r2Key = clean(raw.r2_key);
  if (!diagramId) throw new Error(`line ${lineNo}: diagram_id required`);
  if (!releaseId) throw new Error(`line ${lineNo}: release_id required`);
  if (!/^[0-9a-f]{64}$/.test(sha256)) throw new Error(`line ${lineNo}: sha256 must be 64 hex chars`);
  if (!r2Key) throw new Error(`line ${lineNo}: r2_key required`);
  return {
    diagram_id: diagramId,
    release_id: releaseId,
    maker_slug: clean(raw.maker_slug) || "nissan",
    sha256,
    r2_key: r2Key,
    bytes: raw.bytes == null ? null : Number(raw.bytes),
    width: raw.width == null ? null : Number(raw.width),
    height: raw.height == null ? null : Number(raw.height),
    content_type: clean(raw.content_type) || "image/png",
    source_hash: clean(raw.source_hash) || null,
  };
}

async function rpc(name, body) {
  const resp = await fetch(`${supabaseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
  const text = await resp.text();
  if (!resp.ok) throw new Error(`${name} ${resp.status}: ${text.slice(0, 1000)}`);
  return text ? JSON.parse(text) : null;
}

async function publish(rows, n) {
  if (dryRun) {
    console.log(`[dry-run] batch ${n}: ${rows.length}`);
    return rows.length;
  }
  const changed = await rpc("catalog_v2_ingest_diagram_image_index_batch", { p_rows: rows });
  console.log(`batch ${n}: ${rows.length} input, ${changed ?? 0} inserted/updated`);
  return Number(changed ?? 0);
}

const rl = createInterface({ input: createReadStream(input), crlfDelay: Infinity });
let batch = [];
let lineNo = 0;
let batchNo = 0;
let accepted = 0;
let changed = 0;
let release = null;
for await (const line of rl) {
  lineNo += 1;
  const text = line.trim();
  if (!text || text.startsWith("#")) continue;
  let raw;
  try { raw = JSON.parse(text); } catch (e) { throw new Error(`line ${lineNo}: invalid JSON: ${e.message}`); }
  const row = normalize(raw, lineNo);
  if (release && release !== row.release_id) throw new Error(`line ${lineNo}: mixed releases are not allowed`);
  release = row.release_id;
  batch.push(row);
  accepted += 1;
  if (batch.length >= batchSize) {
    batchNo += 1;
    changed += await publish(batch, batchNo);
    batch = [];
  }
}
if (batch.length) {
  batchNo += 1;
  changed += await publish(batch, batchNo);
}
console.log(`accepted ${accepted} diagram mappings for ${release ?? "(none)"}; inserted/updated ${changed}`);
