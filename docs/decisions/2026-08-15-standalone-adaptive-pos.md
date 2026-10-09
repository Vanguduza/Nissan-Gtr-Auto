# Standalone adaptive POS app (greenfield till UI)

- Date: 2026-08-15
- Lane: `@pos_app_agent` (`apps/android-pos/**`) → `@backend_agent` (snapshot additive only) → `@hardware_mobile_agent` (scan / ESC/POS / drawer)
- Status: **accepted** (epic + Post-EPIC GREEN 2026-08-15 — former P4 Should + Before Live Done; see plan §13)
- Plan: [`docs/plans/2026-08-15-standalone-adaptive-pos.md`](../plans/2026-08-15-standalone-adaptive-pos.md)
- Related: [dedicated-delivery-app](./2026-07-25-dedicated-delivery-app.md) (product-split pattern); [offline-sqlcipher-pos-cache](./2026-08-03-offline-sqlcipher-pos-cache.md) (cache + outbox, not a ledger); [customer-epc-shop-stock-context](./2026-08-13-customer-epc-shop-stock-context.md) (OEM identity); [pos-scan-session-pairing](./2026-07-25-pos-scan-session-pairing.md) (standalone sale required); [pos-receipt-contact-customer-bind](./2026-07-25-pos-receipt-contact-customer-bind.md)

## Decision

1. **New product** at `apps/android-pos/` — native Jetpack Compose, one APK, layout by **window size class** (phone + tablet). Optional Lock Task / Device Owner on counter tablets. Not a flavor of `apps/android-management`.
2. **Greenfield till UI.** Do not port, restyle, or extract current management/web POS screens. Keep **backend SoR only**: `pos_carts`, checkout/tender/park/quote/refund RPCs, RLS, catalog/fitment search, offline pull/replay.
3. **Cafe till chrome, parts-counter finder.** Three-pane shell (catalog | ticket | utility rail) on expanded width. Left pane is a **vehicle-latched 4-way spare finder** (scan/OEM, shop stock, EPC/PNC, VIN/model) — not a restaurant category mosaic.
4. **Client engines, server validators.** Split-tender allocation and cash change live in the app (`TenderAllocator`). `settle_invoice_tenders` still requires applied amounts to equal open balance. Offline catalog/stock + `PosSyncManager` speak `pull_pos_offline_snapshot` / `replay_offline_pos_sale` only.
5. **Till tile contract.** Shop stock, OEM hydrate, and EPC leaves go through `list_pos_till_items` (and the same JSON on `pull_pos_offline_snapshot`). Search `vehicle` hits **latch**; `pnc` hits **drill**; only `part` hits become tiles. Qty never from Meili.
6. **Quote-intent on the cart.** Priced OOS lines may `add_cart_line` (qty is checked at checkout) and flip the CTA to **QUOTE** → `create_pos_quotation_from_cart` then park + new cart. Unpriced never adds. Mixed tickets cannot Pay.
7. **Fitment predicate** is client SoT on `chassis_codes` / `engine_codes` (empty → Verify; chassis mismatch → No fit). Year not in v1.

## Why

- Delivery already split for UX/release isolation; a fat management APK fights a dedicated counter till.
- Existing till chrome optimized dual-pane density, not Nissan FAST fitment or shop-stock honesty.
- Restaurant POS donors supply **touch layout**; GTR differentiator is **correct OEM for latched chassis + WH2 qty**.

## Alternatives rejected

| Alternative | Why rejected |
|-------------|--------------|
| Restyle POS inside `android-management` | Couples warehouse/HR/finance release to till; leftover hub chrome |
| Flutter / Expo rewrite | Conflicts with `bridges/`, SQLCipher path, native-Android standing rule |
| Fork SaleFlex / Odoo / TailPOS as SoR | AGPL/GPL / second ledger; standing OSS filter |
| Cafe tiles as primary lookup | Wrong-part risk; ignores `search_catalog` + `part_fitment` |
| Offline EcoCash/Paynow/ContiPay | Live intents; replay is cash-only by ADR |
| Caching manager approval tokens | Privilege escalation; offline login (#58) stays Later |
| N+1 `lookupSaleableQtyByOem` / Meili qty on tiles | Slow + dishonest stock; use `list_pos_till_items` |
| Add vehicle/PNC search hits to the cart | Wrong document; latch or drill only |
| Unpriced `add_cart_line` / Pay with OOS lines | $0 invoices or checkout stock fail; Needs price / QUOTE CTA |

## Consequences

- Agents **must** implement the till in `apps/android-pos/` against existing RPCs; **must not** copy `feature/pos` Compose or web `staff-pos-shell`.
- Management keeps warehouse/HR/finance/dispatch; POS entry is **Open POS** deep-link to `co.zw.nissangtr.pos` (dual-pane till retired as primary). Web `/staff/pos` remains fallback, not kiosk SoR.
- Additive `list_pos_till_items` + snapshot `TillItem` shape are required; a second cart/ledger is not.
- Fitment chips use the locked chassis/engine predicate; agents must not invent year/VIN-digit matching in v1.
- Qty on the till is Postgres saleable WH2 (or the encrypted snapshot of it). Meili/FTS never invent stock.
- Hard exclusions unchanged: no ZIMRA, no payroll tax, no HTML5 camera, Bridge-First hardware.
- **Post-EPIC Done (was P4 Should):** Lock Task/Device Owner on POS APK; till float open/close via account periods (1120; sales/warehouse scoped); refund→quarantine sheet; chassis shortcut chips; real staff auth + bridges + LivePosClient; management session handoff — see plan §13.
