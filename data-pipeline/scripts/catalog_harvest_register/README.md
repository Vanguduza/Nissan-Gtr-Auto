# Full Nissan catalogue registration (2026-10-02)

Registers the full Nissan catalogue (already in R2) in the hosted Supabase routing tables that
`catalog-live-r2` serves from. Supabase stores ids and R2 object keys only — no vendor names or
URLs (decision 2026-08-11).

## Why

The hosted database was rebuilt from migrations on 2026-09-04, which dropped the catalogue
hierarchy. Only a 16-vehicle sample of the partial v2 release was registered afterwards. The R2
shards of the full release (`private/serving/nissan/v3-2026-09-05/`) carry no vehicle names, so
the vehicle identity was rebuilt from the harvest database (`archive/nissan/harvest.sqlite` in R2,
not a serving object).

## Inputs

- `archive/nissan/harvest.sqlite` (R2): families, variants, diagram nodes, placements, images.
- R2 listings: `private/serving/nissan/v3-2026-09-05/{fitment,section-parts}/`, `diagrams/nissan/`.

## Id schemes (verified against existing rows)

- Section: `SEC-` + sha256(`nissan|{family}|{variant}|{section}`)[:28]
- Diagram: `DG-` + sha256(`{node_key}`)[:28]
- Vehicle (new): `VM-` + sha256(`nissan|{family}|{variant}`)[:28]
- R2 shard file names: sha256(scope id)[:32]
- Diagram images are content-addressed: `diagrams/nissan/{sha[:2]}/{sha}.png` = `nodes.image_sha256`.

## Steps

1. `map_fitment.py` — map each v3 vehicle shard to its harvest variant by the section ids in its
   first rows (9,298 / 9,303 shards, no ambiguous votes).
2. `identity.py`, `identity2.py` — engine, years, region and model per variant: from the variant's
   own diagrams, else from shared engine-section diagrams when at least 80% agree.
3. `build_reg.py` — registration rows: vehicle master + `vehicle_search` / `vehicle_fitment`
   (multi-page, `metadata.pages`), `section_parts`, `diagram_parts` (section or vehicle shard with
   `metadata.filter = "diagram_id"`), `diagram_image`.
4. `push_reg.py objects | vehicles | cleanup` — inserts (idempotent, `on conflict do nothing`),
   then removes the 16 v2 sample vehicles. Needs `SBP_TOKEN` (Supabase management token).

R2 reads need `R2_ACC`, `R2_KEY`, `R2_SEC` in the environment (read-only use).

## Result (hosted, 2026-10-02)

- 9,296 vehicles across 93 models; engines: 6,754 single, 2,516 multiple candidates listed as
  `A / B` (the catalogue page covers several engines), 26 blank (no evidence).
- Serving objects: 220,441 diagram images, 220,441 diagram part lists, 91,927 section part
  lists, 9,312 vehicle search + 9,312 vehicle fitment (16 v2 sample entries remain, unreferenced).
- Spot-checked end to end: X-Trail T31, Frontier D40, Truck-Hardbody D21U.

Known gaps: diagram callout boxes (hotspots) are in the harvest but not yet served; several build
variants share one chassis + engine and are told apart only by years/region.

## Follow-up (2026-10-02): look-alike merge and callout boxes

Many catalogue builds shared model, chassis, engine, years and region and looked identical in
pickers (1,349 groups, 8,574 vehicles). Builds in a group share ~79% of their diagrams (median);
the union is ~8% larger than the largest build. Each group is now one vehicle whose shard is the
de-duplicated union (section ids re-keyed to the kept build, so a section never lists twice).

5. `merge_groups.py` — builds the merged shards (`serving/nissan/harvest-2026-09/vehicle/`).
6. `build_dparts.py` — one shard per diagram from the harvest parts table, with callout boxes
   (`bbox_*`, fractions of the image) and quantities. Some diagrams' hotspot coordinates are on a
   canvas 1.5x the served image; they are detected by overflow and rescaled (4 of 5.47M boxes
   remain out of bounds and are dropped). Spot-checked visually on X-Trail and Skyline diagrams.
7. `r2put.py` — uploads the new shards (resumable via a done-log).
8. `push_groups.py`, `push_dparts.py` — repoint the routing rows and drop duplicate vehicles.

Result: 2,071 distinct vehicles (0 look-alike groups); diagram part lists carry callout boxes.
