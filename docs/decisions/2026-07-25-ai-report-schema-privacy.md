# AI staff reports — aggregates only + fail-closed narrative

- Date: 2026-07-25
- Lane: `@backend_agent` (schema/Edge); `@web_agent` (staff UI)
- Status: accepted
- Plan: [`../plans/2026-07-25-ai-analytics-staff-reports.md`](../plans/2026-07-25-ai-analytics-staff-reports.md)

## Decision

1. **KPI → Gemini:** Only SECURITY DEFINER aggregate RPCs feed narrative prompts (currency totals, counts, top SKUs by OEM/qty/revenue). No customer names/phones/emails, no invoice line dumps, no journal/ledger exports.
2. **Tables:** `ai_report_subscriptions`, `ai_report_runs`, `ai_report_deliveries` with RLS for `admin|finance|sales`; worker writes via service_role / DEFINER helpers.
3. **Gemini absent:** Interactive `analytics-insights` fails closed for narrative; cron `process-ai-reports` still delivers **numeric-only** email/WhatsApp when `include_narrative` cannot be fulfilled.
4. **Channels:** Reuse `_shared/email_send.ts` and `_shared/whatsapp_cloud.ts`; AuthZ via existing `worker_auth` / staff JWT — no new browser hardware.

## Why

Keeps scheduled AI insights useful without leaking PII or mutable ledger detail to a third-party model, and keeps report delivery resilient when the model key is unset.

## Consequences

- Implementers copy `GEMINI_API_KEY` into Edge secrets from a local vault (e.g. Claude-mem settings) — never commit keys into docs or `.env`.
- UI must show a clear “numeric only” state when narrative is unavailable.
- No ZIMRA / payroll-tax surfaces in this feature.
