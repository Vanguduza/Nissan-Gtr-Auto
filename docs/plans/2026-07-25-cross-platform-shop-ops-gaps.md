# Cross-platform shop & ops gaps

- Status: **Done** (verified 2026-07-25 — `/security-reviewer` harden + `/verifier`)
- Date: 2026-07-25
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Immediate handoff
- Prior wave (Done): [`2026-07-25-audit-followons-no-secrets.md`](./2026-07-25-audit-followons-no-secrets.md) — web-minimum only
- Decisions: [`autodoc-shop-features`](../decisions/2026-07-23-autodoc-shop-features.md), [`web-management-parity-rbac`](../decisions/2026-07-25-web-management-parity-rbac.md); Phase 8b/16 plans for blankets/bins/consignment
- Skills: `/token-discipline`; `/qr-inventory-workflow` only for bin-label ESC/POS; `/parts-catalog-ingestion` only for diagram seed expand
- DB: shop-ops migrations `20260725200000`–`20260725204000` + grants harden `…221000` applied locally; smoke `wishlist_reviews_navara_diagrams_smoke` **OK**

## Goal

Close **remaining** shop/ops gaps across **web + iOS + Android customer + Android management** by binding **existing** tables/RPCs (extend schema only where gaps are real) — no parallel systems, no secrets in git.

## Survey cites (do not reinvent)

| Area | Exists today | Gap |
|------|----------------|-----|
| Wishlist | `…180000` `customer_wishlist_items`; `add_customer_wishlist_item` / `remove_customer_wishlist_item`; web `wishlist-panel` / `customer-wishlist.ts` | No mobile; no move-to-cart; no back-in-stock flag |
| Cart (move-to-cart) | `add_customer_cart_line` / `create_customer_cart` (`…130000`); web `customer-storefront.ts` | Compose wishlist → cart UI; optional thin RPC wrapper |
| Compare | Web `compare-selection.ts` localStorage only; smoke asserts **no** compare table | Server table+RLS for auth; richer attribute matrix; mobile screens |
| Reviews | `customer_product_reviews`; `submit_*` / `moderate_customer_product_review`; web account+PDP list | Staff moderation UI; aggregates; photos Storage; mobile; optional outbox on approve |
| Outbox | `emit_domain_event` + `sms_outbox` + `sms_event_catalog` (fail-closed drain) | New event codes only; **no** SMS send without keys |
| Blankets | `create_blanket_*` (`…060000`/`…063000`); staff `/procurement/blankets`; supplier RLS `purchase_orders_supplier_select` | Supplier portal view; expiry/remaining alerts; management thin UI |
| Bins / consignment | `warehouse_bins` + `get_pick_path_hints` (`…120000`); consignment RPCs (`…122000`); web staff panels | Management screens; ESC/POS bin labels; pick-path hint bind (web+mgmt) |
| Diagrams | Navara seed in `…180000` + `seed_catalog_diagrams.mjs`; fixtures under `data-pipeline/fixtures/navara_d40_yd25/` | Expand fixtures beyond single vehicle; ops doc; idempotent seed |
| B2B credit | `customers.credit_limit` / `credit_hold` / `open_balance`; B2B **read** on `/b2b`; staff `customers` ALL via RLS | Staff set/clear UI (+ management if role-ok); no dedicated mutator RPC yet → add SECURITY DEFINER staff RPC |
| Staff polish | Finance `searchCustomers`; POS stock search only; staff nav lacks reviews/credit deep links | POS named-customer bind; CRM-ish nav labels |

## Feature × platform matrix (shipped)

Legend: **L** = Live · **P** = Partial · **G** = Gap · **—** = N/A

| Feature | Backend | Web | iOS | Android cust | Android mgmt |
|---------|:-------:|:---:|:---:|:------------:|:------------:|
| Wishlist list/add/remove | L | L | L | L | — |
| Move-to-cart from wishlist | L (`wishlist_move_to_cart`) | L | L | L | — |
| Back-in-stock flag + notify | L (flag + outbox enqueue) | L | L | L | — |
| Compare (local guest) | — | L | L | L | — |
| Compare (server auth sync) | L | L | L | L | — |
| Compare attribute matrix | — | L | P (subset) | P (subset) | — |
| Reviews submit/list | L | L | L | L | — |
| Reviews staff moderate UI | L | L | — | — | — |
| Rating aggregates on PDP | L | L | L | L | — |
| Review photo attachments | L (bucket + RLS) | L | P (PhotosPicker) | L (PodCameraBridge) | — |
| Blanket staff / remaining | L | L | — | — | L |
| Blanket supplier portal | L (RLS) | L | — | — | — |
| Blanket expiry alerts | L | L | — | — | L |
| Warehouse bins UI | L | L | — | — | L |
| Consignment UI | L | L | — | — | L |
| Bin label ESC/POS | L (text lines) | — | — | — | P (no QR glyph) |
| Pick-path guidance | L | L | — | — | L |
| Diagrams beyond Navara | L (X-Trail seed) | L | — | — | — |
| B2B credit view | L | L | — | — | L |
| B2B credit set/hold | L (`set_customer_credit`) | L | — | — | L |
| POS named-customer search | L | L | — | — | L |
| Staff nav CRM entry points | — | L | — | — | — |

## Acceptance criteria

1. [x] **Wishlist mobile** — iOS + Android customer: list + add/remove via existing wishlist RPCs; Fake RPC parity for offline demos.
2. [x] **Move-to-cart** — Web + mobile: `wishlist_move_to_cart` DEFINER helper (compose of cart RPCs).
3. [x] **Back-in-stock** — `notify_when_in_stock` + `set_wishlist_notify_when_in_stock`; stock hook enqueues `wishlist_back_in_stock` domain/sms outbox (**fail-closed**).
4. [x] **Compare** — `customer_compare_items` + RLS; guest local; sync on login; web attribute matrix; smoke requires table + ownership tests.
5. [x] **Reviews moderation** — Web `/staff/crm/reviews` binds `moderate_customer_product_review`; staff CRM nav.
6. [x] **Review aggregates + photos** — `get_product_review_stats`; `review-photos` Storage + RLS; web upload; Android **Bridge-First** `PodCameraBridge`; iOS **PhotosPicker** (library, not camera bridge — residual).
7. [x] **Reviews mobile** — iOS + Android submit/list (approved + own pending).
8. [x] **Optional review-approved notify** — `review_approved` catalog + `emit_domain_event` on moderate approve; drain fail-closed.
9. [x] **Blankets** — Supplier `/supplier/blankets`; expiry/remaining alerts staff + supplier; Android management blankets UI.
10. [x] **Bins / consignment management** — Android management bins/consignment; ESC/POS text bin labels via `EscPosPrinterBridge`; web + mgmt `get_pick_path_hints` (**optional QR glyph on label = residual**).
11. [x] **Diagrams** — X-Trail fixtures + `…204000` seed; `seed_catalog_diagrams.mjs` idempotent; ops in `LOCAL_DEVELOPMENT.md`.
12. [x] **B2B credit staff** — Web `/staff/crm/credit` + management Credit (role-gated); `set_customer_credit` SECURITY DEFINER; explicit currency on RPC snapshot.
13. [x] **Staff polish** — Web + management POS named-customer `searchCustomers`; CRM nav to credit + reviews.
14. [x] **Gates** — Security harden migrations (`…221000` grants); `/verifier` 2026-07-25; master handoff updated; no secrets / ZIMRA / payroll tax; Bridge-First for Android camera + ESC/POS.

### Residuals (non-blocking)

- Native **JDK 17+ / Xcode** assemble not re-run in verifier session (hosts optional).
- Bin label = ESC/POS **text lines only** — optional inventory-QR glyph deferred.
- iOS review photos via **PhotosPicker** (photo library), not an iOS camera bridge yet.
- Parallel auth-OTP / staff-ops migrations may need `db reset` / repair if local `schema_migrations` lags (`…211000`, `…220000`).

## Paths in scope (by lane)

| Lane | Paths |
|------|--------|
| `@backend_agent` | `supabase/migrations/` (compare table+RLS; wishlist back-in-stock; review aggregates/photos bucket; credit mutator RPC; sms_event_catalog codes; diagram seed expand); `supabase/tests/*`; `supabase/seed_catalog_diagrams.mjs`; types regen note |
| `@web_agent` | `apps/web/` — wishlist move-to-cart; compare matrix + sync; staff review moderation; PDP aggregates/photos; supplier blankets; pick-path hints; B2B credit staff panel; POS customer search; staff nav |
| `@ios_agent` | `apps/ios/` — Wishlist/Compare/Reviews screens + `LiveStorefrontApi` / `RpcName`; Fake parity; Bridge-First photo if camera |
| `@android_agent` | `apps/android-customer/` — feature modules wishlist/compare/reviews; `RpcNames` + Fake/Live clients |
| `@management_app_agent` | `apps/android-management/` — bins, consignment, blankets thin UI; pick-path; credit if role; POS customer bind; ESC/POS bin labels via existing bridge |
| `@hardware_mobile_agent` | Only if bin-label / review-camera bridge gaps — `bridges/android/escpos-printer/`, camera contracts (no new WebView APIs) |
| `@data_pipeline_agent` | Optional fixture expand under `data-pipeline/fixtures/` (backend owns seed migration) |

## Out of scope

PSP/SMS/WhatsApp/Gemini/map merchant secrets; ContiPay webhook confirm; inventing Meili/PowerSync; full catalog scrape; garage service reminders; loyalty redesign; ZIMRA; payroll tax; HTML5/browser QR; commits unless user asks; android-delivery app changes.

## Risks / exclusions

- **Compare smoke flip** — `wishlist_reviews_navara_diagrams_smoke.sql` currently **requires** no compare table; replace with RLS ownership tests.
- **Review photos** — new Storage bucket + RLS same migration; mobile must use bridge/local path → upload, not in-WebView camera for QR.
- **Back-in-stock / notify** — enqueue only; never log API keys; drain stays fail-closed.
- **Credit mutator** — staff-role gated DEFINER; multi-currency explicit on limit display; do not let storefront self-set limit/hold.
- **Supplier blankets** — read-only; no call-off from supplier portal unless existing RPC already allows (prefer read + alert only).

## Ordered implementation

```
Plan (this file) → @backend_agent → @web_agent → @ios_agent
  → @android_agent → @management_app_agent
  → (/hardware_mobile_agent if bridge gap)
  → /security-reviewer → /verifier → /manager done gate
  → update master Immediate handoff
```

Exact invokes:

1. **`/manager`** — sequence below; one coding lane at a time.
2. **`@backend_agent`** — schema/RPCs/smokes/seed for compare, back-in-stock, review aggregates+photos+events, credit mutator, diagram expand; regenerate types note.
3. **`@web_agent`** — all web acceptance rows; staff nav polish.
4. **`@ios_agent`** — wishlist/compare/reviews (+ photo bridge if ready).
5. **`@android_agent`** — same customer surfaces.
6. **`@management_app_agent`** — bins/consignment/blankets/pick-path/credit/POS customer; bin label print.
7. **`@hardware_mobile_agent`** — only if label/camera contract missing.
8. **`/security-reviewer`** (+ **`/supabase-rls-auditor`**).
9. **`/verifier`**.
10. **Docs** — mark this plan Done; refresh master Immediate handoff.

## Done when

All acceptance checkboxes met on target platforms; secrets remain fail-closed; master handoff points here as **Done** (or lists only ops/secrets leftovers).

**Exit (2026-07-25):** Met. Verifier **PASS** with residuals above. Master Immediate handoff lists secrets/ops leftovers only.

## Handoff (complete)

Shipped via `@backend_agent` → `@web_agent` → `@ios_agent` → `@android_agent` → `@management_app_agent` → `/security-reviewer` → `/verifier`. No further coding for this epic unless residuals are promoted.
