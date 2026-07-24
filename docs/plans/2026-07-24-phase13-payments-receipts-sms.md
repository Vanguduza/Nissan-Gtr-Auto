# Phase 13 — Payments, ContiPay + Paynow, manager SMS, customer receipts, forecast

- Status: draft
- Lane(s): `@backend_agent` (primary); `@finance_agent` (journal/allocation review); `@web_agent` (signed receipt download route + dual-currency settlement display); `@management_app_agent` (manager SMS prefs + POS contact capture follow-on)
- Skills needed: `/accounting-ledger` (Payment Entry / store credit JEs); (none for ContiPay/Paynow secrets — use env placeholders only)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 13
- Decisions: [`customer-receipt-delivery`](../decisions/2026-07-23-customer-receipt-delivery.md), [`manager-sms-key-events`](../decisions/2026-07-23-manager-sms-key-events.md), [`company-domain`](../decisions/2026-07-23-company-domain.md), [`paynow-payment-rail`](../decisions/2026-07-24-paynow-payment-rail.md)
- Prior: Phase 5 enqueue (`customer_receipt_outbox`, `enqueue_customer_receipts`); Phase 1b `sms_outbox` + `emit_domain_event`; CoA `1100` Cash & Bank, `1200` AR, `2200` Customer Deposits / Store Credit; Phase 8 `material_requests`

## Goal

Wire Payment Entry (multi-invoice, multi-tender, dual-currency), ContiPay **and Paynow** capture (server secrets only), manager SMS send from existing outbox, customer receipt PDF + SMS/email/WhatsApp delivery per decisions, and lightweight demand-forecast → Material Request suggestions — no ZIMRA/fiscal.

## Acceptance criteria

- [ ] Payment Entry allocates cash / bank / ContiPay / Paynow / store-credit across one or many invoices (partial OK); AR/`amount_paid` updates correctly in USD|ZIG with `exchange_rate_applied` stored
- [ ] Store credit issue (refund/overpay) and redeem post balanced, append-only journals (Dr/Cr via `2200` ↔ AR/Cash); no JE edits/deletes
- [ ] ContiPay initiate + webhook settle EcoCash / Visa 3DS / ZimSwitch; Paynow initiate + result/status settle (mobile money / card); secrets only in Edge Function env (never client or plan docs); dual-currency settlement display fields on payment/receipt
- [ ] Manager ops SMS: worker drains `sms_outbox` for opted-in prefs only; gateway may be stubbed; idempotent `(event_code, dedupe_key)`; emits `payment_received` | `payment_failed` | `payment_partial` | `refund_issued` once per occurrence
- [ ] Customer receipts: PDF (tax-agnostic, no fiscal QR) → private Storage → signed URL on `https://nissangtrauto.co.zw/...`; SMS = summary + PDF link at bottom; email/WhatsApp deliver same PDF when contact+pref present; idempotent per `(document_id, channel)` unless explicit resend
- [ ] Failed sends: retry/log via outbox `attempt_count` / `last_error`; success path does not duplicate spam
- [ ] Forecast RPC/edge suggests reorder qty → `create_material_request` (or draft MR rows); no auto-PO without staff submit
- [ ] RLS on every new table in same migration(s); exclusion grep clean (no ZIMRA / payroll tax / browser QR)

## Paths in scope

- Migrations (after `20260724081000`):
  - `20260724090000_payment_entries_store_credit.sql` — payment + allocation + store credit ledger tables/RPCs/RLS
  - `20260724091000_payment_mutation_guards.sql` — AuthZ / over-allocate / immutable posted payment guards
  - `20260724092000_contipay_payment_intents.sql` — intents + webhook idempotency store (no secret values)
  - `20260724095000_paynow_payment_intents.sql` — Paynow tender + intents + webhook idempotency (alongside ContiPay)
  - `20260724093000_receipt_delivery_artifacts.sql` — PDF path columns, short-link/token if needed, Storage bucket policies note
  - `20260724094000_demand_forecast_suggestions.sql` — forecast suggestion table + RPC → MR hook
- Edge functions (`supabase/functions/`): `contipay-initiate`, `contipay-webhook`, `paynow-initiate`, `paynow-webhook`, `process-sms-outbox` (stub OK), `process-customer-receipts` (PDF render + channel send), optional `demand-forecast`
- `supabase/tests/phase13_payments_receipts_smoke.sql`
- `packages/shared/` — payment/tender types, receipt summary builder, ContiPay + Paynow status enums (no secrets)- `packages/supabase-client/` — types regen
- `@web_agent` follow-on: public receipt download route on company domain; checkout settlement display
- `@management_app_agent` follow-on: manager SMS prefs UI; POS phone/email/WhatsApp already on invoice — prefs only

## Tables / RPCs / edge (sketch)

| Object | Notes |
|--------|--------|
| `payment_entries` | `PE-` series; customer; currency; exchange_rate; tender (`cash`\|`bank`\|`contipay`\|`paynow`\|`store_credit`); status draft→posted/cancelled; `posted_by`/`posted_at`; JE link |
| `payment_allocations` | payment_entry_id → sales_invoice_id; amount; currency; unique partials OK until invoice cleared |
| `store_credit_accounts` / `store_credit_ledger` | per-customer balance; append-only movements (issue/redeem/adjust via reversing) |
| `contipay_payment_intents` | external ref, method (ecocash/visa/zimswitch), amount, currency, status, webhook payload hash; **no API keys in DB** |
| `paynow_payment_intents` | external ref, method (ecocash/onemoney/innbucks/visa), amount, currency, status, webhook payload hash; **no API keys in DB** |
| `receipt_pdf_artifacts` (or columns on outbox) | `pdf_storage_path`, signed URL expiry metadata; bucket `customer-receipts` private |
| `forecast_suggestions` | stock_item_id, suggested_qty, horizon, reason/json; optional `material_request_id` |
| RPCs | `create_payment_entry`, `allocate_payment`, `post_payment_entry`, `cancel_payment_entry` (reversing JE), `issue_store_credit`, `redeem_store_credit`, `create_contipay_intent` / `mark_contipay_settled`, `create_paynow_intent` / `mark_paynow_settled` (service), `process_receipt_outbox_batch` hooks, `generate_forecast_suggestions`, `create_mr_from_forecast` |
| Edge | ContiPay + Paynow HMAC/hash webhook verify via env; SMS/email/WhatsApp adapters behind interface (stub → real); PDF via server lib |

## Out of scope

- ZIMRA / FDMS / fiscal QR / tax-authority payloads on PDF or SMS
- Marketing / My Garage promo SMS (separate channel; defer)
- Browser/WebView QR or HTML5 geolocation; hardware printers (Bridge-First Phase 12 if thermal reprint)
- Full ContiPay / Paynow production credential values in repo/plan (placeholders + env names only)
- Auto-submit PO from forecast; supplier portal AP payment runs
- Management Android full Payment Entry UI polish; PowerSync offline (Phase 14)
- DN-as-receipt copy; inventing ContiPay/Paynow API shapes beyond initiate/webhook/settle status

## Risks / exclusions

- **Secrets:** ContiPay + Paynow keys/HMAC/hash only in Edge secrets / CI env — cite sandbox allowlist (`docs/CURSOR_BEST_PRACTICES.md`); never commit
- **Duplicate receipts:** prefer one receipt per completed sale; payment-only receipt only if sale receipt not already sent ([customer-receipt-delivery](../decisions/2026-07-23-customer-receipt-delivery.md))
- **Manager vs customer SMS:** never mix catalogs; customer channel ≠ `sms_outbox` ops path
- **Ledger immutability:** cancel/refund via reversing entries; store credit redeem must not overdraw
- **Multi-currency:** never assume USD; allocation currency must match invoice or explicit conversion with stored rate
- POS must not block sale if receipt send fails (already enqueue-first in Phase 5)

## Smoke expectations

1. Post invoice → `create_payment_entry` partial cash → `amount_paid` up; AR JE balanced; second payment clears; over-allocate denied.
2. ContiPay intent → stub/webhook settle → payment posted; `payment_received` in `domain_events` + `sms_outbox` only for enabled managers; duplicate webhook no double post.
3. Refund/overpay → store credit issue (`2200`); redeem on next PE; balance never negative.
4. Seed `customer_receipt_outbox` pending → worker generates PDF (no fiscal markers) → SMS body ends with `nissangtrauto.co.zw` link; email/WhatsApp rows `sent`; re-run does not duplicate successful channels.
5. Stub SMS gateway marks manager outbox `sent`/`failed` with retries; prefs-off manager gets no row.
6. Forecast suggestions for low-stock item → `create_mr_from_forecast` → draft/submitted MR; no PO until existing procurement RPC.

## Handoff

1. Implement schema/RPCs/edge stubs + smoke in `@backend_agent` (`/accounting-ledger` for JE shape)
2. `/supabase-rls-auditor` → `/security-reviewer` (ContiPay webhook, signed URLs, PII on receipts/SMS)
3. `@web_agent` receipt download route + settlement display; `@management_app_agent` SMS prefs
4. `/verifier` (exclusions + no secrets in client)
5. `/manager` done gate
