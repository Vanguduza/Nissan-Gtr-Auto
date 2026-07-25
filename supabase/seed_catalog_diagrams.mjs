/**
 * Upload Navara placeholder PNGs into Storage bucket `catalog-diagrams`.
 * Run after `supabase db reset` (migration registers object metadata + diagram_path).
 *
 *   node supabase/seed_catalog_diagrams.mjs
 *
 * Env (local defaults OK when unset):
 *   SUPABASE_URL                 default http://127.0.0.1:54321
 *   SUPABASE_SERVICE_ROLE_KEY    required — from `npx supabase status`
 *
 * No secrets committed. Fixture bytes: data-pipeline/fixtures/navara_d40_yd25/diagrams/
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const fixtureDir = path.join(
  root,
  "data-pipeline",
  "fixtures",
  "navara_d40_yd25",
  "diagrams",
  "navara-d40",
);

const FILES = [
  "15208-oil-filter.png",
  "40206-brake-disc.png",
  "21410-water-pump.png",
  "16546-air-filter.png",
];

const url = (process.env.SUPABASE_URL || "http://127.0.0.1:54321").replace(
  /\/$/,
  "",
);
const key = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!key) {
  console.error(
    "ERROR: set SUPABASE_SERVICE_ROLE_KEY (see `npx supabase status` — never commit it).",
  );
  process.exit(1);
}

async function upload(name) {
  const filePath = path.join(fixtureDir, name);
  if (!fs.existsSync(filePath)) {
    throw new Error(`missing fixture PNG: ${filePath}`);
  }
  const body = fs.readFileSync(filePath);
  const objectPath = `navara-d40/${name}`;
  const endpoint = `${url}/storage/v1/object/catalog-diagrams/${objectPath}`;
  const res = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${key}`,
      apikey: key,
      "Content-Type": "image/png",
      "x-upsert": "true",
    },
    body,
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`upload ${objectPath} failed: ${res.status} ${text}`);
  }
  console.log(`uploaded ${objectPath} (${body.length} bytes)`);
}

for (const name of FILES) {
  await upload(name);
}
console.log("catalog-diagrams Navara seed OK");
