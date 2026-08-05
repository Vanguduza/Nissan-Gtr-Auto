# ERP Redesign Brief for Cursor — Batch 1
### Commerce & POS · HR · Finance
**Repo:** Nissan GTR Auto ERP (Supabase + Next.js + Kotlin Compose ×3 + SwiftUI) · **Source doc:** `IMPLEMENTATION-AND-DESIGN-GUIDE.md` (compiled 2026-08-02) · **Batch:** 1 of N — more requirement batches are coming for this same repo.

---

## How to use this brief

This is written to be handed to Cursor directly. Paste **one PART at a time** into a Cursor chat/composer session rather than the whole file at once — each Part touches different apps/tables and is easier to review as a separate diff. Before starting any Part, tell Cursor to re-read `IMPLEMENTATION-AND-DESIGN-GUIDE.md`, `AGENTS.md`, `.cursorrules`, and `.cursor/rules/` — this brief **adds** requirements on top of those, it does not replace them.

## Constraints that must survive every change below

These are existing, deliberate rules from the guide. Nothing in this brief overrides them — if a requirement below seems to conflict with one of these, the constraint wins and Cursor should flag the conflict rather than silently break it:

1. **RPC + RLS are authority.** Clients stay thin. No role/permission logic lives client-side.
2. **Ledger is append-only.** Corrections are reversing/contra entries. Never `UPDATE`/`DELETE` a posted line.
3. **Every money field carries a currency** (`USD` \| `ZIG`); store the FX rate at transaction time.
4. **Bridge-First, always.** Camera, QR, biometric, GPS, receipt printing — only through `bridges/`. Never HTML5/browser camera or GPS on the web staff app, even on a tablet, even "temporarily."
5. **Staff IA is sidebar-submenu only.** No new in-page sibling tab strips. (The unified POS layout in Part 1 is one page with panels, not tabs — that's compliant.)
6. **New table → RLS in the same migration.** No exceptions.
7. **No ZIMRA/fiscalisation** and **no payroll tax engine** (no PAYE/NSSA/tax brackets) — gross pay + manual deduction lines only. Part 2 automates payslip *scheduling and formatting*, not statutory tax computation. Don't reintroduce this scope even implicitly.
8. After non-trivial work, run the existing `/security-reviewer` then `/verifier`; after any migration, `/supabase-rls-auditor`.

## Decisions confirmed for this batch

- **Logo/brand asset.** Use the logo already live in the system (the one referenced by `docs/decisions/2026-07-23-storefront-autodoc-logo.md` / already on the storefront) — no new upload needed. Flag it rather than substituting a placeholder if it turns out too low-res for print.
- **EcoCash.** Direct EcoCash C2B is already implemented, separately from ContiPay and Paynow. Part 3.4 is updated below — give it its own ledger account fed by its own integration's data, not derived from Paynow/ContiPay payloads. It isn't in the guide's Edge Function inventory, so locate the actual implementation before wiring the ledger to it.
- **Card print specs.** ID card: CR80, 85.6 × 54mm. Business card: 90 × 50mm — the size Zimbabwean printers use.
- **Employee number & grade system.** See the expanded §2.1 below — `GTR` + grade code + 3-digit sequence (e.g. `GTRA1001`).
- **WhatsApp shopping.** Already implemented — §1.7 below is now an audit against what exists, not a build.

---

# PART 1 — Omnichannel commerce & POS redesign

### 1.1 Current state (for Cursor's reference)

| Channel | Status |
|---|---|
| Web storefront | Shipped, USD catalog + ZiG settlement |
| Android customer | Catalog Phase 1 only (browse/search/PDP/add-to-cart); Phases 2–4 tracked in `docs/plans/2026-07-27-mobile-storefront-parity.md` |
| iOS customer | Tabs scaffolded; catalog is a shell |
| WhatsApp | `whatsapp-webhook` exists but is scoped to parts-finder bot + catalog search only — **not** a transacting channel today |
| POS | `/staff/pos` (web) + `apps/android-management/feature/pos`; requires `create_pos_cart` before adding items; a companion phone-scan session (`create_pos_scan_session`) already exists but is described as polling-based |

### 1.2 Unified POS page — search + catalog + cart, no "create cart" step

Redesign `apps/web/app/(staff)/staff/pos/` (and the equivalent Android POS feature) into one screen with three always-visible zones — not tabs:

- **Search/filter zone:** free-text search reusing the existing four-way `search_catalog` FTS (part/VIN/model/PNC), plus category/fitment filters, plus a manual SKU/barcode quick-add field.
- **Catalog zone:** results grid — extend the query to join `stock_levels` so staff see live availability per warehouse, which a customer-facing PLP doesn't need to show.
- **Cart zone:** persistent, always on screen. Buttons: **Checkout**, **Clear cart**, **Save order** (park/hold the sale for later resume — new capability, doesn't appear to exist yet).

**On removing the "create cart" button:** don't necessarily delete `create_pos_cart` — that may break downstream assumptions. Instead, call it automatically and invisibly the moment the POS page mounts (or on first item add) so the user never sees or triggers it manually. This removes the friction without a data-model rewrite.

### 1.3 Split-bill / multi-tender checkout

New checkout capability: a single sale accepts an array of tender lines instead of one payment method —

```
checkout_pos_cart(cart_id, tenders: [{ method, amount, currency }, ...])
```

— where tenders can mix cash + EcoCash + Paynow + ContiPay, sum to the total (converting via the existing ZiG rate mechanism if currencies mix), and post proportionally to each payment method's own ledger account (see Part 3.4 — build that account structure first, or in parallel, since split-bill postings depend on it).

### 1.4 Camera QR → auto-add to cart (Bridge-only)

Scanning only ever happens on a native app via `bridges/android/qr-scanner` (or the iOS equivalent) — never in the browser, including on the counter tablet. A scanned OEM/SKU code resolves and adds a line to the active cart automatically, no confirmation step needed for a valid match.

### 1.5 Tablet ↔ phone companion pairing

Extend `create_pos_scan_session` rather than building a parallel system:

1. Tablet displays a pairing QR/code (rendering a QR is fine on web — it's *display*, not camera capture; use `node-qrcode`, already an approved tool in your own OSS audit).
2. Staff phone (Android management app) scans it via the Bridge QR scanner to join the session.
3. Items the phone scans post to the shared cart/session.
4. **Upgrade the tablet's subscription from polling to Supabase Realtime** — the guide's "companion polling interval" note suggests today's version polls, which won't feel "instant." Realtime is already used elsewhere in the stack (e.g. the staff live delivery map subscribing to `delivery_locations`) — reuse that pattern.
5. Both devices show a clear paired/unpaired status and a revoke action.

### 1.6 Role-level access control for POS/staff portal

POS access should be gated by the same mechanism as everything else — `staff_roles` + `STAFF_NAV_TREE` — but driven by the **module access list defined per role in the new HR organogram** (Part 2.1), not a separate permission system. When HR selects which modules a role can reach, that selection should be the source of truth feeding `STAFF_NAV_TREE` gates on web *and* the Android management app's visible modules. If the existing role-tag gating isn't granular enough once organogram roles get more detailed, this is the natural point to wire in **Casbin (`node-casbin`)**, which your own OSS audit already approved for exactly this ("fine-grained RBAC across Next.js + mobile backends").

### 1.7 WhatsApp commerce — audit against what's already built

The shopping flow on WhatsApp is already implemented, so this is a gap-check against `whatsapp-webhook`, not a from-scratch build. Have Cursor confirm each item below before writing any code, and only touch what's actually missing:

- Catalog browsing via WhatsApp interactive list/button messages.
- Cart state keyed to the customer's WhatsApp number, built on the **same** `create_customer_cart`/`add_customer_cart_line` RPCs as web/mobile — not a forked cart implementation (`packages/shared` is meant to be the single source).
- Checkout completes payment — confirm the mechanism (hosted ContiPay/Paynow link sent as a message, or otherwise) and that direct EcoCash C2B (3.4) is reachable from this flow too.
- The delivery-vs-pickup fulfillment choice (1.8) is captured here the same way it is on web/Android/iOS.
- Order status/receipt delivery reuses the existing multi-channel outbox already used by `process-customer-receipts` (SMS/email/WA).

### 1.8 Fulfillment choice + customer app parity

- Every checkout surface (web/Android/iOS/WhatsApp) needs an explicit `fulfillment_method: delivery | pickup` step: delivery creates a `delivery_jobs` entry as today; pickup flags the order for a staff "ready for collection" workflow (likely new — check if this exists before building it).
- Android/iOS customer apps should visually match the web storefront. `docs/plans/2026-07-27-mobile-storefront-parity.md` already tracks this — treat this requirement as reinforcing that plan's priority, and anchor all three clients' colors/type to the same brand kit tokens introduced in Part 2.4, so print, web, and app stay consistent.

### 1.9 Acceptance checklist — Part 1

- [ ] POS page loads directly into search+catalog+cart with zero manual cart-creation clicks
- [ ] Split-bill checkout posts correctly across per-method ledger accounts (depends on Part 3.4)
- [ ] QR scan-to-cart works only via native Bridge scanners; no browser camera code anywhere in `apps/web`
- [ ] Tablet shows phone-scanned items with no perceptible lag (Realtime, not polling)
- [ ] A role with POS access defined via the organogram can reach POS on web *and* Android; a role without it cannot
- [ ] WhatsApp can complete a purchase end-to-end (browse → cart → pay → fulfillment choice)
- [ ] Android/iOS storefronts pass a side-by-side visual comparison with web

---

# PART 2 — HR module

### 2.1 Organogram (tree CRUD)

New role hierarchy table (illustrative naming — follow existing conventions on inspection), self-referencing for the tree:

```
hr_roles: id, parent_role_id, title, department, grade,
          module_access (jsonb — maps to STAFF_NAV_TREE leaves),
          pay_frequency, comms_preferences (jsonb), ...
```

Only "high-level officials" (an explicit permission, RPC+RLS gated — decide if this is a new `org_admin` tag or reuses `admin`) can create/delete roles. On delete: if any employee currently holds the role, block deletion and require reassignment first, or soft-delete/archive — don't orphan employee records or silently break history, consistent with the ledger's own "don't destroy history" ethos.

**Grade system** (drives `hr_roles.grade` above and the employee number in 2.3): a grade code is `{tier letter}{tier number}` — letter is the seniority band, number distinguishes roles within it. Confirmed so far:

| Grade | Role |
|---|---|
| A1 | CEO |
| A2 | Directors (report to the CEO in the organogram) |
| B1 | Shop & warehouse managers |
| C1 | Shop attendant |
| C2 | Delivery personnel |

More grades will be needed for roles not listed yet (finance, HR, procurement, dispatch, support, etc.). Build the grade table as its own small admin-editable list, not a hardcoded enum, so a high-level official can add e.g. `B2` for a finance manager or `C3` for a warehouse picker following the same tier logic — don't have Cursor guess a complete list upfront. Role creation should require picking an existing grade or adding a new one inline.

### 2.2 Role definition & auto-assembled contracts

Role creation captures: duties, role/title, payment periods, position, contract clauses, remuneration, grade, module access (feeds Part 1.6), and communication preferences (which alerts go to email vs SMS). Contract clauses should come from a small clause-template library (not hardcoded), merged with role-specific fields into a rendered contract PDF — reuse the shared document/PDF service from 2.4/3.2 rather than building a separate renderer here.

### 2.3 Staged onboarding wizard

Five stages, resumable, each stage's data visible only to the roles that need it (HR/payroll for banking & health, not general staff):

1. **Personal information** — name, DOB, national ID, phone *and* email (both captured here — both become valid login identifiers), address, next of kin.
2. **Banking & health information** — bank/account/branch for salary EFT; minimal health fields (medical aid provider/number, allergies, emergency medical contact) — RLS-restrict tightly.
3. **Biometrics** — profile photo only (not fingerprint/facial-recognition matching — keep this scoped to capture, not authentication). This is the moment to implement the currently-deferred `bridges/contracts/biometric.ts` device implementation (Android first), since HR onboarding now depends on it.
4. **Contract signing** — render the Part 2.2 contract, capture a signature (web canvas pad for desk onboarding; consider adapting the existing `bridges/android/pod-signature` pattern if field/mobile onboarding is ever needed), store the signed PDF immutably with timestamp + signer identity.
5. **Auto-generation on completion** — employee number (`GTR` + grade code + 3-digit sequence, e.g. `GTRA1001`; sequence increments per grade, not globally, unless a global counter is preferred), QR ID card PDF, business card PDF, login credentials (delivered through the existing outbox, not a new channel).

### 2.4 Biometrics, brand kit, and card generation

**Brand kit — tools to use** (already researched, current as of this writing):

| Purpose | Tool | Why |
|---|---|---|
| Extract a palette from the logo | [`color-thief`](https://github.com/lokesh/color-thief) (npm: `colorthief`) | MIT, works in Node and browser, returns dominant color + full palette + semantic swatches (Vibrant/Muted/DarkVibrant/LightVibrant) — a genuinely useful starting palette from one logo file |
| QR codes on the card | `node-qrcode` | Already approved in your own OSS audit — reuse, no new adoption decision needed |
| Card/statement PDF rendering | Whatever `process-customer-receipts` already uses for its "tax-agnostic PDF" output | Reuse for consistency — inspect it first. If it can't hit precise physical dimensions (CR80 = 85.6×54mm), add [`@react-pdf/renderer`](https://react-pdf.org/) (MIT, actively maintained, React-component PDF generation, works server-side) scoped to this feature |
| UX reference only (not a dependency) | [LibreBadge](https://github.com/LibreBadge/LibreBadge) | Open-source web ID-badge generator — worth looking at for template-management/live-preview UX patterns, but it's Angular/Dart, wrong stack to actually adopt |

**Assets and dimensions — settled, not open questions:**
- Logo: the one already live in the system (same asset behind `docs/decisions/2026-07-23-storefront-autodoc-logo.md` / already on the storefront). No new upload — flag it if it's too low-res for clean print.
- ID card: CR80, 85.6 × 54mm.
- Business card: 90 × 50mm (the size Zimbabwean printers use).

**Build this as one shared branded-document service**, not a one-off for cards. HR needs branded contracts, payslips, ID cards, and business cards; Finance (Part 3.2) needs branded statements. All of it is "logo + extracted palette + typography + a template" rendered to PDF — build it once (e.g. `packages/documents/`) and have every feature above consume it.

**On "review before choosing a style":** have Cursor generate 3–4 rendered ID-card/business-card mockups using the extracted palette as the *first* deliverable of this section, before building the full multi-theme system. Once you pick a direction, extend it into a proper `id_card_templates` table so multiple themes remain selectable later, per your ask.

**QR payload:** mirror the existing receipt pattern (`https://nissangtrauto.co.zw/receipts/{token}`) with something like `https://nissangtrauto.co.zw/staff/verify/{token}` — an opaque link that confirms "active GTR Auto staff" with name/photo/role only. Never expose banking or health data through this link.

### 2.5 Credentials, forced password change, phone-or-email login

- On onboarding completion: auto-generate a temporary password, set `must_change_password = true`, deliver credentials via the existing outbox.
- Post-login, check that flag before allowing access past `/staff` — if set, force a password-change screen first.
- **Phone-or-email login already exists for returning users** ("email or phone + password", per §4 of the guide) — confirm this path is available to staff accounts too, not just customer-labeled ones, and remove any staff-specific restriction to email-only if one exists. This is largely a confirm-and-connect task, not a rebuild.

### 2.6 Universal password reset (email/SMS/WhatsApp)

This doesn't appear to exist yet as a distinct flow (today's `auth-otp` covers signup/login, not "forgot password"). Build a `request-password-reset` / `verify-password-reset` Edge function pair mirroring the existing OTP request→verify→proof pattern, letting the user pick a channel among whichever of email/phone/WhatsApp their profile has, and reusing the **same delivery infrastructure already wired** (Resend, `SMS_GATEWAY_*`, WhatsApp) rather than standing up new senders. One shared capability across web/Android/iOS, customer and staff alike — Supabase Auth is the common identity layer already.

### 2.7 Employee self-service — My Profile

Employment info (role/department/start date/grade/reporting line from the organogram), payslip history (PDF downloads via the shared document service), and leave management. Leave is likely fully new — model it as `hr_leave_types` / `hr_leave_balances` / `hr_leave_requests` following the same draft → submit → approve/reject shape already used elsewhere (procurement, requisitions) for consistency, rather than reusing finance's requisition table (leave isn't a money movement).

### 2.8 Payroll integration & payslip automation

> **Boundary, restated: this automates scheduling and document generation — gross pay from salary/attendance + whatever manual deduction lines HR enters — and explicitly does *not* add PAYE/NSSA/tax-bracket computation.** That exclusion in the guide isn't being lifted by this request.

Each role's `pay_frequency` (Part 2.1) drives a scheduled job that, per pay period, computes gross pay for every employee in that role, applies manual deductions, renders a payslip via the shared document service, and notifies the employee. Model the cadence the same way the existing analytics report subscriptions already do ("cadence daily/weekly/monthly → cron + subscription rows") — same pattern, new payload.

### 2.9 Acceptance checklist — Part 2

- [ ] A high-level-official role can add/remove an org role and see it reflected in the tree immediately
- [ ] Creating a role produces a contract PDF with that role's actual duties/remuneration/clauses merged in
- [ ] Onboarding is resumable across all five stages and enforces field-level access (banking/health hidden from non-HR)
- [ ] Completed onboarding produces: employee number in the `GTR{grade}{3-digit}` format, ID card PDF, business card PDF, working login
- [ ] The grade table is admin-editable (not hardcoded) and a new grade can be added without a code change
- [ ] First login after onboarding forces a password change before anything else is reachable
- [ ] Login succeeds with either the phone or email captured at onboarding
- [ ] Password reset works end-to-end via at least email and SMS (WhatsApp if credentials are available)
- [ ] My Profile shows real payslip history and a working leave request/approval flow
- [ ] No PAYE/NSSA/statutory tax logic appears anywhere in the payroll code path

---

# PART 3 — Finance module

### 3.1 Navigation restructure

Current Finance submenu: Petty cash, Cash sales, Online sales, ZiG rate, Journals, Requisitions, Payments, Reports, Bank recon, Periods. Restructure toward: a unified **Accounts** view (each dedicated account — cash, EcoCash, Paynow, ContiPay, petty cash, etc. — individually selectable, each showing its own bank-statement-style register), plus a new **Statements** tab (3.2). Keep the sidebar-submenu-only rule — no new sibling tab strips inside the page itself.

### 3.2 Financial statements & customer statements

P&L/Balance Sheet/Cash Flow/Trial Balance reports already exist but are CSV-only — "register PDF print" is explicitly listed as not built. Close that gap using the shared document service from 2.4: branded PDF export for each statement type, with company logo/info.

**Customer statements** (per-customer AR: opening balance, invoices, payments, closing balance) look like a new feature building on the existing customer-credit/AR data — same shared PDF service, same branding.

### 3.3 Chart of accounts — plain English

Today's structure uses numeric codes as the primary label (1100 Bank/cash, 1110 Petty cash imprest, 1120 Cash sales/till, 1130 Online sales). Add a clear `display_name` field used as the **primary** UI label everywhere — dropdowns, reports, statements, registers — with any numeric code demoted to a secondary/technical reference, not the first thing staff see.

### 3.4 Per-payment-method multi-currency accounts

Extend the flagged-but-undone "split 1100 into Bank vs Cash" into full method-level granularity: dedicated accounts for Cash, EcoCash, Paynow, ContiPay (each inheriting the existing USD/ZiG dual-currency pattern), each periodically sweeping into the main operating account — the same imprest-style relationship that already exists between 1100 and 1110.

**EcoCash specifically:** direct EcoCash C2B is already implemented as its own integration, separate from ContiPay and Paynow — it isn't in the guide's Edge Function inventory, so locate the actual implementation first. Post EcoCash settlements to their own account straight from that integration's own callback/webhook, not derived from Paynow/ContiPay payloads.

### 3.5 Refunds & outgoings

- **Refunds** reverse the *original* sale's postings via reversing/contra entries (never edit/delete), linked back to the original transaction ID (3.10).
- **Outgoings** (petty cash, order/vendor payments, salaries, assets/capex) become distinct requisition **types**, extending the existing `requisitions` type enum beyond today's `petty_cash`/`payment`, so everything flows through the one submit → approve → disburse pipeline rather than a parallel system.

### 3.6 Account admin (create/archive)

New GL account: display name, currency support, category (asset/liability/equity/income/expense), parent/rollup account. "Delete" should mean archive/deactivate for any account with historical postings — hard delete only for a brand-new, zero-transaction account, to protect the append-only ledger's integrity.

### 3.7 Requisition & approval gate — including thresholds

Route every new outgoing type (3.5) through the existing requisition pipeline. Also build the already-flagged-as-missing **multi-level amount-threshold approvals** now, since this is the crux of what's being asked — e.g. above some amount, require a second/more senior approver. The organogram's reporting-line data (Part 2.1) is a natural source for that escalation path.

### 3.8 Audit trail

Confirm what already exists beyond the ledger's inherent append-only trail. Add an explicit, human-readable log (actor, timestamp, action, entity, before/after) across every requisition submit/approve/reject/disburse and every direct posting, so staff can review "who did what when" without reading raw journal entries.

### 3.9 Bank-statement-style register

`report_account_register` (Date/Description/Debit/Credit/Balance/Currency) already exists and is already close to what's being asked. Apply it to every new dedicated account, add clear opening/closing balance headers per period, and give it a branded PDF export via the shared document service.

### 3.10 Universal transaction/sale IDs

Beyond internal UUIDs, give every sale, order, refund, and ledger-affecting entry a consistent human-readable reference number, shown on receipts/statements/staff UI, and searchable across POS, finance, and CRM so any transaction can be traced end-to-end.

### 3.11 Acceptance checklist — Part 3

- [ ] Every account in the UI shows a plain-English name first, everywhere, with no numeric code as the primary label
- [ ] Cash/EcoCash/Paynow/ContiPay each have their own register, each multi-currency, each sweeping to the main account
- [ ] A refund posts as a reversing entry linked to the original sale ID — nothing is edited or deleted
- [ ] Every money movement (incl. salaries, assets, refunds) requires a requisition + approval before it posts
- [ ] A large enough amount triggers a second-level approval
- [ ] Every account register reads like a bank statement and exports to a branded PDF
- [ ] A financial statement and a customer statement both export as branded, professional PDFs
- [ ] Any sale/transaction can be looked up by its reference number from POS, Finance, or CRM

---

## Suggested build sequence

1. **Finance foundation** (3.3, 3.4) — unlocks split-bill (1.3) and the account-admin/refund work that depends on it.
2. **HR foundation** (2.1, 2.2) — organogram + module access, which POS role-gating (1.6) depends on.
3. **POS layout** (1.2, 1.4, 1.5) — mostly independent, can run in parallel with the above.
4. **Shared document service** (2.4 + 3.2 together) — build once, feed contracts/payslips/ID cards/business cards/statements from it.
5. **Split-bill checkout** (1.3) — once 3.4 lands.
6. **Onboarding wizard + credentials + password reset** (2.3, 2.5, 2.6).
7. **Requisitions/approval thresholds/audit trail** (3.5–3.8).
8. **My Profile + payroll automation** (2.7, 2.8).
9. **Mobile app parity** (1.8) — still the largest remaining piece here; treat as its own workstream. **WhatsApp (1.7)** is now just an audit against the existing implementation, not a build — cheap to do early, no need to wait for the rest.

---

*Compiled from `IMPLEMENTATION-AND-DESIGN-GUIDE.md` (2026-08-02 snapshot). Batch 1 of an ongoing series — later batches should extend this file's structure rather than start fresh.*
