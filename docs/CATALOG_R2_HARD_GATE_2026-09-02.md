# Catalog R2 hard gate — 2026-09-02

Branch: `chatgpt/customer-ui-catalog-hardening-20260902`

## Result

**FAIL — infrastructure credentials required to access Cloudflare R2 are not currently available to the execution environments used by the live catalog gateway or the GitHub hard-gate workflow.**

No serving shards were published and no fake routing records were inserted.

## Evidence

The executable workflow `.github/workflows/catalog-r2-hard-gate.yml` was triggered by commit `cf89ed20f8834de562514cb6daa22c6efa0d45bc`.

GitHub Actions run:

- run id: `33630065896`
- workflow: `Catalog R2 hard gate`
- result: `failure`

The credential preflight found these required values missing from GitHub Actions:

- `SUPABASE_SERVICE_ROLE_KEY` (or `SUPABASE_SERVICE_KEY`)
- `CLOUDFLARE_ACCOUNT_ID` (or `R2_ACCOUNT_ID`)
- `CLOUDFLARE_R2_ACCESS_KEY_ID` (or `R2_ACCESS_KEY_ID` / `AWS_ACCESS_KEY_ID`)
- `CLOUDFLARE_R2_SECRET_ACCESS_KEY` (or `R2_SECRET_ACCESS_KEY` / `AWS_SECRET_ACCESS_KEY`)
- `CLOUDFLARE_R2_BUCKET` (or `R2_BUCKET`)

Because the preflight failed, the workflow correctly skipped R2 inventory, source discovery, serving-index generation, upload and manifest registration.

A separate temporary Supabase Edge probe was also executed to test the runtime environment used by the live gateway. It confirmed that the project Edge runtime currently has no R2 / Cloudflare catalog secrets configured under the names expected by `catalog-live-r2` and `catalog-v2-serve`.

The temporary probe was disabled immediately after the check. A temporary `pg_net` extension used only to invoke the probe from the database was removed after execution.

## Current catalog state

The current published Nissan release remains:

- release version: `v2-storage-2026-09`
- storage backend: `r2`
- bucket recorded by release metadata: `nissangtrauto`
- full encrypted bundle: `bundles/nissan/catalog_nissan_v2_storage.sqlite.enc`

The database-side live serving routing table currently has **zero** registered objects for the current release. Therefore normal customer/staff live R2 browsing must remain fail-closed until the serving publication succeeds.

The hosted structured catalog hierarchy itself is present and large enough for staff navigation and vehicle-master derivation; the blocker is R2 runtime access/publication, not the hierarchy design.

## Required credential bootstrap

Configure the same R2 credentials in both execution planes.

### Supabase Edge Function secrets

Required by `catalog-live-r2` / `catalog-v2-serve`:

- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_R2_ACCESS_KEY_ID`
- `CLOUDFLARE_R2_SECRET_ACCESS_KEY`
- `CLOUDFLARE_R2_BUCKET=nissangtrauto`
- optional: `CLOUDFLARE_R2_ENDPOINT`

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are Supabase-managed runtime values for Edge Functions.

### GitHub Actions secrets

Required by `.github/workflows/catalog-r2-hard-gate.yml`:

- `SUPABASE_SERVICE_ROLE_KEY`
- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_R2_ACCESS_KEY_ID`
- `CLOUDFLARE_R2_SECRET_ACCESS_KEY`
- `CLOUDFLARE_R2_BUCKET` (or repository variable with the same name)
- optional: `CLOUDFLARE_R2_ENDPOINT`

## Gate that must pass after credentials are present

The workflow will then:

1. prove the current encrypted R2 bundle is reachable;
2. inventory R2 without downloading the full catalog;
3. locate the existing normalized applicability/fitment build artifact;
4. build immutable compressed vehicle, section and diagram serving shards;
5. upload them under `serving/nissan/v2-storage-2026-09/`;
6. register all serving objects in Supabase;
7. verify non-zero `vehicle_fitment`, `section_parts`, `diagram_parts` and `diagram_image` routing counts;
8. pass only when the current Nissan release is browse-ready without any full-catalog runtime download.

## Locked safety behavior

Until this gate passes:

- customer fitment must never be guessed;
- staff EPC must never fall back to downloading the 6+ GB bundle for ordinary browsing;
- missing R2 serving shards/mappings must return `CATALOG_REPUBLISH_REQUIRED` or `FITMENT_UNRESOLVED`;
- no direct fabricated inserts into serving/index tables are permitted.
