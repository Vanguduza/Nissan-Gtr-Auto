# Phase C AI satellites — StatsForecast/Prophet + Gorse (scaffold)

**Status:** scaffold only (2026-08-03). No production rewrite of Postgres ABC / `forecast_suggestions`.
No compose services required for day-to-day ERP.

Parent plan: [`docs/plans/2026-08-03-ai-autonomous-erp-layer.md`](../../docs/plans/2026-08-03-ai-autonomous-erp-layer.md).
Gorse / Casbin notes: [`PHASE2_CASBIN_GORSE.md`](./PHASE2_CASBIN_GORSE.md).

---

## StatsForecast / Prophet (`data-pipeline`)

| | |
|--|--|
| **License** | StatsForecast MIT; Prophet (Meta) — check current license before prod |
| **Module** | `data_pipeline.forecast_statsforecast` |
| **Extra** | `pip install -e ".[forecast]"` (optional; CI uses stub without it) |
| **Write target** | `forecast_suggestions.reason` JSON only |

### Out of scope now

- Auto purchase orders / auto journal posts
- Replacing `generate_forecast_suggestions` SQL
- ZIMRA / payroll tax

### Smoke

```bash
cd data-pipeline
python -m data_pipeline.forecast_statsforecast
pytest tests/test_forecast_statsforecast.py -q
```

---

## Gorse (bought-together kits)

| | |
|--|--|
| **License** | Apache-2.0 |
| **Compose profile** | `recommend` in `docker-compose.satellites.yml` (commented stub) |

Feed events from completed sales + quarantine returns only. UI stays in POS / storefront.

```bash
# When ops enables the profile:
# docker compose -f docker-compose.satellites.yml --profile recommend up -d
```

---

## Ordering

Prefer Phase A/B AI workers (CRM promo, finance narrative, stores insights) before enabling
Prophet/Gorse in production. Meilisearch remains optional and independent.
