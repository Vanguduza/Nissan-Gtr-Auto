# Customer self-pay (ContiPay + Paynow) on storefront

- Date: 2026-07-24
- Lane: `@backend_agent` (AuthZ/RPCs); `@web_agent` (checkout UI bind)
- Status: accepted
- Related: [`paynow-payment-rail`](./2026-07-24-paynow-payment-rail.md), [`customer-receipt-delivery`](./2026-07-23-customer-receipt-delivery.md), [`autodoc-shop-features`](./2026-07-23-autodoc-shop-features.md)
- Unblocks: Phase 11–12 feature bind blocker #3 ([`…phase11-12-mobile-scaffold.md`](../plans/2026-07-24-phase11-12-mobile-scaffold.md))

## Decision

Authenticated customers **may create ContiPay and Paynow payment intents for their own unpaid invoices** (and carts that check out to those invoices) on the **web storefront**, and later on customer mobile using the same RPCs. **Settle remains service_role / webhook only** — clients never settle, never hold PSP secrets.

**Pay-at-counter / click-and-collect unpaid** remains a valid alternate path (invoice posted, tender later at POS), but is **not** the exclusive storefront payment model.

## Why

- [`paynow-payment-rail`](./2026-07-24-paynow-payment-rail.md) already requires ContiPay **and** Paynow on storefront when digital tender UI is present.
- Web checkout and AutoDoc “order status” flows need self-serve pay; forcing counter-only would leave storefront cart dead.
- Staff `_require_payments_staff` gates on Phase 13 intents must not be the only path — extend with customer-scoped create RPCs, do not duplicate Payment Entry.

## Consequences

- Do not ship “web-only pay / mobile pay-at-counter forever” as product law — same AuthZ APIs serve web now and mobile later.
- Customer create-intent RPCs/Edge must verify `customers.profile_id = auth.uid()` owns the invoice; cross-customer denied.
- Staff POS create-intent RPCs stay staff-gated; settle/webhook unchanged.
- No ZIMRA / fiscal fields on customer pay or receipts.
