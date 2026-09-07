# POS Operator Screen — Canonical Design Lock

**Status:** LOCKED / authoritative for the Nissan GTR Auto Android tablet POS kiosk
**Date:** 2026-09-07
**Applies to:** `apps/android-management` tablet POS surface

## Decision

The operator-facing POS preview approved on 2026-09-07 is the canonical visual and interaction reference for the tablet sales screen. The implementation must preserve its core composition, density, hierarchy, visual language and operator-first workflow unless a later owner-approved decision explicitly supersedes this lock.

Two owner corrections are part of this lock:

1. **The left navigation item `Reports` is replaced by `EPC Browse`.** Reports are not part of the canonical salesperson POS rail.
2. **The top preview information band is replaced by a Nissan-only vehicle cascade: `Model → Generation → Engine`.** Make is intentionally omitted because Nissan GTR Auto is a Nissan-only shop. Generation is catalog-backed by chassis/year variant grouping; engine is derived from that generation.

## Canonical composition

The wide-tablet screen is a persistent three-zone workspace:

1. **Dark Nissan GTR Auto navigation rail** — Home, Search Spares, Quick Sale, Customer, Orders, Returns, EPC Browse, Settings.
2. **Discovery and operations canvas** — global parts search, operator/status identity, the locked `Model → Generation → Engine` vehicle selector, promotional/brand hero, spare categories, search results/recent searches, and destination-specific operations.
3. **Persistent Current Sale pane** — cart lines, quantity controls, manager-gated price override, customer context, totals, tender and checkout.

The Current Sale pane must remain visible while the operator searches or browses EPC on landscape tablet widths. Compact layouts may stack content, but they must preserve the same capabilities.

## Visual system

- Use the same shared warm commerce theme as the Android customer app via `ShopWarmTheme`.
- Nissan GTR Auto brand red remains the primary action/selection colour.
- The left rail remains dark steel/black with a red selected state.
- Surfaces are warm off-white/white with soft rounded cards and clear spacing.
- Search is the strongest control in the discovery canvas.
- The screen should feel retail/customer-facing in finish while remaining operationally dense and fast for counter staff.

## Functional binding contract

No control in the canonical screen may be decorative-only when presented as actionable. Bindings are:

| Screen feature | Required Nissan GTR Auto function |
|---|---|
| Vehicle cascade | Load every Nissan model from the EPC catalog, then valid generation/chassis options and engines. A complete selection is persisted on the sale cart and drives server-side fitment filtering. |
| Global search | Without a selected vehicle: live `searchCatalog` by Part/OEM, VIN, Model or PNC. With a selected vehicle: `search_pos_vehicle_spares` restricts results to that chassis/engine fitment. Offline flat-catalog fallback is allowed only when no fitment claim is being made. |
| Scan icon | CameraX/Bridge-first inventory QR scan and add-to-sale |
| Spare category tiles | Seed real catalog searches; never hard-coded fake product results |
| Search result cards | OEM/PNC/category/fitment + live saleable stock; Add to Sale uses the canonical POS cart RPC path |
| Quick Sale | Warehouse/currency/fulfilment/customer context, cart actions and checkout |
| Customer | Search/select real customer and bind to current sale |
| Orders | Existing POS quotations plus parked-sale resume workflow |
| Returns | Manager-approved refund through the finance refund pipeline for an eligible completed sale |
| EPC Browse | Existing online EPC maker → model → variant → section → diagram/parts hierarchy; selected OEM adds through the canonical cart path |
| Settings | Sole tablet maintenance doorway: offline snapshot/sync, printer transport, scan companion, and authorized kiosk/device administration. Kiosk tools must not be duplicated in the general hub. |
| Current Sale | Real cart lines, selected vehicle context, quantity mutation/removal, manager price override, totals, receipt contacts, split tender and checkout |
| Final invoice / receipt | Snapshot Model, Generation, Chassis and Engine from the cart onto `sales_invoices`; show vehicle on printed receipt, PDF receipt and POS order/return history. Offline replay preserves the same snapshot. |

## Business-logic rule

The UI must not invent a second cart, price, stock, customer, quotation, refund, EPC or payment source of truth. Existing `PosViewModel` / management `RpcClient` operations remain authoritative. When a capability is genuinely missing, implement it in the correct domain/RPC layer and then bind the UI; do not simulate success in Compose state.

## Security and operational rules

- Manager-gated discount, void, refund and price override remain manager-gated.
- Offline restrictions remain enforced; visual redesign must not weaken them.
- QR scanning and receipt printing remain Android bridge-first, not WebView/browser substitutes.
- Receipt printing supports ESC/POS over Bluetooth SPP and Wi-Fi/LAN raw TCP (normally port 9100). Vendor-only printer protocols require their Android driver/print service rather than false compatibility claims.
- Tablet cold start has **no app splash**: the Android starting window matches the login surface, Device Owner reasserts persistent HOME + Lock Task, and a fresh staff-auth boundary is reached before POS content can render.
- Home, Recents, notification/status-bar escape paths are suppressed in dedicated-kiosk operation. Salesperson routing continues directly to POS after authentication.
- Authorized kiosk maintenance is reachable only through **POS → Settings → Kiosk & device**; temporary system Wi-Fi/Bluetooth maintenance is audited and the POS reasserts kiosk policy on return.

## Implementation authority

Primary implementation files:

- `apps/android-management/feature/pos/src/main/java/co/zw/nissangtr/management/pos/PosOperatorWorkspace.kt`
- `apps/android-management/feature/pos/src/main/java/co/zw/nissangtr/management/pos/PosScreen.kt`
- `apps/android-management/feature/pos/src/main/java/co/zw/nissangtr/management/pos/PosViewModel.kt`
- `apps/android-management/feature/pos/src/main/java/co/zw/nissangtr/management/pos/PosEpcBrowseScreen.kt`
- `apps/android-management/feature/kiosk/src/main/java/co/zw/nissangtr/management/kiosk/LockTaskController.kt`
- `bridges/android/escpos-printer/src/main/java/co/zw/nissangtr/bridges/escpos/PrinterModels.kt`
- `packages/android-ui/src/main/java/co/zw/nissangtr/ui/shop/ShopWarmTheme.kt`
- `supabase/migrations/20260907090000_pos_vehicle_context.sql`

The approved preview remains the visual reference; this document records its software-contract interpretation so future refactors cannot silently thin or replace the screen.
