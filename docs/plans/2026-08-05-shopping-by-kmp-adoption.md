# Shopping-By-KMP adoption (discovery + decision)

> **SUPERSEDED** by the approval-ready plan: [`docs/plans/2026-08-05-shopping-by-kmp-full-adoption-plan.md`](./2026-08-05-shopping-by-kmp-full-adoption-plan.md).  
> Keep this file only as a short discovery note; reply Approve / Approve with changes / Reject on the **full** plan.

**Date:** 2026-08-05  
**OSS:** [razaghimahdi/Shopping-By-KMP](https://github.com/razaghimahdi/Shopping-By-KMP)  
**License:** **MIT** (verified by reading `reference/shopping-by-kmp/LICENSE` — Copyright 2023 Mahdi Razzaghi Ghaleh). Attribution: `NOTICE` + this plan.  
**Clone:** shallow clone at `reference/shopping-by-kmp/` (gitignored via `reference/`).

## Decision: **Fork** (presentation) + **Integrate** (data)

| Option | Verdict |
|--------|---------|
| **Integrate** as dependency submodule | Rejected — Shopping-By-KMP is a full Compose Multiplatform demo with its own Ktor backend, fake services, and multi-target apps (TV/Desktop/Web). Not a library. |
| **Full CMP shared module** (`packages/kmp-storefront`) for Android + iOS UI | Deferred — viable later, but requires CMP toolchain, Compose for iOS, and replacing/parallelizing existing SwiftUI `apps/ios`. Disk/tooling cost is high; iOS already ships LiveStorefrontApi. |
| **Fork** UI architecture into our tree | **Chosen** — copy/adapt screen IA, home merchandising, product cards, PDP sticky CTA, cart/address map pattern, bottom nav, and staff-dense siblings into `packages/android-ui` + app feature modules. Keep Supabase `RpcClient` / iOS `LiveStorefrontApi` as SoR. |

**Do not** rebase SoR onto Odoo/ERPNext. **Do not** copy GSF proprietary assets. Shopping-By-KMP is the shopping UX base; GSF remains layout/IA reference only.

## Module map (OSS → GTR)

| Shopping-By-KMP | GTR landing |
|-----------------|-------------|
| `shared/.../presentation/theme` | Replaced by `packages/android-ui` GTR tokens (red `#C8102E` / steel / chalk / Titillium) — **not** OSS PrimaryColor `#FF4747` |
| `presentation/component` (ProductBox, DefaultScreenUI, …) | Forked as `packages/android-ui/.../shop/ShopKit.kt` |
| `presentation/ui/main/home` | `apps/android-customer` catalog home |
| `presentation/ui/main/detail` | Customer PDP (OEM, fitment, VIN/garage hooks) |
| `presentation/ui/main/cart` + `add_address` + `MapComponent` | Cart fulfillment + address picker (`GOOGLE_MAPS_API_KEY` from local.properties; never commit secrets) |
| Bottom nav Home / Wishlist / Cart / Profile | Customer shell tabs (same four) |
| Ktor / fake MainService | **Replaced** by existing `core/rpc` Supabase RPCs |
| Camera / gallery in shared | **Not copied** — Bridge-First (`bridges/`) for review/POD/QR/GPS |
| `apps/android-management` | Same ShopKit visual language for hub tiles + POS catalog cards; **preserve** POS two-pane, offline SQLCipher, kiosk Lock Task, Device Admin, role routing |
| `apps/android-delivery` | Jobs-first shell using Compact density + ShopKit scaffolds (not shopper merchandising) |
| `apps/ios` | SwiftUI IA/port matching Home→Wishlist→Cart→Account; shared domain stays native LiveStorefrontApi until CMP phase |

## Hard exclusions

- No ZIMRA / FDMS / fiscalisation  
- No payroll tax  
- Bridge-First for camera, QR, printer, GPS  
- No dual SoR  

## What we copy vs replace

| Copy / adapt | Replace with GTR |
|--------------|------------------|
| Home: location row, search bar, banner pager pattern, category row, horizontal product rails, section “see all” | Brand colors, fonts, copy; garage/VIN instead of generic “location” as sole context |
| PDP: hero, sticky add-to-cart, wishlist heart, comments/reviews strip | OEM, fitment lines, core charge, stock label; reviews via existing RPCs |
| Address map pick before checkout details | BuildConfig maps key; dispatch address for Nationwide; Click & collect unchanged |
| Product card (image + title + price + like) | Parts card: OEM + name + USD + wishlist; placeholder art until image pipeline |
| Staff: same chrome density / section labels | Hub modules + POS browse richness |

## Phased delivery

1. **Android customer (flagship)** — ShopKit + 4-tab shell + rich home/PDP/cart address  
2. **Android management** — hub tiles + POS product cards; kiosk/POS/offline intact  
3. **Android delivery** — jobs-first Compact shell  
4. **iOS** — IA/port to match; CMP shared UI = Later  

## Honesty

Visual parity with Shopping-By-KMP screenshots is **aspirational until QA on device**. We claim architecture + branded fork, not pixel parity. No GSF screenshot parity claims.
