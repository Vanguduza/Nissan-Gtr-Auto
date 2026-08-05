# GSF Car Parts APK — UX & Behaviour Specification

- **Date:** 2026-08-04
- **Lane:** `@web_agent` / `@android_agent` / `@ios_agent` (consumers); `@hardware_mobile_agent` for any bridge implications
- **Status:** Reference spec — informs a future redesign task, not itself a redesign
- **Source:** Static reverse-engineering of `catalog/GSF_Car_Partscom.GSFCarParts.androidv1.1.apk` (jadx + apktool decompile, isolated under `reference/gsf-apk/`) + 6 user-provided screenshots (5 live GSF app, 1 unrelated EPOS marketing image)
- **Related:** `2026-07-23-autodoc-shop-features.md` (existing "no UK plate lookup" decision — **do not contradict**), `2026-08-03-mobile-ui-oss-discovery.md` (Jetsnack/Reply OSS base)

## Legal / scope note

This document describes **behaviour and information architecture only**. No GSF strings, logos, icon assets, brand colours, or decompiled code are reused or copied into `apps/android-customer`, `apps/ios`, or `apps/android-management`. The decompiled output lives in the gitignored `reference/gsf-apk/` tree for analyst reference only and must never be merged into production modules.

---

## 0. Tooling substitution note

The plan's §12 workflow specifies **APKLab** (a VS Code/Cursor marketplace extension). A background agent cannot install IDE extensions, so this analysis used the CLI tools APKLab wraps instead:

- **jadx v1.5.6** (Apache-2.0, `github.com/skylot/jadx`) — Java decompiler
- **apktool v3.0.3** (Apache-2.0, `github.com/iBotPeaches/Apktool`) — resource/manifest/smali decoder
- **JDK:** OpenJDK 17.0.19 (Temurin) — already present on the host, confirmed via `java -version`

Both tools were downloaded fresh from their GitHub Releases pages into `reference/tools/` (gitignored, not committed).

**Run results:**
- **apktool: succeeded** — full resource decode (`AndroidManifest.xml`, `res/`, `apktool.yml`) completed cleanly.
- **jadx: partial** — decompiled ~7,200 of 21,563 classes (33%) before hitting `OutOfMemoryError: Java heap space` on the host's constrained JVM heap. This is sufficient: it captured the app's own `com.slide.*` and `com.GSFCarParts.android` packages plus all major third-party library sources (androidx, kotlinx, Firebase, Facebook, OneSignal, Google Play Services). No further retry was attempted because a second finding (below) made full decompilation unnecessary for this task's goals, and the host is under severe disk pressure (see next note).

**Disk-space note (environment finding, not a task output):** the host `C:` drive had only **~0.6 GB free** at the start of this task (later ~1.1 GB after cleanup) out of 231 GB total — a pre-existing, severe constraint unrelated to this task. To avoid worsening it, the smali output (~294 MB across 6 `smali*` folders) was deleted after inspection since resource/manifest analysis only needed `res/` (2.7 MB) and the manifest; the redundant jadx zip download was also deleted. **The host is nearly out of disk space independent of this task — flagging for the user/owner to investigate before other agents run large builds (Gradle, Xcode, Docker) on this machine.**

---

## 1. Architecture finding (most important technical observation)

The GSF Car Parts Android app is **not a native Compose/View app with a REST/GraphQL backend** — it is a **configuration-driven WebView wrapper** built on **MobiLoud "Slide"**, a commercial website-to-app SaaS product. Evidence from the manifest and decompiled sources:

- Package: `com.GSFCarParts.android`; core UI package: `com.slide.*` (not GSF's own code)
- `com.slide.config.entities.ConfGeneral` has a literal field `Powered_By_MobiLoud` and a default `contactEmail` of `"support@mobiloud.com"`
- `com.slide.config.entities.ConfGeneral` also carries `Main_Page_URL`, `Remote_Server_Root_URL`, `Shopify_Bypass_Bot_Protection` — the "app" is a set of WebView tabs pointed at the mobile website, with native chrome layered on top
- `com.slide.config.entities.ConfTab` / `ConfNav` define a **generic, remotely-configurable tab bar** (`tab_bar.xml` has view-stub slots `home / second / third / fourth / alerts / linking`, capped at 5 items by `ConfTab.validateFields()`) and WebView behaviour flags (pull-to-refresh, external-link handling, deep-link injection, cookie persistence, offline caching)
- `com.slide.webview.URLInjector`, `ConfCodeInjecting`, `ConfDeepLinkingInjection` confirm JS/URL injection into the wrapped web pages rather than native screen composition
- Launcher activity is `com.slide.ui.activities.LauncherActivity`; `MainActivity`, `LoginActivity`, `LinkActivity`, `PushDetailsActivity` are all generic Slide-framework activities, not GSF-specific classes

**Implication for us:** GSF's "native-feeling" screens in the screenshots (category chips, vehicle card, deals grid) are their **mobile website rendered in a WebView**, not native platform UI. This means:
1. The visual/IA patterns are still valid and worth studying (a real commerce team designed that mobile-web layout), but
2. There is no native "search flow" or "cart flow" code to reverse-engineer — that logic lives server-side/client-JS on gsfcarparts.com, outside this APK's reach, and
3. **We are already ahead architecturally** — `apps/android-customer` and `apps/ios` are true native Compose/SwiftUI apps against a Supabase RPC backend, not a WebView shell. We should adopt GSF's *layout decisions*, not its *technical approach*.

Per Adopt-First policy: MobiLoud/Slide is a proprietary "Buy" product family, not something to integrate, fork, or emulate architecturally — noted here only because it explains why the screenshots look the way they do.

## 2. Manifest, permissions, and third-party SDK inventory

From `reference/gsf-apk/apktool-output/AndroidManifest.xml`:

**Permissions requested:** `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `READ/WRITE_EXTERNAL_STORAGE`, `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `ACCESS_COARSE/FINE_LOCATION`, `CALL_PHONE`, `SET_ALARM`, `WAKE_LOCK`, `VIBRATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`, plus badge/launcher-icon vendor permissions (Samsung/HTC/Sony/Huawei/Oppo) and Play `ACCESS_ADSERVICES_*` topics/attribution permissions.

**Activities/entry points:** `LauncherActivity` (MAIN/LAUNCHER + `https://www.gsfcarparts.com` app-link, `autoVerify="true"`), `MainActivity`, `LoginActivity`, `LinkActivity` (exported, handles deep links), `PushDetailsActivity` (push-tap landing).

**Third-party SDKs detected:** Firebase (Analytics, Crashlytics, Performance, Messaging, RemoteConfig, Installations, Abt, DataTransport), Facebook SDK (Login, App Events, Custom Tabs), **OneSignal *and* Klaviyo** push (both present — likely a migration in progress), Huawei HMS (Push, AAID, AGConnect — for Huawei devices without GMS), Adjust SDK (marketing attribution), Google Play Core (in-app update dialog), AndroidX WorkManager/Room, **Realm** (local object DB, `io.realm.*` — used for push-tag/notification caching, not catalog data), **ZXing** (`com.google.zxing` barcode/QR library — present but likely for scanning a receipt/loyalty barcode, not core navigation, since the app is WebView-based), and **Pairip** (commercial app-protection/anti-tamper wrapper — explains obfuscated `com.pairip.application.Application` as the manifest's `<application>` class).

**Deep links:** `https://www.gsfcarparts.com` verified app-link on the launcher activity; a `gtrcustomer://`-style custom scheme is not present (GSF relies on the verified HTTPS app-link + `LinkActivity`, consistent with a WebView wrapper needing to intercept in-site navigation).

None of the above should be adopted as architecture (we already use Supabase + Bridge-First native modules); they are recorded only as an intelligence baseline should the team ever need to compare against a competitor's stack.

## 3. Screen-by-screen behaviour spec

### 3.1 Bottom navigation — 5 tabs vs our 3 tabs

Screenshots show a **persistent 5-tab bottom bar**: **Home · Shop · Deals · Account · Settings** (icons: house, shopping basket, price-tag, person, gear). This matches the generic Slide tab-bar capacity (max 5 configurable slots).

Our current shells:

```17:33:apps\ios\GTRCustomer\ContentView.swift
    private var mainTabs: some View {
        TabView {
            NavigationStack {
                CatalogScreen(initialOem: $pendingPartsOem)
            }
            .tabItem { Label("Shop", systemImage: "magnifyingglass") }

            NavigationStack {
                CartScreen()
            }
            .tabItem { Label("Cart", systemImage: "cart") }

            NavigationStack {
                AccountHubScreen()
            }
            .tabItem { Label("Account", systemImage: "person.crop.circle") }
        }
```

```73:78:apps\android-customer\app\src\main\java\co\zw\nissangtr\customer\MainActivity.kt
private enum class ShellTab(val label: String, val icon: ImageVector) {
    Shop("Shop", Icons.Filled.Home),
    Cart("Cart", Icons.Filled.ShoppingCart),
    Account("Account", Icons.Filled.AccountCircle),
}
```

Both apps use **3 tabs: Shop / Cart / Account**, with Deals, Settings, Orders, Garage, Wishlist, Compare, Pay, Chat, and Track all nested under **Account** as a hub-and-list pattern (`AccountHub` → `AccountRow` rows).

**Recommendation: keep 3-tab + relocate, do not adopt 5-tab.**

- Cart is a first-class, frequent action for a parts storefront (quick re-check of core-charge lines, quantities, currency) — demoting it into a drawer/hub the way GSF does (no visible Cart tab; only a bag icon in the top bar) works for a general retailer with fewer basket revisits, but is worse for our higher-friction B2C+B2B parts flow. Keep Cart as a tab.
- **Deals is worth promoting out of the Account hub** — GSF's dedicated Deals tab with a hero carousel is a legitimate merchandising win (promo visibility currently: zero, in our shells — there's no promo/banner surface at all today). Recommend adding a **Deals/Promotions entry point on the Shop tab's home** (a promo carousel row, per §3.2) rather than a 5th bottom-bar slot, to avoid bottom-bar crowding on both phone form factors.
- **Settings does not need a tab** — our "Settings" surface area (sign-out, theme, notification prefs) is thin; it stays as an Account-hub row, consistent with our existing `AccountHub` pattern (see `AccountRow` list) and cheaper to maintain across iOS/Android than a 5th tab.
- **Net change:** stay at 3 tabs (Shop / Cart / Account); add a Deals/promo *section* inside Shop-home; keep Settings inside Account. This is additive to the existing shell, not a rebuild.

### 3.2 Home screen

Screenshot evidence (`gsf-home-category-page.png`, `gsf-home-category-page-dup.png`):

- Top **promo/delivery ticker strip** above the app bar (rotates: "Klarna — Pay in 30 Days" / "Free Delivery for orders above £25" / "Free Click & Collect for all orders")
- App bar row: hamburger (☰) — logo (centred) — location pin icon — account icon — cart icon with red badge count
- Full-width **search bar** directly under the app bar (placeholder text changes contextually — see §3.3)
- **Horizontal quick-category chip row**: Car Parts / Accessories / Detailing / Tools / Service Kits (pill-shaped, active chip underlined)
- Breadcrumb row (Home > Car Parts) + short category description copy
- **"Your Current Vehicle" card**: white card, "Your Current Vehicle" label, large yellow UK-plate-styled **"ENTER REG"** button, "Find Parts" caption link, an "Or" divider, then "Select Your Vehicle" row with a chevron-down (manual fallback)
- Promotional banner strip (single wide image banner, e.g. "Save 25% on Car Parts")
- **"Popular Part Types"** section: horizontally-scrolling image tiles (product photo + label, e.g. Oil Filter / Air Filter / Car Battery), red top-accent border on each tile

### 3.3 Search / vehicle-identification screen

Screenshot evidence (`gsf-search-oe-registration.png`):

- Two-tab segmented control above the reg/model card: **"By Registration"** / **"By Model"**
- Under "By Registration": same yellow UK-plate "ENTER REG" field + a **green "Find Parts"** primary CTA button, with helper text "Enter your Number Plate above to find Parts."
- Search bar placeholder text is **contextual**: home shows a blank/generic search bar; this screen shows **"Search by OE number"** (with "OE number" highlighted in red); another state (`gsf-deals-spotlight-grid.png`'s header) shows **"Search by Part number"** — i.e. the same search input cycles through placeholder hints (OE number → part number → registration) rather than having separate search fields.
- Below the fold: "You Might Also Like" horizontal product carousel (de-icer, screen wash) — cross-sell surfacing even mid-search.

**Critical cross-check against existing decision:** `docs/decisions/2026-07-23-autodoc-shop-features.md` already explicitly places **"UK plate"** and **"marketplace"** patterns in its **Skip (never)** list, and mandates instead: *"Sequential cascading selects: maker → model → generation → engine... VIN alternate path. No UK plate lookup."* This spec does **not** contradict or reopen that decision — the UK number-plate → DVLA-style reg lookup is a UK-market-specific mechanism with no Zimbabwe equivalent and must not be adopted. What **is** worth adopting is the *layout pattern*: a prominent, high-contrast, single-CTA vehicle-identification card placed above the fold on the shop home, with a manual fallback link below it — just wired to our existing VIN input + cascading maker/model/generation/engine selects (`CatalogViewModel`/`CatalogScreen`), not a plate scanner.

### 3.4 Deals tab

Screenshot evidence (`gsf-deals-spotlight-grid.png`):

- Full-bleed **hero carousel** banner (single large promo card with price/percentage-off badge, dot-indicator pagination, and a "Shop Now" CTA)
- Section header: "Spotlight Deals"
- **2-column grid of colour-coded deal tiles**, each with: coloured background block (blue/red/yellow/orange, rotating), bold headline (e.g. "MOT Season Essentials", "Hot Picks", "Free Brake Cleaner", "Save 30% on Batteries"), small supporting line, and a "Shop Now →" text-link CTA. Some tiles carry an icon badge overlay (e.g. "Hot Picks" ribbon).
- This is a classic **merchandising tile wall** — distinct promos each get their own colour identity rather than a uniform card style, which increases scannability at the cost of visual restraint.

### 3.5 Hamburger drawer

Screenshot evidence (`gsf-menu-drawer.png`):

- Slide-in panel, close (×) button top-left
- **Flat list of category rows**, each: leading line icon + label + trailing chevron (Car Parts, Accessories, Detailing, Tools — all four have chevrons implying sub-navigation; Service Kits, Engine Oils, Car Batteries, Wiper Blades, Deals, Shop By Brand, MOT — no chevron, implying direct navigation)
- A divider, then plain-text links: **Store Locator**, **About Us**, **Contact Us**
- A floating chat-bubble FAB is visible bottom-right on nearly every screen (persistent live-chat entry point)

This is a simple, low-effort IA pattern (a flat list, not nested accordions) — cheap to replicate if we ever want a secondary "all categories" surface beyond the chip row, but our current category taxonomy is already exposed via chips (`CatalogHome`'s `categories` list: Brakes, Filters, Engine, Suspension, Electrical, Cooling, Body, Drivetrain) and doesn't yet need a drawer.

### 3.6 EPOS reference image (takepayments — NOT GSF, different product)

`epos-reference-takepayments.png` shows a generic **dual-screen till** setup: a large tablet running a **colour-coded category-grid order-entry screen** (each button is a distinct flat colour block representing a menu/product category — e.g. teal "Fish & Seafood Tapas," orange, purple, blue, green swatches) docked next to a small companion **customer-facing payment terminal** showing a mirrored/simplified view with a running total.

This is a **general EPOS UX pattern**, not GSF's, and is the target reference for **`apps/android-management/feature/pos`** (tablet counter till), not the customer apps:

```148:167:apps\android-management\feature\pos\src\main\java\co\zw\nissangtr\management\pos\PosScreen.kt
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == PosWorkspaceMode.Till,
```

Our current `PosScreen` already uses a `LazyVerticalGrid` for the catalog grid and a `BoxWithConstraints` split-pane layout (grid + cart column) — structurally aligned with the reference. The gap is **visual density and colour-coding**: our POS currently renders `FilterChip`/`OutlinedButton` in Material3's default surface tones, not the flat, high-saturation, colour-per-category button blocks the EPOS reference uses for at-a-glance category recognition on a touch till. Recommend a follow-up design pass that assigns a distinct accent colour per top-level part category on the `LazyVerticalGrid` tiles in `PosScreen`, sized for thumb/stylus targets, while keeping our existing offline-first architecture (`OfflinePosSyncEngine`, `SqlCipherOfflinePosStore`) untouched.

---

## 4. Gap list — concrete next steps against our current screens

| # | Gap | Our file(s) today | GSF/EPOS pattern to adopt | Adopt as-is? |
|---|-----|--------------------|-----------------------------|---------------|
| 1 | No promo/banner surface anywhere in customer apps | `apps/android-customer/.../CatalogScreen.kt` (`CatalogHome`), `apps/ios/GTRCustomer/Features/CatalogScreen.swift` | Home promo banner strip + Deals hero carousel + 2-col spotlight tile grid | Yes — add a promo carousel + deal-tile section to Shop-home; skip literal GSF colours/copy |
| 2 | Vehicle identification is a plain text field with mode chips, not a prominent above-the-fold card | `CatalogHome` (`OutlinedTextField` + `SearchMode` `FilterChip`s), `CatalogViewModel.kt` | "Your Current Vehicle" card pattern: big single CTA + "Or select manually" fallback, placed above search results | Yes for layout/prominence — **No** for UK plate; wire to existing VIN + cascading maker/model/generation/engine selects per `2026-07-23-autodoc-shop-features.md` |
| 3 | Single generic search box with static label ("OEM, VIN, model, or PNC") | `CatalogScreen.kt:151` (`label = { Text("OEM, VIN, model, or PNC") }`) | Contextual placeholder cycling ("Search by OE number" / "Search by Part number") tied to active search mode | Yes — cheap win: swap label text based on `state.searchMode` instead of one static hint |
| 4 | Categories are plain text `AssistChip`s only | `CatalogHome` `categories` list (`Brakes, Filters, Engine, ...`) | Horizontal image-tile chips with product photography, red/brand top-accent | Partial — add imagery to chips; keep our category taxonomy (don't rename to GSF's Car Parts/Accessories/Detailing) |
| 5 | Bottom nav has no Deals/Settings destination; both buried behaviors are actually fine, but Deals has *zero* current surface | `MainActivity.kt` `ShellTab` enum, `ContentView.swift` `TabView` | 5-tab bar incl. Deals + Settings | No — keep 3-tab; surface Deals as a Shop-home section instead (see gap #1) |
| 6 | No "Popular Part Types" merchandising grid on home | `CatalogHome` (goes straight from categories to a plain `LazyColumn` "Browse" list) | Image-tile grid of popular part types under the vehicle card | Yes — add a curated/algorithmic "Popular parts" tile row before the plain browse list |
| 7 | No persistent live-chat entry point visible across screens | `ChatScreen.kt` exists but is buried in `AccountHub` as a row (`AccountRow("Live chat", ...)`) | Floating chat FAB visible on every screen | Partial — consider a small persistent chat affordance (not necessarily a FAB on every screen, but easier discovery than one hub row) |
| 8 | POS category grid uses neutral Material3 tones, not colour-coded category blocks | `PosScreen.kt` (`LazyVerticalGrid`, `FilterChip`, `OutlinedButton`) | takepayments-style flat, high-saturation colour-per-category grid tiles | Yes — accent-colour the grid tiles per category; keep existing offline sync/print/QR architecture unchanged |
| 9 | Hamburger "all categories" drawer has no equivalent — not currently a gap (chips cover it) but flag for when the taxonomy grows | N/A | Flat list + chevron drawer, Store Locator/About/Contact below divider | Defer — only build if category count outgrows the chip row |

## Top 3 concrete IA differences to adopt first (priority order)

1. **Home merchandising surfaces are completely missing.** Add a promo banner + "Popular Part Types" image-tile row + a lightweight Deals section to the Shop-home screen (`CatalogHome` in `CatalogScreen.kt`, and the SwiftUI `CatalogScreen.swift` equivalent) before anything else — this is the single biggest visual/commercial gap and requires no architecture change, only new composables/views bound to existing or new catalog RPCs.
2. **Vehicle identification needs visual prominence, not a new mechanism.** Promote the existing VIN/cascading-select flow into a dedicated, above-the-fold "Your Vehicle" card on Shop-home (large single CTA + "or search manually" fallback) instead of leaving it as one of several `SearchMode` filter chips next to a plain text field — while explicitly keeping the "no UK plate lookup" rule from `2026-07-23-autodoc-shop-features.md` intact.
3. **POS tablet grid should be colour-coded by category**, matching the takepayments EPOS reference's at-a-glance touch-till pattern, applied to the existing `LazyVerticalGrid` in `apps/android-management/feature/pos/.../PosScreen.kt` — a styling-only change on top of the current offline-first POS architecture, not a rework.

---

## Appendix: reference workspace layout

```
reference/gsf-apk/
├── original-apk/GSF_Car_Partscom.GSFCarParts.androidv1.1.apk
├── jadx-output/sources/...        (partial, 33% — OOM-limited, includes com.slide + com.GSFCarParts.android)
├── apktool-output/                (AndroidManifest.xml, res/ only — smali removed post-inspection for disk space)
├── resources/                     (reserved, empty — no extracted assets copied out per no-copy policy)
└── screenshots/
    ├── gsf-home-category-page.png
    ├── gsf-home-category-page-dup.png
    ├── gsf-search-oe-registration.png
    ├── gsf-deals-spotlight-grid.png
    ├── gsf-menu-drawer.png
    └── epos-reference-takepayments.png   (NOT GSF — general EPOS reference)
```

`reference/` is gitignored (see repo `.gitignore`) and must never be committed or merged into `apps/*` or `packages/*`.
