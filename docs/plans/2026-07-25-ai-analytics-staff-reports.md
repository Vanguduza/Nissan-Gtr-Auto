# AI analytics + automated staff reports

- Status: draft
- Lane(s): `@backend_agent` (schema, KPIs, Edge) → `@web_agent` (staff UI + RBAC)
- Skills needed: (none); consult `/accounting-ledger` only if KPI SQL touches journal/CoA
- Decision: [`../decisions/2026-07-25-ai-report-schema-privacy.md`](../decisions/2026-07-25-ai-report-schema-privacy.md)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md)

## Goal

Vertical slice: staff KPI RPCs (aggregates only) → subscription/run/delivery tables + RLS → Gemini narrative Edge + cron worker (email/WhatsApp) → `/staff/analytics` UI for admin/finance/sales.

## Acceptance criteria

1. SECURITY DEFINER KPI RPCs return **aggregates only** (counts, sums by currency, top SKUs by qty/revenue) — no customer PII, no journal line dumps, no ledger exports to Gemini.
2. Tables `ai_report_subscriptions`, `ai_report_runs`, `ai_report_deliveries` exist with RLS (staff role-gated; service_role for worker writes).
3. Edge `analytics-insights`: authz staff JWT + role; body = KPI payload; if `GEMINI_API_KEY` missing → **fail closed** for narrative (`narrative: null`, error flag); numeric payload still returned.
4. Edge `process-ai-reports`: `x-worker-secret` / `WORKER_SHARED_SECRET`; due `daily|weekly|monthly` subs → KPI RPC → optional Gemini → email + WhatsApp via `_shared/email_send.ts` + `_shared/whatsapp_cloud.ts`; numeric-only delivery when Gemini absent.
5. Staff routes `/staff/analytics` and `/staff/analytics/subscriptions` gated for `admin` | `finance` | `sales` in `staff-auth.ts` + StaffNav.
6. `.env.example` placeholders only (no real keys): `GEMINI_API_KEY`, `REPORT_FROM_EMAIL` (fallback `EMAIL_FROM`), WhatsApp vars, `WORKER_SHARED_SECRET`; cron documented in `supabase/functions/README.md`.
7. Manual trigger documented: `curl` daily run with `x-worker-secret` + `{"cadence":"daily","force":true}`.
8. No ZIMRA, no payroll tax, no browser QR/hardware.

## Paths in scope

| Area | Paths |
|------|--------|
| Migration | `supabase/migrations/20260725XXXXXX_ai_analytics_reports.sql` |
| Types | `packages/supabase-client/` (regen) |
| Edge | `supabase/functions/analytics-insights/`, `process-ai-reports/`; reuse `_shared/{email_send,whatsapp_cloud,worker_auth}.ts`; `config.toml`; `functions/README.md` |
| Web | `apps/web/app/(staff)/staff/analytics/**`, `apps/web/lib/staff-auth.ts`, StaffNav |
| Env/docs | `.env.example`, this plan, ADR |

## Migration outline

- **Enums:** `ai_report_cadence` (`daily`,`weekly`,`monthly`); `ai_report_run_status` (`pending`,`running`,`succeeded`,`partial`,`failed`); `ai_delivery_channel` (`email`,`whatsapp`); `ai_delivery_status` (`queued`,`sent`,`failed`,`skipped`).
- **`ai_report_subscriptions`:** `id`, `created_by`, `cadence`, `channels[]`, `recipient_emails[]`, `recipient_whatsapp_e164[]`, `include_narrative bool`, `kpi_set text` (e.g. `ops_sales_v1`), `timezone`, `active`, `last_run_at`, timestamps. RLS: select/insert/update for `has_staff_role({admin,finance,sales})`; no anon.
- **`ai_report_runs`:** `subscription_id`, `cadence`, `period_start/end`, `status`, `kpi_json jsonb` (aggregates), `narrative text null`, `error text null`, `gemini_used bool`, `started_at`/`finished_at`. RLS: staff read; writes service_role / SECURITY DEFINER helpers.
- **`ai_report_deliveries`:** `run_id`, `channel`, `recipient`, `status`, `provider_ref`, `error`, `sent_at`. Same RLS pattern.
- **RPCs (names):**
  - `kpi_sales_summary(p_from timestamptz, p_to timestamptz)` → orders/revenue by currency, order count, AOV
  - `kpi_inventory_summary()` → on-hand qty, low-stock count, quarantine count (no customer ids)
  - `kpi_top_skus(p_from, p_to, p_limit int)` → oem/sku, qty, revenue by currency
  - `list_due_ai_report_subscriptions(p_cadence, p_now)` — worker/service_role
  - `insert_ai_report_run` / `finalize_ai_report_run` / `insert_ai_report_delivery` — worker helpers
- All KPI RPCs: `has_staff_role({admin,finance,sales})` (or service_role for worker).

## Edge contracts

**`POST /functions/v1/analytics-insights`** (JWT staff)

```json
{ "from": "ISO", "to": "ISO", "kpi_set": "ops_sales_v1", "include_narrative": true }
```

Response: `{ "kpis": {...}, "narrative": string|null, "gemini_used": bool, "error": string|null }`  
Gemini missing + `include_narrative` → `narrative: null`, `gemini_used: false`, non-2xx **or** 200 with `error: "gemini_unavailable"` (prefer **422** for interactive UI; worker treats as soft-fail for narrative only).

**`POST /functions/v1/process-ai-reports`** (worker secret)

```json
{ "cadence": "daily"|"weekly"|"monthly", "force": false, "subscription_id": "uuid?" }
```

Flow: due subs → KPI RPCs → if `include_narrative` && Gemini key → narrative else numeric-only → send channels → write runs/deliveries. Partial channel failure → run `partial`.

## UI routes

- `/staff/analytics` — date range, call `analytics-insights` / KPI RPCs, show numbers + narrative (or “numeric only” banner).
- `/staff/analytics/subscriptions` — CRUD active subs (cadence, channels, recipients, include_narrative).
- RBAC: extend `STAFF_NAV`, `pageRoles`, `requiredRolesForPath` for `/staff/analytics*`.

## Secrets (placeholders only)

| Secret | Use |
|--------|-----|
| `GEMINI_API_KEY` | Narrative; never commit; copy from local Claude-mem settings at deploy time |
| `REPORT_FROM_EMAIL` | Report From; fallback `EMAIL_FROM` / `RECEIPT_FROM_EMAIL` |
| `EMAIL_API_KEY` / `RESEND_API_KEY` | Existing email helper |
| `WHATSAPP_*` | Existing Cloud API helper |
| `WORKER_SHARED_SECRET` | Cron AuthZ |

Cron: schedule `process-ai-reports` daily/weekly/monthly (or one daily cron with cadence filter) — document in `supabase/functions/README.md` alongside SMS/receipts workers.

## Manual test (daily)

```bash
curl -sS -X POST "$SUPABASE_URL/functions/v1/process-ai-reports" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "x-worker-secret: $WORKER_SHARED_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"cadence":"daily","force":true}'
```

Verify: run row + delivery rows; email/WA received; without Gemini → numeric body, `gemini_used=false`.

## Out of scope

- Android management analytics UI; Meilisearch; customer-facing AI; PAYE/ZIMRA; editing ledger; streaming chat; PDF report attachments (text body OK); multi-tenant orgs beyond current staff roles.

## Risks / exclusions

- Never send raw order/customer/ledger rows to Gemini — aggregates + anonymized top SKUs only.
- Fail closed on narrative when Gemini missing; **do not** block numeric delivery in worker.
- Bridge-First N/A (no camera/QR).

## Handoff

1. `@backend_agent` — migration + RPCs + both Edge functions + README/cron + `.env.example`
2. `@web_agent` — analytics pages + `staff-auth` RBAC
3. `/security-reviewer` — RLS + worker secret + no PII to Gemini
4. `/supabase-rls-auditor` after migration
5. `/verifier`
6. `/manager` done gate; optional master-plan bullet: “AI staff analytics + scheduled reports (v1)”
