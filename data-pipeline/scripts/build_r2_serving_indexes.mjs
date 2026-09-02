#!/usr/bin/env node
/**
 * Build immutable, gzip-compressed R2 serving shards from normalized EPC applicability NDJSON.
 *
 * This does NOT upload to R2 and does NOT import millions of part rows into Supabase.
 * It emits:
 *   <out>/objects/.../*.ndjson.gz
 *   <out>/serving-manifest.ndjson
 *
 * The normal catalog publisher uploads `objects/` to the current R2 bucket and then registers
 * `serving-manifest.ndjson` through register_r2_serving_manifest.mjs.
 *
 * Required input fields per row:
 *   vehicle_master_id
 *   normalized_oem_number OR display_oem_number/oem_part_number
 * Recommended:
 *   name, description, category_name, subcategory_name, pnc_code,
 *   section_id, section_slug, diagram_id, aliases, chassis_code, engine_code,
 *   applicability, source_assertion_id, source_hash
 *
 * Usage:
 *   node data-pipeline/scripts/build_r2_serving_indexes.mjs normalized.ndjson \
 *     --out=out/r2-serving --maker=nissan --version=v2-storage-2026-09
 */
import { createReadStream, createWriteStream, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { createInterface } from "node:readline";
import { createHash } from "node:crypto";
import { dirname, join } from "node:path";
import { gzipSync } from "node:zlib";

const args = process.argv.slice(2);
const input = args.find((a) => !a.startsWith("--"));
const opt = (name, fallback = "") => args.find((a) => a.startsWith(`--${name}=`))?.split("=").slice(1).join("=") || fallback;
const outDir = opt("out", "out/r2-serving");
const maker = opt("maker", "nissan").trim().toLowerCase();
const version = opt("version", "unversioned").trim();
const bucketCount = Math.max(16, Math.min(Number(opt("buckets", "64")) || 64, 256));
if (!input) {
  console.error("Usage: build_r2_serving_indexes.mjs <normalized.ndjson> [--out=...] [--maker=nissan] [--version=...] [--buckets=64]");
  process.exit(2);
}

const stage = join(outDir, ".stage");
rmSync(stage, { recursive: true, force: true });
mkdirSync(stage, { recursive: true });
mkdirSync(join(outDir, "objects"), { recursive: true });

function clean(v) { return v == null ? "" : String(v).trim(); }
function normPart(v) { return clean(v).toUpperCase().replace(/[^A-Z0-9]/g, ""); }
function hash(v) { return createHash("sha256").update(String(v)).digest("hex"); }
function bucketFor(v) { return parseInt(hash(v).slice(0, 8), 16) % bucketCount; }
function bucketPath(kind, n) {
  const p = join(stage, kind, `${String(n).padStart(3, "0")}.ndjson`);
  mkdirSync(dirname(p), { recursive: true });
  return p;
}
function appendBucket(kind, scope, row) {
  const path = bucketPath(kind, bucketFor(scope));
  const payload = JSON.stringify({ scope, row });
  append(path, payload + "\n");
}
const open = new Map();
function append(path, text) {
  let s = open.get(path);
  if (!s) {
    if (open.size >= 96) {
      const [k, old] = open.entries().next().value;
      old.end();
      open.delete(k);
    }
    s = createWriteStream(path, { flags: "a", encoding: "utf8" });
    open.set(path, s);
  }
  s.write(text);
}
async function closeWriters() {
  await Promise.all([...open.values()].map((s) => new Promise((resolve) => s.end(resolve))));
  open.clear();
}

function customerRow(raw) {
  const display = clean(raw.display_oem_number || raw.oem_part_number || raw.normalized_oem_number);
  return {
    normalized_oem_number: normPart(raw.normalized_oem_number || display),
    display_oem_number: display,
    name: clean(raw.name || raw.description) || null,
    description: clean(raw.description || raw.name) || null,
    category_name: clean(raw.category_name) || null,
    subcategory_name: clean(raw.subcategory_name) || null,
    pnc_code: clean(raw.pnc_code) || null,
    section_id: clean(raw.section_id) || null,
    section_slug: clean(raw.section_slug) || null,
    diagram_id: clean(raw.diagram_id) || null,
    aliases: Array.isArray(raw.aliases) ? raw.aliases.map(clean).filter(Boolean) : [],
    search_text: [raw.name, raw.description, raw.category_name, raw.subcategory_name, raw.pnc_code, display, ...(Array.isArray(raw.aliases) ? raw.aliases : [])]
      .map(clean).filter(Boolean).join(" ").toLowerCase(),
  };
}
function staffRow(raw) {
  return {
    ...customerRow(raw),
    vehicle_master_id: clean(raw.vehicle_master_id) || null,
    chassis_code: clean(raw.chassis_code) || null,
    engine_code: clean(raw.engine_code) || null,
    applicability: raw.applicability ?? null,
    source_assertion_id: clean(raw.source_assertion_id) || null,
    source_hash: clean(raw.source_hash) || null,
  };
}

console.log(`partitioning ${input} into ${bucketCount} deterministic buckets…`);
const rl = createInterface({ input: createReadStream(input), crlfDelay: Infinity });
let lineNo = 0;
let accepted = 0;
for await (const line of rl) {
  lineNo += 1;
  const text = line.trim();
  if (!text || text.startsWith("#")) continue;
  let raw;
  try { raw = JSON.parse(text); } catch (e) { throw new Error(`line ${lineNo}: ${e.message}`); }
  const vehicleId = clean(raw.vehicle_master_id || raw.vehicle_id);
  const c = customerRow(raw);
  if (!vehicleId) throw new Error(`line ${lineNo}: vehicle_master_id required`);
  if (!c.normalized_oem_number) throw new Error(`line ${lineNo}: part identity required`);
  appendBucket("vehicle", vehicleId, c);
  const sectionId = clean(raw.section_id);
  if (sectionId) appendBucket("section", sectionId, staffRow(raw));
  const diagramId = clean(raw.diagram_id);
  if (diagramId) appendBucket("diagram", diagramId, staffRow(raw));
  accepted += 1;
  if (accepted % 250000 === 0) console.log(`  ${accepted.toLocaleString()} assertions partitioned`);
}
await closeWriters();
console.log(`partitioned ${accepted.toLocaleString()} applicability assertions`);

const manifest = [];
function objectKey(kind, scope) {
  const digest = hash(scope);
  return `serving/${maker}/${version}/${kind}/${digest.slice(0, 2)}/${digest.slice(0, 32)}.ndjson.gz`;
}
function writeObject(kind, scope, rows, manifestKinds) {
  const dedupe = new Map();
  for (const row of rows) {
    const k = [row.normalized_oem_number, row.pnc_code, row.diagram_id, row.vehicle_master_id].map((x) => x ?? "").join("|");
    if (!dedupe.has(k)) dedupe.set(k, row);
  }
  const unique = [...dedupe.values()];
  const ndjson = unique.map((r) => JSON.stringify(r)).join("\n") + (unique.length ? "\n" : "");
  const compressed = gzipSync(Buffer.from(ndjson), { level: 9 });
  if (compressed.byteLength > 16 * 1024 * 1024) {
    throw new Error(`${kind}:${scope} is ${compressed.byteLength} compressed bytes; split this scope before publishing`);
  }
  const key = objectKey(kind, scope);
  const file = join(outDir, "objects", key);
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, compressed);
  const sha256 = createHash("sha256").update(compressed).digest("hex");
  for (const objectKind of manifestKinds) {
    manifest.push({
      maker_slug: maker,
      object_kind: objectKind,
      scope_key: scope,
      object_key: key,
      sha256,
      row_count: unique.length,
      bytes: compressed.byteLength,
      content_type: "application/x-ndjson",
      content_encoding: "gzip",
      metadata: { complete: true, source: "normalized_epc", version },
    });
  }
}

async function processKind(stageKind, objectKind, manifestKinds) {
  for (let i = 0; i < bucketCount; i += 1) {
    const path = bucketPath(stageKind, i);
    let data;
    try { data = readFileSync(path, "utf8"); } catch { continue; }
    const groups = new Map();
    for (const line of data.split(/\r?\n/)) {
      if (!line.trim()) continue;
      const { scope, row } = JSON.parse(line);
      if (!groups.has(scope)) groups.set(scope, []);
      groups.get(scope).push(row);
    }
    for (const [scope, rows] of groups) writeObject(objectKind, scope, rows, manifestKinds);
    if (groups.size) console.log(`  ${stageKind} bucket ${i + 1}/${bucketCount}: ${groups.size} shards`);
  }
}

console.log("building vehicle search/fitment shards…");
await processKind("vehicle", "vehicle", ["vehicle_search", "vehicle_fitment"]);
console.log("building staff section shards…");
await processKind("section", "section-parts", ["section_parts"]);
console.log("building staff diagram shards…");
await processKind("diagram", "diagram-parts", ["diagram_parts"]);

const manifestPath = join(outDir, "serving-manifest.ndjson");
writeFileSync(manifestPath, manifest.map((r) => JSON.stringify(r)).join("\n") + "\n");
rmSync(stage, { recursive: true, force: true });
console.log(`built ${manifest.length.toLocaleString()} manifest registrations`);
console.log(`manifest: ${manifestPath}`);
console.log(`objects root: ${join(outDir, "objects")}`);
