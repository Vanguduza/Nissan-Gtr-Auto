# AI autonomous layer — constraints (CRM · Finance · Stores)

- Date: 2026-08-03
- Lane: `@backend_agent` (schema/Edge); `@web_agent` (thin staff UI)
- Status: accepted
- Plan: [`../plans/2026-08-03-ai-autonomous-erp-layer.md`](../plans/2026-08-03-ai-autonomous-erp-layer.md)
- Supersedes (for sequencing only): root `Cursor_AI_Architecture_Prompt.md` — file kept as raw brief
- Complements: [`2026-07-25-ai-report-schema-privacy.md`](./2026-07-25-ai-report-schema-privacy.md)

## Decision

1. **Adopt-first on existing AI stack** — Extend `analytics-insights` / `process-ai-reports` / `gemini_narrative` / `forecast_suggestions`. Do not introduce a parallel LLM microservice for Phase A.
2. **No Text-to-SQL** — Reject Vanna-style freeform SQL. Finance narratives consume `kpi_finance_performance_v1` (P&L account aggregates via existing report patterns). Models never invent SQL.
3. **CRM outreach is opt-in, not absolute zero-touch** — `customers.marketing_opt_in` must be true; cooldown via `last_promotional_message_at`. Gemini receives only name + garage labels + OEM candidates — never AR/credit/ledger.
4. **Stores forecasting stays Postgres-first** — ABC classification + reorder-based `forecast_suggestions` in SQL. Prophet/StatsForecast and Gorse are Phase C satellites (permissive licenses only when adopted).
5. **Structured outputs** — Stores directives and promo copy use typed JSON / constrained prompts so Kotlin/Compose and web UIs do not parse freeform hallucinations as quantities.
6. **Fail closed on narrative / directives** when `GEMINI_API_KEY` missing; CRM may use a deterministic OEM template fallback; workers never fake channel “sent” without gateway secrets (existing channel helpers).

## Why

The root architecture brief asks for fully autonomous CRM/Finance/Stores AI. The monorepo already has aggregates-only staff reports and a demand-forecast stub. Absolute zero-touch marketing and Text-to-SQL conflict with privacy ADR, injection risk, and hard exclusions. Opt-in + named RPCs keep autonomy where it is safe.

## Consequences

- Implementers schedule Edge workers with `WORKER_SHARED_SECRET`; document cron in `supabase/functions/README.md`.
- Marketing UI must expose opt-in (customer account or staff CRM) before promo volume is meaningful — stub column + worker is enough for Phase A.
- No ZIMRA / payroll-tax / Bridge-First violations in this layer.
