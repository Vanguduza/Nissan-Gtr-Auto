# H4 / B-MONEY-1 — dual-read → cutover (never big-bang)

**Status:** Done (cutover habit) — physical NUMERIC column drop deferred  

**Depends on:** H8 fund-release insert-once (Done)  

**Laws:** Prefer `amountMinor`+currency on new APIs; dual-write then dual-read then cutover; AI never invents payable amounts.

## Already landed (pre-H4 + H4 slices)

| Surface | Dual-write | Notes |
| --- | --- | --- |
| PO lines `unit_price_minor` | Yes (`20260812030000`) | Trigger + approve path |
| `procurement_fund_releases.amount_minor` | Yes | H8 insert-once preserves stored minor |
| Cart / invoice lines `unit_price_minor` / `line_total_minor` | Yes (`20260813300000`) | Triggers + backfill; smoke PASS |
| JE lines `debit_minor` / `credit_minor` | Yes (`20260813400000`) | Triggers + backfill; posted null-fill only |
| `payment_entries.amount_minor` / `settlement_amount_minor` | Yes (`20260813400000`) | Triggers + backfill; posted null-fill only |
| `@gtr/shared` MoneyMinor helpers | Yes | `toAmountMinor`, `dualWriteMoney`, prefer/sum display helpers |
| Android management `MoneyDualRead` | Yes (dual-read) | Prefer `*_minor` on POS cart lines; JVM unit tests |
| iOS `MoneyDualRead` + storefront display | Yes (dual-read, slice 6) | Cart/order/pay prefer `*_minor`; SwiftPM `MoneyDualReadTests` |
| `@gtr/payments` PspInitiateRequest | MoneyMinor | Stub + Edge remain |
| Shared API cutover contracts (slice 7) | Yes | `ApiMoney` / `LegacyMoney`; RPC dual-write helpers; settlement + allocation prefer minor |

## H4 freeze DoD (incremental)

1. **Shared dual-read** — `preferAmountMinor` / `displayMajorFromDual` in `@gtr/shared` (prefer minor when present).  
2. **Web PO lines** — `listPoLines` selects `unit_price_minor` and dual-reads for display.  
3. **Document** remaining cutover order (below) — no column drop this slice.  
4. **Cart / D-57 checkout display (slice 2)** — web storefront + staff POS dual-read line/totals via `sumPreferAmountMinor` / `displayLineTotalMajor`; ZiG settlement from `MoneyMinor` payable.  
5. **Cart/invoice `*_minor` dual-write (slice 3)** — `pos_cart_lines` + `sales_invoice_lines` columns + triggers + backfill (`20260813300000`); smoke `cart_invoice_amount_minor_dual_write_smoke.sql`.  
6. **Android management POS dual-read (slice 4)** — `MoneyDualRead` + `PosCartLineSummary` minors; `listPosCartLines` selects `unit_price_minor` / `line_total_minor`; cart total / line display prefer minor.  
7. **Ledger JE + payment_entry dual-write (slice 5)** — `debit_minor`/`credit_minor` on `journal_entry_lines`; `amount_minor`/`settlement_amount_minor` on `payment_entries`; triggers + backfill (`20260813400000`); smoke `ledger_payment_amount_minor_dual_write_smoke.sql` PASS.  
8. **iOS money formatters + dual-read (slice 6)** — `MoneyDualRead.swift` parity helpers; cart lines select `unit_price_minor`/`line_total_minor`; cart/order/pay UI prefer minor; SwiftPM tests; invoice header minors forward-compat only (no header dual-write yet).  
9. **API cutover habit (slice 7)** — new/shared TS contracts prefer/require `amountMinor`; major marked legacy/derived; web PO create + settlement + allocate dual-write `*_minor` fields. **Physical drop of NUMERIC columns deferred.**

## Cutover order (never big-bang)

```
PO lines + fund releases (done dual-write)
  → dual-read web procurement (slice 1 — Done)
  → dual-read cart / D-57 checkout display (slice 2 — Done)
  → cart/invoice *_minor dual-write (slice 3 — Done)
    → dual-read management Android POS (slice 4 — Done)
    → ledger / payment_entry columns + backfill (slice 5 — Done)
    → iOS formatters + dual-read (slice 6 — Done)
    → cutover habit: APIs prefer amountMinor; major derived (slice 7 — Done)
    → later: drop NUMERIC majors after evidence (deferred)
```

## Slice 7 — API inventory (major-only vs cutover)

| API / surface | Money shape | Cutover habit |
| --- | --- | --- |
| `@gtr/payments` `PspInitiateRequest` | `MoneyMinor` required | Already canonical |
| `@gtr/payments` `buildCheckoutDisplay` | payable `MoneyMinor` | Already canonical |
| `@gtr/shared` `Money` | major NUMERIC | `@deprecated` → use `MoneyMinor` / `ApiMoney`; alias `LegacyMoney` |
| `PaymentAllocationInput` | prefer `amountMinor` | `toAllocatePaymentArgs` dual-writes `amount` + `amount_minor` |
| Web `createPreferredPurchaseOrder` | sends `unit_price` + `unit_price_minor` | SQL still reads major; trigger fills minor |
| Storefront ContiPay/Paynow/EcoCash settle | prefer `amountMinor` | metadata/body include `settlement_amount_minor`; major derived |
| Cart add RPCs (`add_cart_line`, `add_customer_cart_line`) | no client price | Server price + INSERT triggers dual-write minors |
| Checkout RPCs | no client money args | Lines already dual-written |
| `create_purchase_order` SQL | reads `unit_price` major from JSONB | Client may send `unit_price_minor` (ignored by SQL; trigger fills) |
| `allocate_payment` SQL | reads `amount` major | Client may send `amount_minor` (ignored by SQL until later migration) |
| Loyalty / store credit / credit-limit RPCs | major NUMERIC | Out of H4 freeze; leave for later money surfaces |
| Invoice header `subtotal`/`total` minors | not dual-written | Deferred (line-level first) |
| Android-customer / delivery dual-read | not required for freeze | Optional follow-on |

## Slice 7 verify

```bash
cd packages/shared && npm test
cd packages/payments && npm test
```

Evidence (2026-08-14): `@gtr/shared` 37/37 PASS; `@gtr/payments` 11/11 PASS.

## Out of scope / deferred after Done (cutover habit)

- Dropping `NUMERIC` money columns (physical cutover)
- Migrating SQL RPCs to *require* `amount_minor` args (clients send both; SQL still authoritative on major until then)
- Invoice header `subtotal`/`total`/`amount_paid` minors
- Android-customer / delivery dual-read
  - **Android customer cart dual-read:** Done (follow-on) — `MoneyDualRead` + `getOpenCart` minors + Fake seeds + cart UI
  - **Android delivery dual-read:** still open (optional)
- ZIMRA / tax amounts
