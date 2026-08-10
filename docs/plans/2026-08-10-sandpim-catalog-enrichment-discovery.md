# Discovery: SandPIM for GTR catalog enrichment

**Date:** 2026-08-10  
**Repo inspected:** [autopartsource/sandpim](https://github.com/autopartsource/sandpim) (PHP/LAMP PIM; build `2026-07-04`; active as of 2026-08-10)  
**Policy:** Adopt-first — Integrate / Fork / Build; keep Supabase catalog SoR; no ZIMRA; no mid-project SoR rebase.

---

## Verdict

**Integrate (thin adapter) — do not Fork, do not rebase SoR onto SandPIM.**

SandPIM is a useful **ACES/PIES reference implementation and optional ops satellite**, not a replacement for FAST/Megazip EPC browse or our flat/hierarchy catalog SoR.

| Path | Decision |
|------|----------|
| **Integrate** | Prefer: consume **ACES/PIES XML** (+ Auto Care ref DBs when licensed) via `data-pipeline/` → enrich existing tables |
| **Fork** | Avoid — PHP/MySQL LAMP tree; merge cost high; does not match Supabase/Python monorepo |
| **Build** | Keep building EPC diagram SoR + publish gates; optionally later Build a thin ACES export without hosting SandPIM |
| **Skip (as SoR)** | Never make SandPIM MySQL the catalog/ledger system of record |

---

## SandPIM snapshot

| Item | Finding |
|------|---------|
| **What** | LAMP Product Information Manager for Auto Care **ACES** (applications/fitment) + **PIES** (product/attributes/assets) |
| **License** | README claims **MIT**; GitHub `license` field is **null** and no `LICENSE` file in tree — treat as MIT-intended but **confirm SPDX before deep embed** |
| **Stack** | PHP (no framework), MySQL, Apache; Docker demo images on Docker Hub |
| **Maturity** | Early/needs in-house PHP; production at AutoPartSource for brakes/filters/exhaust; ~25★ |
| **Standards files** | ACES 4.1 / 4.2 / 5.0 XSDs; PIES 6.7–8.0 XSDs; Excel “Rhubarb” / Flat ACES templates |
| **I/O** | ACES & PIES XML import/export; Sandpiper API (primary/secondary); Auto Care FTP loaders for VCdb/PCdb/PAdb MySQL dumps |
| **Domain tables (conceptual)** | `part`, `application` (basevehicleid, parttypeid, positionid, …), assets, attributes, interchange, pricesheets |
| **Not in SandPIM** | OEM exploded-diagram hotspots, FAST PNC, Megazip hierarchy browse, Bridge-First hardware |

**Paid dependency (honest gap):** usable ACES/PIES validation and ref lookups require an [Auto Care Association subscription](https://www.autocare.org/data-standards/subscriptions) (VCdb/Qdb/PCdb/PAdb/Brand Table). SandPIM alone does not ship that data.

Related OSS from same org: **ACESinspector** (MIT .NET validator against local VCdb/PCdb/Qdb) — useful QA satellite without adopting the full PIM UI.

---

## Map to GTR SoR

```text
SandPIM / Auto Care                    GTR (keep as SoR)
─────────────────────────              ──────────────────────────────
ACES App (BaseVehicle, Engine, …)  →   vehicle_master + part_fitment (additive keys)
PCdb PartTerminologyID             →   pnc_categories.pcdb_part_type_id  (already schema + enrich_pcdb)
PIES descriptions / PAdb attrs     →   stock_items.description + future attr columns / oem_display_names
PIES / asset digital files         →   Storage (non-diagram marketing images) — not catalog-diagrams hotspots
Brand table / interchange          →   optional cross-ref tables; join on oem_part_number
Pricesheets                        →   do NOT overwrite ERP pricing SoR without explicit finance design
Qdb qualifiers                     →   facets / ACES export only — do not replace chassis+engine EPC fitment

Megazip catalog_* + bbox diagrams  =   UNCHANGED (EPC browse UX)
Bridge-First / QR / printers       =   UNCHANGED (out of catalog PIM scope)
```

Existing pipeline already anticipates this dual taxonomy: `docs/guides/partsouq-multimake-catalog-pipeline.md` §10; skill `parts-catalog-ingestion`; megazip phase `pcdb` (`data_pipeline.megazip.enrich_pcdb` + `config/epc_to_pcdb.json`).

---

## What SandPIM features enrich (concretely)

| Enrichment | SandPIM / standards source | GTR target | Priority |
|------------|----------------------------|------------|----------|
| Cross-brand part type labels | PCdb via PIES / mapping | `pnc_categories.pcdb_part_type_id` | High (already stubbed) |
| Marketing descriptions | PIES Item / descriptions | `stock_items`, `oem_display_names` | High when supplier PIES exists |
| Attributes (weight, hazmat, …) | PAdb / PIES | Additive columns or JSON attrs — design later | Medium |
| YMM / VCdb-coded fitment | ACES `App` | Map ↔ `vehicle_master` for facets/feeds; **EPC chassis remains shop-by-diagram authority** | Medium |
| Product photos (non-EPC) | Digital assets | Supabase Storage separate from exploded GIFs | Medium |
| Brand / competitor interchange | Brand table + interchange | Lookup for search / supersession UX | Low–Medium |
| Marketplace ACES/PIES feeds | Export generators | Export satellite from Supabase — not crawl SoR | Follow-on |

---

## Adapter placement (`data-pipeline/`)

Suggested modules (names illustrative; build when licensed data or supplier files exist):

| Module | Role |
|--------|------|
| `data_pipeline/aces_pies_import.py` | Parse ACES/PIES XML → normalized enrich rows keyed by OEM |
| `data_pipeline/megazip/enrich_pcdb.py` | **Keep** — EPC name → PartTerminologyID (curated JSON or PIES join) |
| `data_pipeline/aces_export.py` | Optional later: emit ACES from `vehicle_master` / `part_fitment` |
| `config/epc_to_pcdb.json` | Curated mapping (exists) |
| Optional compose | SandPIM **demo** or ACESinspector for ops validation only — never write path for ERP ledger |

Do **not** put SandPIM PHP under `apps/` or replace `import_catalog` / hierarchy RPCs.

---

## What we would NOT replace

- Flat SoR: `vehicle_master`, `pnc_categories`, `part_fitment`, `stock_items`
- Hierarchy Megazip: `catalog_*` + Storage diagram hotspots + `get_catalog_diagram`
- FAST / PartSouq / Megazip crawl–parse–publish gates
- Bridge-First hardware (`bridges/`)
- Pricing/ledger/finance SoR (SandPIM pricesheets are a different product)

---

## Phased approach

1. **Now (no SandPIM host):** Expand curated `epc_to_pcdb.json`; keep EPC names on diagram UX; document Auto Care license decision.
2. **When supplier sends ACES/PIES XML:** Python importer in `data-pipeline/` → enrich `stock_items` / `pcdb_part_type_id` / optional attrs; validate with ACESinspector if VCdb licensed.
3. **Optional ops:** Run SandPIM Docker **as a satellite** for human curation of aftermarket lines (brakes/filters-style SKUs), export XML into step 2 — still one-way into Supabase.
4. **Follow-on:** ACES XML **export** for marketplace partners from Supabase (guide §10 already sketched).

---

## License / risk flags

- SandPIM: README MIT; **no LICENSE file / GitHub license null** — confirm before shipping derivative PHP.
- Auto Care VCdb/PCdb/PAdb: **paid membership** — required for compliant ACES/PIES create/validate.
- Stack mismatch (LAMP vs Supabase) → Fork cost unjustified.
- AGPL/GPL: **not** SandPIM’s claimed model; no AGPL flag on SandPIM itself.
- Do not dual-run SandPIM MySQL as a second catalog SoR.

---

## References

- SandPIM README / features: https://github.com/autopartsource/sandpim  
- GTR ACES dual taxonomy: `docs/guides/partsouq-multimake-catalog-pipeline.md` §10  
- Flat load: `docs/guides/erp-catalog-v1-load.md`  
- Hierarchy: `docs/guides/megazip-multivehicle-catalog.md`, `docs/guides/vehicle-cascade-and-epc-browse.md`  
- Adopt-first: `docs/CURSOR_ERP_SAAS_SETUP_GUIDE.md`, `docs/plans/2026-08-02-cursor-oss-strategy-findings.md`
