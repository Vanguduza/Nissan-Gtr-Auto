# POS visual & build-plan audit — 2026-09-30

**Question (owner):** Are the POS visual direction and build plan headed toward the app the owner
wants — the 2026-09-07 benchmark, modified by the owner's corrections, as a standalone POS that
also gives access to management features?

**Verdict:** **No, not as things stand.** Most of the pieces are right, but they sit on branches
that have diverged. If the current Antigravity build continues from where it is, it will rebuild a
POS that is missing the working features already on the canonical branch, and it has no plan for
the management access the owner asked for.

Benchmark: `docs/design/pos/reference/benchmark-home-expanded-2026-09-07.jpg`, which is on the
`cursor/phase*-ad25` branches. It is the same image the owner re-attached on 2026-09-30, re-encoded
at 1024×682.

---

## 1. There are three POS lines

| Line | Branch | Where the app lives | Look | Function | State |
|---|---|---|---|---|---|
| **A. Reconcile (declared canonical)** | `chatgpt/pos-reconcile-green-20260907` | `apps/android-management` **tablet flavour** (`co.zw.nissangtr.management.tablet`), `feature/pos` | Benchmark layout, but rendered in **`ShopWarmTheme`**: a warm cream canvas `#F7F1EA` | The most complete: operator workspace, vehicle cascade, Popular Items + operator pins (backend migration included), customer garage, multi-vehicle sale, offline EPC (SQLCipher v7), reserve-first checkout, staff portal for management access, x86 CI | Not merged; no PR |
| **B. Rev 1.5 / Antigravity** | `cursor/phase1-foundations-ad25` → `cursor/phase2-design-system-ad25` | Same module (`apps/android-management/feature/pos`), new `pos-*` modules | **Closest to the benchmark**: cool canvas `#F4F5F7` (benchmark measures about `#F3F4F8`), steel rail `#12151C`, red `#C8102E`, measured geometry | Design system only. **21 of 159** register rows done, **138 to do**; no screens yet | Not merged; no PR |
| **C. Standalone till (PR #11)** | `cursor/standalone-adaptive-pos-ad25` | New app `apps/android-pos` | **Dark** `GtrTheme` "till" with finder, ticket and icon rail. **Not the benchmark layout** | A separate till; forbids reusing the management POS | Open PR #11, from before the benchmark |

## 2. Findings

### F1 — The Antigravity line is on the wrong base (critical)
`cursor/phase2-design-system-ad25` branches from `a7da5d2` on `cursor/erp-cursor-setup-ad25`. It
does **not** contain the 118 commits on `chatgpt/pos-reconcile-green-20260907`. The Rev 1.5 blueprint
was therefore written without sight of line A. As a result:
- §7.4 says the operator-pin backend is "not yet built". Line A already has
  `20260907140000_pos_operator_popular_pins.sql`.
- `APPROVED_VISUAL_DELTAS.md` ("Withdrawn") says "`ShopWarmTheme` … was never in the POS path".
  On line A, `PosOperatorWorkspace.kt:146` wraps the POS in `ShopWarmTheme`.
- Customer garage, multi-vehicle context, offline EPC v7, reserve-first checkout and the staff portal
  are all missing from line B's base. A "clean-room" rebuild from there would thin these features
  silently, which `PROJECT_TRUTH_PROTOCOL.md` forbids.

### F2 — The POS on the canonical line has the wrong colour ground (visual)
The benchmark canvas is cool light grey. Line A deliberately moved the POS onto the customer app's
warm cream theme (the design lock of 2026-09-07 says "use `ShopWarmTheme`"). That is a visible
departure from the benchmark, and nothing in the delta registry sanctions it. Line B's tokens are
correct here.

### F3 — The canonical line cannot build an APK from GitHub (build)
`PROJECT_CANONICAL_STATE.json` on line A requires the ancestor `1e0a8b764e13…` for `pos-android`.
`verifyCanonicalPosLineage` runs before every `assemble*` or `bundle*` task. **That commit is on no
pushed branch**, so every build from GitHub fails with `BLOCKED … exit 33`. The customer
app's required ancestor `56797ca3…` is also missing. Either those commits exist only on a local
machine and need pushing, or the manifest is wrong.

### F4 — "Standalone" is defined three different ways (product)
- Line A: the tablet APK flavour of the management app. Sales staff land directly in the POS, and
  management modules are reached through **Settings → Staff portal** (a second login re-checks role
  and module access). This matches action plan L1 ("separate APKs, shared modules").
- Line B: says nothing about management access. Neither the blueprint nor the feature register
  mentions a staff portal, hub access or `module_access` from the POS.
- Line C: a separate app with no management features at all.

The owner's stated goal ("standalone, with access to management features") matches **line A's
model**. Line B needs that capability added to its register; line C does not fit.

### F5 — The recorded owner corrections contradict each other
| Topic | 2026-09-07 design lock (line A) | Rev 1.5, "Owner 2026-09-13" (line B) |
|---|---|---|
| Popular row | **Popular Items** = server best-sellers **plus** per-operator pins; pin targets are part, model, category and **subcategory** | **Quick Access** = operator pins **only**; "no server popularity ranking"; pin targets are spare, category and vehicle |
| Vehicle cascade | Model → Generation → Engine; **Make intentionally omitted** (Nissan-only shop) | Same, plus Maker "when the multi-make catalog is active" |
| Cold start | "**No app splash**"; the starting window matches the login screen | Defers to the kiosk spec and action plan #10–11, which **require** a branded animated GT-R splash |
| Payment | "Proceed to Payment" opens a payment surface | Not stated explicitly |

Both columns cannot be true. Each row needs one owner decision.

### F6 — Smaller drifts in line B
- The blueprint specifies the **Inter** typeface for the UI; `PosType.kt` uses `FontFamily.Default`
  (Roboto).
- SYS-01 is marked "Style Dictionary pipeline — done". The implementation is a custom Node script
  (`build-tokens.mjs`). It works, but the register row misdescribes it.

### What is right and should be kept
- Rev 1.5's scoping of the benchmark: composition, hierarchy and proportion are binding. Currency
  (`KSh`), tax (`VAT 16%`), sample data and copy typos are not. Money comes from USD/ZiG backend
  state, invoices stay tax-agnostic (no ZIMRA), and `GNGUINE` is corrected.
- Deltas D-001 (Reports → EPC Browse), D-002 (the vehicle cascade replaces "Spares · Service ·
  Performance"), D-005 (offline indicator), D-006, D-011 (error red distinct from the CTA red) and
  D-012.
- Line B's token values and the adaptive, phone-parity approach.
- Line A's working features and its staff-portal model for management access.

## 3. Recommended path

1. **Owner decisions** on the F5 rows and on F4 (confirm: POS = tablet APK, management through
   Settings → Staff portal). Record them in one decision file and update the delta registry.
2. **Close or supersede PR #11.** Its dark till contradicts the benchmark.
3. **Fix F3.** Push the missing ancestor commits, or correct `PROJECT_CANONICAL_STATE.json`.
4. **Rebase line B onto line A.** The phase 1 and 2 work is mostly new, separate modules
   (`packages/pos-design`, `pos-*` feature modules, tokens), so it should port cleanly.
5. **Re-skin and refactor; do not clean-room.** Move the existing `PosOperatorWorkspace` from
   `ShopWarmTheme` to `PosTheme`. Then run Rev 1.5's architecture changes (typed gateways, a single
   store) against the §12 capability contract, keeping every feature from line A.
6. Update the Rev 1.5 blueprint and feature register for what line A already has. Add a
   management-access / staff-portal row. Fix the pins backend and theme statements.
7. Only then continue the Antigravity phases (screens) from the corrected register.
