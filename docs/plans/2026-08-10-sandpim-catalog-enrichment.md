# SandPIM → GTR catalog enrichment (advisory)

**Date:** 2026-08-10  
**Scope:** How [autopartsource/sandpim](https://github.com/autopartsource/sandpim) can enrich Nissan GTR Auto ERP catalog — **not** how the existing PartSouq/Megazip pipeline works.  
**Decision posture:** Adopt-first — **Integrate** as an ACES/PIES satellite; do **not** rebase Supabase SoR onto SandPIM. No product code in this note.

---

## 1. What SandPIM is

| Aspect | Finding |
|--------|---------|
| **Purpose** | LAMP Product Information Manager built around Auto Care **ACES** (applications/fitment) and **PIES** (product + media attributes). Name nods to community **Sandpiper** sync protocol. |
| **Stack** | PHP (no framework), MySQL, classic LAMP UI; Docker demo images on Docker Hub. |
| **License** | README claims **MIT**. GitHub `license` field is **null** and there is **no LICENSE file** in-tree — treat as **MIT-intent, confirm before shipping** (vendor a LICENSE or ask maintainers). **Not AGPL/GPL.** |
| **Maturity** | Small public repo (~25★); AutoPartSource uses it in production for aftermarket brands (brakes, filters, exhaust). Expect PHP adaptation work. |
| **Hard deps** | Auto Care **VCdb / PCdb / PAdb / Qdb** reference data (paid membership) + brand AAIA IDs for real exports. |

### Capabilities that matter for enrichment

- Catalog **fitment** (Make–Model–Year and/or Mfr-Equipment) with Qdb qualifiers  
- **PCdb** part terminology + position browsers  
- **PAdb** part attributes; digital **asset** management  
- Competitor **interchange**, pricesheets, VIO/PIO  
- **ACES** XML import/export (4.1 / 4.2 / 5.0 generators + XSDs)  
- **PIES** XML import/export (6.7 → 8.0) + Excel (“Rhubarb”) converters  
- **Sandpiper** primary/secondary API endpoints  
- Auto Care download/update scripts (`getAutoCarePCdb.php`, `updateVCdbFromACAAPI.php`, …)

SandPIM is an **aftermarket PIM / feed factory**, not an OEM EPC diagram browser.

---

## 2. Map onto GTR SoR (Supabase stays authoritative)

GTR already documents a dual taxonomy in `docs/guides/partsouq-multimake-catalog-pipeline.md` §10: EPC for shop-by-diagram; ACES/PCdb additive for facets/feeds.

| GTR SoR | SandPIM / Auto Care signal | Enrichment role |
|---------|----------------------------|-----------------|
| `vehicle_master` | VCdb BaseVehicle (+ EngineConfig / SubModel / DriveType via app attributes) | Optional columns or side table: `vcdb_base_vehicle_id`, year/make/model keys for YMM cascade — **do not** replace `chassis_code` / VIN prefix |
| `pnc_categories` | PCdb `PartTerminologyID` (+ PositionID) | Already scaffolded: `pcdb_part_type_id`; expand `config/epc_to_pcdb.json` or import from PIES/ACES apps. **Never** overwrite EPC `category_name` on diagram UX |
| `part_fitment` | ACES `App` rows (PartNumber + BaseVehicle + PartTerminology + Qty) | **Additive** aftermarket applications (brand/private-label). EPC rows keep bbox + `diagram_path`. Prefer separate provenance (`source = aces|epc`) rather than merging geometries into ACES apps |
| `stock_items` | PIES Item / descriptions / EXPI / packaging / brand | Descriptions, brand AAIA, weight/UOM, hazmat flags, marketing copy — extend via attribute JSON or child tables; keep inventory qty/cost in GTR |
| `catalog_*` (makers→diagrams) | Weak fit | Keep **FAST / Megazip / PartSouq** hierarchy. SandPIM has no OEM exploded-view drill-down model |
| Storage `catalog-diagrams` | PIES digital assets | Useful for **marketing / pack-shot** images on SKUs; **not** a substitute for EPC hotspot diagrams |

**Keep (GTR-native):** FAST / Megazip EPC browse, PNC, chassis fitment, bbox hotspots, diagram Storage, complete-only publish gates.

**Borrow (SandPIM-shaped):** ACES/PIES XML I/O semantics, PCdb/VCdb ID discipline, brand + attribute + interchange curation for aftermarket lines.

---

## 3. Adopt-first decision

| Path | Verdict | Why |
|------|---------|-----|
| **Integrate (thin adapter)** | **Preferred** | Aligns with guide §10 (“accept ACES/PIES without rebasing SoR”). Adapters live under `data-pipeline/` (Python): parse ACES/PIES → enrich bundle / upsert Supabase. Optionally run SandPIM Docker as a **curation satellite** that only **exports** XML. |
| **Fork** | Avoid unless needed | Large PHP surface; merge cost high; GTR is Python + Supabase. Fork only if you need durable patches to generators and upstream is dead. |
| **Build** | Only for missing pieces | Reimplement minimal ACES/PIES **importers** in Python if you never need the SandPIM UI. Do **not** rebuild a full PIM UI early. |
| **Rebase SoR onto SandPIM** | **Rejected** | Wrong stack (MySQL/PHP), wrong domain center (aftermarket feeds vs EPC diagrams), conflicts with one-SoR rule. |

**License flag:** MIT-intent is fine for Integrate; confirm LICENSE file before embedding any SandPIM PHP verbatim. Prefer **file-format interoperability** over copying PHP classes. No ZIMRA / fiscalisation surface here.

---

## 4. Concrete phases

### Phase 0 — Gates (1–2 days)

- Confirm Auto Care membership path (VCdb/PCdb at minimum).  
- Confirm SandPIM license text (add/vendor LICENSE or written OK).  
- Optional: spin Docker demo to learn export UX — not production SoR.

### Phase 1 — PCdb enrichment (already started)

- Expand `data-pipeline/config/epc_to_pcdb.json` from real PCdb PartTerminology (SandPIM’s PCdb browser / ACA download as **reference tooling**).  
- Keep `megazip` / import `pcdb` phase additive (`enrich_pcdb`).  
- Success: storefront facet on `pcdb_part_type_id` without changing diagram group names.

### Phase 2 — PIES → `stock_items` (attributes & brands)

- Adapter: `data-pipeline/data_pipeline/aces_pies/` (or similar) ingest PIES 7.x/8.0 XML (supplier drop **or** SandPIM export).  
- Map Item → `stock_items` + optional `stock_item_attributes` / brand code.  
- Images: pack-shots to Storage bucket distinct from `catalog-diagrams` (provenance tagged).

### Phase 3 — ACES applications (YMM / VCdb)

- Map ACES Apps → additive fitment (part + BaseVehicleID + PartTerminologyID).  
- Bridge VCdb ↔ `vehicle_master` (chassis/year/engine) via curated lookup — Nissan RHD/Africa coverage may be thin; treat as best-effort for aftermarket, not OEM authority.  
- Export path later: GTR → ACES XML for marketplaces (mirror SandPIM generator **semantics**, implement in Python).

### Phase 4 — Optional SandPIM satellite ops

- Run SandPIM only for private-label / AmeriBRAKES-style SKU curation.  
- Nightly: SandPIM ACES+PIES export → GTR adapter → Supabase.  
- Do not dual-write EPC hierarchy through SandPIM.

### Phase 5 — Interchange / pricesheets (defer)

- Competitor interchange and APA pricefile exports are SandPIM strengths; wire only when GTR commerce needs aftermarket cross-refs.

---

## 5. Adapter placement (when coding starts)

```text
data-pipeline/
  config/epc_to_pcdb.json          # Phase 1 (exists)
  data_pipeline/megazip/enrich_pcdb.py
  data_pipeline/aces_pies/         # NEW — XML parse, validate vs XSDs (vendor copies carefully)
  schemas/                         # extend bundle schemas for aces_apps / pies_items
→ import_* / upsert into Supabase (SoR)
```

Client apps and ledger untouched. Catalog browse remains Megazip/FAST.

---

## 6. Non-goals

- Replacing Megazip/PartSouq/FAST crawl with SandPIM  
- Storing financial ledger or inventory quantities in SandPIM  
- ZIMRA / fiscalisation / payroll tax  
- Browser QR / hardware bridges (irrelevant to this satellite)
