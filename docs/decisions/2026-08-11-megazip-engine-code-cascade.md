# Decision: Megazip multimaker catalog — Nissan lessons applied

**Date:** 2026-08-11  
**Status:** accepted  
**Lane:** `@data_pipeline_agent`

## Context

Nissan Megazip → hosted Supabase hit: empty cascade engines, vendor `megazip` strings in SoR,
schema rename lag, MAIN stall after priority drain, and SQLite lock fights during backfill.

## Decisions (apply to every maker)

1. **Engines** — Parse diagram/variant `Engine` attrs; transform emits `vehicle_master.engine_code`;
   page-type order hub→variant→section→diagram; label `{Maker} {display_name}`.
2. **Vendor scrub** — Transform + import `sanitize_hierarchy_vendor_leakage`; Storage prefix `epc/{slug}`;
   never persist megazip hosts/paths; repair with `scrub_megazip_catalog_values.py`.
3. **Schema dual-map** — Import supports `external_*` and legacy `megazip_*` until migration applied.
4. **Ops** — Nissan two-phase auto-remaining; other makers `--no-nissan-two-phase`; workers via leases;
   post-import `megazip_post_import_verify.py`; engine backfill via cache-only extract while crawling.
5. **Guide of record** — `docs/guides/megazip-multivehicle-catalog.md` (checklist + lessons table).

## Consequences

Toyota/Honda/… must pass the same verify gate before “import done”. Engine gaps warn by default;
use `--fail-on-engine-gaps` for release cuts.
