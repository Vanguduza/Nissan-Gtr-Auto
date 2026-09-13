# POS adaptive benchmark fidelity, Quick Access panel, header vehicle cascade

- Date: 2026-09-13
- Lane: `@management_app_agent` (UI) · `@backend_agent` (operator pins, reservation contract)
- Status: **accepted**
- Supersedes: POS Master Blueprint Rev 1.3 (never committed to this repo); blueprint Rev 1.4 (same-day revision)
- Blueprint: [`docs/design/pos/POS_FRONTEND_BLUEPRINT_REV_1_5.md`](../design/pos/POS_FRONTEND_BLUEPRINT_REV_1_5.md)
- Related: [tablet kiosk POS action plan](../plans/2026-08-03-tablet-kiosk-pos-full-action-plan.md) (L1–L7 stand) · [offline SQLCipher ADR](./2026-08-03-offline-sqlcipher-pos-cache.md) · [vehicle cascade guide](../guides/vehicle-cascade-and-epc-browse.md)

## Decision

Rebuild the POS **clean** in `apps/android-management`, certifying **proportion and grammar** rather
than pixel equality, and extend it to full operational parity on the phone APK. The implementation
currently in `feature/pos` is a wrong implementation of the product and carries no authority over
the design: its screen composition, component set, theming path, gateway shape and ViewModel
structure are all replaced. What survives is **capability**, not code — every behaviour the counter
performs today is enumerated in blueprint §11 and must exist when the rebuild ships.

Owner product decisions (action plan L1–L7), `AGENTS.md` hard exclusions and the truth protocol
continue to bind; none of them is an implementation detail.

Ten substantive decisions:

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

7. **The data and state architecture is replaced, not deferred.** The 102-method `RpcClient` becomes
   eight narrow, feature-owned gateways returning a typed `PosResult`, with idempotency keys on every
   money- or stock-moving call. State becomes a single store over **pure reducers** with screen-scoped
   projections, in a `pos-domain` module carrying no Android dependency. The feature splits into
   `pos-design` / `pos-domain` / `pos-data` / `pos-ui`, and Hilt is adopted (there is no DI today).
   This is what makes JVM screenshot tests, reducer tests and recomposition isolation possible;
   without it the certification regime in blueprint §10 cannot be met.

   Business outcomes stop being exceptions. The current code throws `IllegalStateException` for
   ordinary rules such as "offline checkout is cash-only", which is control flow through exceptions
   carrying engineering prose one `catch` from an operator's screen.

8. **Tokens are generated; the POS gets its own theme.** Style Dictionary emits Kotlin, CSS/TS and
   Swift from `brand-tokens.json`, so hand-written `GtrColors.kt` becomes build output. The POS
   themes through a dedicated `PosTheme` over `CompositionLocal`s rather than by overriding
   Material3's `ColorScheme` — M3's semantic slots cannot address a nav rail, a canvas, a cart pane,
   a hero backdrop and six status families independently, and approximating them is visible in the
   current app. Material3 supplies interaction primitives only. Product UI moves to **Inter** for its
   variable weight axis, true tabular figures and small-size legibility; a display face for brand
   moments is a separate owner choice.

9. **Reserve-first is specified, not blocked.** Blueprint §9.6 carries the RPC contract, TTL,
   idempotency and expiry behaviour so `@backend_agent` can build it, rather than leaving the
   product's most important correctness property as a deferred note.

10. **The modern-UI design language is a specified layer, not a mood.** Blueprint §5 carries the
   governing principles, a pattern permission matrix (where bento, glass, soft depth, ambient
   gradient, micro-interactions, spatial layering, progressive disclosure and shared-element
   transitions are permitted and where they are forbidden), micro-interaction and haptic specs,
   spatial continuity rules, a two-layer nesting limit, and skeleton/empty-state rules — and it is
   certified by a gate (V8), not left to taste.

   Two decisions inside it: **neumorphism is withdrawn entirely** (Rev 1.3 allowed it for "rare
   tactile segmented controls"; it cannot meet WCAG AA and no control here needs it), and **every
   glass surface must declare a capability pair**. Blur on Android is API 31+ and `Modifier.blur`
   silently no-ops below it, so an unreviewed glass surface ships as a flat translucent rectangle on
   part of the fleet. Both treatments — backdrop blur and the pre-31 fallback — are screenshot-
   certified, and backdrop blur must be applied to a captured graphics layer rather than to the
   overlay's own content.

## Why

- The blueprint as received declared itself "canonical certification authority" while the repository
  already has `PROJECT_TRUTH_PROTOCOL.md`, `AGENTS.md` exclusions and locked action-plan decisions
  that outrank any design document. Rev 1.5 subordinates itself to them explicitly.
- Its fidelity model could not survive contact with a second screen size, and its own illustrative
  coordinates were 6-10 Rpx off the measured values against a +/-2 Rpx tolerance.
- It specified token *names* with no values while forbidding arbitrary values in feature code, so
  independent implementers would still have diverged on every colour, radius, type size and
  breakpoint.
- The existing POS is not a base to extend. A 947-line screen against a 102-method interface through
  a 1533-line ViewModel with two tests cannot be made benchmark-faithful, fast, or certifiable by
  retheming. The gateway shape in particular is the root cause: narrow, fakeable gateways are what
  make JVM screenshot tests and pure reducer tests possible at all.

## Consequences

- Agents **may** replace any POS client code: screen composition, component set, theming path,
  gateway interface, ViewModel structure and icon dependency. Nothing in `feature/pos` is preserved
  on the grounds that it exists.
- Agents **may** restructure the feature into `pos-design` / `pos-domain` / `pos-data` / `pos-ui`,
  adopt Hilt, and introduce the Style Dictionary token pipeline (which makes `GtrColors.kt`
  generated output).
- Agents **must not**: change the cart/checkout **system of record** — the Postgres RPCs, ledger and
  RLS are untouched, and replacing the *client* gateway is not the same thing; weaken or
  client-side-only an authorisation gate; queue non-cash tenders offline; cache a manager approval
  token; introduce ZIMRA/fiscalisation; define a brand primitive outside `brand-tokens.json`; or
  drop a capability listed in blueprint §11.
- **New backend work is authorised and required:** operator pin storage + RPCs (blueprint §6.4) and
  the reserve-first contract (§9.6), the latter now fully specified rather than deferred.
- The foundations phase — module split, DI, token pipeline, screenshot/lint/benchmark harness — is
  **Phase 1** and gates everything after it. None of it exists today.
- Rebuilding rather than retheming costs more up front. It is the cheaper path here because the
  certification regime (§10) is unmeetable against a 947-line screen bound to a 102-method
  interface, and because every phase after 3 depends on being able to test without a device.
- The committed reference is a lossy JPEG. Colour tokens are provisional until resampled from a
  lossless export; geometry ratios are unaffected.

## Open

1. Lossless benchmark export for colour resampling.
2. Supported baseline device for performance targets — blocked on the action plan's open Path B SKU
   list.
3. Confirm the benchmark's actual typeface against the lossless export; Inter is recommended on
   merit regardless, so a mismatch is a delta to register rather than a reason to change course.
4. Whether the brand display face (Titillium Web) is retained for the rail lockup and hero headline.
5. Whether `View All` survives as Quick Access management or is dropped (delta D-004).
