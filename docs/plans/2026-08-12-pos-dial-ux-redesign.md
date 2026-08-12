# Tablet / web POS redesign notes (Dial UX)

**Donors (pattern only):** CoolMallKotlin (Android Compose density), Nimara / Medusa DTC checkout chrome, `@gtr/ui` brand tokens.  
**Not Expo.** Tablet kiosk remains `apps/android-management` LockTask.

## Done this landing
- Web POS shell chrome (`staff-pos-shell.module.css`) — desktop + mobile breakpoints
- Shared surface language with staff chrome via `@gtr/ui` CSS vars (`--gtr-chalk` / `--gtr-mist` / `--gtr-steel` / `--gtr-red` / `--gtr-radius-staff`)
- POS saleable warehouse list restricted to WH2 storefloor (`listSaleableWarehouses` + panel copy)

## Next (Android tablet) — `@management_app_agent` handoff
- Apply `packages/android-ui` Shop/Gtr tokens to `feature/pos/PosScreen.kt`
- Larger touch targets (≥48dp), dual-pane cart on landscape tablet
- Keep offline SqlCipher path; Bridge-First QR only
- Close redesign QA box: tablet landscape cart + catalog without horizontal scroll traps

## QA before Done
- [x] Desktop 1280px + mobile 390px web POS usable
  - **Evidence (static recon, 2026-08-12):**
    - Shell: `apps/web/components/staff-pos-shell.module.css` — `@media (min-width: 960px)` roomier `.posBody` padding (covers 1280); `@media (max-width: 720px)` stacks `.posChrome` + tightens padding (covers 390).
    - Staff shell / form density: `apps/web/components/account.module.css` — `.shell` → 1-col at `max-width: 800px`; `.formGrid` → 1-col at `max-width: 640px`; inputs `min-height: 2.75rem`, `width: 100%` (no fixed-width overflow traps).
    - Panel uses stacked fieldsets + `formGrid` (usable scroll at 390; two-column fields at 1280). True catalog|cart dual-pane remains Android tablet (handoff below).
- [ ] Tablet landscape cart + catalog without horizontal scroll traps
  - **Handoff:** `@management_app_agent` — `apps/android-management` PosScreen dual-pane ≥700dp + ≥48dp touch targets; leave web alone.
- [x] WH2 stock source for POS picks (WH1 is receiving only)
  - **Evidence:** `apps/web/lib/staff-pos.ts` — `isPosSaleableWarehouse` + `listSaleableWarehouses` filter `.or("role_code.eq.WH2,code.eq.WH2")` with quarantine/inactive excluded; UI empty-state + “Warehouse (WH2 storefloor)” label in `staff-pos-panel.tsx`.
