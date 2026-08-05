# `:android-ui` — ShopKit design system

Shared Jetpack Compose UI kit forked from **Shopping-By-KMP**
(MIT © 2023 Mahdi Razzaghi Ghaleh —
<https://github.com/razaghimahdi/Shopping-By-KMP>) and branded with GTR tokens.

See repo-root `NOTICE`. License text: `reference/shopping-by-kmp/LICENSE`
(gitignored shallow clone).

**Presentation only** — no Supabase / RPC / networking.

## Phase A status (2026-08-05)

ShopKit foundation is a **real KMP fork** of presentation chrome (not
`Gtr*` renames). Status: **CODE-COMPLETE — FREEZE PENDING** (await `/verifier`
compile-green + manager freeze). Public `Shop*` names held stable.

**Preview harness (debug):** `src/debug/java/.../shop/ShopKitPreviews.kt` —
home rails, PDP, cart, lists, splash, bottom nav (realistic OEM samples).

| File | KMP source (adapted) | Symbols |
|------|----------------------|---------|
| `shop/ShopTheme.kt` | `theme/Theme.kt` entry | `ShopTheme` |
| `shop/ShopScaffold.kt` | `component/DefaultScreenUI.kt` | `ShopTopBar`, `ShopDefaultScreen`, `ShopTabBody`, `ShopFailedNetworkScreen`, `ShopIconAction` |
| `shop/ShopNavChrome.kt` | `ui/main/MainNav.kt` BottomNavigationUI | `ShopBottomBar`, `ShopBottomTab`, `shopNavigationBarItemColors` |
| `shop/ShopCards.kt` | `component/ProductBox.kt`, OrderBox | `ShopProductCard`, `ShopListCard`, `ShopOrderBox`, `ShopStatusChip`, `ShopCircleBadge` |
| `shop/ShopLists.kt` | HomeScreen CategoryBox/BannerImage, ProfileItemBox | rails, banners, profile rows, empties, step/presence |
| `shop/ShopPdp.kt` | detail + ExpandingText | `ShopStickyCtaBar`, `ShopExpandableDescription`, `ShopRatingRow` |
| `shop/ShopControls.kt` | `component/Buttons.kt`, Filter/Sort dialogs | circle/primary buttons, search, location, address, filter/sort, GTR pay list |
| `shop/ShopSplash.kt` | `ui/splash/SplashScreen.kt` | `ShopSplash` (expanding circle; no CMP fashion assets) |
| `shop/ShopKitStaff.kt` | DefaultScreenUI adapted for staff | `ShopStaffToolbar`, `ShopStaffScreen`, … |
| `theme/*` | Color/Type/Shape → GTR hex + OFL fonts + KMP shape scale | `GtrTheme`, `GtrColors`, `GtrTypography`, `GtrShapes`, `GtrLogo` |

### Honest adaptations (not literal CMP copy)

- No `org.jetbrains.compose.resources` / Coil3 / Ktor domain types — Android
  Compose + host `imageSlot` / callbacks.
- No KMP `ProgressBarState` / `UIComponent` queue — optional `loading` /
  `networkFailed` flags on `ShopDefaultScreen`.
- Splash: expanding primary circle + GTR logo (no shoe/watch pager assets).
- Payments: ContiPay / Paynow / EcoCash only (never KMP PayPal/Apple/Google stubs).

## Entry

```kotlin
ShopTheme {
    ShopDefaultScreen(title = "Cart") { /* Shop* content */ }
}
```

## Legacy `Gtr*`

`GtrBrandBar`, `GtrScreen`, `GtrFeatureBody`, `GtrSectionLabel` are
`@Deprecated` aliases → ShopKit. `GtrScaffold` is `@Deprecated(ERROR)`.
Do not add new `Gtr*` scaffolds. Remove aliases after B–D migrate.

## Resources

- `res/font/titillium_web_{regular,semibold,bold}.ttf`
- `res/font/source_sans_3_{regular,semibold,bold}.ttf`
- `res/drawable/gtr_logo.png`
- OFL notes: `packages/ui/fonts/ATTRIBUTION.md`

## Build

```bash
cd apps/android-customer && ./gradlew :android-ui:compileDebugKotlin
```
