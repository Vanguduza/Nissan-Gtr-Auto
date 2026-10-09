#!/usr/bin/env node
/** Register already-uploaded R2 serving objects in Supabase control-plane metadata. */
import { createReadStream } from "node:fs";
import { createInterface } from "node:readline";

const args = process.argv.slice(2);
const input = args.find((a) => !a.startsWith("--"));
const batchSize = Math.max(50, Math.min(Number(args.find((a) => a.startsWith("--batch="))?.split("=")[1] ?? 500) || 500, 1000));
const dryRun = args.includes("--dry-run");
if (!input) {
  console.error("Usage: register_r2_serving_manifest.mjs <serving-manifest.ndjson> [--batch=500] [--dry-run]");
  process.exit(2);
}
const supabaseUrl = process.env.SUPABASE_URL?.replace(/\/$/, "");
const serviceRole = process.env.SUPABASE_SERVICE_ROLE_KEY;
if (!supabaseUrl || !serviceRole) {
  console.error("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required");
  process.exit(2);
}
const headers = { apikey: serviceRole, Authorization: `Bearer ${serviceRole}`, "Content-Type": "application/json" };

async function getCurrentRelease() {
  const url = `${supabaseUrl}/rest/v1/catalog_releases?maker_slug=eq.nissan&is_current=eq.true&published_at=not.is.null&select=id,version,bucket_name&limit=1`;
  const resp = await fetch(url, { headers });
  const text = await resp.text();
  if (!resp.ok) throw new Error(`current release ${resp.status}: ${text.slice(0, 1000)}`);
  const rows = text ? JSON.parse(text) : [];
  if (!rows[0]?.id) throw new Error("no current Nissan catalog release");
  return rows[0];
}
async function rpc(name, body) {
  const resp = await fetch(`${supabaseUrl}/rest/v1/rpc/${name}`, { method: "POST", headers, body: JSON.stringify(body) });
  const text = await resp.text();
  if (!resp.ok) throw new Error(`${name} ${resp.status}: ${text.slice(0, 1000)}`);
  return text ? JSON.parse(text) : null;
}

const release = await getCurrentRelease();
console.log(`registering against ${release.version} (${release.id})`);
const rl = createInterface({ input: createReadStream(input), crlfDelay: Infinity });
let batch = [];
let accepted = 0;
let changed = 0;
let batchNo = 0;
async function flush() {
  if (!batch.length) return;
  batchNo += 1;
  const rows = batch.map((r) => ({ ...r, release_id: release.id }));
  if (dryRun) {
    console.log(`[dry-run] batch ${batchNo}: ${rows.length}`);
  } else {
    const n = await rpc("catalog_v2_ingest_r2_serving_objects_batch", { p_rows: rows });
    changed += Number(n ?? 0);
    console.log(`batch ${batchNo}: ${rows.length} registered`);
  }
  batch = [];
}
for await (const line of rl) {
  const text = line.trim();
  if (!text || text.startsWith("#")) continue;
  const row = JSON.parse(text);
  if (!row.object_kind || !row.scope_key || !row.object_key) throw new Error("manifest row missing object_kind/scope_key/object_key");
  batch.push(row);
  accepted += 1;
  if (batch.length >= batchSize) await flush();
}
await flush();
console.log(`accepted ${accepted}; registered/updated ${changed}`);
console.log("The catalog gateway will now resolve these R2 shards by current release + scope.");
