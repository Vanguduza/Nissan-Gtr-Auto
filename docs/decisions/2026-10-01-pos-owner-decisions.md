# POS owner decisions — resolves the 2026-09-30 audit conflicts

- Date: 2026-10-01
- Decided by: owner (Vanguduza)
- Resolves: `docs/audit/2026-09-30-pos-visual-build-audit.md` findings F4 and F5
- Supersedes, where they conflict: `docs/decisions/2026-09-07-pos-operator-screen-design-lock.md`
  (cold-start splash) and `docs/design/pos/POS_FRONTEND_BLUEPRINT_REV_1_5.md` §7, §8.1 and
  `APPROVED_VISUAL_DELTAS.md` D-002 / D-003

## D1 — Popular row: best sellers plus operator pins, fully editable

The row shows **server-ranked best sellers together with the operator's own pins**. The operator
can **remove or add any item in the row**, whether it came from the best-seller ranking or from a pin.

- Adding: long-press from anywhere an entity renders (search results, categories, EPC browse,
  vehicle cascade, cart rows) → Pin.
- Removing: long-press any card in the row → Remove. Removing a best-seller hides it for that
  operator. It must not reappear on the next ranking refresh until the operator adds it back.
- Pinnable kinds keep the 2026-09-07 scope: part, model/vehicle, category, subcategory.
- Hides and pins are operator-scoped and server-persisted (they follow the operator between devices)
  and queue offline like other mutations.
- Rev 1.5 §7.3 "No server popularity ranking is merged into this row" is **withdrawn**.

## D2 — Vehicle cascade never shows Make

The header cascade is **Model → Generation → Engine** only. Make is never shown, in any deployment
or window class. Rev 1.5 §8.1 "A Maker field precedes them only when the multi-make catalog is
active" and the "plus Maker" clause of D-002 are **withdrawn**.

## D3 — Animated GT-R start-up, built from the POS hero image

The tablet **does** show the animated GT-R start-up sequence (kiosk spec §3, action plan #10–11).
The animation is built from **the same GT-R hero image used on the POS home screen**
(`pos_home_hero_locked.webp`, the rear-three-quarter GT-R from the benchmark hero), not from a
separate asset pack.

- The splash runs before staff login, as the kiosk spec requires. It plays once per full boot,
  not after an ordinary logout or idle lock.
- The 2026-09-07 lock line "Tablet cold start has **no app splash**" is **withdrawn**.
- The no-HOME-escape and Lock Task requirements still apply. The splash must not expose the
  launcher.
- Engine audio stays optional and is controlled by the existing Device Admin toggle.

## D4 — Standalone POS = the tablet APK; management through the Staff portal

The POS is the **tablet flavour** of the management app (`co.zw.nissangtr.management.tablet`,
action plan L1). A salesperson lands directly in the POS after authentication. Management features
are reached through **POS → Settings → Staff portal**, which requires a **second login** and
recalculates role and `module_access` before showing any management module.

- `apps/android-pos` (PR #11) is **not** the product direction.
- The Rev 1.5 blueprint and feature register must add the Staff portal as a required capability.

## D5 — Rebuild the POS UI from the benchmark; keep only capabilities from the old POS

The POS screens are built **new** in `:feature:pos-ui` from `:pos-design` (Rev 1.5), against the
benchmark and these decisions. The existing UI is **not** re-themed:

- `PosOperatorWorkspace.kt` (2026-09-07) gives the benchmark layout, but it is assembled from the
  pre-benchmark kit. Its Current Sale pane is `RightCartPane` in `ShopStaffPanel`. Quick Sale,
  Orders and Settings re-host the July panels `CartSetupSection`, `QuotesPanel`, `PrinterSection`
  and others. Re-theming it would carry that old screen forward.
- What is kept from the canonical lineage is **behaviour**: `PosViewModel`, RPCs, offline/SQLCipher,
  pins, customer garage, multi-vehicle context, kiosk, Staff portal. The new UI binds to these.
- When each new screen reaches parity (§12 capability contract), the matching old composable is
  deleted. No screen ships with both.
- Enforcement: `scripts/design-lint.py` DL-09 (no `ui.shop` / `GtrTheme` / `management.pos` imports in
  clean modules) and DL-10 (no dependency on `:feature:pos` or `:android-ui`). This runs in the
  Android POS workflow.

## D6 — Delete the pre-benchmark POS frontend; keep only behaviour

To stop old UI leaking into the benchmark POS, the existing POS Compose UI was **deleted**
(2026-10-01), not kept for gradual replacement. This replaces the "delete each old composable at
parity" step in D5.

**Deleted (UI):** `PosScreen.kt` (incl. payment and manager-auth dialogs, till/printer/companion/
quotes panels), `PosOperatorWorkspace.kt`, `PosCustomerWorkspace.kt`, `PosEpcBrowseScreen.kt`,
`StaffOfflineEpcBrowseScreen.kt`. Retrievable from git history at `chatgpt/pos-reconcile-green-20260907`
for **capability reference only**. Their layouts and components must not be copied.

**Kept (behaviour, no UI):** `PosViewModel.kt` (all sale, cart, customer, garage, vehicle, pins,
quotes, park/resume, returns, payment, manager-gate logic), `PosCartLineOps.kt`,
`PosPopularItems.kt` (row merge and pin logic), `EpcCatalogSource.kt`, `offline/*` (SQLCipher store,
sync engine and worker, connectivity, passphrase), `PosModule.kt`, all unit tests, all Supabase
migrations and RPCs, kiosk module, Staff portal flow in `MainActivity.kt`.
**New:** `PosRuntime.kt`, the non-visual wiring formerly inside `PosScreen` (offline store, sync engine,
ViewModel factory) for the new UI to bind to.

**Module boundary:** `:feature:pos` is now logic-only. It has no Compose and no dependency on
`:android-ui` (the shop kit). The hero and rail artwork (`pos_home_hero_locked.webp`,
`pos_nav_car_locked.webp`) moved to `:feature:pos-ui` for the benchmark hero and the D3 splash.

**Interim state:** until the benchmark POS UI ships, the tablet POS route shows a holding screen
with **no sales capability**. It keeps only the Staff portal, Kiosk & device and hub doorways, so a
locked kiosk is never stranded. The hub's "Offline EPC catalog" entry is removed until EPC Browse is
rebuilt. **Builds from this lineage must not be deployed to a live counter** until the new POS reaches
§12 capability parity.

## D7 — The web POS is the same product as the tablet POS

The web POS (`apps/web` `/staff/pos`) is redesigned to **look and function exactly like the tablet
POS**: the same benchmark, the same decisions D1–D6, the same tokens (`packages/ui/brand-tokens.json`
→ `tokens.css` / `tokens.ts`), the same blueprint anatomy and the same feature register. Only the
platform deltas W-001 to W-006 in `docs/design/pos/WEB_POS_PARITY.md` are allowed: online-only, no
browser camera scanning, browser print instead of ESC/POS, no drawer kick, and no kiosk. The Staff
portal second login is kept identical.

The July 2026 web POS UI (`staff-pos-panel.tsx`, `staff-pos-shell.tsx`) is pre-benchmark and is
retired. It must not be restyled. New code goes in `apps/web/components/pos/` and `apps/web/lib/pos/`
(design-lint DL-12).

## Still open

- `PROJECT_CANONICAL_STATE.json` on `chatgpt/pos-reconcile-green-20260907` requires the ancestor
  `1e0a8b764e13…` for `pos-android`. No branch on GitHub contains it, so `assemble*` is blocked
  until it is pushed or the manifest is corrected.
