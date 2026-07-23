# Phase 5 — Sales, cart, invoices, commercial controls

- Status: draft
- Lane(s): `@backend_agent` (migrations, RPCs, shared); `@management_app_agent` (**API contracts only** — no Android UI yet)
- Skills needed: `/accounting-ledger`
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 5
- Depends on: Phase 3 (`post_journal_entry`, `next_series_value` / `SINV-`), Phase 4 (`parseInventoryQrPayload`, stock issue / `post_return_to_quarantine`)

## Goal

Ship sales documents, POS cart (core-charge parent/child), commercial pricing/credit controls, and early customer-receipt + manager domain emits — without payment gateways or PDF send.

## Acceptance criteria

- [ ] Cart insert splits core charge via `splitCoreCharge` → parent part line + child deposit line (`4200`)
- [ ] Sales invoice + credit note + return-against-invoice; Draft → Submit → Cancel; naming `SINV-` / `CN-`
- [ ] Submit posts sale/return/COGS journals via `post_journal_entry`; cancel = reverse linkage (immutable submitted rows)
- [ ] Counter/POS: scan payload → resolve OEM/batch → price-list resolve → cart add (RPC; Bridge-First only)
- [ ] Credit limit / credit hold blocks submit (override role allowed); emits `order_on_hold`
- [ ] Partial fulfill / backorder: line `qty_ordered` vs `qty_fulfilled`; open qty remains until stock available
- [ ] Emits manager events: `order_received`, `order_completed`, `order_cancelled`, `order_on_hold`, `return_initiated`, `return_completed` (+ `large_order` if threshold met)
- [ ] Posted invoice/return enqueues `customer_receipt_outbox` rows (`pending`) per contact/channel; idempotent `(document_type, document_id, channel)` — no PDF/SMS gateway yet

## Paths in scope

| Area | Paths |
|------|--------|
| Migration | `supabase/migrations/20260723*_sales_pos.sql` (customers, price lists, cart/order/invoice/CN, receipt outbox, RPCs, RLS) |
| Shared | `packages/shared/src/cart.ts` (extend), `ledger/journal.ts` (return/core helpers), pricing helpers, `notifications/` if receipt emit args needed |
| Client types/queries | `packages/supabase-client/` |
| Tests | `supabase/tests/phase5_sales_smoke.sql` |
| Contracts (mgmt) | thin RPC/DTO notes under `packages/supabase-client` or `docs/` API shapes for POS — **not** `apps/android-management` screens |

**Reuse (do not reinvent):** `splitCoreCharge`, `saleJournalLines`, `post_journal_entry`, `next_series_value('SINV-')`, `emit_domain_event` / `SMS_EVENT_CODES`, `parseInventoryQrPayload` / `gtr://part/…`, CoA `4100`/`4110`/`4200`/`5100`/`1200`/`1100`/`1310`, `post_return_to_quarantine`.

## Schema sketch (implement in one migration)

- **customers** — profile link optional; `default_price_list_id`; `credit_limit` + currency; `credit_hold`; receipt prefs (`sms_receipts`, `email_receipts`, `whatsapp_receipts`); contacts `phone_e164` / `email` / `whatsapp_e164`
- **price_lists** + **price_list_items** (retail / B2B / fleet) + **customer_price_overrides** (simple SKU/customer rule)
- **sales_orders** / **sales_order_lines** — open qty for backorder; status includes `on_hold`
- **carts** / **cart_lines** — `parent_line_id`, `line_kind` `part` \| `core_charge`
- **sales_invoices** / lines — docstatus draft/submitted/cancelled; `name` from series; links order; currency + `exchange_rate_applied`
- **credit_notes** / lines — against invoice; return qty → Quarantine path
- **customer_receipt_outbox** — `document_type`, `document_id`, `channel` (`sms`\|`email`\|`whatsapp`), `status` (`pending`…), payload stub; unique `(document_type, document_id, channel)`
- Seed naming series **`CN-`** ( `SINV-` already in Phase 3)

## Out of scope

- ContiPay / Payment Entry / store-credit redeem (Phase 13)
- PDF render, signed URLs, SMS/email/WhatsApp **send** workers (Phase 13)
- HTML5 / browser QR; physical bridge implementations (Phase 12)
- Warranty claims UI (Phase 5b); Delivery Note / pick-pack (Phase 10)
- Web storefront checkout UI (Phase 6 consumes these APIs)
- Attachments / comment timeline (soft; skip unless trivial)
- ZIMRA / fiscal QR / tax fields

## Risks / exclusions

| Risk | Mitigation |
|------|------------|
| Revenue vs core deposit mixed | Enforce parent/child at cart insert; journal `4100` vs `4200` separately |
| Return restocks saleable | Always `post_return_to_quarantine` + `4110` / AR reverse |
| Credit hold bypass | Submit RPC checks hold/limit; override via explicit staff role only |
| Duplicate receipts / SMS | Outbox unique key; manager events use catalog codes + dedupe_key |
| ContiPay / HTML5 QR / ZIMRA creep | Hard exclude; smoke grep |

## Ordered implementation (max 8)

1. Customers + price lists/overrides + credit fields (RLS)
2. Cart/order/invoice/CN tables + `CN-` series; Draft/Submit/Cancel RPCs
3. Core-charge split on cart insert; price resolve before totals
4. POS scan→resolve→cart RPC (shared QR parse; no browser scan)
5. Submit: stock issue + `post_journal_entry` (sale/COGS/core); cancel reverse
6. Credit hold/limit → block submit + `order_on_hold`; partial fulfill open qty
7. Credit note / return-against-invoice → Quarantine + return journals + return events
8. `customer_receipt_outbox` enqueue on invoice/return post + manager `order_*` / `return_*` emits; smoke test

## Handoff

1. `/manager` opens Phase 5 → `@backend_agent` implements (primary)
2. `@management_app_agent` consumes RPC contracts when wiring POS later (Phase 12)
3. `/supabase-rls-auditor` → `/security-reviewer` → `/verifier`
4. `/manager` exit gate → Phase 5b or Phase 6
