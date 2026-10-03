# POS operations backend → frontend integration (2026-10-03)

Owner request: integrate the live POS operations backend (82 unused RPCs) into the POS apps with
full UX, follow existing documentation, and integrate the same functions into other ERP apps where
they apply.

## Sources of guidance (searched 2026-10-03)

| Source | What it gives | Status |
|---|---|---|
| `docs/design/pos/POS_FRONTEND_BLUEPRINT_REV_1_5.md` §10.2–10.12 | Gateways (Till, Tender, Checkout, Recovery), checkout state machine, reserve-first, tender capabilities, five terminal outcomes, reduced basket, idempotency, manager reauth never cached, audit, catalogue package lifecycle, offline rules | **Governing spec** |
| `docs/design/pos/FEATURE_REGISTER.md` | CART-11, PAY-01…06, HW-06, SEC-06, TAB-02 rows still `todo/partial` | Update rows as phases land |
| `docs/design/pos/APPROVED_VISUAL_DELTAS.md` | Rail/visual changes need a delta row | D-016 (Till rail item) |
| `docs/contracts/warranty-claims-rpc.md` | Warranty claim RPC contract | Phase 6 |
| Live DB comments (`obj_description`) | Few: `is_pos_approver`, `list_pos_till_items`, `commerce_payment_exceptions` (never discard a settlement) | Used |
| Deployed edge `card-terminal-result` (no git source) | Terminal evidence = RSA-signed payload `gtr-card-terminal-evidence-v1` from a paired device key; server verifies before `record_pos_card_terminal_result`; outcomes approved/declined/cancelled/unknown/failed; approved needs txn id + RRN or auth code; evidence ≤10 min old | Phase 5 contract |
| Deployed edge `render-payment-resolution-letter` (no git source) | Renders letters from `get_payment_resolution_letter_render_data` | Phase 8 |
| Deployed edge `catalog-offline-release` (no git source) | Encrypted single-file offline catalogue with RSA-OAEP key wrap per device, grants, revocation. **No release has ever been published** (tables empty) | Note: hardening option for the offline bundle |
| Git history (all refs + orphaned pre-rewrite history) | **No documentation or client code** for these RPCs anywhere | — |

## Deviation found in already-shipped work

The offline catalogue bundle (2026-10-02) clears the previous build before downloading a new one;
blueprint §10.9 requires staging, verification, atomic activation and keeping the previous version.
To fix in the bundle clients.

## Function → app map

| Area | RPCs | POS web | POS tablet/phone | Other apps |
|---|---|---|---|---|
| 1 Till | open/get/attach/close/denominated close/cash movement/expected/variance/handover/list, `list_pos_till_items` | ✓ | ✓ | Staff web: till oversight for finance/managers |
| 2 Governance | approval policies/reasons, `pos_action_requires_manager`, `*_governed` (discount, override, void, refund) | ✓ | ✓ | Staff web: `set_pos_approval_policy` |
| 3 Reserve-first checkout | prepare v2, settle tenders, payment status, recovery, repair, collect/cancel, EcoCash/ContiPay/Paynow intents, account credit, pickup | ✓ | ✓ | — |
| 4 Split payments | start/find/get, legs, per-leg intents, affordable items (§10.8), cancellation, refunds, recovery | ✓ | ✓ | — |
| 5 Card terminal | list/upsert terminals, device keys, purchase/refund/reversal, attempt status, recovery | operator flows; signing needs a paired device | ✓ via `bridges/` | **Driver app**: card on delivery |
| 6 Returns & warranty | return cases, core returns, warranty serial/claims, invoice detail, stock availability | ✓ | ✓ | — |
| 7 Fulfilment | create/list/ready/collect/approve/cancel | ✓ | ✓ | Warehouse module |
| 8 Letters | resolution letters, manager signature, business document profile | ✓ (create/print) | ✓ | Staff web: profile + signatures |
| COD | `get_delivery_job_payment_context`, `collect_delivery_cash`, delivery card terminal begin/finalize/recovery, device key | — | — | **Driver app** |
| Customer checkout v2 | `checkout_customer_cart_v2`, `set_customer_cart_delivery_payment_method`, `prepare_customer_checkout` | — | — | **Customer app + web shop** |

Out of scope here (main's superseded staff features, not POS operations): procurement GRN invoices,
petty cash requests, kits creation, product pages, payroll funding.

## Rules carried from the blueprint

- Every money/stock mutation carries a client idempotency key (`p_request_id`, `p_checkout_request_id`).
- Manager approval = live reauth per action; never cached (§10.10).
- Tenders render disabled with a reason, never hidden (§10.7).
- `Unknown` terminal/provider outcome blocks a duplicate charge and routes to the recovery screen (§10.7, §10.11).
- The frontend never computes remaining balance or the reduced basket (§10.5, §10.8).
- Fake providers only in test/preview source sets (§10.10).
