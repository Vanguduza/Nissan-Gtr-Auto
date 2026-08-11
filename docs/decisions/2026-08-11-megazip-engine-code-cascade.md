# Decision: Megazip engine_code for vehicle cascade (all makers)

**Date:** 2026-08-11  
**Status:** accepted  
**Lane:** `@data_pipeline_agent`

## Context

Live catalog showed “No engine codes in catalog” because Megazip transform built
`vehicle_master` with chassis only (`engine_code` always null). Engines live mainly
on diagram page `s-catalog__attrs` (`Engine` / `двигатель`), not only on variant lists.

## Decision

1. Parse `Engine` (and aliases) in `parse_variant_list` and `parse_diagram_page`.
2. Transform emits `vehicle_master` rows with `engine_code` when known; label is
   always `{MakerName} {catalog_models.display_name}`.
3. Transform sorts parsed pages so variants index before diagrams.
4. Quality report tracks `variant_chassis_missing_engine*` for every maker build.
5. After parser changes: `--skip-crawl --phase parse,transform,...` (or
   `scripts/extract_engines_from_cache.py`) — do not assume crawl alone refreshes engines.

## Consequences

- Applies to Nissan, Toyota, Honda, … same Megazip pipeline.
- Cascade generations without diagram HTML / Engine attrs still show no engines until re-fetched.
- `catalog_variants.engine_code` may stay empty when one chassis has multiple engines;
  cascade uses `vehicle_master`.
