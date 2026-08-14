# Tablet / web POS redesign notes (Dial UX)

**Donors (pattern only):** CoolMallKotlin (Android Compose density), Nimara / Medusa DTC checkout chrome, `@gtr/ui` brand tokens.  
**Not Expo.** Tablet kiosk remains `apps/android-management` LockTask.

## Done this landing
- Web POS shell chrome (`staff-pos-shell.module.css`) — desktop + mobile breakpoints
- Shared surface language with staff chrome via `@gtr/ui` CSS vars (`--gtr-chalk` / `--gtr-mist` / `--gtr-steel` / `--gtr-red` / `--gtr-radius-staff`)
- POS saleable warehouse list restricted to WH2 storefloor (`listSaleableWarehouses` + panel copy)
- Android tablet PosScreen Dial pass — GtrTheme + Shop density, dual-pane ≥700dp, ≥48dp targets, FlowRow chip wrap (no horizontal scroll traps)

## Next (Android tablet) — closed
- ~~Apply `packages/android-ui` Shop/Gtr tokens to `feature/pos/PosScreen.kt`~~ **Done**
- ~~Larger touch targets (≥48dp), dual-pane cart on landscape tablet~~ **Done**
- ~~Keep offline SqlCipher path; Bridge-First QR only~~ **Verified**
- ~~Close redesign QA box: tablet landscape cart + catalog without horizontal scroll traps~~ **Done**

## QA before Done
- [x] Desktop 1280px + mobile 390px web POS usable
  - **Evidence (static recon, 2026-08-12):**
    - Shell: `apps/web/components/staff-pos-shell.module.css` — `@media (min-width: 960px)` roomier `.posBody` padding (covers 1280); `@media (max-width: 720px)` stacks `.posChrome` + tightens padding (covers 390).
    - Staff shell / form density: `apps/web/components/account.module.css` — `.shell` → 1-col at `max-width: 800px`; `.formGrid` → 1-col at `max-width: 640px`; inputs `min-height: 2.75rem`, `width: 100%` (no fixed-width overflow traps).
    - Panel uses stacked fieldsets + `formGrid` (usable scroll at 390; two-column fields at 1280). True catalog|cart dual-pane remains Android tablet (below).
- [x] Tablet landscape cart + catalog without horizontal scroll traps
  - **Evidence (static recon, 2026-08-12, `@management_app_agent`):**
    - Breakpoint: `PosScreen.kt` `TWO_PANE_MIN_WIDTH = 700.dp` — `BoxWithConstraints` → `Row` catalog|cart weights `0.6|0.4` with `fillMaxHeight`; below 700dp stacks `Column`.
    - No `horizontalScroll` anywhere in POS feature; chip / tender / warehouse / quote / mode groups use `PosChipFlow` (`FlowRow`) so narrow 40% cart (~280dp at 700dp) and 60% catalog wrap instead of clipping or H-scroll.
    - Cart line Price/−/+/qty moved **under** `ShopListCard` (not trailing Row) to kill the main landscape overflow trap in the right pane.
    - Touch: `POS_TOUCH_MIN = 48.dp` on chips, outlined/primary actions, customer/printer rows, qty ± (`widthIn`+`heightIn`); former 40dp qty buttons removed.
    - Theme: `GtrTheme(density = GtrDensity.Standard)` + Shop staff panels/buttons/cards (`packages/android-ui`).
    - Offline: `SqlCipherOfflinePosStore.open` with `InMemoryOfflinePosStore` fallback; sync via `OfflinePosSyncEngine` / WorkManager — no Expo / RN.
    - Hardware: `QrScannerBridge` + `EscPosPrinterBridge` only (Bridge-First); subtitle still “No ZIMRA”.
- [x] WH2 stock source for POS picks (WH1 is receiving only)
  - **Evidence:** `apps/web/lib/staff-pos.ts` — `isPosSaleableWarehouse` + `listSaleableWarehouses` filter `.or("role_code.eq.WH2,code.eq.WH2")` with quarantine/inactive excluded; UI empty-state + “Warehouse (WH2 storefloor)” label in `staff-pos-panel.tsx`.
