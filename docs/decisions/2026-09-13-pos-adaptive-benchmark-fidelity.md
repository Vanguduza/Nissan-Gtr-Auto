# POS adaptive benchmark fidelity, Quick Access panel, header vehicle cascade

- Date: 2026-09-13
- Lane: `@management_app_agent` (UI) · `@backend_agent` (operator pins, reservation contract)
- Status: **accepted**
- Supersedes: POS Master Blueprint Rev 1.3 (never committed to this repo)
- Blueprint: [`docs/design/pos/POS_FRONTEND_BLUEPRINT_REV_1_4.md`](../design/pos/POS_FRONTEND_BLUEPRINT_REV_1_4.md)
- Related: [tablet kiosk POS action plan](../plans/2026-08-03-tablet-kiosk-pos-full-action-plan.md) (L1–L7 stand) · [offline SQLCipher ADR](./2026-08-03-offline-sqlcipher-pos-cache.md) · [vehicle cascade guide](../guides/vehicle-cascade-and-epc-browse.md)

## Decision

Rebuild the POS **user interface** in `apps/android-management/feature/pos` against the
owner-approved benchmark, certifying **proportion and grammar** rather than pixel equality, and
extend it to full operational parity on the phone APK. The Batch 1 cart/checkout system of record,
the offline outbox and all authorisation RPCs are reused unchanged.

Six substantive decisions:

1. **Adaptive fidelity replaces pixel fidelity.** The benchmark is measured, committed and reduced
   to normative *ratios* plus a canonical **1280 × 800 dp** frame on which the whole composition
   lands on an 8 dp grid (rail 144, cart 352, header 96, hero 240, nav pill 48). Certification
   asserts zone ratios ±1.5%, band ratios ±3%, card aspect ±5% across five reference sizes. The
   Rev 1.3 "±2 Rpx" gate is withdrawn — it was both unsatisfiable and incompatible with an app that
   must run on more than one screen.

2. **Benchmark authority is scoped.** It governs composition, hierarchy, proportion and treatment.
   It does **not** govern copy, currency, tax or sample data. The mock shows `KSh` and `VAT (16%)`
   (Kenyan) and contains the typo `GNGUINE PARTS`; unscoped benchmark authority would have shipped
   all three.

3. **Popular Spares becomes the Quick Access panel.** Operator-scoped, server-persisted,
   horizontally scrollable, unbounded, and heterogeneous — a pinned item may be a spare, a category
   or a vehicle. Pin/unpin is a long-press contextual action available anywhere those entities
   render. Server popularity ranking leaves this row.

4. **The header taxonomy line becomes the vehicle cascade.** `Spares · Service · Performance` is
   replaced by Model → Generation → Engine fields that set a session-scoped, visible, clearable
   fitment context, reusing `vehicle_master` and `search_catalog`. The hero still carries no
   cascade controls.

5. **The phone POS reaches full operational parity.** Not a shrunken tablet — a designed compact
   product sharing tokens, grammar, state machines and RPCs, with bottom navigation, a persistent
   cart bar expanding to a sheet, and checkout as navigation rather than a layer. L1 stands: no
   Device Owner, no Lock Task, no Magisk on the phone APK.

6. **Error red is separated from brand red.** `GtrColors.Danger` and `GtrColors.Primary` are the
   same byte value `#C8102E`, and `GtrTheme`'s light scheme wires `error = Danger` — so a blocking
   error currently renders in the same colour as the primary call to action. Error moves to a
   darker `#8E0F22`, always icon-paired. This is the only new brand primitive; it goes into
   `packages/ui/brand-tokens.json` so web and iOS inherit the same separation.

   Benchmark sampling otherwise **confirms** the existing palette rather than changing it: canvas
   measures `#F3F4F8` against `GtrColors.Chalk` `#F4F5F7` already in use, and the rail measures
   cool (`#1B2024`), resolving Rev 1.3's open "unless sampling proves a blue bias" conditional. Its
   proposed `CanvasWarm` token was simply the wrong name for a colour the app already had right.

## Why

- The blueprint as received declared itself "canonical certification authority" while the repository
  already has `PROJECT_TRUTH_PROTOCOL.md`, `AGENTS.md` exclusions and locked action-plan decisions
  that outrank any design document. Rev 1.4 subordinates itself to them explicitly.
- Its fidelity model could not survive contact with a second screen size, and its own illustrative
  coordinates were 6–10 Rpx off the measured values against a ±2 Rpx tolerance.
- It specified token *names* with no values while forbidding arbitrary values in feature code, so
  independent implementers would still have diverged on every colour, radius, type size and
  breakpoint.
- It was written as though governing an existing implementation. The shipped `PosScreen.kt` shares
  no structure with the benchmark, so a migration contract was required and absent.

## Consequences

- Agents **may** rebuild POS composables, introduce the `PosPalette` semantic layer, vendor the
  Lucide icon set, remove `material-icons-extended` from `feature/pos`, and build the compact phone
  POS.
- Agents **must not** rewrite the cart/checkout SoR, weaken an authorisation gate, queue non-cash
  tenders offline, delete a capability that has no disposition row in blueprint §11, or define a
  brand primitive anywhere but `packages/ui/brand-tokens.json`.
- **New backend work is authorised and required:** operator pin storage + RPCs (blueprint §6.4) and
  a POS stock-reservation contract (§9.2). Neither exists; reserve-first UI states stay marked
  blocked until the second lands.
- The screenshot/lint/benchmark harness moves to Phase 1. It does not exist today and gates
  everything after it.
- The committed reference is a lossy JPEG. Colour tokens are provisional until resampled from a
  lossless export; geometry ratios are unaffected.

## Open

1. Lossless benchmark export for colour resampling.
2. Supported baseline device for performance targets — blocked on the action plan's open Path B SKU
   list.
3. Typeface confirmation (Inter assumed) against the lossless reference.
4. Whether `View All` survives as Quick Access management or is dropped (delta D-004).
