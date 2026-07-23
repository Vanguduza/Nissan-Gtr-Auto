# Phase 5 — Sales, cart, invoices, commercial controls

- Status: **done**
- Lane(s): `@backend_agent` (migrations, RPCs, shared); `@management_app_agent` (**API contracts only**)
- Skills needed: `/accounting-ledger`
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 5

## Goal

Ship sales documents, POS cart (core-charge parent/child), commercial pricing/credit controls, and early customer-receipt + manager domain emits — without payment gateways or PDF send.

## Acceptance (shipped)

- [x] Cart insert splits core charge (parent + child deposit line)
- [x] Sales invoice + credit note return-against-invoice; `SINV-` / `CN-`
- [x] Checkout posts sale/COGS/core journals via `post_journal_entry`
- [x] `add_cart_line_from_qr` (Bridge-First payload; no browser QR)
- [x] Credit hold / limit → `on_hold` + `order_on_hold`
- [x] `qty_fulfilled` on lines (partial fulfill fields present; full fulfill on checkout v1)
- [x] Manager events: `order_received`, `order_completed`, `order_on_hold`, `large_order`, `return_*`, `quarantine_received`
- [x] `customer_receipt_outbox` enqueue (sms/email/whatsapp) — PDF link placeholder until Phase 13

## Shipped artifacts

| Artifact | Notes |
|----------|-------|
| `20260723230000_sales_pos.sql` | customers, prices, cart, invoices, RPCs, outbox |
| `packages/shared/.../customer-receipt.ts` | SMS summary helper |
| `supabase/tests/phase5_sales_smoke.sql` | smoke |

## Handoff

Phase 5 complete → **Phase 6** storefront (preferred) or **5b** warranty / **4b** cycle count.
