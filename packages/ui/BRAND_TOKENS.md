# GTR brand tokens

**Source of truth:** web storefront + staff (`apps/web`), ADR [`docs/decisions/2026-07-23-storefront-autodoc-logo.md`](../../docs/decisions/2026-07-23-storefront-autodoc-logo.md).

| Artifact | Role |
|----------|------|
| [`brand-tokens.json`](./brand-tokens.json) | Cross-platform hex / spacing / radii / font intent |
| [`src/index.ts`](./src/index.ts) | TS tokens + `tokensToCssVars()` for web |
| `packages/android-ui` | Material3 `GtrTheme` (Compose) |
| `apps/ios/GTRCustomer/Theme` | SwiftUI `GTRTheme` |

## Palette (do not invent)

| Token | Hex | Use |
|-------|-----|-----|
| primary / CTA | `#C8102E` | Buttons, active nav, accents |
| steel | `#12151C` | Headers, chrome, body ink |
| steelLift | `#1E2430` | Elevated chrome |
| silver | `#C0C5CE` | Secondary on dark |
| mist / chalk | `#E8ECF1` / `#F4F5F7` | Borders / page background |
| accent / USD | `#0B6E4F` | In-stock, USD |
| warning / ZiG | `#B45309` | Low stock, ZiG |

## Typography

| Surface | Display (chrome / H) | Body |
|---------|----------------------|------|
| Web | Titillium Web (next/font) | Source Sans 3 |
| Android | Titillium Web (`packages/android-ui` `res/font`) | Source Sans 3 |
| iOS | Titillium Web (`GTRCustomer/Fonts` + `UIAppFonts`) | Source Sans 3 |

Both faces are **SIL OFL 1.1** — attribution + license texts: [`fonts/ATTRIBUTION.md`](./fonts/ATTRIBUTION.md).

**Bundled weights (2026-08-03, disk-checked):** Regular / SemiBold / Bold for each family.

| Role | Paths |
|------|--------|
| Canonical OFL + TTF | `packages/ui/fonts/TitilliumWeb-{Regular,SemiBold,Bold}.ttf`, `SourceSans3-{Regular,SemiBold,Bold}.ttf`, `OFL-*.txt`, `ATTRIBUTION.md` |
| Android `res/font` | `packages/android-ui/src/main/res/font/{titillium_web,source_sans_3}_{regular,semibold,bold}.ttf` → `GtrTypography` (`GtrDisplayFont` / `GtrBodyFont`) |
| iOS Fonts | `apps/ios/GTRCustomer/Fonts/*.ttf` + `Info.plist` `UIAppFonts` + `project.pbxproj` Resources → `GTRType` |

Themes: Android `GtrTheme` (`packages/android-ui/.../GtrTheme.kt`); iOS `.gtrTheme()` (`apps/ios/GTRCustomer/Theme/GTRTheme.swift`).

### Logo

| Surface | Path |
|---------|------|
| Web (canonical) | `apps/web/public/brand/logo.png` |
| Android (shared) | `packages/android-ui/src/main/res/drawable/gtr_logo.png` → `GtrLogo` / `GtrBrandBar` |
| iOS | `apps/ios/GTRCustomer/Assets.xcassets/BrandLogo.imageset/logo.png` → `GTRLogo` / `GTRBrandBar` |

Canonical PNG is byte-identical across web → Android drawable → iOS BrandLogo (SHA-256 verified 2026-08-03).

## Radii

- Storefront controls / chips: **2dp** (`radius.sharp`)
- Header icon buttons: **8dp** (`radius.control`)
- Staff cards / nav: **10dp** (`radius.staff`)

## Mobile wiring

1. Wrap each Android `setContent` in `GtrTheme { … }` (`co.zw.nissangtr.ui.theme`).
2. Wrap iOS root in `.gtrTheme()` / apply `GTRTheme.tint`.
3. Prefer `MaterialTheme.colorScheme` / `GTRColors` — no one-off purple seeds.

## Verify side-by-side

1. Web: `cd apps/web && pnpm dev` — home, `/account`, staff hub, POS, HR.
2. Android customer / management / delivery: `assembleDebug`, open login + hub/home + catalog/POS/HR.
3. iOS: run GTRCustomer — Sign in + tabs.
4. Check CTA red, chalk page ground, steel header ink, no Material purple.
