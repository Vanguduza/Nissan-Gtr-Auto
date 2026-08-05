# Shopping-By-KMP adoption — implementation delta audit

- **Date:** 2026-08-05
- **Kind:** Implementation closure pass (manager-orchestrated) + evidence update
- **Sources of truth:**
  1. `docs/plans/2026-08-05-shopping-by-kmp-full-adoption-plan.md` (§1, Adopt/Adopt-adapt/Defer matrix, surfaces)
  2. `docs/plans/2026-08-05-shopkit-clean-slate-execution.md` (Phases A–E)
  3. MIT reference: `reference/shopping-by-kmp/` (present locally; gitignored)
  4. Tree: `packages/android-ui`, `apps/android-customer`, `apps/android-management`, `apps/android-delivery`, `apps/ios`, `apps/web`
- **Method:** Coding lanes closed gaps; Android `:app:assembleDebug` **BUILD SUCCESSFUL**; Meili unit test pass; RLS + security reviews run; iOS static only (Windows).

---

## Executive summary

**ShopKit foundation and mobile clean-slate A–E remain landed.** Customer IA remains **DIVERGED** (approved 5-tab + top Cart/Account). Gap-closure pass (2026-08-05) closed must-fix orphans and most Adopt PARTIAL rows on Android + iOS + Web, and promoted **Meilisearch** from deferred to **implemented** (Edge proxy + sync pipeline + FTS fallback).

**Android customer assemble:** `BUILD SUCCESSFUL` (`:app:assembleDebug`). **iOS:** static parity present; **BLOCKED** for assemble — no macOS `xcodebuild` on this host. **Meili runtime:** code ready; live index requires Docker + Edge secrets + sync job.

---

## Status legend

| Status | Meaning |
| --- | --- |
| **DONE** | Wired UI + GTR SoR / behavior matches Adopt intent |
| **PARTIAL** | Present but incomplete, placeholder, or not fully wired |
| **MISSING** | Plan Adopt/Adopt-adapt item not found in product surface |
| **DEFERRED** | Explicit plan Defer (or product deferral with reason) |
| **DIVERGED** | Intentional change vs plan text (usually approved fine-tune) |
| **BLOCKED** | Implemented as far as possible; external toolchain/credentials |

---

## Matrix — plan item → status → evidence → notes

### Platforms & module targets (§4.1)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| P1 | Android phone/tablet ShopKit | **DONE** | `packages/android-ui/.../shop/*`; assembleDebug green | |
| P2 | iOS SwiftUI ShopKit port | **PARTIAL** / **BLOCKED** | `ShopKit.swift` + parity screens | **xcodebuild unconfirmed** (Windows) |
| P3–P6 | Desktop / Web JS / TV / Automotive | **DEFERRED** | Plan | |
| P7 | Shared CMP → fork | **DONE** (Android) / **DEFERRED** (CMP) | ShopKit fork | |

### Navigation & shell (§4.2) + customer fine-tunes

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| N1 | Splash → auth → main | **DONE** | MainActivity / ContentView | |
| N2 | Bottom tabs | **DIVERGED** | 5-tab + top Cart/Account | Approved fine-tune |
| N3 | Type-safe Compose Navigation | **PARTIAL** | Overlay + tab state | Acceptable for IA |
| — | Labeled top icons + logo | **DONE** | CustomerShellTopBar; assemble green | ripple → LocalIndication |
| — | Full hamburger + category icons | **DONE** | Leaves → live PLP when stock | |
| — | All car parts → categories grid | **DONE** | CategoriesGrid → CategoryPlpScreen | |
| — | KMP-style cart chrome | **DONE** | CartScreen | |

### Auth & session (§4.3) — all **DONE** (unchanged)

### Splash (§4.4) — **DONE**

### Home merchandising (§4.5)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| H1 | Location → vehicle context | **PARTIAL** | Garage fitment on Home | Automotive adapt |
| H2 | Search entry | **DONE** | Meili Edge + FTS fallback + facets | Was deferred Meili |
| H3 | Settings from home | **DIVERGED** | Settings tab | |
| H4 | Notifications entry | **PARTIAL** | Honest empty | No inbox RPC |
| H5 | Banner carousel | **DONE** | | |
| H6 | Category chips | **DONE** | Chips → CategoryPlpScreen | Was empty dialog |
| H7 | Flash Sale + countdown | **PARTIAL** | Honest empty | No deals feed RPC |
| H8–H9 | Rails | **PARTIAL** | Browse heuristics | Acceptable until velocity feed |

### Categories (§4.6)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| C1 | Full category list → PLP | **DONE** | `CategoryPlpScreen` + `listCatalogBrowse` | Honest empty only when no rows |

### Search / PLP (§4.7)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| R1 | Paginated search / typeahead | **DONE** | Meili + FTS; facet chips | |
| R2 | FilterDialog | **DONE** | Wired on CategoryPlpScreen | |
| R3 | SortDialog | **DONE** | Wired on CategoryPlpScreen | |
| R4 | Filter use-case layer | **DONE** | CatalogBrowseFilters + VM | |

### PDP (§4.8)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| D1 | Image gallery | **DONE** | Coil / AsyncImage when URLs; single honest placeholder | |
| D2 | Wishlist heart | **DONE** | | |
| D3 | Rating display | **DONE** | + reviews route | |
| D4 | Expandable description | **DONE** | metadata-backed `descriptionText()` | |
| D5–D7 | Price / ATC / fitment | **DONE** | | |

### Comments / reviews (§4.9)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| V1 | Comments list | **DONE** | `PdpReviewsScreen` from PDP | Was orphaned |
| V2 | Add comment + photo | **DONE** | Bridge-First pod-camera / ReviewCamera | |

### Wishlist / Cart / Address / Payment — **DONE** (unchanged commerce path)

### Profile hub extras (§4.14–4.15)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| PR1 | Profile hub | **DONE** | Account overlay + Phase2 entries | |
| PR2 | Edit profile | **DONE** | EditProfileScreen + RPCs | |
| PR3 | My Orders | **DONE** | | |
| PR4 | Coupons | **PARTIAL** | Honest empty | No coupon RPC |
| PR5 | Wallet / loyalty | **DONE** | LoyaltyWalletScreen + `get_loyalty_balance` | |
| PR6 | Notifications | **PARTIAL** | Honest empty | No inbox RPC |
| PR7 | Settings + logout | **DONE** | Theme Light/Dark/System | |
| PR8 | Help Center | **PARTIAL** | Legal & help URLs | |
| G1–G4 | Chat / Compare / Track / Garage | **DONE** | | |

### Cross-cutting tech (§4.16)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| T1 | Dark mode | **DIVERGED** | Prefs shipped | |
| T2 | Fonts | **DONE** | | |
| T3 | Coil images | **DONE** | `ShopImages.kt` Coil helpers | |
| T6–T7 / T12–T15 | Koin / Ktor SoR / fakes | **DEFERRED** (reject) | | |
| T16 | Bridge-First | **DONE** | | |
| Meili | Catalog search | **DONE** (code) / **BLOCKED** (ops) | Edge + sync + ADR | Needs Docker/Edge secrets/sync |

### Management & delivery (§4.17) — **DONE** (untouched this pass; prior gate)

### GTR backend gaps (§5.1 P2+) on mobile

| Capability | Status | Notes |
| --- | --- | --- |
| Diagram canvas | **MISSING** | PDP diagram *image* only; no interactive canvas |
| Kits | **DONE** | KitsScreen + `listActiveKits` |
| Loyalty | **DONE** | Live balance UI |
| Returns (quarantine CN) | **DONE** | Thin flow + `post_customer_return_credit_note` |
| B2B procurement | **DEFERRED** | Web-only Later |

### Web ← KMP (§14)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| WKH1 | Banner carousel | **DONE** | | |
| WKH2 | Flash / countdown | **PARTIAL** | Honest empty | No deals RPC |
| WKH3 | Merch rails | **DONE** | | |
| WKH4–5 | Filter / sort | **DONE** | | |
| WKH6 | Richer PDP gallery | **PARTIAL** | Diagram + review photos when assets exist | Asset-dependent |
| WKH7/8/12 | Map / pay / checkout polish | **DEFERRED** | Optional | |
| WKH9–10 | Coupons / notifications | **PARTIAL** | Honest empties | |
| WKH11 | Profile hub IA | **DONE** | | |
| Meili typeahead | **DONE** | `searchCatalogMeili` + facets | No browser Meili keys |

### Clean-slate phases (A–E)

| Phase | Status | Caveat |
| --- | --- | --- |
| A ShopKit freeze | **DONE** | README now **FROZEN / DONE**; deprecated Gtr* shell aliases removed |
| B–D | **DONE** | Prior gates + customer gap closure |
| E iOS | **PARTIAL** | Parity code; **xcodebuild BLOCKED** on Windows |

---

## Ordered gap list — closure evidence

### Must-fix (was)

1. **Reviews on PDP** — **DONE** (Android `PdpReviewsScreen`; iOS twin).
2. False “§4 complete” banner — **DONE** (this audit honesty).
3. **iOS xcodebuild** — **BLOCKED** (Windows host; macOS required).
4. **ShopKit README freeze** — **DONE** (`packages/android-ui/README.md`).
5. Release sec WARN triage — **PARTIAL** (pre-existing allowBackup / offline PII / JWT Keychain still open; Meili grant + facet allowlist fixed this pass).

### Later (was) → now

1. Filter/Sort dialogs — **DONE**
2. PDP Coil gallery — **DONE**
3. Category → live PLP — **DONE**
4. Meili — **DONE** (code); ops **BLOCKED** until Edge+Docker+sync
5. Loyalty / returns / kits — **DONE**; diagram canvas still **MISSING**
6. Edit profile — **DONE**
7. Live flash deals — still **PARTIAL** (no RPC)
8. Web WKH7/8/12 — remain **DEFERRED** optional
9. Deprecated Gtr* aliases — **DONE** (removed)
10. Phase N CMP / Koin / i18n — **DEFERRED**

---

## Verification evidence (2026-08-05 closure)

| Gate | Result |
| --- | --- |
| Exclusions (ZIMRA / payroll tax / HTML5 QR) | **PASS** — only docs mentioning exclusions |
| `pytest tests/test_meili_documents.py` | **PASS** (1 passed) |
| Android `:app:assembleDebug` | **PASS** — `BUILD SUCCESSFUL in 3m 33s` |
| iOS `xcodebuild` | **BLOCKED** — Windows; key Swift files present |
| `/supabase-rls-auditor` | **PASS** after grant fix on `catalog_meili_sync_state` (was FAIL) |
| `/security-reviewer` | **PASS (WARN)** — Meili keys server-side; JWT Edge; ops WARNs remaining |

### Security / RLS notes

- Migration `20260805190000_catalog_meili_sync_state.sql`: RLS + staff SELECT + service_role ALL + **explicit GRANTs**.
- Edge `catalog-search-meili`: `verify_jwt = true`; facet allowlist; FTS fallback with caller JWT.
- Clients: no Meili master/search keys; `NEXT_PUBLIC_MEILI_*` stubs removed from web env example.
- Ops: set `MEILI_SEARCH_KEY` (not master) on Edge; run `python -m data_pipeline.meili_sync --full`.

---

## Remaining blockers

1. **iOS assemble** — macOS `xcodebuild -scheme GTRCustomer …` required.
2. **Meili live** — Docker Meili + Edge secrets + sync; until then FTS fallback serves search.
3. **Mobile diagram canvas** — interactive canvas not ported (image on PDP only).
4. **Flash / coupons / notifications inbox** — honest empties until backend feeds exist.
5. **Pre-release sec WARNs** (unchanged from prior audits): management offline PII / fail-open roles; delivery `allowBackup` / plaintext queues; iOS Keychain/JWT storage.

---

## What NOT to claim as DONE

- Parent plan **§4 matrix 100%** including Phase N / CMP / fake flash timers.
- **iOS build-verified** on this host.
- **Meili production cutover** without ops secrets + synced index.
- **Interactive diagram canvas** on mobile.
- Pre-release security WARN backlog fully cleared.

---

*End of audit (implementation closure update).*
