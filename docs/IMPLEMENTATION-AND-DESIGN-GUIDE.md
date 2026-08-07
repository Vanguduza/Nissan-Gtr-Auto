# Nissan GTR Auto ERP — Implementation & Design Guide

**Purpose:** Owner runbook for what is implemented, how it is designed, and which tools to adopt next.  
**Compiled:** 2026-08-02 (from **live restored monorepo**)  
**Audience:** Product owner / operator adjusting behaviour, UX, data, or infra.

> Evidence sources: `STAFF_NAV_TREE` (`apps/web/lib/staff-auth.ts`), `supabase/migrations/`, `apps/*/README.md`, `docs/LOCAL_DEVELOPMENT.md`, `docs/plans/*`, `AGENTS.md`, `supabase/functions/README.md`, `docs/plans/2026-08-02-open-source-erp-toolkit-audit.md`.

---

## Table of contents

### Part A — Implemented features & design

1. [System overview](#1-system-overview)
2. [Local URLs, seeds, run commands](#2-local-urls-seeds-run-commands)
3. [Hard exclusions](#3-hard-exclusions)
4. [Auth, OTP, login tabs](#4-auth-otp-login-tabs)
5. [Staff shell & navigation](#5-staff-shell--navigation)
6. [Catalog, VIN, diagrams, hotspots](#6-catalog-vin-diagrams-hotspots)
7. [Storefront (USD + ZiG checkout)](#7-storefront-usd--zig-checkout)
8. [POS / shop floor](#8-pos--shop-floor)
9. [Warehouse](#9-warehouse)
10. [Finance](#10-finance)
11. [Procurement & approvals](#11-procurement--approvals)
12. [Logistics, live map, CARTO](#12-logistics-live-map-carto)
13. [Fleet](#13-fleet)
14. [CRM, reviews, credit](#14-crm-reviews-credit)
15. [HR](#15-hr)
16. [Chat](#16-chat)
17. [Warranty](#17-warranty)
18. [Analytics](#18-analytics)
19. [Mobile apps](#19-mobile-apps)
20. [Hardware bridges](#20-hardware-bridges)
21. [Data pipeline](#21-data-pipeline)
22. [Edge: payments, SMS, email](#22-edge-payments-sms-email)
23. [How to adjust safely](#23-how-to-adjust-safely)
24. [Suggested adjustment priorities](#24-suggested-adjustment-priorities)

### Part B — Tools & open-source going forward

25. [OSS toolkit stance](#25-oss-toolkit-stance)
26. [$20 startup stack mapping](#26-20-startup-stack-mapping)
27. [Adoption decision table](#27-adoption-decision-table)
28. [Recommended sequence](#28-recommended-sequence)
29. [Document history](#29-document-history)

---

# Part A — Implemented features & design

## 1. System overview

### Status

**Shipped foundation** — one Supabase system of record; web + three Android apps + iOS customer shell; bridges + data-pipeline + Edge workers.

### Design

| Layer | Stack | Path |
|-------|--------|------|
| System of record | Supabase (Postgres + RLS + SECURITY DEFINER RPCs + Auth + Storage + Realtime + Edge) | `supabase/` |
| Staff + storefront web | Next.js App Router | `apps/web/` |
| Management / POS | Kotlin Compose | `apps/android-management/` |
| Customer shopping | Kotlin Compose | `apps/android-customer/` |
| Drivers | Kotlin Compose (GPS FGS, POD) | `apps/android-delivery/` |
| Customer iOS | SwiftUI | `apps/ios/` |
| Shared money/cart/types | TS packages | `packages/shared/`, `packages/supabase-client/`, `packages/ui/` |
| Hardware | Native bridges only | `bridges/` |
| Catalog ingest | Python batch (independent of client builds) | `data-pipeline/` |

**Design principles (adjust carefully):**

1. **RPC + RLS are authority** — clients are thin; roles never escalate client-side.
2. **Ledger is append-only** — correct with reversing/contra entries, never edit/delete posted lines.
3. **Multi-currency** — every money field carries `USD` \| `ZIG`; store rate at transaction time.
4. **Catalog displays USD**; **ZiG is settlement at checkout** via finance daily rate.
5. **Staff IA** — module → sidebar submenu only (no duplicate in-page sibling tab strips).
6. **Bridge-First** — camera / QR / Bluetooth printer / biometric / GPS only via `bridges/`.

### Key paths

- `README.md`, `AGENTS.md`, `.cursorrules`, `rufler.yaml`
- Roadmap: `docs/plans/2026-07-23-master-erp-development.md`
- Domain: production `https://nissangtrauto.co.zw`

### Adjustment knobs

- Agent lanes → `rufler.yaml`
- Global laws → `.cursorrules` / path rules under `.cursor/rules/`
- Shared pricing/cart math → `packages/shared/` (do not fork per app)

---

## 2. Local URLs, seeds, run commands

### Status

**Documented and wired** for local Docker + pnpm + Gradle/Xcode.

### Local URLs

| Service | URL |
|---------|-----|
| Web (Next.js) | `http://127.0.0.1:3000` |
| Supabase API / Auth / Storage / Functions | `http://127.0.0.1:54321` |
| Supabase Studio | `http://127.0.0.1:54323` |
| Edge function invoke | `http://127.0.0.1:54321/functions/v1/<name>` |

Ports from `supabase/config.toml` (`[api]` 54321, `[studio]` 54323). Prefer `127.0.0.1` over `localhost` on Windows.

### Seed users (`supabase/seed.sql` via `db reset`)

| Email | Password | Role |
|-------|----------|------|
| `admin@gtr.local` | `local-dev-admin` | admin |
| `finance@gtr.local` | `local-dev-finance` | finance |
| `warehouse@gtr.local` | `local-dev-warehouse` | warehouse |
| `storefront-a@gtr.local` | `local-dev-customer` | customer |
| `storefront-b@gtr.local` | `local-dev-customer` | customer |

Source: `docs/LOCAL_DEVELOPMENT.md` §9. Customers also appear in AuthZ smoke tests.

### Run commands

```bash
# Root
pnpm install
pnpm db:start          # supabase start
pnpm db:reset          # migrations + seed
pnpm db:types          # regenerate database.types.ts
pnpm dev:web           # http://127.0.0.1:3000

# Edge (second terminal)
npx supabase functions serve

# Catalog diagram bytes (after reset)
node supabase/seed_catalog_diagrams.mjs --docker

# Data pipeline
cd data-pipeline && pip install -e ".[dev]" && pytest

# Android (one app at a time — Gradle wrapper lock)
cd apps/android-management && .\gradlew.bat assembleDebug
cd apps/android-customer && .\gradlew.bat assembleDebug
cd apps/android-delivery && .\gradlew.bat assembleDebug

# iOS (macOS)
cd apps/ios && xcodebuild -scheme GTRCustomer -destination 'platform=iOS Simulator,name=iPhone 16'
```

### Adjustment knobs

- Web env → `apps/web/.env.local` from root `.env.example`
- Android secrets → `local.properties` (gitignored); Fake when URL/key missing or `rpc.forceFake=true`
- iOS secrets → `Secrets.xcconfig` / scheme env; Fake via `STOREFRONT_FORCE_FAKE=1`

---

## 3. Hard exclusions

| Exclusion | Meaning |
|-----------|---------|
| **No ZIMRA** | No FDMS, fiscalisation QR, mTLS fiscal devices, tax-authority payloads in checkout/invoices/receipts |
| **No payroll tax** | No PAYE, NSSA, P4/P4A, tax brackets. Gross pay + manual deductions only |
| **Bridge-First** | No HTML5 / WebView QR, camera, or GPS for ops scanning / tracking |

Do not scaffold TODOs or “temporary” browser QR/GPS to unblock web staff.

---

## 4. Auth, OTP, login tabs

### Status

**Shipped** — web login/signup; Edge `auth-otp`; GoTrue password for returning users; staff context post-login.

### Design

| Concern | How it works |
|---------|----------------|
| Public GoTrue email signup | Blocked by `hook_before_user_created` (`enable_signup = true` for OAuth first login) |
| OTP | Edge `auth-otp`: `request` → `verify` → HMAC `proof_token` in `auth_otp_proofs` → `complete_signup` / `complete_login` |
| OAuth | Google / Apple via Supabase Auth; `customers` ensured on first login — [`CUSTOMER_OAUTH_SETUP.md`](./CUSTOMER_OAUTH_SETUP.md) |
| Returning login | Email **or** phone + **password** (OTP not required on password login) |
| Phone login | `complete_login` resolves `profiles.phone_e164` → Auth email → session |
| Staff vs customer | `loadStaffContext` / `staff_roles`; `postLoginPath` → `/staff` (sales-only → `/staff/pos`) |
| Local OTP stub | `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1` + code `000000` (never in prod) |

### Key paths / RPCs / tables

- UI: `apps/web/app/(auth)/login/page.tsx`, `signup/page.tsx`
- Libs: `apps/web/lib/auth-otp.ts`, `country-dial-codes.ts` (`DEFAULT_COUNTRY_DIAL = "+263"`), `staff-auth.ts`
- Edge: `supabase/functions/auth-otp/`
- Tables: `profiles`, `staff_roles`, `auth_otp_proofs`, `customers`

### Adjustment knobs

- Email-only login → remove phone tab / phone Edge path
- Default dial → `DEFAULT_COUNTRY_DIAL`
- Staff landing → `postLoginPath` / `staffHomePath` / `prefersPosHome`
- OTP gateways → `SMS_GATEWAY_*`, `EMAIL_API_KEY` / Resend; fail-closed without secrets

---

## 5. Staff shell & navigation

### Status

**Shipped** — hierarchical sidebar from single tree; hub cards; role gates.

### Design

- Layout: `apps/web/app/(staff)/layout.tsx` + `StaffGate`
- Tree: `STAFF_NAV_TREE` in `apps/web/lib/staff-auth.ts` — modules with children; filtered by roles
- Hub `/staff` driven from same tree
- Subfeature pages: switch via **sidebar only** (no sibling button strips)

### Module → submenu (live tree)

| Module | Submenu |
|--------|---------|
| POS | Cart, Online prep |
| Warehouse | Overview, Receive, Transfers, Cycle count, Bins, Consignment |
| Finance | Petty cash, Cash sales, Online sales, ZiG rate, Journals, Requisitions, Payments, Reports, Bank recon, Periods |
| CRM | Customer credit, Review moderation |
| Logistics | Jobs / pick, Sales prep, Live tracking, Panic inbox |
| Fleet | Company fleet |
| HR | HR desk |
| Warranty | Warranty claims |
| Chat | Customer chat |
| Analytics | KPIs, Report subscriptions |
| Procurement | Overview, RFQs, New RFQ, Blankets, Approvals |

### Adjustment knobs

- Add/rename leaf → `STAFF_NAV_TREE` + matching route page
- Visibility → `roles` on each leaf + `pathAccessFor`
- Theme → CSS vars `--gtr-red`, `--gtr-steel` in staff layout CSS

---

## 6. Catalog, VIN, diagrams, hotspots

### Status

**Partial → working hybrid.** Schema + fixtures + web canvas exist. Full Nissan coverage needs licensed FAST/EPC + rights-cleared art.

### Design

| Piece | Role |
|-------|------|
| `vehicle_master` | Chassis / series / year / engine hierarchy |
| `pnc_categories` | Parts numbering categories |
| `part_fitment` | OEM ↔ vehicle/PNC + optional `diagram_path` + `bbox_*` (0–1) |
| Storage `catalog-diagrams` | Diagram images |
| `search_catalog` | Four-way PG FTS: `part` \| `vin` \| `model` \| `pnc` |
| Pipeline | `data-pipeline/` validate → parse_fast → import; fixtures Navara D40, X-Trail T31 |
| Web canvas | Diagram + hotspots → click → `/parts/{oem}` |

**Policy:** Prefer own/original or licensed diagrams. Scraping Amayama/7zap for production art is high legal risk.

### Key paths

- Web: `apps/web/lib/catalog-search.ts`, `catalog-product.ts`, `catalog-diagram.ts`
- Migration: `20260724010000_catalog_search_fts.sql` (and related catalog migrations)
- Seed diagrams: `supabase/seed_catalog_diagrams.mjs`
- Decision: `docs/decisions/2026-07-24-search-index-interim-pg-fts.md` (Meilisearch deferred)

### Adjustment knobs

- Meilisearch CE later if FTS insufficient (Part B)
- Public vs auth-gated fitment SELECT for storefront diagrams
- Hotspot authoring UI / Label Studio offline for bbox MVP

---

## 7. Storefront (USD + ZiG checkout)

### Status

**Shipped** as primary shopping surface on web.

### Design

| Flow | Design |
|------|--------|
| Browse / PDP | Catalog search + product page; **USD-only** display (`PriceDual` ignores ZiG) |
| Cart | Forced **USD** cart currency |
| Checkout | USD total → optional **Settle in ZiG** via `get_zig_exchange_rate` / env fallback |
| Payments | ContiPay / Paynow intents via Edge (not Stripe-first) |
| Account | Orders, garage, addresses, wishlist, compare, reviews |
| B2B | Trade price lists (incl. FLEET **price list** name — not company fleet module) |
| Track | `/track/[token]` — last point + ETA only |

### Key paths / RPCs

- `apps/web/lib/customer-storefront.ts`, `components/price-dual.tsx`, cart/checkout components
- RPCs: `create_customer_cart`, `add_customer_cart_line`, `checkout_customer_cart`, `get_customer_order`, garage/wishlist/compare/review RPCs
- Rate: `get_zig_exchange_rate`, table `daily_exchange_rates`

### Adjustment knobs

- Daily ZiG rate → Finance **ZiG rate** tab
- Re-enable catalog ZiG display → change `PriceDual` (currently intentional USD-only)
- Payment gateways → ContiPay / Paynow Edge secrets
- Site URL / brand → `NEXT_PUBLIC_SITE_URL`, `docs/decisions/2026-07-23-storefront-autodoc-logo.md`

---

## 8. POS / shop floor

### Status

**Shipped** — web `/staff/pos` + Android management POS module.

### Design

- Staff cart, named customer bind, checkout, receipt contacts
- Tabs: **Cart** \| **Online prep** (from `STAFF_NAV_TREE`)
- OTP is for **customer signup/confirm**, not till login
- Optional companion phone QR via Bridge — web must not use HTML5 camera QR
- Sales-only role lands on POS by design

### Key paths / RPCs

- Web: `apps/web/app/(staff)/staff/pos/`
- Android: `apps/android-management/feature/pos`
- RPCs: `create_pos_cart`, checkout/receipt RPCs, `create_pos_scan_session` (companion)

### Adjustment knobs

- Till float / variance engine — **deferred**
- Companion polling interval / revoke session behaviour
- Default warehouse → `NEXT_PUBLIC_DEFAULT_WAREHOUSE_ID`

---

## 9. Warehouse

### Status

**Shipped** — core RPCs + web staff pages + Android warehouse / bins / consignment.

### Design

- Warehouses include **Quarantine** (returns never go straight to saleable stock)
- Mutations via SECURITY DEFINER RPCs (not raw client writes)
- Flows: receive, transfers, cycle count, bins/pick-path, consignment
- Routes under `/staff/warehouse/...`

### Key paths / RPCs / tables

- Web: `apps/web/app/(staff)/staff/warehouse/**`
- Android: `:feature:warehouse` (receive/transfer/cycle + bins + consignment)
- Tables: `warehouses`, `stock_items`, `stock_levels`, bins, consignment tables
- Core charge: parent-child cart lines split deposit from part price (`packages/shared/`)

### Adjustment knobs

- Default warehouse env
- Bin topology / ESC/POS label templates (Android bridge)
- Quarantine warehouse code — do not bypass for returns

---

## 10. Finance

### Status

**Core shipped** (Phase 3 + period balances + requisitions + registers). AP payment-run desk and some polish deferred.

### Chart of accounts (cash / imprest)

| Code | Role |
|------|------|
| **1100** | Bank / cash — **funds** petty cash (imprest source) |
| **1110** | Petty cash imprest asset |
| **1120** | Cash sales / till |
| **1130** | Online sales cash/bank equivalent |

**Imprest:** Replenish drafts move **1100 → 1110**. Expense posts from **1110** at disburse (GTR continuous expense-at-disburse).

### Feature behaviours

| Feature | How it works | UI |
|---------|----------------|-----|
| Journals | Draft → post → reverse (immutable posted) | Finance → Journals |
| Account register | `report_account_register` — Date \| Description \| **Debit** \| **Credit** \| Balance \| Currency | Petty / cash / online + statement views |
| Period open/close | `account_period_balances` — opening, open, close with optional physical count | Periods + strip on cash tabs |
| Requisitions | Type `petty_cash` \| `payment`; lines; submit → finance\|admin approve/reject → disburse JE | Requisitions |
| ZiG rate | `daily_exchange_rates`; get/set/list RPCs | ZiG rate |
| Payments / AR | Payment entries / allocate | Payments |
| Reports | P&L, BS, CF, TB + CSV | Reports |
| Bank recon | Manual statement match | Bank recon |

Migrations of note: `20260725250000_finance_period_balances_requisitions.sql`, `20260725260000_finance_requisition_lines.sql`.

### Not built / deferred

- AP **payment-run desk** (vendor batch Dr AP / Cr Bank)
- Multi-level amount-threshold approvals
- Finance requisition ↔ PO document link
- Register PDF print / bank-feed AI import
- CoA split of 1100 into separate Bank vs Cash
- Dedicated Android finance screen

### Adjustment knobs

- Imprest funder account → replenish JE template / CoA seed (`app.settings` funding account → `1100`)
- Period length → open/close RPC args + UI
- Who can approve requisitions → `_require_*` role checks in RPCs

---

## 11. Procurement & approvals

### Status

**Shipped** — RFQ / PO / MR / blankets / GRN path; **PO/MR approve Phase 1 shipped**.

### Design

| Status flow | `draft` → `submitted` → **`approved`** / `rejected` → then GRN / blanket release / MR→PO |
| Approvers | finance \| admin via `approve_*` / `reject_*` RPCs |
| UI | `/procurement/approvals` + blanket approve/reject; nav **Procurement → Approvals** |
| Supplier portal | `/supplier/rfqs/*` — invited quotations |
| Side effect | `po_approved` domain event / SMS when wired |

Reject requires a reason in UI. GRN gated on `approved`.

### Key paths / RPCs

- Migration: `20260725271000_procurement_approve.sql`
- RPCs: `approve_purchase_order`, `approve_material_request`, reject counterparts, RFQ/blanket/GRN RPCs
- Web: `/procurement/**`, `/supplier/**`
- Android: blanket POs module

### Adjustment knobs

- Approver roles → `_require_procurement_finance` (or equivalent)
- Multi-level approval → Phase 2 / not built
- Require approval before GRN — already gated

---

## 12. Logistics, live map, CARTO

### Status

**Shipped** — jobs, assign, dispatch, live staff map, panic inbox, customer last-point track; driver GPS in **delivery app only**.

### Design

| Piece | Design |
|-------|--------|
| Jobs | `delivery_jobs` + status RPCs; pickup/dropoff geo for suggest + ETA |
| Live map (staff) | Subscribe to `delivery_locations`; **no browser GPS**; default centre **Harare** |
| Basemap | Keyless **CARTO Positron** (+ raster fallback); override `NEXT_PUBLIC_MAP_STYLE_URL` |
| Customer track | `get_delivery_track_point` — last point + ETA only (no trail) |
| Panic | Staff inbox for `panic_events` |
| SMS out-for-delivery | Status update → outbox; Edge fail-closes without gateway key |
| GPS producer | **Only** `apps/android-delivery` FGS → `ingest_delivery_location` |

### Key paths

- Web: `/staff/logistics/**`, `components/staff-delivery-live-map.tsx` (`HARARE_CENTER`), `lib/map-basemap.ts`
- Management Android: dispatch (subscribe only; `ALLOW_DRIVER_GPS_PRODUCER = false`)
- Delivery Android: tracking + POD + panic

### Adjustment knobs

- Map centre/zoom → `HARARE_CENTER`
- Tile provider → `map-basemap.ts` / env
- Optional **Traccar** / **OSRM** satellites (Part B) without replacing job RPCs

---

## 13. Fleet

### Status

**MVP shipped** — company vehicles CRUD (plan `2026-07-25-fleet-management.md`).

### Design

- Table `fleet_vehicles` (plate, label, status, optional driver assignee)
- Mutations via `upsert_fleet_vehicle` / `set_fleet_vehicle_status` / `list_fleet_vehicles`
- Staff web `/staff/fleet` + Android management fleet feature
- **Not** wired to dispatch suggest/assign in v1 (`fleet_vehicle_id` on jobs = follow-on)
- Distinct from B2B **FLEET** price list name

### Adjustment knobs

- Link vehicle → delivery job (Phase 2)
- Traccar device id on vehicle row (optional later)
- No telematics / fuel / maintenance in v1

---

## 14. CRM, reviews, credit

### Status

**Shipped** — staff CRM credit + review moderation; customer reviews on web/mobile.

### Design

- B2B credit limits / holds via `set_customer_credit` (explicit currency)
- Product reviews submit → staff moderate
- Photos via Storage `review-photos`; Android uses Bridge camera when available

### Key paths

- Web: `/staff/crm/credit`, `/staff/crm/reviews`
- Android management: `:feature:credit`
- Customer apps: reviews modules + Storage upload

### Adjustment knobs

- Who can set credit → admin \| sales \| finance
- Review moderation workflow / auto-approve — not default

---

## 15. HR

### Status

**Desk-level / partial** — attendance clock; **no payroll tax engine**.

### Design

- Staff HR desk UI `/staff/hr`
- Android: `clock_attendance`
- Gross pay from attendance/hours/salary structure + **manual** deduction lines only
- Biometric clock = contract-only / deferred (Phase 12 bridges)

### Adjustment knobs

- Attendance rules → `clock_attendance` RPC
- Do **not** add PAYE/NSSA/statutory forms

---

## 16. Chat

### Status

**Shipped** — customer ↔ staff threads; same RPCs across web / Android / iOS.

### Design

- Customer start thread / post message / mark read
- Staff inbox: open / mine / closed; claim → reply → close
- Edge `chat-notify-on-message` stubbed until push/email wired
- Mobile often polls (~4–5s); web may use Realtime where enabled

### Key RPCs

- `start_chat_thread`, `post_chat_message`, `mark_chat_thread_read`, `chat_unread_count`

### Adjustment knobs

- Notify channels → wire worker beyond stub
- WhatsApp `wa.me` CTA digits → env `WHATSAPP_E164`

---

## 17. Warranty

### Status

**Shipped desk** — claims panel (Phase 5b smoke exists).

### Design

- Staff `/staff/warranty` — claims intake/status against sales/returns flows
- Quarantine protocol for faulty returns (contra-revenue + quarantine warehouse)

### Adjustment knobs

- Claim status machine / required evidence fields in RPCs
- Link to quarantine warehouse on return

---

## 18. Analytics

### Status

**Shipped** — KPIs + report subscriptions; optional Gemini narrative.

### Design

- Staff `/staff/analytics` + `/staff/analytics/subscriptions`
- Edge `analytics-insights` (interactive) + `process-ai-reports` (cron)
- Aggregates only — no customer PII / journal dumps to Gemini
- Timezone default **Africa/Harare**
- Missing `GEMINI_API_KEY` → numeric KPIs still return; narrative 422 / numeric-only cron

### Adjustment knobs

- Cadence daily/weekly/monthly → cron + subscription rows
- `GEMINI_MODEL` (default `gemini-2.0-flash`)
- Later: Apache Superset on read-only Postgres (Part B)

---

## 19. Mobile apps

### 19.1 Android management (`apps/android-management`)

| Status | **Shipped scaffolds** — POS, warehouse, bins, consignment, blankets, credit, dispatch, fleet, HR clock, chat |
| Design | Thin Compose modules + Fake/Live `RpcClient`; nested module submenu; Bridge QR + ESC/POS |
| Fake | Empty Supabase keys or `rpc.forceFake=true` |
| GPS | Staff view only — **no** driver GPS producer |
| APK | `app/build/outputs/apk/debug/app-debug.apk` |

### 19.2 Android customer (`apps/android-customer`)

| Status | Account flows **+ Catalog Phase 1** (browse / `search_catalog` / PLP / PDP / add-to-cart) |
| Design | Same AuthZ RPCs as web storefront helpers |
| Still missing (Phases 2–4) | Diagram hotspots, checkout ZiG polish, home merchandising — see `docs/plans/2026-07-27-mobile-storefront-parity.md` |
| Fake demo | Home → **Catalog** → OEM `15208-65F0C` → Add to cart → Cart |
| Bridge | Review photos via `pod-camera` |

### 19.3 Android delivery (`apps/android-delivery`)

| Status | **Driver-only** dedicated app |
| Design | Presence, FGS GPS ingest, navigate, geofence **suggest** (never auto), POD photo+signature+OTP, fail/reattempt, optimize stops, panic |
| Not included | POS / warehouse / HR / finance |
| RPCs | `ingest_delivery_location`, `set_driver_presence`, `submit_delivery_pod`, `raise_delivery_panic`, etc. |

### 19.4 iOS customer (`apps/ios`)

| Status | Tabs scaffolded; **Catalog Phase 1 = shell**; full catalog API = Phase 2 |
| Design | Fake vs Live via env; GoTrue email/password; URLSession PostgREST (no supabase-swift on Windows) |
| Tabs | Catalog, Cart, Orders, Garage, Wishlist, Compare, Reviews, Pay, Chat, Track |
| Track | `get_delivery_track_point` — single pin, no trail |

### Adjustment knobs (all mobile)

- Force Fake → `rpc.forceFake` / `STOREFRONT_FORCE_FAKE`
- Live keys → never commit; `local.properties` / xcconfig only

---

## 20. Hardware bridges

### Status

**Android impls shipped** for QR, ESC/POS, location FGS, POD camera, POD signature. **iOS** QR / ESC/POS / LocationTracker packages present. **Biometric** = TypeScript contracts only (impl deferred).

### Design

| Bridge | Path | Use |
|--------|------|-----|
| QR scanner | `bridges/android/qr-scanner`, `bridges/ios/QRScanner` | POS/warehouse inventory QR |
| ESC/POS | `bridges/android/escpos-printer`, `bridges/ios/escpos-printer` | Receipts / bin labels |
| Location | `bridges/android/location-tracker`, `bridges/ios/LocationTracker` | Delivery GPS ingest |
| POD camera | `bridges/android/pod-camera` | POD + review photos |
| POD signature | `bridges/android/pod-signature` | Delivery signature |
| Biometric | `bridges/contracts/biometric.ts` | Interface only — no device impl yet |
| Contracts | `bridges/contracts/*` | Shared TS shapes |

### Adjustment knobs

- Payload formats → `bridges/contracts/`
- Do not “fix” missing bridges with browser APIs on web staff

---

## 21. Data pipeline

### Status

**Shipped** — validate / parse_fast / import / search_index smoke; Navara + X-Trail fixtures.

### Design

- Independent of client builds
- Idempotent upserts into catalog tables
- Production search = Postgres `search_catalog` (Meilisearch deferred)
- Demo OEMs: `15208-65F0C`, `40206-EA00A`, `21410-JF00A`, `16546-00Q0A`

### Key paths

- `data-pipeline/README.md`, `data_pipeline/*.py`, `fixtures/*`, `schemas/*`
- Tests: `cd data-pipeline && pytest`

### Adjustment knobs

- Fixture packs → add under `fixtures/<vehicle>/`
- Live import → service role env (never in client apps)
- Meilisearch sync hook later (Part B)

---

## 22. Edge: payments, SMS, email

### Status

**Shipped** function set; real sends **fail-closed** without secrets; local stubs gated.

### Functions (live tree)

| Function | Role |
|----------|------|
| `auth-otp` | Signup/login OTP + proof |
| `contipay-initiate` / `contipay-webhook` | ContiPay intents + settle |
| `paynow-initiate` / `paynow-webhook` | Paynow intents + settle |
| `process-sms-outbox` | SMS drain |
| `process-customer-receipts` | Tax-agnostic PDF + SMS/email/WA |
| `receipt-download` | Opaque token → Storage signed URL |
| `chat-notify-on-message` | Notify targets (stub delivery) |
| `analytics-insights` / `process-ai-reports` | Staff KPIs + subscriptions |
| `demand-forecast` | Demand worker |
| `whatsapp-webhook` | Parts-finder bot + catalog search |

### Design notes

- Workers require `x-worker-secret` (`WORKER_SHARED_SECRET`)
- Email: Resend-compatible (`EMAIL_API_KEY` / `RESEND_API_KEY`)
- Receipts: **no** ZIMRA/FDMS/fiscal QR; reject fiscal path patterns
- Payments: ContiPay / Paynow — **not** Stripe-first for ZW settlement

### Adjustment knobs

- Secrets via Supabase Edge / local `supabase/functions/.env` — never commit
- `WORKER_ALLOW_UNVERIFIED_LOCAL=1` / OTP local stub — local only
- Public receipt host → `https://nissangtrauto.co.zw/receipts/{token}`

---

## 23. How to adjust safely

1. Prefer **RPC + migration** for business rules; keep clients thin.
2. Any new table → **RLS in the same migration**.
3. Money → always pass **currency**; store FX at transaction time.
4. Ledger mistakes → **reversing entries**, never UPDATE/DELETE posted lines.
5. Staff UX → update `STAFF_NAV_TREE` when adding routes; no sibling tab strips.
6. Hardware → Bridge-First only.
7. Local verify: `pnpm db:reset`, `pnpm dev:web`, seed users, `supabase/tests/*_smoke.sql`.
8. Android: assemble **one** app at a time (wrapper lock).
9. After non-trivial work: `/security-reviewer` then `/verifier`; after migrations: `/supabase-rls-auditor`.

---

## 24. Suggested adjustment priorities

| Priority | Area | Why |
|----------|------|-----|
| P1 | Production catalog (licensed FAST + original diagrams) | Fixtures ≠ full Nissan catalog |
| P1 | Mobile Phase 2–3 (iOS catalog API, Android diagrams) | Shopping parity |
| P1 | Prod deploy (Vercel + DNS + remote Supabase + Edge secrets) | Go-live ops |
| P2 | AP payment runs | Completes procure-to-pay |
| P2 | Meilisearch CE or Traccar | High-leverage satellites |
| P3 | Finance register polish (print/CSV) | Ops convenience |
| P3 | Biometric bridge impl | Attendance / staff unlock |

---

# Part B — Tools & open-source going forward

## 25. OSS toolkit stance

Full audit: [`docs/plans/2026-08-02-open-source-erp-toolkit-audit.md`](plans/2026-08-02-open-source-erp-toolkit-audit.md).

**Do not rebase** onto ERPNext / Odoo / Frappe HR — conflicts with existing Supabase ERP and the **payroll tax exclusion**.

### Integrate (satellites / libs)

| Candidate | Why for GTR |
|-----------|-------------|
| **Meilisearch CE** | Typo-tolerant SKU/keyword/fitment search over existing catalog |
| **Traccar** | Fleet live GPS / geofences; keep dispatch UI custom |
| **OSRM** | Delivery ETA / route optimization |
| **Casbin (`node-casbin`)** | Fine-grained RBAC across Next.js + mobile backends |
| **Gorse** | Cross-sell / “replaced together” from sales events |
| **Apache Superset** | BI dashboards on Postgres (Apache-2.0 friendlier than Metabase AGPL) |
| **NHTSA vPIC** | VIN decode enrichment (US-centric coverage gaps OK) |
| **node-qrcode / ZXing / ML Kit** | Labels & Bridge scan helpers (not platforms) |

### Skip

| Candidate | Reason |
|-----------|--------|
| **ERPNext / Frappe** | Existential rewrite; GPLv3 |
| **Frappe HR / Payroll** | Hard exclusion + stack |
| **Odoo** | Same replacement conflict |
| **Bagisto** | PHP/Laravel — wrong monorepo language |
| **TailPOS** | Abandoned; ERPNext-only |
| **Vendure** | GPLv3 core; prefer Medusa only if headless commerce gap |
| **Hyperledger Fabric** | Ops-heavy; overkill vs hash-chain if needed |
| **Typesense** | Prefer Meilisearch CE (MIT) |
| **Metabase** | Prefer Superset unless internal-only and team prefers UX |
| **Keycloak** | Overlaps Supabase Auth unless true multi-IdP SSO |
| **Outline / Wiki.js** | License / overkill for notice board |

**Medusa:** conditional only if a true headless B2B/mechanic portal gap remains — do **not** replace back-office ERP.

---

## 26. $20 startup stack mapping

Maps a typical low-cost SaaS starter kit onto this monorepo.

### Keep (already core)

| Tool | Verdict |
|------|---------|
| **Supabase** | System of record — Auth, Postgres, RLS, Storage, Edge. Keep. |
| **GitHub** | Source control (`Vanguduza/Nissan-Gtr-Auto`). Keep. |
| **Cursor / Claude** | Dev tooling only — not a runtime product dependency. |

### Adopt / wire (fit this ERP)

| Tool | Suggested use |
|------|----------------|
| **Vercel** | Deploy `apps/web` (storefront + staff). Free tier OK to start. |
| **Namecheap** (registrar) + **Cloudflare** DNS/TLS | Production domain `nissangtrauto.co.zw` (+ www redirect). |
| **Resend** | Transactional email — receipts, OTP, report subscriptions (Edge already Resend-shaped). |
| **Sentry** | Error tracking for Next.js + optional mobile. |
| **PostHog** | Product analytics / funnels on storefront (privacy-aware; no finance PII dumps). |
| **Upstash** | Redis for rate limits / short-lived caches at Edge or Next middleware (optional). |

### Skip / replace

| Tool | Verdict |
|------|---------|
| **Clerk** | Skip — keep **Supabase Auth** (+ existing OTP Edge). |
| **Pinecone** | Skip — no vector-search product need; catalog is structured FTS / later Meilisearch. |
| **Stripe-first** | Skip as primary PSP — use **ContiPay / Paynow** for ZW settlement; Stripe only if a future USD-card niche appears. |

---

## 27. Adoption decision table

| Project | License | Fit for GTR | Suggested use | Priority |
|---------|---------|-------------|----------------|----------|
| Meilisearch CE | MIT (CE) | Excellent | Index catalog/SKU/fitment; keep PG as source of truth | P1 |
| Traccar | Apache-2.0 | Excellent | Fleet GPS / geofence; custom dispatch in GTR | P1 |
| OSRM | BSD-2-Clause | Strong | Delivery ETA / route optimize | P2 |
| Casbin (node-casbin) | Apache-2.0 | Strong | API RBAC across web + mobile backends | P2 |
| Gorse | Apache-2.0 | Strong | Cross-sell / replaced-together | P2 |
| Apache Superset | Apache-2.0 | Strong | Finance/ops dashboards on Postgres | P2 |
| Vercel | Proprietary SaaS | Strong | Host Next.js web | P1 |
| Cloudflare + Namecheap | SaaS | Strong | DNS/TLS for `nissangtrauto.co.zw` | P1 |
| Resend | SaaS | Strong | Email provider for Edge outbox | P1 |
| Sentry | SaaS / proprietary | Strong | Error monitoring | P2 |
| PostHog | MIT (self-host) / cloud | Good | Storefront product analytics | P2 |
| Upstash | SaaS | Good | Rate limit / cache | P3 |
| NHTSA vPIC | Public API | Good | VIN decode enrichment | P3 |
| node-qrcode / ZXing / ML Kit | MIT / Apache / proprietary | Good | Labels + Bridge scan | P3 |
| Medusa | MIT | Conditional | Headless B2B portal **if** gap remains | P3 |
| Supabase | Apache-2.0 (studio mix) | Core | Keep backend | — |
| GitHub | SaaS | Core | Keep VCS / CI | — |
| ContiPay / Paynow | Merchant | Core (ZW) | Keep as primary PSP path | — |
| Typesense | GPL-3.0 | Alt only | Prefer Meilisearch CE | Skip |
| Metabase | AGPLv3 | OK internal | Prefer Superset | Skip default |
| Keycloak | Apache-2.0 | Weak | Only if multi-IdP SSO required | Skip |
| Bagisto | MIT | Poor | Wrong stack | Skip |
| Vendure | GPLv3 | Weak | Prefer Medusa if needed | Skip |
| ERPNext / Frappe | GPLv3 | Conflict | Do not rebase | Skip |
| Frappe HR / Payroll | GPLv3 | Excluded | Payroll ban | Skip |
| TailPOS | GPLv3 | Skip | Abandoned | Skip |
| Hyperledger Fabric | Apache-2.0 | Skip now | Ops cost | Skip |
| Outline | BSL 1.1 | Skip | Not true OSS; overkill | Skip |
| Wiki.js | AGPLv3 | Skip | Overkill notice board | Skip |
| Odoo | Mixed | Conflict | Skip as platform | Skip |
| Clerk | SaaS | Skip | Overlaps Supabase Auth | Skip |
| Pinecone | SaaS | Skip | No vector RAG need | Skip |
| Stripe (primary) | SaaS | Skip first | ContiPay/Paynow first | Skip |

---

## 28. Recommended sequence

1. **Prod web path:** Vercel + Cloudflare/Namecheap DNS + remote Supabase + Edge secrets (Resend, SMS, ContiPay/Paynow, `WORKER_SHARED_SECRET`).
2. **Meilisearch CE** — sync catalog documents from Supabase; keep `search_catalog` or dual-read.
3. **Traccar** — map devices to `fleet_vehicles`; thin API adapter; keep GTR dispatch RPCs.
4. **OSRM** — ETA for logistics / delivery app.
5. **node-casbin** — unify staff/customer/driver policy checks at API boundary.
6. **Gorse** — feed sales/returns events; surface in POS + storefront.
7. Optional **Superset**, **Sentry**, **PostHog**, **Upstash**.
8. Continue catalog/VIN/hotspots as **first-party** build — no full OSS shortcut.

---

## 29. Document history

| Date | Note |
|------|------|
| 2026-08-02 | First compiled guide (pre-restore note: docs-only workspace). |
| 2026-08-02 | **Overwrite** from live restored monorepo evidence + OSS toolkit audit + $20 startup stack mapping. Part A domains + Part B adoption table. |

---

*License interpretations in Part B are not legal advice; re-read LICENSE files before commercial distribution of modified copies.*
