/**
 * Seed catalog diagram placeholder PNGs into Storage bucket `catalog-diagrams`.
 * Discovers packs under data-pipeline/fixtures/{vehicle}/diagrams/{storage-prefix}/
 * Run after `supabase db reset` (migrations set diagram_path + object metadata).
 *
 * Preferred (no secrets):
 *   node supabase/seed_catalog_diagrams.mjs --docker
 *
 * Alternate (Storage API upsert):
 *   set SUPABASE_SERVICE_ROLE_KEY from `npx supabase status`
 *   node supabase/seed_catalog_diagrams.mjs
 *
 * Local file backend path: /mnt/{TENANT}/{BUCKET}/… → /mnt/stub/stub/catalog-diagrams/
 *
 * Ops one-liner (also in docs/LOCAL_DEVELOPMENT.md §9):
 *   pnpm db:reset && node supabase/seed_catalog_diagrams.mjs --docker
 */
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const fixturesRoot = path.join(root, "data-pipeline", "fixtures");

/**
 * @typedef {{ fixtureDir: string, storagePrefix: string, files: string[] }} DiagramPack
 */

/** @returns {DiagramPack[]} */
function discoverPacks() {
  /** @type {DiagramPack[]} */
  const packs = [];
  if (!fs.existsSync(fixturesRoot)) {
    return packs;
  }
  for (const vehicle of fs.readdirSync(fixturesRoot, { withFileTypes: true })) {
    if (!vehicle.isDirectory()) continue;
    const diagramsRoot = path.join(fixturesRoot, vehicle.name, "diagrams");
    if (!fs.existsSync(diagramsRoot)) continue;
    for (const prefix of fs.readdirSync(diagramsRoot, { withFileTypes: true })) {
      if (!prefix.isDirectory()) continue;
      const fixtureDir = path.join(diagramsRoot, prefix.name);
      const files = fs
        .readdirSync(fixtureDir)
        .filter((f) => f.toLowerCase().endsWith(".png"))
        .sort();
      if (files.length === 0) continue;
      packs.push({
        fixtureDir,
        storagePrefix: prefix.name,
        files,
      });
    }
  }
  return packs;
}

const packs = discoverPacks();
if (packs.length === 0) {
  throw new Error(`no diagram PNG packs under ${fixturesRoot}`);
}

const useDocker = process.argv.includes("--docker");
const container =
  process.env.SUPABASE_STORAGE_CONTAINER ||
  "supabase_storage_gylrgwqyuiwkyykardwc";

function assertFixtures() {
  for (const pack of packs) {
    for (const name of pack.files) {
      const filePath = path.join(pack.fixtureDir, name);
      if (!fs.existsSync(filePath)) {
        throw new Error(`missing fixture PNG: ${filePath}`);
      }
    }
  }
}

function seedViaDocker() {
  for (const pack of packs) {
    const remoteDir = `/mnt/stub/stub/catalog-diagrams/${pack.storagePrefix}`;
    execFileSync("docker", ["exec", container, "mkdir", "-p", remoteDir], {
      stdio: "inherit",
    });
    for (const name of pack.files) {
      const local = path.join(pack.fixtureDir, name);
      const remote = `${container}:${remoteDir}/${name}`;
      execFileSync("docker", ["cp", local, remote], { stdio: "inherit" });
      console.log(`docker cp ${name} → ${remoteDir}/${name}`);
    }
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

  for (const pack of packs) {
    for (const name of pack.files) {
      const filePath = path.join(pack.fixtureDir, name);
      const body = fs.readFileSync(filePath);
      const objectPath = `${pack.storagePrefix}/${name}`;
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
}

assertFixtures();
if (useDocker) {
  seedViaDocker();
} else {
  await seedViaApi();
}
console.log(
  `catalog-diagrams seed OK (${packs.map((p) => p.storagePrefix).join(", ")})`,
);
