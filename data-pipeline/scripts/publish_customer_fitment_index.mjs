#!/usr/bin/env node
/**
 * Publish the compact online customer-fitment index from normalized EPC build output.
 *
 * This script is intentionally NOT a catalog downloader. It runs in the catalog publishing
 * pipeline, after EPC normalization and before/alongside R2 bundle publication. Customer clients
 * never execute this script and never receive the 6+ GB catalog bundle.
 *
 * Input: newline-delimited JSON (NDJSON), one normalized applicability assertion per line.
 * Required per row:
 *   release_id
 *   normalized_oem_number OR display_oem_number
 * Strongly recommended:
 *   maker_id, family_id, variant_id, chassis_id, chassis_code, engine_code,
 *   pnc_code, diagram_id, source_assertion_id, source_hash, confidence, source_kind
 *
 * Usage:
 *   SUPABASE_URL=https://... \
 *   SUPABASE_SERVICE_ROLE_KEY=... \
 *   node data-pipeline/scripts/publish_customer_fitment_index.mjs ./out/customer-fitment.ndjson
 *
 * Options:
 *   --batch=500
 *   --truncate-release=CAT-...
 *   --dry-run
 */
import { createReadStream } from "node:fs";
import { createInterface } from "node:readline";

const args = process.argv.slice(2);
const input = args.find((a) => !a.startsWith("--"));
const batchArg = args.find((a) => a.startsWith("--batch="));
const truncateArg = args.find((a) => a.startsWith("--truncate-release="));
const dryRun = args.includes("--dry-run");
const batchSize = Math.max(50, Math.min(Number(batchArg?.split("=")[1] ?? 500) || 500, 1000));
const truncateRelease = truncateArg?.split("=")[1]?.trim() || null;

if (!input) {
  console.error("Usage: publish_customer_fitment_index.mjs <input.ndjson> [--batch=500] [--truncate-release=CAT-...] [--dry-run]");
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

function normalizePart(value) {
  return String(value ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
}

function clean(value) {
  return value == null ? "" : String(value).trim();
}

function normalizeRow(raw, lineNo) {
  const releaseId = clean(raw.release_id);
  const display = clean(raw.display_oem_number || raw.oem_part_number);
  const normalized = normalizePart(raw.normalized_oem_number || display);
  if (!releaseId) throw new Error(`line ${lineNo}: release_id is required`);
  if (!normalized) throw new Error(`line ${lineNo}: OEM/part identity is required`);

  return {
    release_id: releaseId,
    normalized_oem_number: normalized,
    display_oem_number: display || normalized,
    maker_id: clean(raw.maker_id),
    family_id: clean(raw.family_id),
    variant_id: clean(raw.variant_id),
    chassis_id: clean(raw.chassis_id),
    chassis_code: clean(raw.chassis_code),
    engine_code: clean(raw.engine_code),
    pnc_code: clean(raw.pnc_code),
    diagram_id: clean(raw.diagram_id),
    source_assertion_id: clean(raw.source_assertion_id),
    source_hash: clean(raw.source_hash) || null,
    confidence: Number.isFinite(Number(raw.confidence)) ? Math.max(0, Math.min(Number(raw.confidence), 1)) : 1,
    source_kind: clean(raw.source_kind) || "epc",
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

async function deleteRelease(releaseId) {
  const url = `${supabaseUrl}/rest/v1/catalog_v2.customer_part_fitment_index?release_id=eq.${encodeURIComponent(releaseId)}`;
  const resp = await fetch(url, {
    method: "DELETE",
    headers: { ...headers, Prefer: "return=minimal" },
  });
  if (!resp.ok) throw new Error(`release truncate failed ${resp.status}: ${(await resp.text()).slice(0, 1000)}`);
}

async function publishBatch(rows, batchNo) {
  if (dryRun) {
    console.log(`[dry-run] batch ${batchNo}: ${rows.length} rows`);
    return rows.length;
  }
  const changed = await rpc("catalog_v2_ingest_customer_part_fitment_batch", { p_rows: rows });
  console.log(`batch ${batchNo}: ${rows.length} input, ${changed ?? 0} inserted/updated`);
  return Number(changed ?? 0);
}

if (truncateRelease) {
  if (dryRun) console.log(`[dry-run] would clear release ${truncateRelease}`);
  else {
    // The raw catalog_v2 table is intentionally service-role-only. If PostgREST schema exposure
    // does not include catalog_v2, omit this option and publish idempotently via the upsert RPC.
    try {
      await deleteRelease(truncateRelease);
      console.log(`cleared existing customer fitment rows for ${truncateRelease}`);
    } catch (error) {
      console.warn(`truncate skipped: ${error.message}`);
    }
  }
}

const rl = createInterface({ input: createReadStream(input), crlfDelay: Infinity });
let batch = [];
let lineNo = 0;
let batchNo = 0;
let accepted = 0;
let changed = 0;
let releaseSeen = null;

for await (const line of rl) {
  lineNo += 1;
  const text = line.trim();
  if (!text || text.startsWith("#")) continue;
  let raw;
  try {
    raw = JSON.parse(text);
  } catch (error) {
    throw new Error(`line ${lineNo}: invalid JSON: ${error.message}`);
  }
  const row = normalizeRow(raw, lineNo);
  if (releaseSeen && releaseSeen !== row.release_id) {
    throw new Error(`line ${lineNo}: mixed releases are not allowed (${releaseSeen} vs ${row.release_id})`);
  }
  releaseSeen = row.release_id;
  batch.push(row);
  accepted += 1;
  if (batch.length >= batchSize) {
    batchNo += 1;
    changed += await publishBatch(batch, batchNo);
    batch = [];
  }
}

if (batch.length) {
  batchNo += 1;
  changed += await publishBatch(batch, batchNo);
}

console.log(`accepted ${accepted} normalized EPC assertions for release ${releaseSeen ?? "(none)"}`);
console.log(`inserted/updated ${changed}`);

if (!dryRun) {
  const health = await rpc("customer_catalog_health", {});
  const row = Array.isArray(health) ? health[0] : health;
  console.log("customer catalog health:", JSON.stringify(row, null, 2));
  if (!row?.exact_customer_fitment_ready) {
    console.error("ERROR: exact customer fitment index is still not ready after publication");
    process.exit(1);
  }
}
