# Tablet / web POS redesign notes (Dial UX)

**Donors (pattern only):** CoolMallKotlin (Android Compose density), Nimara / Medusa DTC checkout chrome, `@gtr/ui` brand tokens.  
**Not Expo.** Tablet kiosk remains `apps/android-management` LockTask.

## Done this landing
- Web POS shell chrome (`staff-pos-shell.module.css`) — desktop + mobile breakpoints
- Shared surface language with procurement tracker (warm stone + accent)

## Next (Android tablet)
- Apply `packages/android-ui` Shop/Gtr tokens to `feature/pos/PosScreen.kt`
- Larger touch targets (≥48dp), dual-pane cart on landscape tablet
- Keep offline SqlCipher path; Bridge-First QR only

## QA before Done
- [ ] Desktop 1280px + mobile 390px web POS usable
- [ ] Tablet landscape cart + catalog without horizontal scroll traps
- [ ] WH2 stock source for POS picks (WH1 is receiving only)
