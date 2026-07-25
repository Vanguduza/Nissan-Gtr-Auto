/**
 * Seed Navara placeholder PNGs into Storage bucket `catalog-diagrams`.
 * Run after `supabase db reset` (migration sets diagram_path + object metadata).
 *
 * Preferred (no secrets):
 *   node supabase/seed_catalog_diagrams.mjs --docker
 *
 * Alternate (Storage API upsert):
 *   set SUPABASE_SERVICE_ROLE_KEY from `npx supabase status`
 *   node supabase/seed_catalog_diagrams.mjs
 *
 * Fixture bytes: data-pipeline/fixtures/navara_d40_yd25/diagrams/navara-d40/
 * Local file backend path: /mnt/{TENANT}/{BUCKET}/… → /mnt/stub/stub/catalog-diagrams/
 */
import { execFileSync } from "node:child_process";
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

const useDocker = process.argv.includes("--docker");
const container =
  process.env.SUPABASE_STORAGE_CONTAINER ||
  "supabase_storage_gylrgwqyuiwkyykardwc";
/** Local supabase file backend: /mnt/{tenant}/{globalS3Bucket}/{bucket}/{object} */
const remoteDir = "/mnt/stub/stub/catalog-diagrams/navara-d40";

function assertFixtures() {
  for (const name of FILES) {
    const filePath = path.join(fixtureDir, name);
    if (!fs.existsSync(filePath)) {
      throw new Error(`missing fixture PNG: ${filePath}`);
    }
  }
}

function seedViaDocker() {
  execFileSync("docker", ["exec", container, "mkdir", "-p", remoteDir], {
    stdio: "inherit",
  });
  for (const name of FILES) {
    const local = path.join(fixtureDir, name);
    const remote = `${container}:${remoteDir}/${name}`;
    execFileSync("docker", ["cp", local, remote], { stdio: "inherit" });
    console.log(`docker cp ${name} → ${remoteDir}/${name}`);
  }
}

async function seedViaApi() {
  const url = (process.env.SUPABASE_URL || "http://127.0.0.1:54321").replace(
    /\/$/,
    "",
  );
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!key) {
    console.error(
      "ERROR: set SUPABASE_SERVICE_ROLE_KEY, or pass --docker (no secrets).",
    );
    process.exit(1);
  }

  for (const name of FILES) {
    const filePath = path.join(fixtureDir, name);
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
}

assertFixtures();
if (useDocker) {
  seedViaDocker();
} else {
  await seedViaApi();
}
console.log("catalog-diagrams Navara seed OK");
