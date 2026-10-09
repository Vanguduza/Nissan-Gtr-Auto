# Catalog APK quality gates

## Pass / fail (hard)

A job or on-device test is **PASS** only when `bundle_quality.txt` reports `ok=true`.

| Result | Meaning |
|--------|---------|
| **PASS** | Megazip or PartSouq bundle has complete fitments (chassis + bbox + `diagram_path`) and diagram artifacts; `quality_report.publishable` / §2c filter green |
| **FAIL** | Hierarchy-only `catalog_models.json`, `diagrams=0`, custom/7zap/catcar adapters, smoke `--max-pages` that exits before diagram depth, or gate reasons in `bundle_quality.txt` |
| **RUNNING** | Heartbeat `phase=running` with `diagrams=0` is **not** success — still crawling/parsing |

## Scripts

```bash
# After adb pull of a job out-root:
python apps/catalog-apk/scripts/assert_bundle_quality.py /path/to/job/out
# exit 0 = PASS, 1 = FAIL
```

Embedded path: `catalog_worker.run_job` calls `data_pipeline.bundle_quality_gate` after the orchestrator; non-zero exit → Room status **FAILED** with `errorMessage` from the gate.

## Laptop vs phone (known gaps that were fixed)

1. PartSouq `--single-chassis` was accepted but **not** applied to the priority claim filter → full maker tree. Now writes `_apk_priority_<CODE>.json` and passes `--priority-chassis-file`.
2. Megazip single-chassis still enqueued the maker hub (90+ models) before seeds → `seed_only` skips hub when model seeds exist.
3. Megazip `assert_publishable` only ran with `--live-import` → now runs whenever `--strict-gate` (default on).
4. Custom engines lied with `publishable=true` from HTML page counts → always `publishable=false` / not diagram SoR.
5. Job UI marked **COMPLETE** on orchestrator exit 0 without reading quality → worker gate + `CatalogCrawlWorker` require `bundle_quality.txt` `ok=true`.

FlareSolverr sidecar (8191) remains required for PartSouq CF targets (same as laptop).
