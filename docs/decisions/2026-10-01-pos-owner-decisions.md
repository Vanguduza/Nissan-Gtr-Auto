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

## Still open

- `PROJECT_CANONICAL_STATE.json` on `chatgpt/pos-reconcile-green-20260907` requires the ancestor
  `1e0a8b764e13…` for `pos-android`. No branch on GitHub contains it, so `assemble*` is blocked
  until it is pushed or the manifest is corrected.
