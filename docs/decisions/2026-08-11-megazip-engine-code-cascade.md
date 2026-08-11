# Decision: Megazip multimaker catalog — engine + sequential makers

**Date:** 2026-08-11  
**Status:** accepted  
**Lane:** `@data_pipeline_agent`

## Context

Nissan Megazip → hosted Supabase hit empty cascade engines, vendor `megazip` strings in SoR,
and schema rename lag (`megazip_*` → `external_*`). Multimaker runs must not carry Nissan-only
orchestration quirks.

## Decisions (apply to every maker)

1. **Sequential makers** — One maker at a time in `config/megazip_makers.json` order
   (homepage popularity: Toyota → Lexus → Honda → Suzuki → Nissan → Subaru → Mitsubishi).
   Full hub crawl queues all models; optional `--priority-chassis` is smoke-only.
2. **Engines** — Parse diagram/variant `Engine` attrs during crawl/parse; transform emits
   `vehicle_master.engine_code`; page-type order hub→variant→section→diagram;
   label `{Maker} {display_name}`.
3. **Vendor scrub** — Transform + import `sanitize_hierarchy_vendor_leakage`; Storage prefix
   `epc/{slug}`; never persist megazip hosts/paths; repair with `scrub_megazip_catalog_values.py`.
4. **Schema ensure** — Live import auto-renames `megazip_*` → `external_*` via
   `megazip.schema_ensure` when a Postgres URL is available; dual-maps until then.
5. **Ops** — Workers via leases; post-import `megazip_post_import_verify.py`; engine backfill via
   cache-only extract while crawling.
6. **Guide of record** — `docs/guides/megazip-multivehicle-catalog.md`.

## Consequences

Toyota first, then remaining makers in config order. Engine gaps warn by default;
use `--fail-on-engine-gaps` for release cuts. Client/GTR storefront parity is out of scope for
the multimaker catalog track.
