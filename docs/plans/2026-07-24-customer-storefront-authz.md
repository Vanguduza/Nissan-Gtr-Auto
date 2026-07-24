# Customer storefront AuthZ / API

- Status: draft
- Lane(s): `@backend_agent` (primary); `@web_agent` (follow-on bind); `@finance_agent` only if Payment Entry shape drifts
- Skills needed: `/token-discipline`; `/accounting-ledger` only if settle/JE touched (prefer none)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) — Immediate handoff “Customer storefront AuthZ”
- Unblocks: [`…phase11-12-mobile-scaffold.md`](./2026-07-24-phase11-12-mobile-scaffold.md) blockers 1–4; web checkout
- Decisions: [`customer-self-pay`](../decisions/2026-07-24-customer-self-pay.md), [`paynow-payment-rail`](../decisions/2026-07-24-paynow-payment-rail.md), [`customer-receipt-delivery`](../decisions/2026-07-23-customer-receipt-delivery.md), [`autodoc-shop-features`](../decisions/2026-07-23-autodoc-shop-features.md)

## Goal

Give authenticated customers customer-scoped cart/checkout, own-invoice SELECT, and ContiPay/Paynow **create-intent** AuthZ by extending Phase 5 POS + Phase 13 payments (reuse tables/RPCs — no parallel staff POS clone) so web checkout can bind and mobile can reuse later.

## Acceptance criteria

- [ ] Customer can open/mutate/checkout a cart bound to `customers.profile_id = auth.uid()`; staff POS RPCs unchanged and still `_require_sales_staff`
- [ ] Customer SELECT (RLS and/or RPC) on own `sales_invoices` / lines + lightweight order-status summary; cross-customer denied
- [ ] `create_customer_contipay_intent` / `create_customer_paynow_intent` (or equivalent) succeed only for caller-owned unpaid invoices; settle stays service_role/webhook
- [ ] Multi-currency `USD`|`ZIG` + `exchange_rate_applied` preserved on cart/invoice/intent paths
- [ ] Minimal My Garage table(s) + RLS/RPC for sticky fitment (adopt-soon); wishlist/returns portal deferred
- [ ] Migration(s) after `20260724123000`; RLS on every new table; smoke SQL passes
- [ ] Exclusion grep clean (no ZIMRA, payroll tax, HTML5 QR); types regen in `packages/supabase-client`

## Tables / RPCs (extend, don’t duplicate)

| Object | Notes |
|--------|--------|
| `pos_carts` / lines / `checkout_pos_cart` | Reuse; add `channel` (`pos`\|`storefront`) or rely on `created_by` + ownership helpers — **no second cart schema** |
| Customer cart RPCs | e.g. `create_customer_cart`, `add_customer_cart_line`, `checkout_customer_cart` — SECURITY DEFINER; force `customer_id` from `auth.uid()`; call shared pricing/core-charge logic |
| RLS | `pos_carts`/`pos_cart_lines`/`sales_invoices`/`sales_invoice_lines` — SELECT(+cart write via RPC) where invoice/cart.customer_id → `customers.profile_id = auth.uid()`; staff policies remain |
| Order status | RPC e.g. `get_customer_order` — invoice status, fulfillment_mode, amount_paid/total, optional DN/job **status enum only** (no staff assignee/GPS) |
| Payment intents | Customer create wrappers over ContiPay/Paynow intent tables; keep `mark_*_settled` service-only; Edge `*-initiate` accept customer JWT when RPC allows |
| `customer_garage_vehicles` | id, customer_id, make/model/generation/engine and/or VIN, is_primary, timestamps; RLS own-row; CRUD RPCs or policies |
| Helper | e.g. `_current_customer_id()` mirroring loyalty pattern (`20260724123000`) |

## Migration naming

- `20260724130000_customer_storefront_authz.sql` — helpers, cart/invoice RLS, customer cart + order RPCs, garage table+RLS
- Optional split: `20260724131000_customer_payment_intents_authz.sql` if intent Edge/RPC changes are large
- Smoke: `supabase/tests/customer_storefront_authz_smoke.sql`

## Smoke expectations

1. Auth as customer A: create cart → add line (core charge split) → checkout → see own invoice; customer B cannot SELECT A’s invoice/cart.
2. Customer A creates ContiPay + Paynow intents on own unpaid invoice; denied on B’s invoice; staff settle/webhook still posts once (idempotent).
3. Garage: insert/list own vehicles; cannot read another customer’s garage.
4. Staff POS cart/checkout/intent still works under sales/payments roles.
5. Currency + exchange_rate present on cart/invoice/intent; no ZIMRA markers.

## Paths in scope

- `supabase/migrations/20260724130000_*.sql` (+ optional `…131000_*.sql`)
- `supabase/tests/customer_storefront_authz_smoke.sql`
- `supabase/functions/contipay-initiate`, `paynow-initiate` (customer JWT path; settle/webhook untouched)
- `packages/supabase-client/` types regen; thin query helpers if needed
- Docs: this plan + [`customer-self-pay`](../decisions/2026-07-24-customer-self-pay.md)

## Out of scope

- Full native iOS/Android feature UIs (scaffold only until this lands)
- Wishlist, returns portal, compare, loyalty UI bind, reviews
- Delivery GPS / assignee Realtime for customers; staff logistics mutation
- Payment Entry create/allocate/post by customers; store-credit issue
- Duplicating POS into a separate “web_carts” schema; ZIMRA; payroll tax; HTML5 QR
- `@web_agent` full checkout polish beyond bind readiness (separate follow-on)

## Risks / exclusions

- **AuthZ holes:** never grant customers staff cart/payment RPCs; ownership checks inside SECURITY DEFINER
- **Reuse discipline:** extend Phase 5/13 — do not fork checkout JE/stock-issue paths
- **Secrets:** PSP keys stay Edge env only ([paynow decision](../decisions/2026-07-24-paynow-payment-rail.md))
- Bridge-First unchanged (no browser QR)

## Handoff

1. `@backend_agent` — migration(s) + smoke + Edge initiate AuthZ
2. `/supabase-rls-auditor` → `/security-reviewer` (customer vs staff intent create)
3. `@web_agent` — bind checkout / orders / garage to new RPCs
4. `/verifier` → `/manager` done gate; then schedule mobile feature-bind child plans
