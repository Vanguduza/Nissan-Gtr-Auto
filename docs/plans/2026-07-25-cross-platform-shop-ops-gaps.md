# Cross-platform shop & ops gaps

- Status: **Ready for implementation**
- Date: 2026-07-25
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Immediate handoff
- Prior wave (Done): [`2026-07-25-audit-followons-no-secrets.md`](./2026-07-25-audit-followons-no-secrets.md) — web-minimum only
- Decisions: [`autodoc-shop-features`](../decisions/2026-07-23-autodoc-shop-features.md), [`web-management-parity-rbac`](../decisions/2026-07-25-web-management-parity-rbac.md); Phase 8b/16 plans for blankets/bins/consignment
- Skills: `/token-discipline`; `/qr-inventory-workflow` only for bin-label ESC/POS; `/parts-catalog-ingestion` only for diagram seed expand

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

## Feature × platform matrix

Legend: **L** = Live · **P** = Partial · **G** = Gap · **—** = N/A

| Feature | Backend | Web | iOS | Android cust | Android mgmt |
|---------|:-------:|:---:|:---:|:------------:|:------------:|
| Wishlist list/add/remove | L | L | G | G | — |
| Move-to-cart from wishlist | P (cart RPCs) | G | G | G | — |
| Back-in-stock flag + notify | G | G | G | G | — |
| Compare (local guest) | — | L | G | G | — |
| Compare (server auth sync) | G | G | G | G | — |
| Compare attribute matrix | — | P | G | G | — |
| Reviews submit/list | L | L | G | G | — |
| Reviews staff moderate UI | L (RPC) | G | — | — | — |
| Rating aggregates on PDP | G | G | G | G | — |
| Review photo attachments | G | G | G | G | — |
| Blanket staff / remaining | L | L | — | — | G |
| Blanket supplier portal | P (RLS) | G | — | — | — |
| Blanket expiry alerts | P (`expected_date`) | G | — | — | G |
| Warehouse bins UI | L | L | — | — | G |
| Consignment UI | L | L | — | — | G |
| Bin label ESC/POS | P (bridge) | — | — | — | G |
| Pick-path guidance | L (`get_pick_path_hints`) | P | — | — | G |
| Diagrams beyond Navara | P | P | — | — | — |
| B2B credit view | L | L | — | — | G |
| B2B credit set/hold | P (RLS) | G | — | — | G |
| POS named-customer search | L (`p_customer_id`) | G | — | — | P |
| Staff nav CRM entry points | — | P | — | — | — |

## Acceptance criteria

1. [ ] **Wishlist mobile** — iOS + Android customer: list + add/remove via existing wishlist RPCs; Fake RPC parity for offline demos.
2. [ ] **Move-to-cart** — Web + mobile: from wishlist call `create_customer_cart` / `add_customer_cart_line` (or one thin DEFINER helper); remove or keep wishlist item per UX note in handoff.
3. [ ] **Back-in-stock** — Preference column/flag on wishlist (or linked prefs) + enqueue `sms_outbox` / domain event when stock returns (**fail-closed**, no SMS without keys); staff/system hook if cheap.
4. [ ] **Compare** — `customer_compare_items` (or equivalent) + RLS for auth users; guests stay localStorage; sync on login; richer side-by-side attribute matrix on web (mobile feasible subset); **update** smoke that previously forbade compare table.
5. [ ] **Reviews moderation** — Web `/staff/...` UI binds `moderate_customer_product_review`; nav entry from staff CRM polish.
6. [ ] **Review aggregates + photos** — PDP avg/count (RPC or view); photo paths via Storage bucket + RLS; web file upload OK; mobile camera via **Bridge-First** (`bridges/` / POD-camera pattern reuse — not HTML5 QR).
7. [ ] **Reviews mobile** — iOS + Android submit/list (approved + own pending).
8. [ ] **Optional review-approved notify** — `emit_domain_event` + catalog code; drain fail-closed without keys.
9. [ ] **Blankets** — Supplier portal lists own blankets (RLS already); expiry/remaining alerts in staff + supplier UI; Android management thin procurement/blanket RPC bind.
10. [ ] **Bins / consignment management** — Android management screens on Phase 16 RPCs; bin label print via ESC/POS where bridge already wired; staff web + management call `get_pick_path_hints` for preferred-bin guidance.
11. [ ] **Diagrams** — Expand seed beyond Navara where fixtures exist; migration + `seed_catalog_diagrams.mjs` idempotent; PDP canvas works; ops step documented (LOCAL_DEVELOPMENT or seed README cite).
12. [ ] **B2B credit staff** — Staff finance/sales UI: set `credit_limit`, set/clear `credit_hold`, view `open_balance` (explicit currency); management surface if role-appropriate; SECURITY DEFINER mutator preferred over raw table UPDATE.
13. [ ] **Staff polish** — POS named-customer search (reuse finance `searchCustomers` pattern); clearer CRM-ish labels + nav links to credit + review moderation.
14. [ ] **Gates** — `/security-reviewer` (+ `/supabase-rls-auditor` on new tables); `/verifier`; master Immediate handoff updated; **no secrets / no ZIMRA / no payroll tax / Bridge-First**.

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

## Handoff

1. `/manager` opens this epic  
2. First coding prompt → **`@backend_agent`** (bullets below)  
3. Then web → ios → android-customer → android-management  
4. `/security-reviewer` → `/verifier` → `/manager` done gate  

### First coding lane (`@backend_agent`) — prompt bullets

- Add `customer_compare_items` (+ RLS own-row; staff optional SELECT); RPCs add/remove/list; migrate smoke away from “no compare table”.
- Wishlist: `notify_when_in_stock` (or equiv) + stock-return hook enqueue via `emit_domain_event` / `sms_outbox` (new catalog codes); fail-closed.
- Optional `wishlist_move_to_cart` thin DEFINER **or** document compose of existing cart RPCs (prefer compose if no race).
- Reviews: aggregate view/RPC (avg + count approved); photo table or path columns + Storage bucket RLS; on `moderate_*` approved → optional notify event.
- Staff `set_customer_credit` (limit, hold) SECURITY DEFINER; explicit currency on responses.
- Diagram seed: extend beyond Navara when fixtures present; keep `seed_catalog_diagrams.mjs` idempotent; ops one-liner.
- No ZIMRA / payroll tax / secrets; RLS in same migration files; types regen note for web/mobile.
