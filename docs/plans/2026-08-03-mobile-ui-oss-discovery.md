# Discovery: Mobile UI redesign base (Adopt-First)

**Date:** 2026-08-03  
**Ask:** Redesign all mobile apps to match `apps/web` storefront + staff UX.  
**Policy:** OSS Integrate → Fork → Build; prefer MIT / Apache-2.0 / BSD; flag AGPL/GPL.  
**Hard stops:** No ZIMRA · no payroll tax · Bridge-First (no HTML5 QR) · do not rebase SoR onto Odoo/ERPNext.

## Brand fit (`packages/ui/brand-tokens.json`)

Target chrome must map to GTR tokens — steel `#12151C`, chalk `#F4F5F7`, CTA `#C8102E`, Titillium + Source Sans 3, sharp radius ~2. Avoid purple Material seeds, cream-serif terracotta, and generic fashion-store kits that fight AutoDoc-like density (header + four-way search + category strip).

## Candidates considered

| Candidate | License | Stack | Fit | Notes |
|-----------|---------|-------|-----|-------|
| **android/compose-samples · Jetsnack** | **Apache-2.0** | Compose shopping shell (home feed, PDP, cart, bottom nav, custom design system) | **Strong** | ~23k★ official Google sample; commerce IA without purple dashboard kits |
| **android/compose-samples · Reply** | **Apache-2.0** | Adaptive phone/tablet navigation (bar / rail / drawer) | **Strong for staff** | Matches management phone + tablet flavors |
| SilentFURY-x/ShopAThing-App | MIT | Compose e-comm + Room cart | Weak | Low maturity; Firebase SoR conflict risk if absorbed |
| amanuelyosef/Aman-shop | Apache-2.0 | Compose UI-only shop | Weak | Thin / low activity |
| Dukkan-ITI/Dukkan | *(unclear)* | Shopify GraphQL storefront | Reject | Wrong SoR (Shopify); license not verified permissive |
| ozancck/E-CommerceApp-SwiftUI | MIT | SwiftUI modular shop IA | Medium | Good tab/home/PDP/cart shape; fashion visuals |
| gichukipaul/EasyCommerce | MIT | SwiftUI + FakeStore | Medium | Lightweight IA reference |
| tunacosgun/eCommerce | MIT | SwiftUI + Firebase + Stripe | Weak for Integrate | Full second backend — absorb patterns only, never SoR |
| [3wiida/OmniCart](https://github.com/3wiida/OmniCart) | **None (no LICENSE file — all rights reserved by default)** | Compose e-comm, Clean Architecture (data/domain/presentation) + MVVM, Hilt, Retrofit | **Reject (code); Adapt (architecture pattern only)** | Verified 2026-08-04 — the master design plan names "OmniCart" as the Android architecture reference; this is the actual repo. No license is a **harder stop than AGPL/GPL** (no conditional reuse right at all) — do not vendor, fork, or copy any of its source. Its data→domain→presentation / repository+use-case layering is a generic, uncopyrightable industry pattern and is separately adopted as a structural reference (see `docs/plans/2026-08-04-unified-master-plan-enhancement.md` §4.5 item 6 / §4.6 / §4.7.1) — distinct from, and layered underneath, the Jetsnack visual shell chosen below. |

**Flagged / not chosen:** ERPNext / Odoo (GPL) mid-rebase · Twenty CRM (AGPL) · Metabase (AGPL) · OmniCart (no license at all — see row above) · any HTML5 QR kit.

## Chosen strategy

| Surface | Path | Base |
|---------|------|------|
| Customer Android + iOS | **Integrate** (patterns only — no vendored tree) | **Jetsnack** (Apache-2.0) shopping IA: bottom destinations Shop / Cart / Account; dense search-first home; PDP → cart; keep `SupabaseRpcClient` / `LiveStorefrontApi` |
| Management (phone + tablet) | **Integrate** | **Reply** (Apache-2.0) adaptive density cues; restyle hub chrome; **preserve** existing POS / kiosk / offline modules |
| Delivery | **Integrate** | Compact Jetsnack-style two-destination shell (Jobs / POD); keep GPS/POD bridges |
| iOS review camera | **Build** thin adapter under `bridges/ios/` | Mirror Android `pod-camera` / contracts; UIKit `UIImagePickerController` — no WebView camera |

**Not Forking** Jetsnack/Reply into the monorepo — we already have `packages/android-ui` + `GTRTheme` brand systems. Fork cost > benefit; Integrate navigation + merchandising structure only.

## Why this fits GTR

- Jetsnack proves Compose commerce chrome with a **custom** design system (swap snack palette → GTR steel/red/chalk).
- Reply proves staff adaptive navigation without inventing tablet IA from scratch.
- Keeps one SoR (Supabase) — templates supply UX structure, not carts/auth/stock.
- Aligns with AutoDoc-like parts storefront: search + categories + PDP + cart, not analytics dashboards.

## Out of scope / Later

- Full diagram canvas + hotspots (Phase 3) — **Later** if large.
- B2B procurement portal — web-only.
- Category facets beyond static chip strip — Later.
- Forking Dukkan/Shopify stacks — rejected (dual SoR).
