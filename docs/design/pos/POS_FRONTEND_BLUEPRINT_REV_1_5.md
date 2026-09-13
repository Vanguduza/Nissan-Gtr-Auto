# Nissan GTR Auto POS — Frontend Development System

**Revision:** 1.5 — Clean-Room Edition
**Date:** 2026-09-13
**Lane:** `@management_app_agent` (UI) · `@backend_agent` (pin + fitment + reservation contracts)
**Status:** accepted — supersedes Rev 1.3 and 1.4 in full
**Implementation path:** `apps/android-management/feature/pos` (tablet + phone, shared module)
**Visual reference:** [`reference/benchmark-home-expanded-2026-09-07.jpg`](reference/benchmark-home-expanded-2026-09-07.jpg) · geometry in [`VisualReferenceSpec.json`](VisualReferenceSpec.json) · deltas in [`APPROVED_VISUAL_DELTAS.md`](APPROVED_VISUAL_DELTAS.md)

> **Prime directive.** The POS shall read as one mature product — the benchmark's composition, hierarchy
> and restraint — on every screen it runs on. Fidelity is measured as **proportion, grammar and
> behaviour**, never as pixel equality. A difference that cannot be traced to a rule in this document
> or a row in the delta registry is a regression.

---

## 0. What this revision is

**Rev 1.5 treats the POS as a clean-room rebuild.** The implementation currently in
`apps/android-management/feature/pos` is a wrong implementation of the product — it is one of the
reasons this redesign exists — and it has **no authority over any recommendation here**. Rev 1.4
still deferred to it in places ("adopt-first", "keep the vendored typefaces", "do not split the
ViewModel until the gateway is split"). Those deferrals are withdrawn. This document now recommends
what a modern, professional, fully functional SaaS POS requires, and treats the existing code purely
as a source of *capability requirements* — never as a design constraint.

### From Rev 1.3

| Area | Rev 1.3 | Now |
|------|---------|-----|
| Fidelity model | ±2 Rpx against a 1536×1024 raster | Ratio + grammar + behaviour across four window classes (§3, §11) |
| Geometry source | Illustrative JSON, "measure later" | Measured, committed, ratio-normative `VisualReferenceSpec.json` |
| Tokens | 23 colour names, 0 values | Complete valued system, generated from one source (§4) |
| Benchmark authority | Total | Scoped to composition and geometry (§2) |
| Popular row | "no seven-item cap" | **Quick Access panel** — operator-pinned, heterogeneous, unbounded (§7) |
| Vehicle cascade | Progressive disclosure out of search | **Permanent header cascade** (§8) |
| Phone | "compact recomposition" | A designed phone POS with full operational parity (§9) |
| Repo governance | Not referenced | Subordinated to the truth protocol and owner decisions (§1) |

### From Rev 1.4

| Area | Rev 1.4 | Now |
|------|---------|-----|
| Posture toward existing code | "Adopt-first is binding" | **Clean-room.** Capability continuity, not code continuity (§1.2) |
| Theming | Keep `ShopTheme` → `GtrTheme` (Material3 slot overrides) | **Dedicated `PosTheme`** over generated tokens; M3 for interaction primitives only (§4.1) |
| Typography | Keep vendored Titillium Web + Source Sans 3 because they exist | **Inter** for product UI on merit; display face is a brand-moment choice (§4.6) |
| Data access | Keep the 102-method `RpcClient`; defer the split | **Replace with typed feature gateways** returning `Result` (§10.2) |
| State | Three ViewModels sharing a `StateFlow`; defer decomposition | **One store, pure reducers, screen-scoped projections** (§10.3) |
| Reserve-first | "Blocked on backend" | **Contract specified here** so it can be built (§10.6) |
| Migration | Per-file keep/rebuild table | **Capability continuity contract** (§12) |
| Design language | Dropped in the 1.4 rewrite | **Restored and extended as §5** — principles, permission matrix, micro-interactions, spatial continuity, layering, plus what Android can actually render |

---

## 1. Authority hierarchy

Rev 1.3 declared itself "canonical implementation and certification authority". It is not — the
repository already has governance that outranks any design document. Resolve conflicts strictly in
this order.

| Priority | Authority | Scope |
|---|---|---|
| **A0** | [`PROJECT_TRUTH_PROTOCOL.md`](../../../PROJECT_TRUTH_PROTOCOL.md) + [`PROJECT_CANONICAL_STATE.json`](../../../PROJECT_CANONICAL_STATE.json) | Lineage, release blocking, the prohibition on silent feature thinning. Nothing below may delete a capability to satisfy a visual goal. |
| **A1** | [`AGENTS.md`](../../../AGENTS.md) hard exclusions + global laws | No ZIMRA, no payroll tax, Bridge-First, RLS mandate, ledger immutability, explicit multi-currency. |
| **A2** | [Tablet Kiosk POS action plan](../../plans/2026-08-03-tablet-kiosk-pos-full-action-plan.md) §11 locked decisions | Separate tablet/phone APKs (L1), Admin‑or‑shop‑manager authorisation (L2), finance refund pipeline (L3), quotations (L4), idle timeout (L5). |
| **A3** | [Kiosk / role-routing specification](../../../Nissan_GTR_Auto_POS_Kiosk_Role_Based_Routing_Specification.md) | Boot, splash, login, role routing, Lock Task. Owns everything **before** the POS surface renders; this document owns everything **after**. |
| **A4** | Owner-approved benchmark | Expanded composition and visual quality — **within the scope fixed by §2**. |
| **A5** | [`APPROVED_VISUAL_DELTAS.md`](APPROVED_VISUAL_DELTAS.md) | Every intentional benchmark difference. |
| **A6** | This document | Tokens, adaptive geometry, component anatomy, motion, certification. |
| **A7** | Domain / RPC contracts | Stock, cart, till, tender, recovery truth. Nav hiding is UX only; authorisation is RPC + RLS. |
| **A8** | Platform constraints | Compose, Android APIs, bridge SDKs. |
| **A9** | Developer preference | Cannot override A0–A8. |

### 1.1 Conflict procedure

1. Name both requirements and classify each by level.
2. Preserve the higher level.
3. On a tie, prefer the option that touches fewer canonical **zones** (§3) and fewer domain
   contracts. Rev 1.3's "fewer benchmark pixels" test is withdrawn — it is not measurable before
   implementation.
4. Still ambiguous → raise an unresolved decision. Do not improvise a new visual convention in code.

### 1.2 What binds, and what does not

**The existing POS implementation is not an authority.** No part of it constrains a recommendation
in this document — not `PosScreen.kt`'s composition, not the `ui.shop` component set, not the
`ShopTheme` / `GtrTheme` plumbing, not the `RpcClient` gateway shape, not `PosViewModel`'s
structure, not the currently vendored UI typefaces. Where a design choice here differs from what is
in the tree, the tree is what changes.

Three things do bind, and none of them is an implementation detail:

| Binds | Why it is not "existing implementation" |
|---|---|
| `AGENTS.md` hard exclusions — no ZIMRA/FDMS, no payroll tax, Bridge-First hardware, RLS mandate, ledger immutability, explicit multi-currency | Legal scope and correctness law, not a code shape |
| Action plan §11 locked decisions — L1 separate tablet/phone APKs, L2 Admin-or-shop-manager authorisation, L3 refunds post through the finance pipeline, L4 quotations | Owner product decisions |
| Truth protocol A0 — no silent feature thinning | Governance. A rebuild may replace any code; it may not quietly drop a capability |

**Capability continuity, not code continuity.** Every behaviour the counter performs today must
exist in the rebuild — offline cash sales, split tender, manager reauth for discount/void/refund/
price override, quotations, park and resume, companion scan pairing, ESC/POS printing. None of the
current code has to survive for that to be true. §12 enumerates the capabilities; it no longer
enumerates files to keep.

### 1.3 No accidental redesign

Before changing any canonical geometry — a zone ratio, a band height, a component's anatomy —
answer all five:

1. Is the change required by an owner-approved functional requirement?
2. Is it required for accessibility or for a supported window class?
3. Is it already a row in the delta registry?
4. Does it preserve the benchmark's hierarchy and product identity?
5. Does it still pass the §11 gates afterwards?

If 1, 2 and 3 are all no, do not make the change. Raise it instead.

---

## 2. Benchmark authority scope

The benchmark is a rendered mock. It is authoritative for **composition, hierarchy, proportion,
component anatomy and visual treatment**. It is **not** authoritative for:

| Not authoritative | Evidence in the benchmark | Governs instead |
|---|---|---|
| Currency | `KSh` (Kenyan shillings) | Backend money state; USD / ZiG; `AGENTS.md` multi-currency law |
| Tax rate and model | `VAT (16%)` — the Kenyan rate | Backend tax policy; invoices stay tax-agnostic (no ZIMRA) |
| Copy strings | `GNGUINE PARTS` (typo); `Add Customer (Optional)` (helper copy) | §10.10 copy rules |
| Sample data | Part numbers, prices, `Tue, 27 May 2025` | Real catalogue and device clock |
| Feature inventory | `Reports` in the rail | Delta D-001 → `EPC Browse` |

**Rule.** A golden test may assert that a price *element* exists with the right style, alignment and
tabular numerals. It may never assert the literal string `KSh 2,500`.

This scoping is what makes A4 safe to place above this document: without it, "benchmark fidelity"
literally instructs an implementer to ship the wrong country's tax rate and a spelling error.

---

## 3. Adaptive geometry system

This replaces Rev 1.3 §2 entirely. The benchmark is not a size the app must be; it is a set of
**proportions the app must preserve**.

### 3.1 The measured basis

Measured from the committed reference (see `VisualReferenceSpec.json` for full data and caveats):

| Zone | Measured ratio | Canonical dp @1280 | Ratio at that dp |
|------|---------------|--------------------|------------------|
| Nav rail | 0.1102 | 144 | 0.1125 |
| Discovery canvas | 0.6148 | 784 | 0.6125 |
| Cart pane | 0.2750 | 352 | 0.2750 |

**The key finding:** interpreted at a **1280 × 800 dp** frame, the entire benchmark composition
resolves onto an 8 dp grid — rail 144, cart 352, header 96, hero 240, nav pill **48** (exactly the
minimum touch target). A mock that lands on the platform's own grid at a real device size was
designed at that size. That, not the 1536 × 1024 raster, is the frame to build against.

### 3.2 Terms

- `Rpx` — a pixel in the 1536 × 1024 reference raster. **Provenance only.** Never a target.
- **Canonical frame** — 1280 × 800 dp, the Expanded reference.
- **Zone ratio** — a zone's share of available width. Normative at every size.
- **Clamp** — `minDp`/`maxDp` bounds that override the ratio when it would produce an unusable zone.

`sx`/`sy` uniform scale factors from Rev 1.3 §2.1 are **deleted**. They described an approach the
same section then forbade, and were never used.

### 3.3 Layout law

```
zoneWidth = clamp(zone.minDp, availableWidth * zone.ratio, zone.maxDp)
```

applied in this order, so the operationally critical zone never loses:

1. **Cart pane** takes its clamped share first. It is the operational anchor; below `minDp` 320 it
   does not shrink — it leaves the row (§3.5).
2. **Nav rail** takes its clamped share, or collapses to an icon rail, or leaves.
3. **Discovery canvas** absorbs all remaining width. It is the only flexible zone.

Within the discovery canvas, item counts are **derived, never constant**:

```
n         = floor((contentWidth + gap) / (minItemWidth + gap))
itemWidth = clamp(minItemWidth, (contentWidth - (n-1)*gap) / n, maxItemWidth)
```

The benchmark's 7 categories and 4 visible Quick Access cards are *outputs of this formula at 1280
dp*, not constants. No count from the benchmark may appear as a literal in layout code — this is
lint-enforced (§11.4).

### 3.4 What scales, what holds

| Property | Behaviour |
|---|---|
| Zone widths, canvas gutters, card widths | Scale by ratio, within clamps |
| Band heights (hero, category row) | Scale by ratio against a vertical budget, down to `minDp` |
| Type sizes | **Hold.** One step down at Compact only. Never continuously scaled. |
| Touch targets, icon boxes, stroke weight | **Hold** at every size. Never below 48 dp. |
| Radii, border widths, elevation | **Hold.** A card is the same card on a phone. |
| Spacing tokens | Hold; density selects a different token, it does not interpolate one |

This is the line Rev 1.3 gestured at and never drew: **structure is fluid, grammar is fixed.**

### 3.5 Window classes

Derived from available width and height after insets — never from a device label.

```kotlin
enum class PosWindowClass { CompactPortrait, CompactLandscape, Medium, Expanded }
```

| Class | Trigger | Rail | Cart | Hero |
|---|---|---|---|---|
| `Expanded` | w ≥ 1040 dp | Full rail, 144 dp | Side pane, persistent | Full, 240 dp |
| `Medium` | 600 ≤ w < 1040 dp | Icon rail, 88 dp | Side pane if ≥ 320 dp fits, else summary bar | Reduced, ≥ 160 dp |
| `CompactLandscape` | w ≥ 600 dp and h < 480 dp | Icon rail | Summary bar → sheet | Identity strip |
| `CompactPortrait` | w < 600 dp | Bottom navigation | Summary bar → full sheet | Identity strip |

Reference sizes that must be certified: `1280×800`, `1024×768`, `800×1280`, `412×915`, `360×800`.

### 3.6 Vertical budget

Height is allocated top-down, and the hero yields first:

```
available = windowHeight - header(96) - systemInsets
hero        = clamp(160, available * 0.34, 280)      // first to yield, may drop out entirely
categoryRow = 96                                      // fixed; scrolls horizontally instead
quickAccess = clamp(176, remaining * 0.45, 224)
recent      = 56 if remaining >= 56 else hidden       // lowest priority band
```

Bands drop in reverse priority: recent searches → hero → category row. The Quick Access row and the
cart never drop.

---

## 4. Token system

Rev 1.3 listed 23 colour names with no values, five radii with no dp, elevation tokens whose
values were "specified" but absent, and eleven type roles with no sizes — while forbidding
arbitrary values in feature code. Rev 1.4 supplies the values.

### 4.1 Token pipeline

A serious multi-surface product **generates** its tokens. It does not hand-maintain a Kotlin object,
a CSS file and a Swift struct and hope they agree.

`packages/ui/brand-tokens.json` stays the single source. Build [Style
Dictionary](https://amzn.github.io/style-dictionary/) over it and emit, per build:

| Output | Consumer |
|---|---|
| `PosTokens.kt` (Compose) | `apps/android-management`, `apps/android-customer`, `apps/android-delivery` |
| `tokens.css` + typed TS constants | `apps/web` |
| `Tokens.swift` | `apps/ios` |

Hand-written `GtrColors.kt` becomes generated output rather than a maintained file. A brand value
that exists in Kotlin but not in `brand-tokens.json` is a build failure, not a review comment.

**Do not theme the POS by overriding Material3's `ColorScheme`.** M3's semantic slots — `primary`,
`surface`, `surfaceVariant`, `onSurfaceVariant`, `outline` — are a general app vocabulary. The POS
needs the nav rail, the canvas, the cart pane, the hero backdrop, elevated layers and six status
families to be independently addressable, and mapping those onto M3 slots means approximating your
own design in someone else's vocabulary. That approximation is visible in the current app.

Provide a dedicated theme instead:

```kotlin
@Composable
fun PosTheme(
    windowClass: PosWindowClass,
    density: PosDensity = PosDensity.Operational,
    content: @Composable () -> Unit,
) = CompositionLocalProvider(
    LocalPosPalette   provides PosPalette.resolve(isSystemInDarkTheme()),
    LocalPosType      provides PosType.resolve(windowClass),
    LocalPosSpace     provides PosSpace,
    LocalPosShape     provides PosShape,
    LocalPosElevation provides PosElevation,
    LocalPosMotion    provides PosMotion.resolve(reducedMotionEnabled()),
    LocalPosGeometry  provides PosGeometry.resolve(windowClass),
    content = content,
)
```

Material3 remains a dependency for interaction primitives — ripple, focus, gesture and
accessibility plumbing — and supplies **no** visual identity. `MaterialTheme.colorScheme` and
`MaterialTheme.typography` are lint failures inside POS components (§11.4).

### 4.1.1 Semantic palette

```kotlin
@Immutable
data class PosPalette(
    val navBackground: Color, val navSurfaceRaised: Color, val navActiveFill: Color,
    val brandRed: Color, val brandRedPressed: Color,
    val canvas: Color, val surfacePrimary: Color, val surfaceElevated: Color,
    val heroBackdrop: Color,
    val borderSubtle: Color, val borderStrong: Color, val borderFocus: Color,
    val textPrimary: Color, val textSecondary: Color, val textMuted: Color, val textOnBrand: Color,
    val success: Color, val warning: Color, val error: Color, val unknown: Color,
    val offline: Color, val scrim: Color,
)
```

Dark scheme is a **first-class requirement**, not an afterthought: counter tablets run evening
shifts, and every value above resolves in both schemes from the same generated source.

### 4.2 Colour values

Measured values are provisional until resampled from a lossless export; the ratios and the
*decisions* below are not.

Every value below is a **target derived from the benchmark**, expressed as a brand-token name to be
emitted by the §4.1 pipeline. Where a name already exists in `brand-tokens.json` it is reused because
the measurement matches, not because the file already contained it.

| Semantic | Target | Brand token | Provenance |
|---|---|---|---|
| `canvas` | `#F4F5F7` | `neutral.canvas` | Measured `#F3F4F8` |
| `navBackground` | `#12151C` – `#1E2430` | `neutral.ink.deep` | Measured `#1B2024`; pick on the lossless export |
| `surfacePrimary` | `#FFFFFF` | `neutral.surface` | Measured `#FEFEFE` |
| `heroBackdrop` | `#0A0C0E` | `neutral.ink.absolute` | Measured `#060709` |
| `brandRed` | `#C8102E` | `brand.red` | Measured `#CB1432`, within JPEG error |
| `brandRedPressed` | `#E01234` | `brand.red.pressed` | Existing brand value |
| `error` | `#8E0F22` | `status.error` | **New** — decision, see below |
| `unknown` | `#5B4A2E` | `status.unknown` | **New** — ambiguous terminal outcome |
| `warning` | `#B45309` | `status.warning` | Existing brand value |
| `success` | `#0B6E4F` | `status.success` | Existing brand value |
| `offline` | `#B45309` | `status.offline` | Shares the warning hue, distinct icon and label |

**What sampling settles.** Rev 1.3 hedged on whether the dark neutrals carry a blue bias and
proposed a token named `CanvasWarm`. Measurement settles it: rail and canvas are both cool
(`#1B2024`, `#F3F4F8`), so `CanvasWarm` was the wrong name, and the benchmark's red is the brand
red. These are measured facts about the target, independent of what any current code does.

**The red collision.** `Danger` and `Primary` are the same value `#C8102E` in the brand file, so a
blocking error and the primary call to action are indistinguishable. Rev 1.3 raised this and left it
optional. Decided: **error resolves to `#8E0F22`, always icon-paired**; brand red is reserved for
primary CTA, active navigation and brand emphasis. `unknown` — the ambiguous-terminal family, where
a duplicate charge is the failure mode — gets its own treatment distinct from both. All three go
into `brand-tokens.json` so every surface inherits the separation. Registered as **D-011**.

Colour is never the only status signal (§10.9).

### 4.3 Spacing

8-point base. Carried forward from Rev 1.3 §4.2 — the one scale that was complete.

| Token | dp | Use |
|---|---:|---|
| `space.0` | 0 | Flush |
| `space.0_5` | 2 | Optical only |
| `space.1` | 4 | Icon/text gaps |
| `space.2` | 8 | Compact internal |
| `space.3` | 12 | Dense rows, **card gaps** |
| `space.4` | 16 | Standard component padding |
| `space.5` | 20 | **Canvas gutter**, medium card padding |
| `space.6` | 24 | Section padding |
| `space.8` | 32 | Large separation |
| `space.10` | 40 | Major canvas separation |
| `space.12` | 48 | Exceptional |

### 4.4 Radius

| Token | dp | Applies to |
|---|---:|---|
| `radius.xs` | 6 | Badges, tiny chips |
| `radius.sm` | 10 | Inputs, quantity controls, nav pill |
| `radius.md` | 14 | Category cards, Quick Access cards, primary CTA |
| `radius.lg` | 20 | Hero, cart pane, dialogs |
| `radius.pill` | 999 | Search/status/recent-search chips |

### 4.5 Border and elevation

Borders: `1.dp` subtle (`borderSubtle`) on ordinary cards; `1.5.dp` strong on focus, selected,
error and active controls. Never strong border + strong shadow + tinted fill together.

| Token | y | blur | spread | opacity | Use |
|---|---:|---:|---:|---:|---|
| `elevation.0` | 0 | 0 | 0 | — | Canvas, flat card |
| `elevation.1` | 1 | 2 | 0 | 0.06 | Ordinary raised card |
| `elevation.2` | 2 | 8 | 0 | 0.08 | Cart pane, floating section |
| `elevation.3` | 8 | 24 | 0 | 0.12 | Popover, modal, contextual layer |

Values describe the visual outcome. Use whichever renderer matches on the supported API level;
`BlurMaskFilter` is not mandatory.

### 4.6 Typography

**Recommendation: Inter (variable) for all product UI.**

This is a merit choice for a dense, numeric, operational interface, not an inventory choice:

- A true **variable weight axis**, so 500 and 600 are real cuts — no synthetic bold anywhere.
- Genuine **tabular lining figures** (`tnum`), plus slashed zero (`zero`) and disambiguated `1`/`l`/`I`
  (`ss02`) — which matters when an operator reads a part number like `15208-65F0A` aloud.
- Large x-height and open apertures at 11–14 sp, where most of this interface lives.
- OFL, variable-font small, and it renders predictably across Android API levels.

A display face for brand moments only — the rail logo lockup and the hero headline — is a separate,
owner-level choice and may remain Titillium Web. It must not set product UI: it has a narrow weight
range and squarish letterforms that lose legibility in dense rows, which is precisely the opposite of
what a cart pane needs.

Confirm the benchmark's actual face against the lossless export in Phase 1. If it is neither, that is
a delta to register — not a reason to keep whatever happens to be vendored today.

| Role | Family | Size / line | Weight | Compact | Use |
|---|---|---|---|---|---|
| `display.hero` | Display | 32 / 38 | 700 | 24 / 30 | Hero headline only |
| `heading.1` | Inter | 24 / 30 | 700 | 20 / 26 | Pane title, Total |
| `heading.2` | Inter | 20 / 26 | 600 | 18 / 24 | Section titles |
| `heading.3` | Inter | 16 / 22 | 600 | — | Card titles |
| `body.primary` | Inter | 14 / 20 | 400 | — | Product names, labels |
| `body.secondary` | Inter | 12 / 16 | 400 | — | Part numbers, metadata |
| `label.action` | Inter | 14 / 18 | 500 | — | Buttons, nav labels |
| `label.meta` | Inter | 11 / 14 | 500 | — | Date, role, small metadata |
| `numeric.price` | Inter | 15 / 20 | 600 | — | Unit price — **tnum** |
| `numeric.total` | Inter | 24 / 30 | 700 | 20 / 26 | Total, balance — **tnum** |
| `numeric.quantity` | Inter | 14 / 18 | 500 | — | Quantity, stock — **tnum** |
| `mono.reference` | JetBrains Mono | 12 / 16 | 400 | — | Correlation IDs, terminal references in recovery |

Part numbers use `body.secondary` with `ss02`; payment correlation references use `mono.reference`,
because an operator reading one to a support line cannot afford an ambiguous glyph.

### 4.7 Icons

**One family: Lucide**, packaged as vector drawables. The benchmark's uniform-stroke set is not
Material.

This has a live consequence: commit `6f0b8a7` added
`androidx.compose.material:material-icons-extended` to the POS module on **2026-09-07 — the same
day the benchmark was approved**. That dependency is to be removed from `feature/pos` as part of
Phase 2 and replaced with the packaged Lucide set. Lucide ships no first-party Compose artifact, so
this is a vendoring task, not a dependency swap.

Icon box 24 dp, stroke 1.75 dp, optical centering permitted within ±2 dp and documented per
component.

### 4.8 Density

```kotlin
enum class PosDensity { Comfortable, Operational, Compact }
```

| Intention | Row height | Section gap | Secondary metadata | Use |
|---|---:|---:|---|---|
| `Comfortable` | 80 dp | 24 dp | Always shown | Training, low-volume counters |
| `Operational` | 72 dp | 16 dp | Shown | **POS default** |
| `Compact` | 64 dp | 12 dp | On demand | Constrained displays, management surfaces |

Density is a POS concern and owns its own scale. It may change padding, row height and
secondary-metadata visibility within these bounds. It may never reduce a touch target below 48 dp,
move a primary CTA out of reach, or change type sizes — those are §4.6's and only §4.6's.

### 4.9 Motion

| Tier | Scope | Duration | Easing |
|---|---|---:|---|
| `M1` | Touch/press feedback | 70–140 ms | `FastOutLinearIn` |
| `M2` | Local component state | 140–220 ms | `FastOutSlowIn` |
| `M3` | Layer transition | 180–280 ms | `FastOutSlowIn` |
| `spring.press` | Press scale 0.98 | — | `dampingRatio 0.75, stiffness 900` |

One easing family, one spring family. Reduced-motion removes translation and scale, never state
clarity. Destructive actions never bounce.

---

## 5. Design language and interaction grammar

Tokens (§4) say what values exist. This section says **how they are allowed to be combined** — which
is what keeps a product from reading as a pile of trends. It carries forward Rev 1.3's governing
principles, permission matrix and SaaS interaction grammar, and adds what Rev 1.3 omitted: what
Android can actually render, and at what cost.

### 5.1 Governing principles

1. **Quiet base, strong hierarchy.** The canvas is calm. Importance is carried by scale, weight and
   placement before colour.
2. **One dominant accent.** Brand red is the only dominant action and selection colour. Status hues
   are never decorative.
3. **Borders before shadows.** Subtle borders and tonal separation are the default; shadow means
   genuine elevation.
4. **Glass is contextual, never structural.** Frosted surfaces belong to transient overlays — never
   the rail, canvas, or cart pane.
5. **Density without noise.** Operational information may be dense; decorative UI stays sparse.
6. **Motion explains state.** Animation is feedback, never ornament.
7. **Every value has provenance.** Colour, radius, spacing, type and motion come from a token or a
   measured reference value.

### 5.2 Pattern permission matrix

| Pattern | Permitted | Forbidden | Why |
|---|---|---|---|
| Industrial minimalism | Everywhere — it is the base language | — | Closest to benchmark identity |
| Bento / modular composition | Category cards, Quick Access cards, till and shift panels, EPC section grids | Resizing the §3 canonical zones | Organisation without redesigning the composition |
| Glass / acrylic | Vehicle cascade popover, search overlay, product detail, modal layers | Nav rail, canvas, cart pane, ordinary cards, phone bottom bar | Prevents trend noise; see §5.3 for what it costs |
| Soft depth | Cart pane, dialogs, floating menus, phone cart sheet | Every ordinary card | Elevation must keep semantic meaning |
| Soft borders | Cards, controls, chips, section boundaries | — | Modern SaaS cleanliness |
| Ambient gradient | Hero image overlay only | Commerce canvas, cart, any data-dense surface | Keeps the product professional |
| Micro-interaction | Press, add-to-cart, scan success, chip select, quantity change | Continuous decorative animation | Tactile quality without distraction |
| Spatial layering | Sheets, drawers, detail inspectors | Navigation that should be direct | Preserves task context |
| Progressive disclosure | Fitment filters, advanced stock, recovery detail, supplier metadata | Primary checkout controls | Reduces clutter |
| Data-rich minimalism | Search results, cart, totals, till, management surfaces | Marketing ornament | Optimised for professional use |
| Neumorphic cues | Nowhere by default | Cards, shell, navigation | Dated, low-contrast, fails WCAG AA |
| Shared-element transition | Card → detail, search → results, cart → checkout | Anywhere it drops frames | Continuity must not cost fluidity |

Rev 1.3 permitted neumorphism for "rare tactile segmented controls if visually justified". That
exception is withdrawn: it cannot meet §10.13 contrast requirements and there is no control in this
product that needs it.

### 5.3 Depth, and what Android actually does

Rev 1.3 specified "low blur" for contextual glass without noting that on Android this is a
capability question, not a styling one. Three constraints shape the implementation:

- **Blur is API 31+.** `Modifier.blur()` and `RenderEffect.createBlurEffect` require Android 12.
  Below that, `Modifier.blur` is ignored — it does not throw, it silently renders unblurred, which is
  exactly how an unreviewed glass surface ships as a flat translucent rectangle on half the fleet.
- **`Modifier.blur` blurs the composable's own content, not what is behind it.** Backdrop blur needs
  the background captured into a graphics layer (`rememberGraphicsLayer()` / `record()`) and blurred
  there, or an equivalent library approach. Applying `blur` to the overlay itself blurs the overlay's
  text.
- **Blurring live content every frame is expensive.** Blur a captured snapshot taken when the overlay
  opens; do not blur continuously while content animates behind it.

So the contract for every glass surface is a **declared pair**:

| Capability | Treatment |
|---|---|
| API 31+ | Backdrop blur 16–20 dp radius on a captured layer · `surfacePrimary` at 72% · 1 dp `borderSubtle` · `elevation.3` |
| Below API 31 | **No blur.** `surfacePrimary` at 94% over a `scrim` at 32% · 1 dp `borderSubtle` · `elevation.3` |

Both must be screenshot-certified (§11.3). A glass surface that has not had its fallback rendered and
reviewed is not done. Never a neon glow, never a coloured blur, never blur on a surface that scrolls.

Set and record the project's `minSdk` next to this table — it determines which column most operators
actually see.

### 5.4 Micro-interactions

Acknowledgement only. Each maps to a motion tier from §4.9.

| Interaction | Behaviour | Tier |
|---|---|---|
| Press (card, button, chip) | Scale 0.98, `spring.press` return | M1 |
| Add to cart | `Add` label swaps to a check for ~600 ms, then returns; the cart row inserts with a slide-and-fade | M2 |
| Scan success | Row highlight pulse once + **light haptic tick** | M1 |
| Chip / category select | Background, border and label transition together, never staggered | M2 |
| Quantity change | Optimistic increment immediately; settle from the backend. If the settled value differs, **animate to the true value** | M2 |
| Pin / unpin | Card scales in or out of the Quick Access row in place | M2 |
| Tender leg confirmed | Remaining balance counts down to the backend figure | M2 |
| Destructive (clear, void, remove) | Fade and collapse. No bounce, no playful easing | M2 |

**Haptics are part of the design, not a platform default.** A counter operator is often looking at
the customer, not the screen — a light tick on add-to-cart, scan success and tender confirmation is
the fastest feedback channel available. Never vibrate on error alone; pair it with the visual state.

A quantity that settles to a different value must never snap silently — that is how an operator sells
the wrong count.

### 5.5 Spatial continuity

Preserve object identity across a transition where it is honest to do so:

| From | To | Technique |
|---|---|---|
| Product card | Detail overlay / sheet | Shared bounds on the image and title |
| Search suggestion | Result context | Shared text bounds |
| Cart | Checkout | Shared totals block |
| Tender leg | Remaining balance | Shared numeric position |

Use `SharedTransitionLayout` where the Compose version in use supports it. **Never fake a shared
element that janks** — a clean M3 crossfade reads as deliberate; a stuttering shared element reads as
broken. If it cannot hold frame rate on the baseline device, it does not ship.

### 5.6 Contextual actions and layering

Primary operational actions are **always visible**: search, add, quantity, proceed to payment. Never
behind a hover, a long-press, or a menu.

Secondary actions appear through, in order of preference: row overflow menu · long-press contextual
menu (the pin/unpin path, §7.2) · anchored popover · selected-item toolbar.

**Maximum two transient layers deep.** If a third would be required, promote the workflow to a full
screen or a dedicated state. Recovery is always a screen (§10.4). Every transient layer dismisses on
outside tap and on back, except where a selection flow is incomplete — and an incomplete flow says so
rather than trapping the operator silently.

### 5.7 Loading, empty and skeleton states

- **Local-first.** If the data exists locally, render it. No spinner for trivial local work.
- **Skeletons match final geometry exactly** — the same card width the §3.3 formula produces at that
  window size, the same row height density produces. A skeleton that reflows on load is worse than
  no skeleton.
- **No indeterminate spinner as a default pattern.** A spinner is for a genuinely unbounded wait, and
  it carries a label saying what is being waited on.
- **Every surface has a designed empty state**: empty cart, no search results, empty Quick Access
  (§7.5), no vehicle selected, empty till. Empty states state the next useful action.

### 5.8 Forbidden outright

Continuous decorative animation · hero parallax · confetti or celebration effects · neon or coloured
glow · gradient-filled text · a second saturated accent hue · glass on the rail, canvas, cart pane or
phone bottom bar · animated splash inside the POS surface (the branded startup sequence belongs to
the kiosk specification, A3) · shadow used where a border would do.

---

## 6. Component anatomy (Expanded)

### 6.1 Navigation rail — 144 dp

Top to bottom: logo lockup · eight destinations · flexible spacer · GT-R artwork · brand statement
(`BUILT FOR A HIGHER STANDARD`). Both bottom blocks are **present in the benchmark** — Rev 1.3's
"if retained" / "if present" conditionals are resolved as yes.

Destinations, canonical order:

**Home · Search Spares · Quick Sale · Customer · Orders · Returns · EPC Browse · Settings**

`Reports` is forbidden on the salesperson rail (D-001). The lint rule is scoped to
`apps/android-management/feature/pos/` so it cannot false-positive on legitimate dashboard reporting.

Active: brand-red filled rounded rect, 48 dp tall, `radius.sm`, white icon and label, no glow, no
gradient. Inactive: transparent, muted icon and label.

### 6.2 Header — 96 dp

Four zones: **vehicle cascade** (§8, replacing the taxonomy line) · dominant search field ·
operator identity · date/time. Offline status surfaces in the context zone when applicable (D-005).

Search field: 48 dp tall, max 560 dp, leading search icon, trailing scanner affordance. No
permanent filter clutter.

### 6.3 Hero — 240 dp, collapsible to 160

Dark GT-R identity panel: image, headline, supporting line, three value indicators
(`GENUINE PARTS` — D-007). No permanent cascade dropdowns; the cascade lives in the header.
Permitted: overlay gradient for legibility, restrained ambient highlight, crossfade on legitimate
asset change. Forbidden: continuous parallax, decorative motion.

### 6.4 Category row — 96 dp

Icon-first modular cards, `radius.md`, 96 dp square nominal, 12 dp gaps, horizontally scrollable.
Seven cards is the benchmark's *output at 1280 dp*, not a cap.

### 6.5 Cart pane — 352 dp, min 320

Order: header (`Current Sale` + clear) · scrollable item list · `Add Customer` (D-008) ·
subtotal / discount / tax · divider · total · `Proceed to Payment`.

Only the item list scrolls. Totals and the CTA stay reachable.

**Row anatomy** — fixed columns: thumbnail · name · part number · **unit price** (D-012) ·
overflow · quantity stepper. Deterministic truncation; name wraps to at most 2 lines; part number
in `body.secondary`; price in `numeric.price`.

Money rows render backend values with explicit currency (D-006). A zero discount row stays visible
— the operator reads the same row positions on every sale.

---

## 7. Quick Access panel

**This replaces "Popular Spares" (D-003).** The row evolves from a merchandising strip into the
operator's own working set.

### 7.1 Definition

A per-operator, horizontally scrollable, **unbounded** row of items the operator pins for everyday
use. It is heterogeneous — three entity kinds share one row:

| Kind | Card content | Tap | Long-press |
|---|---|---|---|
| `Spare` | image, name, part number, unit price, `Add` | Add to cart | Pin / Unpin |
| `Category` | icon, label, item count | Filter discovery to that category | Pin / Unpin |
| `Vehicle` | model / generation / engine identity | Set the fitment context (§8) | Pin / Unpin |

### 7.2 Pinning

Pin and unpin are available by **long-press from anywhere in the app** that renders one of those
three entities — Quick Access itself, search results, category browse, EPC browse, the vehicle
cascade, cart rows. A long-press opens a contextual menu containing Pin/Unpin; there is no
permanently visible pin control cluttering cards.

### 7.3 Ordering

Deterministic, in precedence order:

1. Explicit operator pin order (drag to reorder within the row).
2. Pin recency for items with no explicit order.
3. Stable entity id as final tie-break.

No server popularity ranking is merged into this row — that was the old model. Server-ranked
popular items, if surfaced at all, belong in search and category browse.

### 7.4 Persistence

Pins are **operator-scoped and server-persisted**, so they follow the operator between the counter
tablet and the phone. They are not device-local preferences.

**Backend dependency — `@backend_agent` lane, not yet built.** This needs a pin table keyed by
staff user and entity reference, with RLS restricting rows to their owner, plus list/pin/unpin/
reorder RPCs. Contract shape:

```text
pos_operator_pins(staff_user_id, entity_kind, entity_ref, pin_order, pinned_at)
  list_operator_pins()               -> [{kind, ref, order, resolved_display}]
  pin_operator_item(kind, ref)       -> pin_order
  unpin_operator_item(kind, ref)
  reorder_operator_pins(refs[])
```

Until those exist, the row renders from a local projection and the UI must surface pin failures
rather than simulating success — a server rejection is never swallowed.

### 7.5 Behaviour

- `LazyRow`, stable keys, unbounded. **No literal item count anywhere in the code.**
- Card width from the §3.3 formula; clamp 160–220 dp; 12 dp gaps.
- Empty state: a single explanatory card inviting the first pin — the one place operational copy is
  permitted, because the feature is invisible until used.
- Offline: cached pins render; pin mutations queue and are marked pending, consistent with the
  offline outbox model.

---

## 8. Vehicle cascade in the header

**This replaces the `Spares · Service · Performance` taxonomy line (D-002).** The line was
decorative; fitment is the highest-value filter at the counter.

### 8.1 Fields

Model → Generation → Engine, left to right, in header zone 1. A Maker field precedes them only when
the multi-make catalog is active; in single-make deployments Maker is implicit.

Each field is a compact dropdown: label above, value or placeholder inside, `radius.sm`, 40 dp tall.
Selecting a level enables and resets the levels to its right.

### 8.2 Data contract

Reuses the existing cascade — no new query layer. Per
[`docs/guides/vehicle-cascade-and-epc-browse.md`](../../guides/vehicle-cascade-and-epc-browse.md):
Model = `vehicle_master.model_variant`, Generation = `chassis_code`, Engine = `engine_code`.
Search stays on `search_catalog` / `part_fitment`. EPC hierarchy browse stays separate and joins at
`chassis_code`.

### 8.3 Fitment context

A confirmed vehicle sets a **session-scoped fitment context** that filters search results, category
browse and EPC entry until cleared. It renders as a dismissible chip once set, so the operator can
always see and drop the active filter. It is pinnable to Quick Access (§7.1).

### 8.4 Responsive form

| Class | Presentation |
|---|---|
| `Expanded` | Three inline fields in the header |
| `Medium` | Single "Select Vehicle" field → anchored popover cascade |
| `Compact` | Single chip → full-height bottom sheet cascade |

Rev 1.3's Path A (cascade reached only through search activation) is **withdrawn** — the cascade is
now permanent at Expanded and Medium. Path B (Search Spares screen) and Path C (compact sheet)
survive as §8.4 rows.

---

## 9. Phone POS

The phone POS is **not a shrunken tablet**. It is a designed compact product that performs every
counter operation, built from the same tokens, grammar, state machines and RPCs.

Action plan **L1** stands: the phone APK carries no Device Owner, no Lock Task, no Magisk
ownership. What changes in Rev 1.4 is capability — the phone moves from "optional POS" to **full
operational parity**, because a salesperson away from the counter must be able to complete a sale.

### 9.1 What makes it the same product

Inherited without exception: colour semantics, type roles, radius family, icon family, card
grammar, motion tiers, status semantics, error taxonomy, domain state machines, RPC contracts,
offline restrictions and authorisation gates.

Deliberately different: navigation model, cart presentation, checkout choreography, hero treatment,
and information density. Identity comes from grammar, not geometry.

### 9.2 Shell

```
┌────────────────────────────────┐
│ ▾ R35 · VR38DETT      [scan] ⋮ │  fitment chip + scan + overflow   56dp
├────────────────────────────────┤
│ ⌕ Search parts…                │  search field, always first       48dp
├────────────────────────────────┤
│ QUICK ACCESS        ▸          │
│ ┌────┐┌────┐┌────┐┌────┐       │  horizontal, unbounded           152dp
│ └────┘└────┘└────┘└────┘       │
│ CATEGORIES                     │
│ (chips, horizontal)            │                                   40dp
│ ── recent searches ──          │                                   
├────────────────────────────────┤
│ 🛒 3 items          $326.00  ▲ │  persistent cart bar              64dp
├────────────────────────────────┤
│ Sell  Orders  EPC  Customers ⋯ │  bottom navigation                80dp
└────────────────────────────────┘
```

The marketing hero does not survive at compact width — it becomes a slim identity strip, or is
dropped entirely when vertical space is scarce (D-009). The rail's eight destinations recompose to
four tabs plus an overflow sheet: **Sell · Orders · EPC · Customers · More** (More holds Quick
Sale, Returns, Settings, Till).

The cart bar is the phone's equivalent of the Expanded cart pane: always visible, always showing
live item count and backend-authoritative total, expanding to a full-height sheet on tap.

### 9.3 Operational parity

Every POS operation, and how it is reached on each surface. No operation is tablet-only.

| Operation | Expanded (tablet) | Compact (phone) | Notes |
|---|---|---|---|
| Catalogue search | Header field | Persistent search field | Same `search_catalog` path |
| Vehicle fitment | Header cascade | Chip → bottom sheet cascade | §8.4 |
| Category browse | Category row | Category chips → results screen | |
| EPC browse | Rail destination | Bottom-nav tab | Reuses `PosEpcBrowseScreen` |
| Barcode scan | Physical scanner + CameraX | CameraX; physical scanner if paired | Bridge-First |
| Add to cart | Card `Add` | Card `Add` / detail sheet | |
| Edit quantity | Inline stepper | Stepper in cart sheet | |
| Remove line | Row overflow | Swipe or row overflow | |
| Customer association | Cart pane action | Cart sheet action | |
| Park / resume sale | Cart header overflow | Cart sheet overflow | Existing capability |
| Quotation create/send/convert | Cart overflow | Cart sheet overflow | Action plan L4 |
| Discount | Cart, manager reauth | Cart sheet, manager reauth | L2 — Admin or shop manager |
| Void | Cart, manager reauth | Cart sheet, manager reauth | L2 |
| Refund | Returns destination | Returns via More | L3 — posts through `post_finance_refund` |
| Price override | Line overflow, manager reauth | Line overflow, manager reauth | L2 |
| Proceed to payment | Pane CTA | Cart bar CTA → checkout screen | Reserve-first (§10.6) |
| Split tender | Payment surface | Dedicated tender screen, one leg per step | Same normalised state |
| Terminal / EcoCash / Paynow | Payment surface | Same adapters | Online only |
| Payment recovery | Dedicated surface | Dedicated screen | Never a sheet — §10.4 |
| Print receipt | ESC/POS bridge | ESC/POS bridge | Bridge-First |
| Till / shift open-close | Rail → Till | More → Till | |
| Offline cash sale | Restricted mode | Restricted mode | Identical rules |
| Pin / unpin | Long-press | Long-press | §7.2 |

### 9.4 Compact choreography

- **Checkout is navigation, not a layer.** At Expanded the cart is already visible, so payment can
  be a focused layer. At Compact, `Proceed to Payment` pushes a dedicated screen: review → tender →
  confirm. Each split-tender leg is its own step with the backend-returned remaining balance as the
  step header.
- **Two-layer maximum.** No more than two transient layers deep; a third promotes to a screen.
- **Thumb reach.** Primary actions occupy the lower third. Destructive actions are never adjacent
  to frequent positive actions without separation.
- **Keyboard discipline.** A paired physical scanner must not raise the soft keyboard.

### 9.5 Compact-specific certification

Golden surfaces at `412×915` and `360×800`: Sell home, cart sheet, checkout review, tender step,
offline state, recovery. Plus a font-scale variant at the documented maximum (§11.3).

---

## 10. Application architecture

The current POS is a 947-line screen talking to a 102-method interface through a 1533-line
ViewModel with two tests. That shape is why it is hard to make it look right, hard to make it fast,
and impossible to certify. The architecture below is the recommendation, independent of it.

### 10.1 Module structure

One feature is not one module. Split so that the design system and the domain can be tested without
Android, and so screenshot tests can run on the JVM against fakes:

```text
packages/pos-design/            PosTheme, tokens (generated), primitives, component library
                                — no domain types, no gateways, Compose only
apps/android-management/
  feature/pos-domain/           PosState, PosIntent, PosEvent, reducers, projections
                                — pure Kotlin, zero Android dependencies, 100% unit-testable
  feature/pos-data/             gateway implementations, DTO mapping, offline outbox, cache
  feature/pos-ui/               composables, PosStore wiring, navigation
  feature/pos-ui/src/test/      Roborazzi screenshot tests against fake gateways
```

`pos-domain` having no Android dependency is the load-bearing constraint: it makes the payment,
reservation and recovery state machines testable in milliseconds, which is the only way they get
tested at all.

Dependency injection: adopt **Hilt**. There is none today (`PosModule` is a three-line stub), and
constructor-injected gateways are what make the fakes above possible.

### 10.2 Typed gateways — replacing the God interface

A 102-method `RpcClient` shared by catalogue, cart, checkout, manager reauth, quotations, companion
pairing and offline replay is the root technical cause of the current POS's problems. Replace it
with narrow, feature-owned gateways:

```kotlin
interface CatalogGateway {
    suspend fun search(q: SearchQuery): PosResult<SearchPage>
    suspend fun categories(): PosResult<List<Category>>
    suspend fun product(id: ProductId): PosResult<Product>
}

interface CartGateway {
    suspend fun open(warehouse: WarehouseId, currency: CurrencyCode): PosResult<CartId>
    suspend fun addLine(cart: CartId, product: ProductId, qty: Int): PosResult<CartProjection>
    suspend fun setQuantity(cart: CartId, line: LineId, qty: Int): PosResult<CartProjection>
    suspend fun attachCustomer(cart: CartId, customer: CustomerId?): PosResult<CartProjection>
}

interface CheckoutGateway {
    suspend fun reserve(cart: CartId, key: IdempotencyKey): PosResult<CheckoutSnapshot>
    suspend fun release(reservation: ReservationId): PosResult<Unit>
    suspend fun finalize(reservation: ReservationId): PosResult<Receipt>
}

interface TenderGateway {
    suspend fun capabilities(snapshot: CheckoutSnapshot): PosResult<List<TenderCapability>>
    suspend fun submit(leg: TenderLeg, key: IdempotencyKey): PosResult<TenderOutcome>
}
```

plus `FitmentGateway`, `PinGateway`, `TillGateway`, `RecoveryGateway`, `QuotationGateway`. Each is
declared in `pos-domain` and implemented in `pos-data`. Eight small interfaces are trivially fakeable;
one large one is not.

**Business outcomes are values, never exceptions.**

```kotlin
sealed interface PosResult<out T> {
    data class Ok<out T>(val value: T) : PosResult<T>
    data class Err(val error: PosError) : PosResult<Nothing>
}
```

The current code throws `IllegalStateException("Offline checkout is cash-only (EcoCash/Paynow
require live rails)")` for an ordinary business rule. That is control flow through exceptions, and
the message is engineering prose one `catch` away from an operator's screen. A restricted tender is a
*modelled state*, surfaced by disabling the tender with a reason — not a thrown error.

**Idempotency keys are mandatory** on every mutating call that moves money or stock. They are what
makes a retry after a dropped connection safe, and they are the difference between "we think the
charge went through" and knowing.

### 10.3 State — one store, pure reducers, scoped projections

Three ViewModels sharing a `cartId` inside one `StateFlow` is the shape to avoid; so is one
ViewModel that owns everything. Use a single store over a pure reducer, with screens selecting
narrow projections.

```kotlin
// pos-domain — pure, no Android, no coroutines in the signature
fun reduce(state: PosState, event: PosEvent): Reduction   // Reduction(state, effects)

class PosStore(scope: CoroutineScope, effects: PosEffectHandler) {
    val state: StateFlow<PosState>
    fun dispatch(intent: PosIntent)
}
```

- **Reducers are total and pure.** Same state + event ⇒ same result. This is what makes split
  tender, reserve-first and payment recovery testable without a device or a backend.
- **Effects are declarative.** A reducer returns `Effect.Reserve(cartId, key)`; the handler calls the
  gateway and dispatches the resulting event back. No reducer performs I/O.
- **Screens read projections, not state.**

```kotlin
val cart:      StateFlow<CartProjection>      = store.select { it.toCartProjection() }
val discovery: StateFlow<DiscoveryProjection> = store.select { it.toDiscoveryProjection() }
```

  Every projection is `@Immutable` with stable keys. A quantity change must not recompose the
  catalogue — §10.11 makes that a measured gate, not an aspiration.

Illegal combinations are structurally impossible rather than guarded: there is no
`isCartLocked` boolean to contradict an `isEditable` boolean, because the state is a sealed
hierarchy where "locked" and "editable" are different types.

### 10.4 Navigation

Type-safe routes (Navigation Compose 2.8+ `@Serializable` destinations), **one graph for both form
factors**. The window class decides *presentation*, never *existence*:

| Destination | Expanded | Compact |
|---|---|---|
| Cart | Persistent pane | Summary bar → full sheet |
| Checkout | Focused layer over the canvas | Pushed screen |
| Product detail | Anchored popover | Bottom sheet |
| Vehicle cascade | Inline header fields | Full-height sheet |
| Recovery | Dedicated screen | Dedicated screen |

Forking the graph per form factor is how parity rots — a destination gets added on the tablet and
silently never reaches the phone. One graph makes the §9.3 parity table enforceable.

Recovery is always a dedicated screen on both. An ambiguous payment is not a thing to dismiss by
tapping outside it.

### 10.5 Cart and checkout state machine

```kotlin
sealed interface CheckoutState {
    data object Idle : CheckoutState
    data class Open(val cart: CartProjection) : CheckoutState
    data class Reserving(val cart: CartProjection, val key: IdempotencyKey) : CheckoutState
    data class Locked(val snapshot: CheckoutSnapshot) : CheckoutState
    data class TenderInFlight(val snapshot: CheckoutSnapshot, val leg: TenderLeg) : CheckoutState
    data class PartiallyPaid(val snapshot: CheckoutSnapshot, val remaining: Money) : CheckoutState
    data class RecoveryRequired(val recovery: RecoveryContext) : CheckoutState
    data class Settled(val receipt: Receipt) : CheckoutState
}
```

Only `Open` permits cart mutation — the type system enforces it, so no UI path can edit a reserved
cart. `PartiallyPaid.remaining` is **always** the backend's figure; the frontend never computes a
remaining balance.

### 10.6 Reserve-first — the contract to build

Rev 1.4 marked this "blocked on backend". That defers the most important correctness property in
the product. Specify it here so `@backend_agent` can build it:

```text
reserve_pos_cart(p_cart_id uuid, p_idempotency_key text)
  → { reservation_id, expires_at, snapshot: { lines[], subtotal, tax, total, currency } }
  · validates cart, session, warehouse, price drift
  · reserves stock atomically; fails closed with the shortfall per line
  · idempotent on p_idempotency_key — a retry returns the same reservation
  · TTL (recommend 10 minutes), extended by tender activity

release_pos_reservation(p_reservation_id uuid)     -- explicit cancel or TTL sweep
finalize_pos_sale(p_reservation_id, p_tenders[])   -- posts through existing checkout SoR
```

Expiry behaviour must be designed, not discovered: on expiry the UI returns to `Open` with the
lines intact and a non-destructive notice, and re-reserves on the next attempt. Stock must never be
held by an abandoned cart, and an operator must never be told a sale failed because a timer they
could not see ran out.

### 10.7 Errors are data

```kotlin
sealed interface PosError {
    data class Transient(val retryable: Boolean) : PosError
    data class Input(val field: FieldRef, val reason: InputReason) : PosError
    data class BusinessRule(val rule: RuleId, val detail: RuleDetail) : PosError
    data class PaymentUnknown(val correlation: CorrelationRef) : PosError
    data class HardwareUnavailable(val device: DeviceKind) : PosError
    data class OfflineRestricted(val blocked: Set<TenderType>) : PosError
}
```

Every variant maps to a string resource keyed by `RuleId` — so operator copy is written once, by
someone who writes copy, and reviewed independently of the code. No `catch (e: Exception) { e.message }`
reaches a screen. Never surface exception names, HTTP codes, SQL terms or stack traces.

`PaymentUnknown` is not failure. It means the system cannot prove whether funds moved: block
further charging for that amount and provider, carry the correlation reference into recovery, and
require an explicit resolution path.

### 10.8 Offline

Offline is a restricted mode with modelled restrictions, not a degraded imitation of online:

- Local catalogue search stays available.
- **Cash only** — non-cash tenders render disabled with the reason, never fail on submit.
- **Walk-in only** — named credit customers require live credit state.
- Discount, void, refund and price override stay online: they need manager reauth against a live
  RPC (action plan L2), and caching an approval token is a privilege-escalation hole.
- Ambiguous prior payments stay blocked from retry.
- Sync state is explicit in the header (D-005).

Sales queue through an encrypted outbox with a client-generated id, replayed idempotently; price
drift and stock shortfall surface as **conflicts for operator review**, never as invented ledger
rows. Card and mobile-money requests are never queued.

### 10.9 Semantic states

| Family | Treatment | Consequence |
|---|---|---|
| Positive | `success` + icon + label | Continue |
| Attention | `warning` + icon | Continue with awareness |
| Blocking | `error` `#8E0F22` + icon + explicit action | Progression blocked |
| Unknown | `unknown` + distinct icon | **Duplicate charge blocked** |
| Offline | `offline` amber + label | Tender set restricted |
| Selected | `brandRed` | Current context |

Colour is never the only signal — every family carries an icon and a label, which is also what makes
the design legible to a colour-blind operator and to a screenshot diff.

### 10.10 Copy

No tutorial paragraphs, no helper copy on obvious controls (D-008), verbs on buttons, errors that
state what happened and the next permitted action, no engineering language, no placeholder or fake
data in production builds. Every operator string is a resource keyed to §10.7. Deterministic preview
data lives only in a debug or test source set.

### 10.11 Performance

Targets apply to the supported baseline device — to be named from the pilot fleet's lowest spec.

- Touch feedback within one frame; local cart acknowledgement immediate.
- Indexed local catalogue lookup < 100 ms; first useful search results < 250 ms.
- Sustained 60 fps on cart and Quick Access scrolling.
- No blank intermediate frame on transition; no spinner for trivial local work.

Enforced, not hoped for: **Baseline Profile generated from day one** and a Macrobenchmark suite in
CI. Compose discipline — stable keys, `@Immutable` projections, `derivedStateOf` only where
measured, pre-sized images, async decode, no I/O in composition, and cart recomposition isolated
from catalogue by construction (§10.3), verified by a recomposition-count test.

Prefer local-first rendering. Where loading is genuinely required, use skeletons that match final
geometry exactly; indeterminate spinners are not a default.

### 10.12 Product imagery

A deterministic media contract per catalogue image: background treatment, bounding-box padding, crop
mode, aspect class, thumbnail variants, fallback, cache key and version. Adjacent cards must not
show wildly different object scales — this is the single largest contributor to a catalogue looking
cheap. Missing image resolves to a neutral placeholder, never a bright illustration that steals
emphasis. Hero imagery is treated separately from product imagery.

### 10.13 Accessibility

Contrast to WCAG AA; 48 dp minimum touch targets at every window class; focus order following
visual order; predictable keyboard and D-pad traversal; TalkBack labels describing action and state
in operator language; colour never the sole signal; reduced motion honoured; font scaling to a
documented maximum with layout adaptation rather than clipping.

Where accessibility scaling and benchmark proportion conflict, accessibility wins — and the scaled
layout is still certified (§11.3 requires golden surfaces at maximum font scale).

---

## 11. Certification

Rev 1.3 gated on pixel equality. That gate was unsatisfiable (its own illustrative coordinates were
6–10 Rpx off the real ones, against a ±2 Rpx tolerance) and incompatible with an adaptive product.
Rev 1.4 certifies **proportion, grammar and behaviour**.

### 11.1 Gates

| Gate | Asserts | Fails when |
|---|---|---|
| **V1 Structure** | Required zones and components exist and nest correctly | A band or component is missing |
| **V2 Proportion** | Zone ratios within ±1.5%, band ratios ±3%, card aspect ±5%, at every reference size | Layout drifts from the measured composition |
| **V3 Grammar** | Every colour, radius, spacing, type style and icon resolves to a token | A raw literal appears in feature code |
| **V4 Typography** | Family, role, weight, line height, wrapping, tabular numerals | A role is substituted or numerals are proportional |
| **V5 Assets** | Icon family, hero crop, product-image treatment | A foreign icon set or inconsistent image scaling |
| **V6 Provenance** | Every benchmark difference maps to a registry row | An unregistered difference exists |
| **V7 Responsive identity** | Compact preserves tokens, grammar, semantics, state machines | The phone reads as a different product |
| **V8 Design language** | §5 permission matrix respected; every glass surface renders both capability treatments; motion within tier durations; no forbidden pattern present | Glass on a structural surface, a second accent hue, decorative animation, or an unrendered blur fallback |
| **V9 Behaviour** | State machines, authorisation gates, offline restrictions | A gate is bypassable from the UI |
| **V10 Owner sign-off** | Final render approved at the canonical frame | — |

**V10's owner is the shop owner**, recorded as a dated row in the delta registry. Rev 1.3 referred to
"the owner" nine times without ever naming who signs.

### 11.2 Reference sizes

Every gate runs at `1280×800`, `1024×768`, `800×1280`, `412×915`, `360×800`.

### 11.3 Golden surfaces

Expanded: Home empty cart · Home populated · search active · vehicle cascade open · Quick Access
populated · Quick Access empty · cart locked · payment · offline · EPC browse.
Compact: Sell home · cart sheet · checkout review · tender step · offline · recovery.
Design language: every glass surface twice — API 31+ backdrop blur and the pre-31 fallback (§5.3).
Empty states: empty cart, empty Quick Access, no search results, no vehicle selected.
Accessibility: Expanded Home and Compact cart at the documented maximum font scale.

Perceptual comparison uses a locked threshold with a difference heatmap, never a demand for
mathematically zero pixel difference — Android font rasterisation makes that unrealistic.

### 11.4 Design lint

CI fails the build on any of these inside `apps/android-management/feature/pos/`:

- raw colour literals outside token definitions;
- ad hoc `RoundedCornerShape` values outside the design system;
- any reference to `MaterialTheme.colorScheme` or `MaterialTheme.typography` in POS components;
- exceptions thrown for business outcomes instead of returning `PosError`;
- a bare `Double`/`BigDecimal` used as money without a currency;
- unapproved icon libraries, including `material-icons-extended` (§4.7);
- a `Reports` navigation destination;
- **any integer literal used as an item count in a lazy row** — counts are derived (§3.3);
- fake payment providers in the production source set;
- a duplicate canonical POS screen implementation;
- price or tax arithmetic inside a composable;
- `Modifier.blur` applied without a declared pre-API-31 fallback (§5.3);
- a second saturated accent hue, gradient-filled text, or a glow effect;
- an infinite-repeating animation outside a designed loading state.

### 11.5 Harness

None of this exists yet — the repo has no screenshot tooling, no Macrobenchmark module, and two
test files in `feature/pos`. **The harness is a Phase 1 deliverable, not a Phase 9 one.** Standing
it up (screenshot runner, lint script, reference-size matrix) gates everything downstream.

Performance certification adds Macrobenchmark + Baseline Profiles for: cold launch into POS, Home
render, search typing, Quick Access fling, barcode add-to-cart, repeated quantity change, proceed to
payment, tender step, cart → receipt.

---

## 12. Capability continuity contract

The POS is rebuilt clean. No file in `feature/pos` is preserved on the grounds that it exists — the
rebuild is judged on capability, not on code lineage.

What the truth protocol forbids is losing a capability silently. So the contract is a list of
**behaviours that must exist when the rebuild ships**, each with where its logic now lives. How they
are implemented is this document's recommendation, not the old code's precedent.

| Capability | Must survive as | Home in the new architecture |
|---|---|---|
| Catalogue search (name, part number, fitment, barcode, EPC) | Same coverage, cancellable, debounced | `CatalogGateway` + discovery projection |
| Vehicle fitment cascade | Extended — now permanent in the header (D-002) | `FitmentGateway` + session fitment context |
| Cart line add / quantity / remove | Same, with optimistic echo and authoritative settle | `CartGateway` + reducer |
| Split-bill tenders | Same, as explicit legs with backend-returned remaining balance | `TenderGateway` + `CheckoutState` |
| Offline cash sale queue | Same rules — cash-only, walk-in-only — as *modelled restrictions*, not thrown exceptions | `pos-data` outbox + `PosError.OfflineRestricted` |
| Offline replay and conflict surfacing | Same idempotent replay; conflicts reviewable | `pos-data` sync engine |
| Manager reauth: discount, void, refund, price override | Same, online-only, RPC-enforced (L2) | `PosError.BusinessRule` + reauth flow |
| Refunds through the finance pipeline (L3) | Unchanged — no parallel refund path | Existing finance RPC |
| Quotations create / send / convert (L4) | Same | `QuotationGateway` |
| Park and resume sale | Same | `CartGateway` |
| Companion scan pairing | Same | Bridge + `pos-data` |
| ESC/POS receipt printing | Same, Bridge-First | `bridges/android/escpos-printer` |
| QR / barcode camera scanning | Same, Bridge-First | `bridges` + CameraX |
| Till / shift open and close | Same | `TillGateway` |
| EPC browse | Same hierarchy, rebuilt on the new design system | `CatalogGateway` hierarchy calls |
| Currency and exchange rate on every money field | Same — `AGENTS.md` law | `Money` value type, never a bare `Double` |

Anything a current screen does that is not in this table is an omission in the table, not a licence
to drop it. Add the row.

**Explicitly not preserved:** `PosScreen.kt`'s composition, its two-pane fallback, the `ui.shop`
component set inside POS, `PosViewModel`'s structure, the `RpcClient` interface shape, the
Material3-slot theming path, and `material-icons-extended`. Each is replaced by a recommendation
in §4 and §10.

**Out of scope:** `apps/web/(staff)/staff/pos` is a different platform and a legitimate surface, not
a duplicate POS to delete. It inherits the same generated tokens (§4.1) and nothing else.

### 12.1 Sequence

| Phase | Deliverable | Gate |
|---|---|---|
| **0 Freeze** | Benchmark committed · delta registry · reference spec · this document | Done |
| **1 Foundations** | Module split (`pos-design` / `pos-domain` / `pos-data` / `pos-ui`) · Hilt · Style Dictionary pipeline · Roborazzi + Macrobenchmark + design-lint CI · lossless benchmark resample | Harness green on an empty baseline; tokens generate for all three platforms |
| **2 Design system** | `PosTheme` · generated tokens · Inter + display face · Lucide vendoring · component library · adaptive primitives (`PosScaffold`, clamp law) | V3 on a component gallery, screenshot-tested at all five reference sizes |
| **3 Domain core** | `PosState` · reducers · projections · gateway interfaces · fakes | Reducer suite covers cart, reserve, split tender, recovery — on the JVM |
| **4 Expanded shell** | Rail · header + cascade · hero · category row · Quick Access · cart pane, against fakes | V1, V2, V4, V5 at every Expanded size |
| **5 Data binding** | Gateway implementations · search · fitment · categories · EPC · image pipeline | Contract tests per gateway |
| **6 Cart and Quick Access** | Cart flows · long-press pin/unpin · reorder · heterogeneous cards · **pin RPCs** | Pins persist per operator across devices; recomposition isolation verified |
| **7 Compact** | Phone shell · cart sheet · checkout · tender steps · bottom navigation | V7 + compact goldens; §9.3 parity table fully exercised |
| **8 Checkout** | **Reserve-first (§10.6 contract)** · split tender · terminal adapters · unknown → recovery | V9; no duplicate charge reachable under fault injection |
| **9 Hardware** | Scanner · CameraX · ESC/POS · terminal capability matrix | Bridge-First verified on target devices |
| **10 Certification** | Full golden suite · Macrobenchmark · security · accessibility · owner sign-off | All gates green |

Phase 1 is the one that is usually skipped and the one that determines whether any of the rest is
achievable. Two backend dependencies — operator pins (§7.4) and reserve-first (§10.6) — are specified
now so they can be scheduled into `@backend_agent`'s lane rather than discovered at Phase 6 and 8.

---

## 13. Definition of done

- Expanded Home matches the benchmark's **proportions, grammar and hierarchy** at every reference
  size, within §11.1 tolerances.
- Every difference from the benchmark is registered with an owner reference.
- No `Reports` destination in POS navigation.
- Quick Access is operator-scoped, heterogeneous, unbounded, pinnable by long-press from anywhere,
  server-persisted, with **no literal item count in the codebase**.
- Vehicle cascade is permanent in the header at Expanded/Medium and recomposes at Compact, driving a
  visible, clearable fitment context.
- The phone performs every operation in the §9.3 parity table.
- Money is backend-authoritative with explicit currency; no `KSh`, no hardcoded VAT rate, no ZIMRA.
- Copy is correct — `GENUINE PARTS`, `Add Customer` — and lives in string resources.
- Error red is visually distinct from brand red.
- Offline restrictions (cash-only, walk-in-only, no queued card/mobile-money) are enforced and
  visible.
- Authorisation gates are RPC-enforced; hiding navigation is never the control.
- Scanner, CameraX and receipt printing are real bridge integrations.
- Golden, performance, accessibility and security gates pass; design lint is green.
- The owner signs off the canonical-frame render, dated in the registry.

Additionally, as architecture gates: the domain module builds with no Android dependency and its
reducer suite runs on the JVM; tokens are generated from `brand-tokens.json` for all three
platforms; no POS component references `MaterialTheme`; and a Baseline Profile ships with the build.

Reserve-first and server-persisted pins may be **explicitly deferred** with their backend contract
named and scheduled — but never silently marked done.

---

## 14. Coherence

1. The benchmark supplies composition — within the scope §2 fixes.
2. Measured ratios supply adaptivity; pixels supply provenance only.
3. Industrial minimalism supplies the base language.
4. Soft borders and restrained depth supply hierarchy.
5. Modularity supplies organisation, never arbitrary reshaping.
6. Glass supplies transient focus, never permanent structure — and declares its fallback.
7. Progressive disclosure suppresses clutter.
8. Editorial typography and tabular numerals supply scanability.
9. Semantic colour supplies meaning — and brand red is not error red.
10. Motion and haptics supply feedback, not decoration.
11. Recomposition supplies device fitness; tokens supply identity across devices.
12. State machines supply behavioural determinism.
13. The delta registry supplies governance.
14. Backend contracts supply truth; the UI renders, it does not calculate.

The result should not read as a set of combined trends, nor as a tablet layout squeezed onto a
phone. It should read as one mature product that happens to know what size screen it is on.

**clean, expensive, fast, obvious, trustworthy and unmistakably Nissan GTR Auto.**
