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

## Progress

| Phase | Web POS | Tablet / phone POS |
|---|---|---|
| 1 Till | done | done |
| 2 Governance (+ approver ID badges) | done | done (front-camera badge scan) |
| 3 Reserve-first checkout | done | done: reserve on Pay, sale locked, cash/card/store-credit settlement with a kept key, EcoCash/Paynow/ContiPay with a QR for hosted pages and a 3-minute Unknown cut-off, on account, recovery screen with approver repair and release, pickup list and receipt hand-over |
| 4 Split payments | done: Pay in parts (cash, card/bank, store credit), reduced basket, cancel with refunds, recovery with manager refund steps and retry posting | done (same, plus front-camera badge for refund steps) |
| 5 Card terminals | card-machine option shown disabled with the reason (a browser cannot sign terminal results), recovery "Finish the sale" for approved charges, admin Card machines editor | done: card machine as a tender and as a split part through the new `bridges/android/card-terminal` bridge (Android intent to the acquirer app, Keystore-signed evidence), lost answer → Unknown → ask the machine again, charged-not-posted → finish or reverse, Settings → choose and pair (admin) |
| 6 Returns & warranty | done: Returns opens the posted sale (lines, what can still come back, core charges); return by line with condition → cash refund, credit to account, store credit, swap for the same part or send for warranty; staff draft, approver posts (badge, password or own sign-in) and a failed post reuses the draft; old cores; warranty claims (serial check, decide replace / credit / take back / reject, close); refund whole sale only while nothing came back; stock by branch from part cards | done (same, plus card-machine refund of a whole card sale: approver starts it, the machine pays back, approver posts it) |
| 7 Fulfilment | done: Stock by branch → hold here for collection, collect at another branch, bring it here (branch transfer), back-order; a hold goes with the current sale and turns ready with its invoice when paid; Orders → Collections & transfers with send transfer (warehouse staff), mark a back-order ready, handed over / received here, release (never for a paid hold) | done (same) |
| 6–8 | todo | todo |

Live backend note (2026-10-03): EcoCash, Paynow and ContiPay have no keys, so their initiate functions
answer 503; both clients show those tenders disabled with "Not set up for this shop yet."

Phase 4 backend additions (applied to the hosted project): `pos_badge_approve` accepts
`split_refund_approve` / `split_refund_complete` / `split_refund_fail`
(20261003160108), and `retry_pos_split_finalization` re-posts a fully paid split sale whose invoice
did not post (20261003160304). Known gaps: EcoCash, Paynow and ContiPay cannot take a part yet
(the deployed initiate functions only start whole-order payments), and the hosted project has no
FIFO stock batches, so in a rolled-back dry run a fully paid split sale could not post
("insufficient FIFO batch qty") — it lands in Payments to resolve until stock is received properly.

Phase 5 notes: no card machine is set up on the hosted project yet (`pos_card_terminals` is empty)
and no acquirer app has been tested; the adapter follows `upsert_pos_card_terminal`'s allowed keys
and the deployed `card-terminal-result` evidence format (canonical JSON checked byte-for-byte in a
unit test). Card-machine refunds of posted sales belong to returns (phase 6).

Phase 6 notes: the live `post_return_credit_note` had been replaced by a stub that always raises
(20260905131924), so `post_pos_return_case` (credit note, cash refund, store credit) and the warranty
credit note always failed. Migration 20261004003342 adds `private._post_pos_credit_note`: a credit
note tied to source invoice lines (source price pro rata of the line total, stock to quarantine at the
line's cost basis, COGS reversed at that basis, remaining quantity checked against every posted credit
note) and caps cash / store-credit refunds at what was paid on the sale. Checked in a rolled-back run
(1 of 2 lines at 90 → credit 45, customer balance −45, over-return refused). Badge actions for
returns, cores, warranty and card refunds: 20261004002855. The hosted project has no posted invoices,
so returns were exercised on preview data (web) and the fake client (tablet) only.

Phase 7 notes: holds become ready through `sync_pos_fulfillment_invoice` when their cart's sale posts,
transfers through `sync_pos_fulfillment_transfer` when the warehouse posts the stock transfer, so the
counter never marks a hold ready by hand (that would skip the invoice link and block collection).
Known backend gap: a back-order never gets an invoice linked, so `collect_pos_fulfillment_request`
refuses it; the counter sells the arrived part on a normal sale and releases the request. Checked in a
rolled-back run (back-order created → ready → released; a hold refused for lack of stock, as the hosted
project has no stock levels). Sending a transfer is warehouse staff only (`_require_warehouse_staff`).

