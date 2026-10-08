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

The offline catalogue bundle (2026-10-02) cleared the previous build before downloading a new one;
blueprint §10.9 requires staging, verification, atomic activation and keeping the previous version.
**Fixed 2026-10-04 in both clients:**
- Each build lives in its own folder, and a pointer file (`catalog.json`) names the active and previous builds.
- An update downloads into staging while the current build stays in use. Each file is SHA-256 checked and resumable.
- Activation is one atomic pointer write: a rename on the tablet, the atomic writer close on OPFS. The replaced build is kept, and "Use previous" goes back to it.
- At start-up the active build's file sizes are checked. A damaged build falls back to the previous one with a notice.
- Unchanged files are hard-linked on the tablet and copied on the device in the browser, so they are not downloaded again.
- At most two builds are stored, because starting an update drops the older previous one.
- Tablet files are made read-only once verified.
- Builds downloaded under the old layout are kept and migrate in place.
- On the tablet, a file shared by two builds through a hard link is one file, so damage to it hits both builds; the start-up check then asks for a fresh download.

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
| 8 Letters | done: Payment letter block on recovery for a provider payment, a card-machine attempt and a split refund (letters issued, Issue letter for a signed-in manager / finance / admin → printable A4 with the issuer's signature); Settings → My signature (draw or upload) and Business details on documents (admin) | done (same; signature drawn on screen, letter printed through the document printer bridge with the signature image) |

| Other app | Area | Status |
|---|---|---|
| Driver app (`apps/android-delivery`) | COD: cash and card on delivery | done: Payment section above the proof steps (amount due from the server, cash with an optional note, card through the `bridges/android/card-terminal` bridge with the phone's Keystore key, pair with the phone ID an admin assigned, part payments, unknown answer → ask the machine again, charged-not-posted → post it); "Complete delivery" held while money is due |
| Customer app + web shop | Checkout v2 delivery payment method | done: for nationwide delivery the customer picks "Pay on delivery" (cash, card or either; USD only) and the order is invoiced straight away (`set_customer_cart_delivery_payment_method` → `checkout_customer_cart_v2`) with no online payment step; otherwise checkout reserves stock and pays online (`prepare_customer_checkout`, 20 minutes); click & collect shows pay on delivery disabled with the reason; order page says what to have ready for the driver |
| Staff web | POS admin | done: approval policies, card machines, till sessions and business details were already in Settings; card machines gained "Drivers can take card on delivery" (`set_pos_card_terminal_delivery_enabled`) and the driver phone ID |

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
Back-orders (fixed 2026-10-04, migration 20261004144350): an arrived back-order has "Add to sale"
(`attach_pos_fulfillment_to_cart`), which puts the part on the current sale and ties the request to it.
When that sale's invoice posts with the part on it, the trigger links the invoice (a back-order for a
part not on the invoice is left alone), and the request is handed over like a hold. Checked in a
rolled-back run (refused before the sale, linked after posting, refused until paid, collected once
paid), in the web preview end to end, and in tablet reducer tests and a screenshot. Checked in a
rolled-back run (back-order created → ready → released; a hold refused for lack of stock, as the hosted
project has no stock levels). Sending a transfer is warehouse staff only (`_require_warehouse_staff`).

Phase 8 notes: `payment_resolution_letters` and `business_document_profile` are behind RLS with no
table policies; migration 20261004060207 adds read-only `list_payment_resolution_letters` and
`get_business_document_profile` (checked in a rolled-back run). Signatures live in the private
`staff-signatures` bucket under the signer's own folder (storage policies): the web uploads with the
Storage client, the tablet with Storage REST and the user's own token (no service key on devices).
A letter shows the signature image only to users the bucket lets read it (the signer, admin, finance);
others see "signature on file" with its hash. The A4 printer bridge gained `printSignedDocument`
(Bridge-First). Letters are issued by the signed-in user only (no badge path: the signature is theirs).

Driver COD notes: the driver app holds "Complete delivery" while a cash/card-on-delivery invoice has
a balance or a card charge is unresolved, and since migration 20261004144000 `submit_delivery_pod`
refuses the driver too (dispatch / warehouse / admin can still complete; the balance then stays open
on the invoice). Checked in rolled-back runs: unpaid and part-paid refused, paid passes, unresolved
card charge refused, back office passes.

How COD works at the door:
1. The driver opens Proof of delivery. The Payment step shows the balance due, which comes from the server.
2. The driver collects cash, card or both. Each payment posts against the invoice immediately.
3. With the balance at zero, the driver does the photo, signature and customer code, then completes.

What happens otherwise:
- **Customer can't pay:** Report an issue → Refused (`fail_delivery_job`), and the parts come back. A re-attempt is optional.
- **Part-paid, the rest not possible (part settlement, migrations 20261004164444 / 20261004164943):** the driver taps "Customer can't pay the rest?", gives a reason, and asks to leave the balance on account (`request_delivery_balance_on_account`).
  - It is approved at once when the customer's trade account is not on hold and what they owe, this invoice included, fits their credit limit.
  - Otherwise it waits in Staff → Logistics → Balances on account, where a dispatcher or admin leaves it on account or refuses with a note (`decide_delivery_balance_on_account`). Finance and POS approvers may also decide through the server.
  - The driver's screen updates when they decide. An approval lets `submit_delivery_pod` complete with that balance (or a smaller one) open on the invoice; every request is kept with who asked, why, and who decided.
- **Delivery fails after a payment:** the money stays on the invoice. It counts toward the re-attempt or is refunded through returns.
- **Connection:** payment needs a connection at the door, like the customer-code check already does. A stop whose payment context cannot be loaded
shows a warning but is not held, so a proof can still queue offline; a stop without an invoice
shows no payment section. A card machine must be assigned by an admin to the phone's device id
(shown in the app) before the driver can pair; the fake backend uses the simulated machine
(.99 declines, .98 gives no answer) and never the live one. Checked with unit tests on the fake
backend (cash idempotency, card settle, unknown → ask again, decline, prepaid) and screenshots;
no live card machine or COD invoice exists on the hosted project yet.

Checkout v2 / admin notes: `checkout_customer_cart_v2` posts the invoice immediately (no stock
reservation step), as the backend designs pay-on-delivery; checked in a rolled-back run as a customer
(dispatch cart → invoice posted, method `cash_or_card_on_delivery`, nothing paid; click & collect
refused). `list_pos_card_terminals` did not return `allow_delivery`; migration 20261004113923 appends
it so the admin sees the setting (callers read columns by name). The web shop flow is typechecked
only (no customer sign-in in this environment); the customer app has a screenshot and fake-backend
tests.

## Customer suspension for failing to settle (2026-10-04, migration 20261004175556)

Owner decisions: suspend on any of three rules, block all credit (pay on delivery included), and only a
manager may lift.

**Automatic triggers.** Any one of these suspends the customer:
- a balance left on account at delivery is unpaid after 7 days;
- an invoice is unpaid 30 days after it was issued;
- 2 pay-on-delivery deliveries are refused or the customer is absent within 90 days.

**When the rules are checked:**
- daily (pg_cron `customer-suspension-sweep-v1`, 02:10);
- when a delivery fails;
- at the moment the customer tries to use credit.

**Blocked while suspended.** The server refuses each of these, through triggers or in the function:
- every pay-on-delivery purchase: choosing it on a cart or placing the order (`pos_carts.delivery_payment_method`);
- leaving a balance on account at the door (`delivery_balance_approvals`);
- counter sales on account (`checkout_pos_cart_on_account`);
- holds and back-orders for the customer (`pos_fulfillment_requests`).

Paying upfront and paying what they owe still work.

**Lifting** (`lift_customer_suspension`):
- only a POS approver (manager) or admin may lift, and a reason is required;
- everything the customer owed at that moment is waived from the automatic rules, so it does not re-suspend for those debts;
- new overdue debts suspend again.

Admin, finance or a manager may also suspend by hand (`suspend_customer`). Every suspension and lift keeps who, when, why and what it was for.

**Clients:**
- Staff → CRM → Customer credit lists suspensions (with what they were for and what is owed), lets a manager lift one and lets staff suspend by hand.
- The web shop and the customer app show Pay on delivery disabled with the reason.
- The web POS shows On account disabled with the reason.
- The driver app and the tablet show the server's refusal message.

**Checked** in a rolled-back run:
- a fresh pay-on-delivery order is allowed;
- an invoice 30+ days overdue suspends: pay on delivery is refused and upfront checkout is allowed;
- a back-order is refused while suspended;
- lifting is refused for a non-manager and without a reason;
- a manager lifts, the waived debt does not re-suspend, and a new overdue debt does;
- 1 refused delivery does not suspend, 2 do.


## Driver cash hand-in (2026-10-05, migration 20261005023019)

Cash collected on delivery was posted against the invoice but nothing tracked it from the driver's
pocket to the branch. Now:

- **Driver app → Account → Cash to hand in**: amount held per currency, how long, each collection;
  the driver counts and hands it in (note optional). While a hand-in waits to be counted, another
  in the same currency is refused.
- **Staff → Logistics → Driver cash** (admin, finance, sales, dispatcher): who still holds cash and
  since when (over 24 h is flagged), hand-ins to count, and differences. A count that differs from
  what was collected needs a reason (`driver_cash_variance` codes) and a manager sign-off by someone
  other than whoever counted; the driver cannot count or approve their own.
- No journal entry: collections already posted. A difference is recorded for recovery, not posted.

Tested end-to-end on the local replica with simulated users (customer checks out pay-on-delivery →
warehouse picks → dispatcher assigns and dispatches → driver collects in two parts → proof of
delivery with code → hand-in → short count → manager sign-off). Found and fixed: `list_driver_cash`
nested aggregates (failed on first call) and could drop open hand-ins past the row limit.

Finding for later: `generate_delivery_pod_otp` returns the code to the caller, so a driver can read
the code meant for the customer. It should be sent to the customer only.

## Trading-day run on simulated data (2026-10-07, migration 20261007204736)

`supabase/sim/e2e_day.py` runs a full day with users who hold only their real roles (cashier =
sales, manager = sales + dispatcher + approver). Earlier hosted checks used a test user holding
every role, which hid three production bugs, now fixed on hosted:

1. **Cashiers could not finish a counter sale.** Reserve-first checkout
   (`settle_pos_commerce_tenders` → `finalize_commerce_order` → `checkout_pos_cart`) posted the
   sale journal without entering the sales-checkout accounting context, so `create_journal_draft`
   demanded finance/admin. Only admin/finance staff could sell.
2. **Every POS sale on account failed**: `checkout_pos_cart_on_account` emits
   `pos_credit_sale_authorized`, which was never registered in `sms_event_catalog`.
3. **Managers could not post approved returns** (cash refund, store credit, credit note): the credit
   note journal and `issue_store_credit` demanded finance/admin. `post_pos_return_case` now enters
   the accounting context after its own manager check; `issue_store_credit` honours it. Clients
   cannot enter that context themselves (no EXECUTE on the enter function).

After the fix every step passes: drawer expected = counted (USD 242.50), all 82 journals balance,
suspension after two refused pay-on-delivery orders, manager-only lift, ordering again afterwards.

## Proof-of-delivery code (2026-10-07, migration 20261007205401)

- The code is no longer returned to the driver (they could complete a delivery without the
  customer). Back office (dispatcher / warehouse / admin) still gets it to read to a customer with no
  account or no SMS.
- The customer sees the current code on their web order page and in the customer app
  (`get_my_delivery_codes`), besides the SMS. It is kept in `private.delivery_pod_codes`, readable
  only through that function, and cleared when the delivery completes.
- Fixed: the driver app checks the code and then submits, but the submit re-checked it and failed
  ("no active POD OTP for job") because the code was already used — drivers could not complete a
  delivery from the app. A code already verified for the job is now accepted.

## Card machines (2026-10-07, migration 20261007210014)

`supabase/sim/e2e_card.py` found that card payments could not work at all in production:

- Pairing a till tablet or driver phone failed (`digest()` called unqualified under an empty
  search_path) — no device could ever be paired.
- A counter card charge failed at the start: the order's provider `card_terminal` was not allowed by
  `commerce_orders_active_payment_provider_check`.

Both fixed on hosted. After the fix every card path passes (approved, declined, forged or tampered
answers refused, no answer → recovery → approved, card at the door). The `card-terminal-result`
Edge Function was deployed but never committed; its source is now in
`supabase/functions/card-terminal-result/index.ts`, recovered from the deployed bundle.

## Back-orders and transfers for a customer (2026-10-07, migration 20261007210323)

`supabase/sim/e2e_backorder.py` found:

- A back-order could be marked ready before the part arrived, and "ready" held nothing: another
  customer could buy the arrived part, after which the waiting customer's own sale failed for stock.
- A transfer made for a customer was not held on arrival, could not be put on the customer's sale,
  and could be handed over without being paid for (the part stayed in stock, unpaid).

Now: ready needs the part in the branch and holds it for the customer for 14 days (a transfer for
a customer is held when it arrives); checkout counts the customer's own hold as available to their
sale; the paid invoice links the request and releases the hold; a transfer for a customer is
handed over only once paid. Web and tablet show "Add to sale" for arrived transfers too.
Back-orders are paid when the part arrives (no deposits yet).

## Approvals inbox with alerts (2026-10-07, migration 20261007210919)

Staff → Approvals (admin, finance, sales, dispatcher, warehouse) lists everything the signed-in
person may decide, computed live from the source tables by `list_my_approvals()`: balances on
account (driver at the door, urgent), card payments to reconcile (urgent), driver cash to count,
driver cash differences, till differences, returns, part-payment refunds, warranty claims,
requisitions, transfers to send and transfers to receive. Each links to the screen where it is
decided; the same rules as the deciding function apply (e.g. not your own till, not a cash count you
made, not a transfer you started).

Alerts: `private.sweep_approval_alerts()` (pg_cron `approval-alerts-sweep-v1`, every 5 minutes)
writes one `staff_ops_notifications` row per person per item that waited too long (urgent 10
minutes, others 4 hours); shown under "Waiting too long" with Dismiss. The nav shows a count badge
(red when something is urgent), refreshed every minute; the page refreshes every 30 s and raises a
desktop notification for new urgent items when allowed.

## Daily dashboard (2026-10-07, migration 20261007211743)

Staff → Today (admin, finance, or a POS manager; sales staff without manager rights are refused by
the server). `get_daily_dashboard(date, branch)` returns one Harare business day, per currency
(never added across currencies): net sales, returns, gross margin from invoice cost basis, average
sale, change on the same weekday last week, sales by channel (counter / delivery / online collected),
money taken by tender, sold-but-unpaid, sales by hour, best sellers, tills (float, expected,
counted, difference), deliveries (sent, delivered, failed, on the road, waiting for a driver), cash
collected at the door, cash still with drivers, customer debt (and over 30 days), suspensions, stock
at or below its reorder point, back-orders waiting, and decisions waiting (links to Approvals).
A return counts at the branch of the original sale (credit notes post to quarantine). The page
picks a day and a branch, refreshes every 2 minutes for today, and draws sales by hour as a
single-series bar chart (colour validated against the panel; hover tooltip; table view).

## Exception reports (2026-10-08, migration 20261007221807)

Staff → Exceptions (admin, finance, POS managers) runs `get_exception_report(from, to, branch)` over
at most 93 days, built only from what is already recorded: voided sales, parts removed after ringing
up, discounts and price cuts (with the value given away), paid orders repaired by hand, returns
(flagging cash refunds within an hour of the sale), till and driver cash over/short, declined /
cancelled / unanswered card charges, sales below cost, counter sales after hours (before 07:00 or
from 19:00 Harare), failed deliveries, balances left on account by a dispatcher, and stock count
differences (flagging counts posted by one person under the two-person limit). Each item has a
severity, the person (the sale's cashier for POS actions, which managers perform under their own
sign-in), the approving manager, the amount in its own currency and a link; "By kind" and "By person"
filter the list and show patterns.

## Restocking suggestions (2026-10-08, migration 20261007222543)

Staff → Restock (admin, finance, warehouse, POS managers) runs `get_restock_suggestions(branch, sales
days 28, lead days 14, safety days 7, cover days 28)`. Per part and branch: sales speed, free stock,
stock on its way (submitted/approved purchase orders and incoming transfers) less outgoing transfers
and back-orders waiting; reorder point = the part's own, else daily sales × (preferred supplier's lead
time or the default + safety days). At or under it, top up to the reorder point + the cover days of
sales: from another branch's spare stock first (free stock above its own reorder point and cover), the
rest bought from the preferred supplier at its last quoted cost (else the last receipt cost). Draft
orders are shown ("already in a draft order") but not counted as on their way. Urgency: out (none
free or customers waiting), runs out before a delivery could arrive, or low. Also lists stock not sold
in 90 days with its value.

The page ticks the urgent rows, and creates the transfers (`create_stock_transfer`, approved by the
receiving branch) and draft purchase orders per supplier and branch (`create_purchase_order`;
procurement reviews and approves). Reorder points are per part, not per branch (stock_items).

## Regression checks in CI (2026-10-08, migration 20261008032826)

`.github/workflows/simulation.yml` runs `supabase/sim/ci/run.sh` on every pull request touching
`supabase/`: a throwaway stack from a schema snapshot plus newer migrations, simulated seed, every
`e2e_*.py` scenario, then `supabase/tests/sim_role_rules_smoke.sql` (one role per person). The rule
test found two tables without row level security on hosted; `catalog_r2_release_inventory_work` could
be written by signed-out visitors. Both now have RLS and no client grants.

## Money: driver shortages, currencies, deposits, card refunds (2026-10-08)

- **Driver cash differences post to the ledger** (20261008035043). On approval: short and owed by the
  driver (reason driver short / under investigation) Dr 1250 Staff receivables / Cr 1120; other
  shortages Dr 5310 Cash over / short; overs Cr 5310. What the driver owes is repaid at the counter
  (`record_driver_cash_recovery`, Dr 1120 / Cr 1250, not by the driver) or written off by a different
  manager (`write_off_driver_cash_shortage`, Dr 5310 / Cr 1250, reason required). ZiG journals use the
  collections' own rates. Staff → Driver cash lists what each driver owes with both actions; the driver
  app shows "You owe …".
- **No sums across currencies** (20261008035045). `private.customer_exposure` gives unpaid balances per
  currency and a total in the account currency at each invoice's own rate (NULL if a rate is missing:
  credit checks then refuse or go to the back office). Used by on-account checkout, delivery balance on
  account and suspension "owing"; web and customer app show each currency ("USD 95.00 + ZIG 95.00").
- **Back-order deposits** (20261008035048). `take_pos_fulfillment_deposit`: cash (into the open till
  as a pay-in), bank or EcoCash (reference required); Dr tender / Cr 2200 and issued as the customer's
  store credit, which the cashier takes as Store credit at the sale. `refund_pos_fulfillment_deposit`:
  manager, only once the request is cancelled, refused if the credit was already spent; cash leaves
  the manager's till. POS → Collections & transfers shows the deposit held, Take deposit and Give
  deposit back. `list_pos_fulfillment_requests` is now staff-only (customers could call it).
- **Card refunds could never be finalised** (20261008035050): finalising called the disabled
  `post_finance_refund`. A card refund now posts a credit note for the whole sale (parts to
  quarantine, cost reversed) with Dr 4110 / Cr 1170 card clearing. A sale already partly returned is
  refused at the start. Card voids (machine reversal before the sale is finalised) and refunds are now
  in `e2e_card.py`.

## Alerts away from the screen (2026-10-08, migration 20261008035943)

- **Phone alerts, chosen by each person** (Staff → Approvals → Alerts on your phone): phone number,
  SMS or WhatsApp, urgent approvals, and (managers / finance / admin) the 07:00 summary. Stored in
  `staff_alert_settings` (own row only; `get_my_alert_settings` / `set_my_alert_settings`).
- **Urgent approvals by text**: the 5-minute approvals sweep now also texts an urgent item that has
  waited 10 minutes to each person it waits on who asked for it, once per item and person.
- **07:00 summary** (`morning-summary-v1`, 05:00 UTC): yesterday's sales per currency, unpaid, till
  differences, driver cash still held, failed deliveries, reorder count and approvals waiting — built by
  running `get_daily_dashboard` as each recipient, so role limits apply — by text and as an in-app
  notice linking to that day on Staff → Today.
- `sms_outbox.channel` picks WhatsApp; `process-sms-outbox` (redeployed) sends it through WhatsApp
  Cloud when configured and falls back to SMS (e.g. outside WhatsApp's 24-hour window, where only
  approved templates are allowed). Nothing is sent until `SMS_GATEWAY_API_KEY` and/or the WhatsApp
  Cloud secrets are set on the project and the worker is scheduled.
- **Approvals on the tablet**: a header pill ("3 to approve", red when urgent) appears while something
  waits for the signed-in operator; it opens the same list as the web. Till differences, returns, card
  and split payments, and transfers open their tablet screen; the rest say to decide on the web.
