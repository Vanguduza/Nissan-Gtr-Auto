# Shopping-By-KMP adoption — implementation delta audit

- **Date:** 2026-08-05
- **Kind:** Documentation-only audit (no assemble / no build / no commit)
- **Sources of truth:**
  1. `docs/plans/2026-08-05-shopping-by-kmp-full-adoption-plan.md` (§1, Adopt/Adopt-adapt/Defer matrix, surfaces)
  2. `docs/plans/2026-08-05-shopkit-clean-slate-execution.md` (Phases A–E)
  3. MIT reference: `reference/shopping-by-kmp/` (present locally; gitignored)
  4. Tree: `packages/android-ui`, `apps/android-customer`, `apps/android-management`, `apps/android-delivery`, `apps/ios`, `apps/web`
- **Method:** Static evidence (paths + grep/read). Prior clean-slate gate claims (A–E CLOSED) treated as *process history*, not re-verified by assemble.

---

## Executive summary

**ShopKit foundation and mobile clean-slate A–E landed in spirit:** `packages/android-ui` has a real KMP-forked `Shop*` API; customer / management / delivery / iOS consume it (zero live `GtrScaffold` / `GtrScreen` / `GtrBrandBar` call sites under apps). Delivery Maps + signature behavior and management POS/offline/kiosk behavior appear preserved. Web §14 has a documented first pass.

**Do not treat the parent §4 matrix or “mobile complete” as 100% feature-DONE.** Customer IA deliberately **diverged** from plan N2 (4-tab → GSF-style 5-tab + top strip). Several Adopt rows are **PARTIAL** (placeholder PDP gallery, unused filter/sort dialogs, static category empty shells) or **orphaned** (reviews module compiled but not composed). Phase 2 enrichment (loyalty, returns, kits, diagram) remains **MISSING**. Meili is **DEFERRED** — typeahead uses `search_catalog`. iOS gate was **PASS WITH BUILD SKIP** (no macOS `xcodebuild` on Windows). `packages/android-ui/README.md` still says “FREEZE PENDING” — **stale** vs manager freeze claims.

**Honesty on recent customer fine-tunes:** labeled top icons, full hamburger + category icons, All car parts → categories grid, category empty dialog, and KMP-style cart (back / delivery / pay / orders) are present. Category taps and many menu leaves are **honest empties**, not live PLP. That is WIP-correct, not catalog parity.

---

## Status legend

| Status | Meaning |
| --- | --- |
| **DONE** | Wired UI + GTR SoR / behavior matches Adopt intent |
| **PARTIAL** | Present but incomplete, placeholder, or not fully wired |
| **MISSING** | Plan Adopt/Adopt-adapt item not found in product surface |
| **DEFERRED** | Explicit plan Defer (or product deferral with reason) |
| **DIVERGED** | Intentional change vs plan text (usually approved fine-tune) |

---

## Matrix — plan item → status → evidence → notes

### Platforms & module targets (§4.1)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| P1 | Android phone/tablet ShopKit | **DONE** | `packages/android-ui/.../shop/*`; three Android apps use `ShopTheme` | Visual system present; not chrome-only |
| P2 | iOS SwiftUI ShopKit port | **PARTIAL** | `apps/ios/GTRCustomer/Theme/ShopKit.swift`, `ContentView.swift` | IA + chrome ported; **xcodebuild unconfirmed** (Windows skip) |
| P3 | Desktop JVM | **DEFERRED** | Plan | No GTR desktop product |
| P4 | Web JS KMP runtime | **DEFERRED** | Plan | UX bridged into Next.js (§14), not `webApp` |
| P5 | Android TV | **DEFERRED** | Plan | |
| P6 | Automotive OS | **DEFERRED** | Plan | |
| P7 | Shared CMP → fork | **DONE** (Android) / **DEFERRED** (CMP) | `packages/android-ui` | Fork into ShopKit; full CMP Phase N |

### Navigation & shell (§4.2) + customer fine-tunes

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| N1 | Splash → auth → main | **DONE** | `MainActivity.kt` `ShopSplash` → `AuthGate`; iOS `ShopSplash` | |
| N2 | Bottom tabs Home/Wishlist/Cart/Profile | **DIVERGED** | `ShellTab` 5-tab: Home/Shop/Wishlist/Garage/Settings; Cart+Account+Sign-in top bar | GSF-style IA (approved fine-tune). Cart is overlay, not tab |
| N3 | Type-safe Compose Navigation | **PARTIAL** | Overlays + tab state in `MainActivity`; feature modules | Aligns IA; not full KMP typed nav graph |
| — | Labeled top icons + logo | **DONE** | `shell/CustomerShellTopBar.kt`; iOS `CustomerShellChrome.swift` | Icon-over-label; flat chrome |
| — | Full hamburger + category icons | **DONE** (IA) / **PARTIAL** (data) | `HamburgerMenuOverlay.kt` / iOS overlay | Tree + icons present; leaves → empty dialogs |
| — | All car parts → categories grid | **DONE** (shell) / **PARTIAL** (stock) | `CategoriesGridScreen.kt` / `.swift` | Grid + empty-inventory dialog; no live category PLP |
| — | KMP-style cart chrome | **DONE** | `CartScreen.kt` | Back · delivery · pay methods · orders link |

### Auth & session (§4.3)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| A1 | Login | **DONE** | `SignInScreen.kt` + GoTrue | ShopKit restyle |
| A2 | Register | **DONE** | `AuthSessionViewModel.signUp` / `signUpWithEmail` | |
| A3 | Social login stubs | **DONE** (honest) | `SignInScreen` copy — hidden until providers | No fake OAuth |
| A4 | Forget-password | **DONE** | `resetPasswordForEmail` wired | |
| A5 | Session check | **DONE** | `AuthGate` / session VMs | |
| A6 | Logout | **DONE** | Profile / settings sign-out | |

### Splash (§4.4)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| S1 | Animated splash | **DONE** | `ShopSplash.kt` | GTR logo + expanding circle |

### Home merchandising (§4.5)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| H1 | Location → vehicle context | **PARTIAL** | Garage fitment button on Home when vehicle set | Not a full “location row”; automotive adapt |
| H2 | Search entry | **DONE** | `InlineSearch.kt` → `search_catalog` | **Meili deferred** (comment in file) |
| H3 | Settings from home | **DIVERGED** | Settings is bottom tab | Not Profile→Settings from home toolbar |
| H4 | Notifications entry | **PARTIAL** | Account hub → honest empty screen | No inbox RPC |
| H5 | Banner carousel | **DONE** | `ShopBannerCarousel` in `CatalogScreen` | Promo strip (static/home banners) |
| H6 | Category chips | **PARTIAL** | Chips → empty-category dialog | Not live PLP filter |
| H7 | Flash Sale + countdown | **PARTIAL** | Honest empty when `deals` empty | No fake countdown — correct |
| H8 | Most Sale rail | **PARTIAL** | Rail from browse slice | Not true sales-velocity feed |
| H9 | Newest rail | **PARTIAL** | Browse take/drop heuristic | Acceptable proxy until dedicated feed |

### Categories (§4.6)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| C1 | Full category list → PLP | **PARTIAL** | Static `DefaultCatalogCategoryCards` + empty dialog | UI shell; inventory not published path |

### Search / PLP (§4.7)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| R1 | Paginated search results | **PARTIAL** | Typeahead via `search_catalog`; no dedicated SearchResults page (intentional) | Pagination as API allows — typeahead-first |
| R2 | FilterDialog | **MISSING** (consumer) | `ShopFilterDialog` only in `ShopControls.kt` + previews | **Not used** by customer catalog |
| R3 | SortDialog | **MISSING** (consumer) | `ShopSortDialog` same | **Not used** by customer catalog |
| R4 | Filter use-case layer | **PARTIAL** | Catalog repository / VM filters | OmniCart-pattern only; no dialog UX |

### PDP (§4.8)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| D1 | Image gallery | **PARTIAL** | Placeholder numbered thumbs `0..3`, OEM text hero | No Coil/OEM assets wired |
| D2 | Wishlist heart | **DONE** | PDP heart + `WishlistStore` | |
| D3 | Rating display | **DONE** | `ShopRatingRow` + review stats RPC | |
| D4 | Expandable description | **PARTIAL** | `ShopExpandableDescription` uses `product.name` | Thin vs full OEM metadata |
| D5 | Price USD (+ ZiG at cart) | **DONE** | PDP USD; cart currency chips + zig RPC | |
| D6 | Sticky ATC | **DONE** | `ShopStickyCtaBar` | |
| D7 | Fitment / core-charge / stock | **DONE** | Fitment vs garage; core deposit lines; stock label | |

### Comments / reviews (§4.9)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| V1 | Comments list | **PARTIAL** | `feature/reviews` exists; **not composed** in `MainActivity` | Dependency present; no nav entry |
| V2 | Add comment + photo | **PARTIAL** | VM + Bridge-First `pod-camera` | Same orphan — no host screen route |

### Wishlist (§4.10)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| W1 | Wishlist tab | **DONE** | Bottom tab + RPCs | |
| W2 | Nested PDP | **DONE** | Wishlist → product open | |

### Cart & checkout (§4.11)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| K1 | Cart CRUD | **DONE** | Cart RPCs + UI | |
| K2 | Checkout flow | **DONE** | `checkoutCustomerCart` → PayIntent | |
| K3 | Address step | **DONE** | Dispatch → address chips + manage | |
| K4 | Shipping types → GTR modes | **DONE** | Click & collect / Nationwide | Not fake 4 carriers |
| K5 | Place order + GTR pay | **DONE** | ContiPay / Paynow / EcoCash list | |

### Address + Maps (§4.12)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| AD1 | Address list | **DONE** | `AddressScreen` + `listOwnAddresses` | |
| AD2 | Upsert/delete RPCs | **DONE** | `SupabaseRpcClient` + Fake | |
| AD3 | Map pick | **DONE** | `AddressPickMap` / maps-nav | Key via `BuildConfig` / local.properties |
| AD4 | Location permission | **PARTIAL** | Maps bridge path | Runtime/privacy as bridge provides — not re-audited here |

### Payment (§4.13)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| PM1 | GTR PSP UI | **DONE** | `ShopPaymentMethodList` | No PayPal/Apple/Google stubs |
| PM2 | No KMP fake pay | **DONE** | Intent RPCs | |

### Profile hub extras (§4.14–4.15)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| PR1 | Profile hub | **PARTIAL** | Account overlay hub | Reviews/Wallet/Help removed by design (Settings tab) |
| PR2 | Edit profile | **MISSING** | — | Thin / absent |
| PR3 | My Orders | **DONE** | `OrdersScreen` | |
| PR4 | Coupons | **PARTIAL** | Honest empty | No fake coupons |
| PR5 | Wallet / loyalty | **MISSING** | Removed from hub; no `get_loyalty_balance` UI | P2 |
| PR6 | Notifications | **PARTIAL** | Honest empty + settings push pref | No FCM / inbox RPC |
| PR7 | Settings + logout | **DONE** | `SettingsHubScreen` tab | Includes theme + legal links |
| PR8 | Help Center | **PARTIAL** | Settings “Legal & help” external URLs | Not dead stub; not rich FAQ |
| G1 | Live chat | **DONE** | Account → Chat | |
| G2 | Compare | **DONE** | Account → Compare | |
| G3 | Delivery track | **DONE** | Hub + orders track | |
| G4 | Garage / VIN | **DONE** | My Garage tab | |

### Cross-cutting tech (§4.16) — selected

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| T1 | Dark mode | **DIVERGED** | Customer `ThemeMode` Light/Dark/System | Plan said Defer; prefs shipped early |
| T2 | Fonts GTR not Lato | **DONE** | `res/font` Titillium / Source Sans 3 | |
| T3 | Coil images | **MISSING** / **PARTIAL** | No Coil usage in customer catalog PDP | Placeholders only |
| T6 | Koin | **DEFERRED** | Plan | Manual/factory DI |
| T7 | Ktor SoR | **DEFERRED** (reject) | Supabase clients only | |
| T12–T15 | KMP fakes / Laravel / external | **DEFERRED** (reject) | — | |
| T16 | Bridge-First hardware | **DONE** | maps-nav, pod-camera, qr, signature | |

### Management & delivery (§4.17)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| M1 | Staff hub ShopKit | **DONE** | `ShopStaffScreen` + tiles in management `MainActivity` | Not cards-on-old-hub |
| M2 | POS ShopKit two-pane | **DONE** | `PosScreen` `ShopStaffPanel` + QR bridge + SQLCipher | Behavior preserved (static evidence) |
| M3 | Delivery ShopKit rebuild | **DONE** | Delivery `ShopTheme` Standard + `ShopBottomBar`; no `ShopStaff*` on jobs | |
| M4 | Live Maps guidance | **DONE** (behavior) | `DeliveryRouteMap` in `JobsScreen` | Bridge maps-nav |
| M5 | Touch signature POD | **DONE** (behavior) | `PodScreen` + `CanvasPodSignatureBridge` | |

### GTR backend gaps (§5.1 P2+) on mobile

| Capability | Status | Notes |
| --- | --- | --- |
| Diagram canvas | **MISSING** | P2 |
| Kits | **MISSING** | P2 (menu labels only) |
| Loyalty | **MISSING** | P2 |
| Returns (quarantine CN) | **MISSING** | P2 |
| B2B procurement | **DEFERRED** | Web-only Later |

### Web ← KMP (§14)

| # | Item | Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| WKH1 | Banner carousel | **DONE** | `home-banner-carousel.tsx` | |
| WKH2 | Flash / countdown | **PARTIAL** | `flash-sale-panel.tsx` honest empty | Live countdown deferred |
| WKH3 | Merch rails | **DONE** | `home-merch.tsx` + `listHomeMerchRails` | Top movers = qty proxy (honest) |
| WKH4–5 | Filter / sort UX | **DONE** | Catalog + search components | Search price filter progressive via catalog |
| WKH6 | Richer PDP gallery | **PARTIAL** | Plan log: gallery tabs | Asset-dependent |
| WKH7 | Address map polish | **DEFERRED** | Plan §14.3 | CRUD exists |
| WKH8 | Pay chrome polish | **DEFERRED** | Optional | PSPs correct |
| WKH9–10 | Coupons / notifications | **PARTIAL** | Honest empty pages | |
| WKH11 | Profile hub IA | **DONE** | Account nav + orders tabs | |
| WKH12 | Checkout step clarity | **DEFERRED** | Optional follow-up | |

### Clean-slate phases (A–E) — process vs code

| Phase | Plan claim | Audit read | Caveat |
| --- | --- | --- | --- |
| 0 Reference + MIT | DONE | `reference/shopping-by-kmp/` + `NOTICE` present | |
| A ShopKit freeze | FROZEN / DONE | Shop files + fonts + logo + README + 7 previews | README still says “FREEZE PENDING” — **stale doc** |
| B Customer | GATE CLOSED | Shop* shell + features | Post-gate **IA fine-tunes** + orphans alter “matrix DONE” |
| C Management | GATE CLOSED | ShopStaff across hub/POS/modules | Sec WARNs deferred |
| D Delivery | GATE CLOSED | ShopKit + maps + sig | Sec WARNs deferred |
| E iOS | GATE CLOSED | 5-tab + ShopKit SwiftUI + addresses | **Build skip**; sec WARNs deferred |

---

## Customer deep-dive

### What matches KMP / ShopKit

- Design system: `ShopTheme`, `ShopDefaultScreen`, `ShopBottomBar`, product cards, rails, splash, pay list, honest empties.
- Commerce path: Home typeahead (`search_catalog`) → PDP (fitment, core charge, sticky ATC, wishlist) → Cart (delivery modes, address, USD/ZiG, GTR PSPs) → PayIntent → Orders / track.
- Auth: sign-in / sign-up / forgot password; social honest-hidden.
- Addresses + map pick Bridge-First.
- GTR extras retained: garage tab, compare, chat, track.

### Intentional divergences (fine-tunes)

| Plan (Phase B / N2) | Now | Verdict |
| --- | --- | --- |
| 4-tab Home / Wishlist / Cart / Profile | 5-tab Home / Shop / Wishlist / My Garage / Settings; Cart+Account+Sign-in top strip | **DIVERGED** — GSF-style IA, GTR brand |
| Cart as tab | Cart overlay from top bar | **DIVERGED** |
| KMP circle top chrome everywhere | Labeled flat top icons (bottom-nav pattern) | **DIVERGED** (fine-tune) |
| Category → PLP | Category / subcategory → empty inventory dialog | **PARTIAL** WIP — honest, not live stock |

### WIP / honesty items (do not oversell)

1. **Hamburger** — full tree + icons; Deals/About/Contact/Store locator and category leaves often empty or static copy.
2. **Categories grid** — UI complete; tap shows “No items added yet…”.
3. **Home category chips** — empty dialog, not filtered rails.
4. **Flash / coupons / notifications** — honest empties.
5. **Meili** — deferred; `InlineCatalogSearch` documents `search_catalog` only.
6. **PDP gallery** — numbered placeholder slots, not loaded imagery.
7. **Reviews** — feature module + Bridge photo path exist; **no `MainActivity` composition** after hub cleanup (“reviews live on PDP” copy on iOS, but Android PDP only shows stats text — no submit UI route).

### Search posture

- **DONE:** debounced four-way `search_catalog` typeahead on Home/Shop.
- **NOT DONE:** KMP FilterDialog / SortDialog consumption; dedicated paginated SearchResults page (explicitly discarded in `CatalogScreen` KDoc).

---

## Management brief

- **Visual:** Hub and staff modules on `ShopStaffScreen` / `ShopStaffPanel`; POS catalog uses `ShopProductCard`, cart `ShopListCard` / order boxes.
- **Behavior preserved (static):** two-pane POS, `SqlCipherOfflinePosStore`, Lock Task / Device Admin console, `QrScannerBridge` (Bridge-First).
- **Not claimed here:** assemble re-run, PosViewModel split (explicitly not done), resolution of sec WARNs (offline PII retention; `module_access` fail-open).

## Delivery brief

- **Visual:** Standard `ShopTheme` + `ShopBottomBar` Jobs/Route/Me; `ShopOrderBox` / `ShopDefaultScreen`; POD step chrome — not staff compact.
- **MUST behavior:** `DeliveryRouteMap` (Directions polyline / multi-stop / turn-by-turn intent); Canvas + `PodSignatureCaptureActivity` → Storage → `submit_delivery_pod`.
- **Deferred WARNs:** `allowBackup`, plaintext queues, unused `CALL_PHONE`, fake-path test skip.

## iOS brief

- **IA parity with Android fine-tune:** 5-tab + `CustomerShellTopBar` + hamburger + `CategoriesGridScreen` empty dialog.
- **ShopKit SwiftUI** under `Theme/ShopKit.swift`; features migrated off steel brand bar shells.
- **Addresses:** Fake + `LiveStorefrontApi` upsert/delete + MapKit pick present.
- **Gaps:** Reviews screen file exists but Account hub removed reviews; **no xcodebuild proof** on this host; JWT UserDefaults / refresh / demo-track-token WARNs open.

## ShopKit (`packages/android-ui`) brief

| Check | Status |
| --- | --- |
| KMP-shaped files (`ShopScaffold`, `ShopNavChrome`, `ShopCards`, `ShopLists`, `ShopPdp`, `ShopControls`, `ShopSplash`, `ShopKitStaff`, `ShopTheme`) | Present |
| Fonts (6) + `gtr_logo` | Present under `res/` |
| Preview harness (7) | `ShopKitPreviews.kt` |
| MIT / NOTICE | Present |
| Public `ShopFilterDialog` / `ShopSortDialog` | **Library only** — apps do not call |
| README freeze line | **Stale** (“FREEZE PENDING”) vs plan FROZEN |
| `Gtr*` aliases | Deprecated; apps clean of scaffold call sites; alias **removal** still deferred |

## Web ShopKit-bridge brief

§14 first pass largely **DONE/PARTIAL** per plan implementation log and tree (`HomeBannerCarousel`, `FlashSalePanel`, rails, catalog filter/sort, account coupons/notifications empties). Map polish / pay chrome / checkout step redesign remain **DEFERRED** optional. Not part of mobile clean-slate A–E.

---

## Ordered gap list

### Must-fix / must-clarify before treating as ship-ready (before next assemble gate)

1. **Re-wire or delete orphan reviews UX** — `feature/reviews` on classpath but not hosted; PDP lacks add/list UI. Either compose from PDP / Account or drop dependency claim.
2. **Do not assemble under false “§4 complete” banner** — filter/sort unused; category PLP empty; PDP gallery fake; Phase 2 gaps open.
3. **iOS macOS `xcodebuild` confirm** — Phase E was PASS WITH BUILD SKIP; required before claiming iOS assemble-green.
4. **Refresh stale ShopKit README freeze status** — avoids process/code mismatch in audits.
5. **Release sec WARN triage** (not compile blockers, but pre-release): `allowBackup`, management offline PII / fail-open roles, delivery plaintext queues, iOS Keychain/JWT.

### Later (product / Phase 2+ / optional)

1. Wire `ShopFilterDialog` / `ShopSortDialog` into customer (and optionally Shop) browse when RPC supports.
2. Replace PDP placeholder gallery with Coil + OEM/diagram assets; expand description beyond name.
3. Category taxonomy → live `search_catalog` / browse PLP (retire empty-only dialogs where stock exists).
4. Meili (if product still wants) — currently deferred by design.
5. Loyalty wallet, returns CN, kits, diagram canvas (P2).
6. Edit profile; richer Help; notifications inbox when backend exists.
7. Live flash deals feed (no fake timers until then).
8. Web WKH7/8/12 polish; true “most sale” velocity feed.
9. Remove deprecated `Gtr*` aliases from frozen android-ui (post all consumers).
10. Phase N: CMP shared UI, dark-mode brand decision vs current prefs, Koin, i18n.

---

## What NOT to claim as DONE

Do **not** claim any of the following as complete:

- Parent plan **§4 matrix 100% Adopt rows** — filter/sort, image gallery, edit profile, loyalty, reviews navigation, category→PLP are not DONE.
- **“4-tab KMP shell shipped as designed”** — replaced by **5-tab GSF-style** shell (DIVERGED fine-tune).
- **Meili search** — deferred; `search_catalog` typeahead only.
- **Live category inventory / hamburger leaf catalogs** — empty dialogs are WIP honesty, not stocked PLP.
- **PDP multi-image gallery with real assets** — placeholders only.
- **Customer reviews end-to-end in the shell** — module orphaned from navigation.
- **iOS build-verified** — static/gate only; no `xcodebuild` on Windows audit host.
- **Flash deals / coupons / notifications / wallet as live product** — honest empties only.
- **CMP shared UI / TV / Desktop / Automotive** — deferred Phase N.
- **KMP `webApp` runtime** — rejected; web is Next.js §14 adapt only.
- **Clean-slate “complete” = parent ecommerce matrix complete** — A–E means ShopKit visual adoption gates, not every §4/§5 capability.
- **`packages/android-ui` README as freeze SoT** — text lags manager freeze.

---

## Audit limits

- No `./gradlew` / `xcodebuild` / Playwright runs this pass (per instructions).
- Behavior preservation for POS offline / kiosk / Maps / signature inferred from imports and structure, not runtime tests.
- Reference clone presence verified (`reference/shopping-by-kmp/` = true); deep KMP file-by-file diff vs ShopKit not re-run.

---

*End of audit.*
