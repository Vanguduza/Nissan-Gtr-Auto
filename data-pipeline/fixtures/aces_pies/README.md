# Synthetic ACES / PIES fixtures

Sample XML for `python -m data_pipeline.aces_pies_import` and `tests/test_aces_pies.py`.

- **Not** Auto Care Association reference data — PartTerminologyID / BaseVehicleID values are placeholders.
- Production imports need a licensed [Auto Care subscription](https://www.autocare.org/data-standards/subscriptions) (VCdb/PCdb/PAdb) and real supplier or SandPIM-exported XML.
- ACES apply into `part_fitment` is intentionally stubbed until an additive `fitment_source` / `aces_applications` side table + VCdb↔`vehicle_master` bridge exist (EPC bbox/`diagram_path` remain authoritative).
- Whole-catalog PCdb path (no XML): `python -m data_pipeline.aces_pies_import --pcdb-only --bundle …` or `python -m data_pipeline.pcdb_coverage --live-hosted`.
