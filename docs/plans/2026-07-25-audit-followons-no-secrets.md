# Audit follow-ons (no secrets)

- Status: draft
- Lane(s): `@backend_agent` → `@web_agent` → `@ios_agent` (optional cheap `@android_agent`)
- Skills needed: `/token-discipline`; `/parts-catalog-ingestion` only for diagram fixture bind
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) — Immediate handoff + “Still follow-on”
- Related: [`phase8b-rfq-blanket`](./2026-07-24-phase8b-rfq-blanket.md), [`phase16-distributor-extras`](./2026-07-24-phase16-distributor-extras.md), [`docs/parity/polish-backlog.md`](../parity/polish-backlog.md), decisions [`autodoc-shop-features`](../decisions/2026-07-23-autodoc-shop-features.md), [`in-app-live-chat`](../decisions/2026-07-25-in-app-live-chat.md)

## Goal

Ship every incomplete ERP completeness-audit item that does **not** need API secrets/keys — Live (not stub) on web at minimum; secrets stay fail-closed.

## Acceptance criteria

1. [ ] **Wishlist** — table + RLS (missing today); `/account/wishlist` + PDP add/remove via Supabase (replace stub `apps/web/app/(account)/account/wishlist/page.tsx`)
2. [ ] **Compare** — selected SKUs from catalog (session and/or DB), not `DEMO_PRODUCTS` in `apps/web/lib/shop-demo.ts` / `account/compare/page.tsx`
3. [ ] **Reviews** — schema + RLS; `/account/reviews` + PDP list/submit when auth (stubs today; PDP points to account only)
4. [ ] **Blanket PO web UI** — staff list remaining qty/value + call-off via `create_blanket_purchase_order` / `create_blanket_release` (`…060000_rfq_blanket.sql` / guards `…061000`); beside existing `/procurement` RFQ portal
5. [ ] **Consignment + bins UI** — staff surfaces on Phase 16 RPCs: bins `create_warehouse_bin` / `update_warehouse_bin` / `deactivate_warehouse_bin` / `set_stock_level_bin` (`…120000_warehouse_bins.sql`); consignment draft→line→submit/cancel (`…122000_consignment_stock.sql`)
6. [ ] **Catalog diagrams** — seed Navara fixture paths + sample assets into Storage `catalog-diagrams` + `part_fitment.diagram_path` from `data-pipeline/fixtures/navara_d40_yd25/` so PDP canvas (`catalog-diagram.ts` / `catalog-canvas-stub.tsx`) is Live without scrape
7. [ ] **B2B credit UX** — show `customers.credit_limit` / `credit_hold` / `open_balance` (own-row SELECT `…230000_sales_pos.sql`) on `/b2b` + checkout guard messaging when hold / over-limit (checkout already posts `on_hold`)
8. [ ] **iOS chat** — improve poll reliability **or** lightweight Realtime without huge deps (`ChatScreen.swift` / `LiveStorefrontApi.swift` — no supabase-swift today; web Realtime in `apps/web/lib/chat.ts` + `…100000_live_chat.sql`)
9. [ ] **Staff finance/POS polish** — usable labels where names exist; clear empty states in `staff-finance-panel.tsx` / `staff-pos-panel.tsx` (targeted, no redesign)
10. [ ] **Garage service reminders** — **SKIP**: only `customer_garage_vehicles` (`…130000_customer_storefront_authz.sql`); no reminder schema — leave copy note on `/account/garage`
11. [ ] **Master plan** — update Immediate handoff + audit follow-ons for what shipped; secrets/ops items stay listed as ops-only

## Paths in scope

| Area | Cite |
|------|------|
| Account stubs | `apps/web/app/(account)/account/{wishlist,compare,reviews,garage}/page.tsx`, `part-detail.tsx` |
| Backend new | `supabase/migrations/` wishlist + reviews (+ optional compare table); RLS same file |
| 8b / 16 | `…060000`/`…061000` blanket RPCs; `…120000` bins; `…122000` consignment; smokes `phase8b_*`, `phase16_*` |
| Diagrams | `data-pipeline/fixtures/navara_d40_yd25/*`, `…010000_catalog_search_fts.sql` bucket, `apps/web/lib/catalog-diagram.ts` |
| B2B | `apps/web/app/(b2b)/b2b/page.tsx`, `b2b-price-panel.tsx`, checkout paths using credit hold |
| iOS chat | `apps/ios/.../ChatScreen.swift`, `LiveStorefrontApi.swift` |
| Staff polish | `staff-finance-panel.tsx`, `staff-pos-panel.tsx`, warehouse panels as needed for labels |
| Docs | master Immediate handoff; this plan status → Done |

## Out of scope

ContiPay / Paynow / SMS / Resend / WhatsApp Meta tokens / `GEMINI_API_KEY` / `WORKER_SHARED_SECRET` / map tile merchant URL; ContiPay webhook header confirm; production Edge secret deploy; host JDK/Xcode assemble; ZIMRA; payroll tax; HTML5 QR; full redesign; loyalty redesign; PowerSync; management-app native consignment (web staff is enough); inventing parallel systems beside existing RPCs.

## Risks / exclusions

- New tables: RLS in same migration; no secrets in git.
- Multi-currency explicit on any money shown (reviews/wishlist are non-money; B2B credit shows currency).
- Bridge-First unchanged.
- Prefer session compare for guests; persist only if cheap + RLS-safe.
- Diagram seed: fixture PNGs may be path-only — generate minimal placeholder assets if binaries missing; do not commit API keys.

## Ordered implementation (manager pipeline)

1. **`@backend_agent`** — wishlist + reviews (+ compare if DB) migrations/RLS; diagram seed/fixture migration or idempotent seed script applying Navara `diagram_path` + Storage objects; confirm garage reminders **absent** → skip note.
2. **`@web_agent`** — bind wishlist/compare/reviews; blanket UI; consignment+bins staff UI; B2B credit + checkout messaging; diagram bind verification on Navara PDP; staff finance/POS label/empty-state polish.
3. **`@ios_agent`** — chat poll harden (backoff, foreground resume, error surface) **or** minimal Realtime via existing HTTP/WebSocket pattern without large new deps.
4. **Optional `@android_agent`** — wishlist/compare only if trivial parity with web RPCs.
5. **`/security-reviewer`** — new RLS (wishlist/reviews/compare/seed policies).
6. **`/verifier`** — exclusions (no ZIMRA/tax/HTML5 QR/secrets).
7. **Docs** — master Immediate handoff: mark items 1–9 Live; item 10 skipped; keep secrets ops in “Still follow-on”.

## Done when

Every in-scope item Live on web (item 10 skipped with note); iOS chat improved; secrets integrations remain fail-closed; master handoff updated.

## Handoff

1. `/manager` sequences lanes above  
2. Implement per lane — no cross-lane dumps  
3. `/security-reviewer` → `/verifier` → `/manager` done gate  
