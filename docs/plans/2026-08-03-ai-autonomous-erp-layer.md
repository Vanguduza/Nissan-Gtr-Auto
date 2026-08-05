# AI autonomous ERP layer (CRM · Finance · Stores)

- Status: **Phase A landed + Phase B schedule/opt-in polish** — `ai_worker_schedules` + README cron for `process-crm-promos` / `process-ai-reports`; customer + staff marketing opt-in UI. **Phase C scaffold landed** (StatsForecast stub + Gorse compose note) — models not fitted in prod yet. (Supersedes root `Cursor_AI_Architecture_Prompt.md` for sequencing; **keep that file** as the raw brief.)
- Lane(s): `@backend_agent` (schema, Edge, cron) → `@web_agent` (thin staff wires) → `/security-reviewer` → `/verifier`
- Skills: `/token-discipline`; `/accounting-ledger` only when finance KPI SQL touches CoA/P&L aggregates
- Prior art (do not reinvent):
  - Plan: [`2026-07-25-ai-analytics-staff-reports.md`](./2026-07-25-ai-analytics-staff-reports.md)
  - ADR: [`../decisions/2026-07-25-ai-report-schema-privacy.md`](../decisions/2026-07-25-ai-report-schema-privacy.md)
  - Forecast stub: `forecast_suggestions` + Edge `demand-forecast`
  - OSS note: Gorse / Prophet / Vanna deferred — see [`2026-08-02-open-source-erp-toolkit-audit.md`](./2026-08-02-open-source-erp-toolkit-audit.md) + ADR below
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md)
- Decision: [`../decisions/2026-08-03-ai-autonomous-layer-constraints.md`](../decisions/2026-08-03-ai-autonomous-layer-constraints.md)

## Goal

Ship a **headless-capable** AI layer for three modules on the existing Supabase stack (Edge Functions + SECURITY DEFINER KPI RPCs + Gemini + existing email/SMS/WhatsApp helpers), reusing staff analytics and demand-forecast foundations.

| Module | Outcome |
|--------|---------|
| **CRM promo** | Daily worker finds opt-in customers past purchase inactivity, matches garage → compatible OEMs, LLM copy → SMS/email, cooldown stamp |
| **Finance narrative** | On-demand + scheduled **aggregate** P&L / margin narratives (no Text-to-SQL) |
| **Stores / inventory** | Postgres ABC classification + structured restock/clearance directives over forecast suggestions |

## Non-goals

- Text-to-SQL / Vanna-style freeform SQL against live DB
- Prophet / StatsForecast Python service (Phase 2 satellite under `data-pipeline/`)
- Gorse collaborative filtering (Phase 2 — `infra/satellites/PHASE2_CASBIN_GORSE.md`)
- Meilisearch dual-write (still deferred — PG FTS ADR)
- Auto PO / auto journal posts / ledger edits
- Customer-facing chatbots beyond existing WhatsApp parts finder
- ZIMRA / FDMS / fiscalisation; payroll tax (PAYE/NSSA/brackets)
- Browser/WebView camera, QR, GPS, or printer APIs (Bridge-First N/A here)
- Zero-touch promo **without** marketing opt-in (hard stop)

## Architecture

```
pg_cron / external scheduler
        │
        ├─► process-ai-reports     (existing)  KPI ops_sales_v1 → Gemini → email/WA
        ├─► process-crm-promos     (new)       candidates RPC → Gemini copy → SMS/email
        └─► demand-forecast        (existing)  reorder_point → forecast_suggestions
                 │
staff JWT ──────┼─► analytics-insights (+ finance_performance_v1)
                └─► stores-insights            ABC + suggestions → structured JSON directives

Gemini via _shared/gemini_narrative.ts (fail closed when GEMINI_API_KEY missing)
Channels via _shared/{email_send,sms_gateway,whatsapp_cloud}.ts
AuthZ: staff JWT roles | x-worker-secret (WORKER_SHARED_SECRET)
```

**System of record:** Postgres. LLMs never write money/stock; they only produce narrative / structured suggestions that staff or workers persist as append-only run/directive rows.

## Hard exclusions & security

1. Staff finance/ops narratives: **aggregates only** (existing ADR). No customer PII, invoice dumps, or journal line exports to Gemini.
2. CRM promo: **opt-in only** (`customers.marketing_opt_in`). Payload to Gemini is limited to display name, vehicle labels, OEM list — never ledger/AR/credit data.
3. No freeform SQL from the model. All metrics from named SECURITY DEFINER RPCs.
4. RLS on every new table in the same migration; worker writes via `service_role` / DEFINER helpers.
5. Multi-currency: money aggregates carry `USD` | `ZIG` explicitly; ABC ranking uses invoice `exchange_rate_applied` for USD-equivalent share.
6. Ledger remains append-only; AI never posts or reverses journals.

## Phases

### Phase A — Foundations (this pass) ✅ target

1. Improved plan + ADR (this doc + decision).
2. Migration: CRM promo columns/tables/RPCs; ABC snapshot + stores KPI; `kpi_finance_performance_v1`; RLS.
3. Edge: `process-crm-promos`, `stores-insights`; extend `analytics-insights` + Gemini helpers for finance + structured stores JSON.
4. Thin UI: analytics kpi_set selector (finance); warehouse “AI insights” panel.
5. Docs: `supabase/functions/README.md`, `.env.example` placeholders.

### Phase B — Scheduling & harden

- Documented `pg_cron` / external cron calling workers with `x-worker-secret`.
- Smoke SQL for promo candidates, ABC, finance KPI authz.
- Optional: enrich `generate_forecast_suggestions` with velocity for Tier A (still no auto-PO).

### Phase C — Satellites (scaffold)

- StatsForecast/Prophet in `data-pipeline/` writing into `forecast_suggestions.reason`
  — module `data_pipeline.forecast_statsforecast` + optional `.[forecast]` extra; CI uses stub.
- Gorse event feed for “bought together” kits (Apache-2.0) — compose profile stub in
  `docker-compose.satellites.yml`; docs `infra/satellites/PHASE2_PROPHET_GORSE.md`.
- OmniRoute-style multi-provider LLM gateway only if Gemini lock-in becomes a problem (dev-tooling research — optional/caution).

**Scaffold acceptance (2026-08-03):** stub reason JSON + unit tests; no auto-PO; no Gorse container in default compose.

## Acceptance criteria (Phase A)

1. `customers.marketing_opt_in` + `last_promotional_message_at`; promo tables with RLS; `list_crm_promo_candidates` / run helpers exist.
2. Edge `process-crm-promos` refuses without worker secret; skips non-opt-in; respects cooldown; fail-closed template copy when Gemini missing still may send numeric/OEM template if channels configured (or skip LLM-only — prefer **template fallback**, never invent prices).
3. `kpi_finance_performance_v1` returns P&L account aggregates + margin summary by currency; `analytics-insights` accepts `kpi_set: "finance_performance_v1"`; no Text-to-SQL.
4. `run_inventory_abc_classification` + `kpi_stores_forecast_v1`; Edge `stores-insights` returns `{ kpis, directives, narrative?, gemini_used }` with JSON-schema-shaped directives when Gemini present.
5. Staff routes: finance narrative selectable on `/staff/analytics`; stores insights under `/staff/warehouse/insights` for `admin|warehouse`.
6. Exclusion grep clean: no ZIMRA/PAYE/NSSA/HTML5 QR introduced.
7. Original root prompt file left unchanged.

## Paths in scope

| Area | Paths |
|------|--------|
| Docs | `docs/plans/2026-08-03-ai-autonomous-erp-layer.md`, `docs/decisions/2026-08-03-ai-autonomous-layer-constraints.md` |
| Migration | `supabase/migrations/20260803150000_ai_autonomous_crm_stores_finance.sql` |
| Edge | `process-crm-promos/`, `stores-insights/`, `_shared/gemini_narrative.ts`, `analytics-insights/`, `config.toml`, `functions/README.md` |
| Web | `apps/web/.../staff/analytics`, `staff/warehouse/insights`, `staff-auth`, thin lib |
| Env | `.env.example` |

## Manual tests

```bash
# Finance narrative (staff JWT)
curl -sS -X POST "$SUPABASE_URL/functions/v1/analytics-insights" \
  -H "Authorization: Bearer $STAFF_JWT" \
  -H "Content-Type: application/json" \
  -d '{"from":"2026-07-01T00:00:00Z","to":"2026-08-01T00:00:00Z","kpi_set":"finance_performance_v1"}'

# CRM promo worker
curl -sS -X POST "$SUPABASE_URL/functions/v1/process-crm-promos" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "x-worker-secret: $WORKER_SHARED_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"limit":10,"force":false}'

# Stores insights (staff JWT)
curl -sS -X POST "$SUPABASE_URL/functions/v1/stores-insights" \
  -H "Authorization: Bearer $STAFF_JWT" \
  -H "Content-Type: application/json" \
  -d '{"warehouse_id":"<uuid>","include_directives":true}'
```

## Handoff

1. `@backend_agent` — migration + Edge + README
2. `@web_agent` — analytics kpi_set + warehouse insights
3. `/security-reviewer` + `/supabase-rls-auditor`
4. `/verifier`
5. Phase B cron wiring when ops ready — **partially done**: `ai_worker_schedules` + README curl cadences; external/pg_cron still ops-owned
6. Phase C satellites (Prophet/Gorse) — **scaffold landed**: `forecast_statsforecast` + `PHASE2_PROPHET_GORSE.md` + commented Gorse profile; fit + compose enablement still ops/follow-up.
