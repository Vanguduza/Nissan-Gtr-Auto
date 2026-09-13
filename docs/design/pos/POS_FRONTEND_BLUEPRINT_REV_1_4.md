# Nissan GTR Auto POS — Frontend Development System

**Revision:** 1.4 — Adaptive Fidelity Edition
**Date:** 2026-09-13
**Lane:** `@management_app_agent` (UI) · `@backend_agent` (pin + fitment + reservation contracts)
**Status:** accepted — supersedes Rev 1.3 in full
**Implementation path:** `apps/android-management/feature/pos` (tablet + phone, shared module)
**Visual reference:** [`reference/benchmark-home-expanded-2026-09-07.jpg`](reference/benchmark-home-expanded-2026-09-07.jpg) · geometry in [`VisualReferenceSpec.json`](VisualReferenceSpec.json) · deltas in [`APPROVED_VISUAL_DELTAS.md`](APPROVED_VISUAL_DELTAS.md)

> **Prime directive.** The POS shall read as one mature product — the benchmark's composition, hierarchy
> and restraint — on every screen it runs on. Fidelity is measured as **proportion, grammar and
> behaviour**, never as pixel equality. A difference that cannot be traced to a rule in this document
> or a row in the delta registry is a regression.

---

## 0. What changed from Rev 1.3, and why

Rev 1.3 was a sound governance charter with three structural faults: it gated on pixel equality
against a single raster, it specified token *names* without *values*, and it was written as if it
governed an existing implementation rather than a rebuild. Rev 1.4 keeps the governance and fixes
the engineering.

| Area | Rev 1.3 | Rev 1.4 |
|------|---------|---------|
| Fidelity model | ±2 Rpx against a 1536×1024 raster | Ratio + grammar + behaviour across four window classes (§3, §10) |
| Geometry source | Illustrative JSON, "measure later" | Measured, committed, ratio-normative `VisualReferenceSpec.json` |
| Tokens | 23 colour names, 0 values; radius/elevation/type roles with no numbers | Complete valued system routed through `brand-tokens.json` (§4) |
| Benchmark authority | Total (A1 over everything) | Scoped to composition and geometry; copy, currency and tax explicitly excluded (§2) |
| Popular row | "no seven-item cap" | **Quick Access panel** — operator-pinned, heterogeneous, unbounded (§6) |
| Vehicle cascade | Progressive disclosure out of search | **Permanent header cascade** replacing the taxonomy line (§7) |
| Phone | "compact recomposition" of the tablet | A designed phone POS with full operational parity (§8) |
| Scope framing | Governs the existing POS | Explicit rebuild with a per-file migration contract (§11) |
| Repo governance | Not referenced | Subordinated to the truth protocol and the locked action plan (§1) |

---

## 1. Authority hierarchy

Rev 1.3 declared itself "canonical implementation and certification authority". It is not — the
repository already has governance that outranks any design document. Resolve conflicts strictly in
this order.

| Priority | Authority | Scope |
|---|---|---|
| **A0** | [`PROJECT_TRUTH_PROTOCOL.md`](../../../PROJECT_TRUTH_PROTOCOL.md) + [`PROJECT_CANONICAL_STATE.json`](../../../PROJECT_CANONICAL_STATE.json) | Lineage, release blocking, the prohibition on silent feature thinning. Nothing below may delete a capability to satisfy a visual goal. |
| **A1** | [`AGENTS.md`](../../../AGENTS.md) hard exclusions + global laws | No ZIMRA, no payroll tax, Bridge-First, RLS mandate, ledger immutability, explicit multi-currency. |
| **A2** | [Tablet Kiosk POS action plan](../../plans/2026-08-03-tablet-kiosk-pos-full-action-plan.md) §10 locked decisions | Separate tablet/phone APKs (L1), Admin‑or‑shop‑manager authorisation (L2), finance refund pipeline (L3), quotations (L4), idle timeout (L5). |
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

### 1.2 Adopt-first is binding

Action plan §2 and §9 forbid rebuilding the Batch 1 cart/checkout system of record. This document
rebuilds **the POS user interface only**. Every RPC, reducer boundary and offline contract that
exists today is reused. "Delete the legacy POS" in Rev 1.3 §13.1 is narrowed to: delete the legacy
POS **composables**, and only after their behaviour is carried forward per §11.

### 1.3 No accidental redesign

Before changing any canonical geometry — a zone ratio, a band height, a component's anatomy —
answer all five:

1. Is the change required by an owner-approved functional requirement?
2. Is it required for accessibility or for a supported window class?
3. Is it already a row in the delta registry?
4. Does it preserve the benchmark's hierarchy and product identity?
5. Does it still pass the §10 gates afterwards?

If 1, 2 and 3 are all no, do not make the change. Raise it instead.

---

## 2. Benchmark authority scope

The benchmark is a rendered mock. It is authoritative for **composition, hierarchy, proportion,
component anatomy and visual treatment**. It is **not** authoritative for:

| Not authoritative | Evidence in the benchmark | Governs instead |
|---|---|---|
| Currency | `KSh` (Kenyan shillings) | Backend money state; USD / ZiG; `AGENTS.md` multi-currency law |
| Tax rate and model | `VAT (16%)` — the Kenyan rate | Backend tax policy; invoices stay tax-agnostic (no ZIMRA) |
| Copy strings | `GNGUINE PARTS` (typo); `Add Customer (Optional)` (helper copy) | §9.4 copy rules |
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
lint-enforced (§10.4).

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

### 4.1 Source of truth

`packages/ui/brand-tokens.json` → `@gtr/ui` (web) and `co.zw.nissangtr.ui.theme.GtrColors` (Android)
is the **only** place a brand primitive may be defined. It already carries the warning *"Do not
invent alternate brand hues."*

The POS defines a **semantic layer over** those primitives — it does not fork them:

```kotlin
// packages/android-ui/src/main/java/co/zw/nissangtr/ui/pos/PosPalette.kt
@Immutable
data class PosPalette(
    val navBackground: Color,      // GtrColors.Steel
    val navSurfaceRaised: Color,   // GtrColors.SteelLift
    val navActiveFill: Color,      // GtrColors.Primary
    val brandRed: Color,           // GtrColors.Primary
    val brandRedPressed: Color,    // GtrColors.PrimaryHover
    val canvas: Color,             // GtrColors.Chalk
    val surfacePrimary: Color,     // GtrColors.White
    val surfaceElevated: Color,    // GtrColors.White + elevation.2
    val borderSubtle: Color,       // GtrColors.Mist
    val borderStrong: Color,       // GtrColors.Silver
    val textPrimary: Color,        // GtrColors.Steel
    val textSecondary: Color,      // GtrColors.SilverDim
    val textMuted: Color,          // GtrColors.StockBo
    val success: Color,            // GtrColors.StockIn
    val warning: Color,            // GtrColors.StockLow
    val error: Color,              // NEW primitive — see 4.2, must not equal brandRed
    val offline: Color,            // GtrColors.Warning (amber)
    val scrim: Color,
)
```

Every field but `error` resolves to a primitive that already exists. The POS is not getting a new
palette — it is getting **named POS roles over the brand palette it already uses**.

Naming note: Rev 1.3 proposed `object GtrColor`, one character from the existing `GtrColors`. That
collision is rejected outright.

### 4.2 Colour values

Measured values are provisional until resampled from a lossless export; the ratios and the
*decisions* below are not.

| Semantic | Value | Provenance |
|---|---|---|
| `canvas` | `GtrColors.Chalk` `#F4F5F7` | Measured `#F3F4F8`. **Within JPEG error of the token already in use** — the shipped POS canvas is already correct. No migration. |
| `navBackground` | `GtrColors.Steel` `#12151C` | Measured `#1B2024`. Measurement sits between `Steel` and `SteelLift` `#1E2430`; both are cool. Confirm which on the lossless export. |
| `surfacePrimary` | `GtrColors.White` `#FFFFFF` | Measured `#FEFEFE` |
| `heroBackdrop` | `#0A0C0E` | Measured `#060709` — darker than `Steel`; hero-only, add to `brand-tokens.json` if adopted |
| `brandRed` | `GtrColors.Primary` `#C8102E` | Measured `#CB1432`, within JPEG error. Adopt the token, not the sample. |
| `error` | **`#8E0F22` — new primitive** | Decision, not measurement — see below |
| `offline` | `GtrColors.Warning` `#B45309` | Existing token |
| `success` | `GtrColors.StockIn` `#0B6E4F` | Existing token |

**Sampling confirms the existing palette.** Rev 1.3 hedged on whether the dark neutrals carry a blue
bias and proposed a token named `CanvasWarm`. Measurement settles both: the rail is cool
(`#1B2024`, B exceeds R by 9) and the canvas is cool (`#F3F4F8`) — and `GtrColors.Chalk`, which the
POS already renders through `ShopTheme` → `GtrTheme`, is `#F4F5F7`. There is no warm-to-cool
migration to perform. `CanvasWarm` was simply the wrong name for a colour the app already had right.

*(The warm surface — `ShopWarmTheme`, background `#F7F1EA` — belongs to the **customer** app
(`CustomerShopTheme`). It is not in the management/POS path and is unaffected by this document.)*

**The red collision, resolved.** `GtrColors.Danger` and `GtrColors.Primary` are the same byte value
`#C8102E`, and `GtrTheme`'s light scheme wires `error = GtrColors.Danger` — so today a blocking
error and the primary call to action are *the same colour*. Rev 1.3 raised this and left it optional
("may use a differentiated deep red"). It is now decided: **error resolves to a darker `#8E0F22`,
always paired with an error icon**, and brand red is reserved for primary CTA, active navigation and
brand emphasis. This is the one genuinely new primitive in §4.1 and it belongs in
`packages/ui/brand-tokens.json` so web and iOS inherit the same separation. Colour is never the only
status signal (§9.3). Registered as **D-011**.

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

**Families are already chosen and vendored — do not introduce a new one.** `GtrTypography` pairs
**Titillium Web** (display / chrome) with **Source Sans 3** (body), both OFL, vendored under
`res/font/`. Rev 1.3's "choose and lock one primary UI family, package and license it appropriately"
is already satisfied; the POS maps its semantic roles onto that pair rather than adding a third
family and a new licence obligation.

- `display.*`, `heading.*`, `label.action`, `numeric.total` → `GtrDisplayFont` (Titillium Web)
- `body.*`, `label.meta`, `numeric.price`, `numeric.quantity` → `GtrBodyFont` (Source Sans 3)

Sizes are at the canonical frame. Compact drops one step where marked. Confirm the pairing against
the lossless reference in Phase 1; if the benchmark used something else, that is a delta to register,
not a silent substitution.

| Role | Size / line | Weight | Compact | Use |
|---|---|---|---|---|
| `display.hero` | 32 / 38 | 700 | 24 / 30 | Hero headline only |
| `heading.1` | 24 / 30 | 700 | 20 / 26 | Pane title, Total |
| `heading.2` | 20 / 26 | 600 | 18 / 24 | Section titles |
| `heading.3` | 16 / 22 | 600 | — | Card titles |
| `body.primary` | 14 / 20 | 400 | — | Product names, labels |
| `body.secondary` | 12 / 16 | 400 | — | Part numbers, metadata |
| `label.action` | 14 / 18 | 500 | — | Buttons, nav labels |
| `label.meta` | 11 / 14 | 500 | — | Date, role, small metadata |
| `numeric.price` | 15 / 20 | 600 | — | Unit price — **tabular** |
| `numeric.total` | 24 / 30 | 700 | 20 / 26 | Total, remaining balance — **tabular** |
| `numeric.quantity` | 14 / 18 | 500 | — | Quantity, stock — **tabular** |

All `numeric.*` roles use tabular figures (`FontFeature "tnum"`). Never synthetic bold.

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

`GtrDensity` already exists (`Standard` / `Compact`) and carries `screenPadding`, `sectionGap` and
`homePadding` through `LocalGtrExtras`. The POS adds a third intention rather than a parallel system:

| Intention | Maps to | Use |
|---|---|---|
| `Comfortable` | `GtrDensity.Standard` | Touch-heavy, lower information density |
| `Operational` | **POS default** — `Standard` padding, tighter section gaps | Counter POS |
| `Compact` | `GtrDensity.Compact` | Constrained displays, data-heavy management surfaces |

Density may change padding, row height and secondary-metadata visibility within bounded rules. It
may never reduce a touch target below 48 dp or move a primary CTA out of reach.

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

## 5. Component anatomy (Expanded)

### 5.1 Navigation rail — 144 dp

Top to bottom: logo lockup · eight destinations · flexible spacer · GT-R artwork · brand statement
(`BUILT FOR A HIGHER STANDARD`). Both bottom blocks are **present in the benchmark** — Rev 1.3's
"if retained" / "if present" conditionals are resolved as yes.

Destinations, canonical order:

**Home · Search Spares · Quick Sale · Customer · Orders · Returns · EPC Browse · Settings**

`Reports` is forbidden on the salesperson rail (D-001). The lint rule is scoped to
`apps/android-management/feature/pos/` so it cannot false-positive on legitimate dashboard reporting.

Active: brand-red filled rounded rect, 48 dp tall, `radius.sm`, white icon and label, no glow, no
gradient. Inactive: transparent, muted icon and label.

### 5.2 Header — 96 dp

Four zones: **vehicle cascade** (§7, replacing the taxonomy line) · dominant search field ·
operator identity · date/time. Offline status surfaces in the context zone when applicable (D-005).

Search field: 48 dp tall, max 560 dp, leading search icon, trailing scanner affordance. No
permanent filter clutter.

### 5.3 Hero — 240 dp, collapsible to 160

Dark GT-R identity panel: image, headline, supporting line, three value indicators
(`GENUINE PARTS` — D-007). No permanent cascade dropdowns; the cascade lives in the header.
Permitted: overlay gradient for legibility, restrained ambient highlight, crossfade on legitimate
asset change. Forbidden: continuous parallax, decorative motion.

### 5.4 Category row — 96 dp

Icon-first modular cards, `radius.md`, 96 dp square nominal, 12 dp gaps, horizontally scrollable.
Seven cards is the benchmark's *output at 1280 dp*, not a cap.

### 5.5 Cart pane — 352 dp, min 320

Order: header (`Current Sale` + clear) · scrollable item list · `Add Customer` (D-008) ·
subtotal / discount / tax · divider · total · `Proceed to Payment`.

Only the item list scrolls. Totals and the CTA stay reachable.

**Row anatomy** — fixed columns: thumbnail · name · part number · **unit price** (D-012) ·
overflow · quantity stepper. Deterministic truncation; name wraps to at most 2 lines; part number
in `body.secondary`; price in `numeric.price`.

Money rows render backend values with explicit currency (D-006). A zero discount row stays visible
— the operator reads the same row positions on every sale.

---

## 6. Quick Access panel

**This replaces "Popular Spares" (D-003).** The row evolves from a merchandising strip into the
operator's own working set.

### 6.1 Definition

A per-operator, horizontally scrollable, **unbounded** row of items the operator pins for everyday
use. It is heterogeneous — three entity kinds share one row:

| Kind | Card content | Tap | Long-press |
|---|---|---|---|
| `Spare` | image, name, part number, unit price, `Add` | Add to cart | Pin / Unpin |
| `Category` | icon, label, item count | Filter discovery to that category | Pin / Unpin |
| `Vehicle` | model / generation / engine identity | Set the fitment context (§7) | Pin / Unpin |

### 6.2 Pinning

Pin and unpin are available by **long-press from anywhere in the app** that renders one of those
three entities — Quick Access itself, search results, category browse, EPC browse, the vehicle
cascade, cart rows. A long-press opens a contextual menu containing Pin/Unpin; there is no
permanently visible pin control cluttering cards.

### 6.3 Ordering

Deterministic, in precedence order:

1. Explicit operator pin order (drag to reorder within the row).
2. Pin recency for items with no explicit order.
3. Stable entity id as final tie-break.

No server popularity ranking is merged into this row — that was the old model. Server-ranked
popular items, if surfaced at all, belong in search and category browse.

### 6.4 Persistence

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

### 6.5 Behaviour

- `LazyRow`, stable keys, unbounded. **No literal item count anywhere in the code.**
- Card width from the §3.3 formula; clamp 160–220 dp; 12 dp gaps.
- Empty state: a single explanatory card inviting the first pin — the one place operational copy is
  permitted, because the feature is invisible until used.
- Offline: cached pins render; pin mutations queue and are marked pending, consistent with the
  offline outbox model.

---

## 7. Vehicle cascade in the header

**This replaces the `Spares · Service · Performance` taxonomy line (D-002).** The line was
decorative; fitment is the highest-value filter at the counter.

### 7.1 Fields

Model → Generation → Engine, left to right, in header zone 1. A Maker field precedes them only when
the multi-make catalog is active; in single-make deployments Maker is implicit.

Each field is a compact dropdown: label above, value or placeholder inside, `radius.sm`, 40 dp tall.
Selecting a level enables and resets the levels to its right.

### 7.2 Data contract

Reuses the existing cascade — no new query layer. Per
[`docs/guides/vehicle-cascade-and-epc-browse.md`](../../guides/vehicle-cascade-and-epc-browse.md):
Model = `vehicle_master.model_variant`, Generation = `chassis_code`, Engine = `engine_code`.
Search stays on `search_catalog` / `part_fitment`. EPC hierarchy browse stays separate and joins at
`chassis_code`.

### 7.3 Fitment context

A confirmed vehicle sets a **session-scoped fitment context** that filters search results, category
browse and EPC entry until cleared. It renders as a dismissible chip once set, so the operator can
always see and drop the active filter. It is pinnable to Quick Access (§6.1).

### 7.4 Responsive form

| Class | Presentation |
|---|---|
| `Expanded` | Three inline fields in the header |
| `Medium` | Single "Select Vehicle" field → anchored popover cascade |
| `Compact` | Single chip → full-height bottom sheet cascade |

Rev 1.3's Path A (cascade reached only through search activation) is **withdrawn** — the cascade is
now permanent at Expanded and Medium. Path B (Search Spares screen) and Path C (compact sheet)
survive as §7.4 rows.

---

## 8. Phone POS

The phone POS is **not a shrunken tablet**. It is a designed compact product that performs every
counter operation, built from the same tokens, grammar, state machines and RPCs.

Action plan **L1** stands: the phone APK carries no Device Owner, no Lock Task, no Magisk
ownership. What changes in Rev 1.4 is capability — the phone moves from "optional POS" to **full
operational parity**, because a salesperson away from the counter must be able to complete a sale.

### 8.1 What makes it the same product

Inherited without exception: colour semantics, type roles, radius family, icon family, card
grammar, motion tiers, status semantics, error taxonomy, domain state machines, RPC contracts,
offline restrictions and authorisation gates.

Deliberately different: navigation model, cart presentation, checkout choreography, hero treatment,
and information density. Identity comes from grammar, not geometry.

### 8.2 Shell

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

### 8.3 Operational parity

Every POS operation, and how it is reached on each surface. No operation is tablet-only.

| Operation | Expanded (tablet) | Compact (phone) | Notes |
|---|---|---|---|
| Catalogue search | Header field | Persistent search field | Same `search_catalog` path |
| Vehicle fitment | Header cascade | Chip → bottom sheet cascade | §7.4 |
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
| Proceed to payment | Pane CTA | Cart bar CTA → checkout screen | Reserve-first (§9.2) |
| Split tender | Payment surface | Dedicated tender screen, one leg per step | Same normalised state |
| Terminal / EcoCash / Paynow | Payment surface | Same adapters | Online only |
| Payment recovery | Dedicated surface | Dedicated screen | Never a sheet — §9.5 |
| Print receipt | ESC/POS bridge | ESC/POS bridge | Bridge-First |
| Till / shift open-close | Rail → Till | More → Till | |
| Offline cash sale | Restricted mode | Restricted mode | Identical rules |
| Pin / unpin | Long-press | Long-press | §6.2 |

### 8.4 Compact choreography

- **Checkout is navigation, not a layer.** At Expanded the cart is already visible, so payment can
  be a focused layer. At Compact, `Proceed to Payment` pushes a dedicated screen: review → tender →
  confirm. Each split-tender leg is its own step with the backend-returned remaining balance as the
  step header.
- **Two-layer maximum.** No more than two transient layers deep; a third promotes to a screen.
- **Thumb reach.** Primary actions occupy the lower third. Destructive actions are never adjacent
  to frequent positive actions without separation.
- **Keyboard discipline.** A paired physical scanner must not raise the soft keyboard.

### 8.5 Compact-specific certification

Golden surfaces at `412×915` and `360×800`: Sell home, cart sheet, checkout review, tender step,
offline state, recovery. Plus a font-scale variant at the documented maximum (§10.3).

---

## 9. Architecture and behaviour

### 9.1 State ownership

```text
PosViewModel              catalogue query · fitment context · categories
                          · quick access projection · catalogue readiness
CartViewModel / slice     cart display projection · quantity intents
                          · customer association · checkout readiness
PosOperationsViewModel    till/shift · reservation · payment orchestration
                          · terminal state · recovery queue · fulfilment
```

No composable performs authoritative tax, price, stock or payment arithmetic. All I/O passes
through typed gateways; composables consume state and emit intents.

> **Note on scope.** `PosViewModel.kt` is 1533 lines today and its own header records that the
> audit recommends splitting `RpcClient` (102 methods) *before* splitting the ViewModel, or the
> dependency simply spreads across more files. Rev 1.4 does not mandate the ViewModel split as part
> of the UI rebuild. Treat the boundaries above as the target, reached after the gateway split.

### 9.2 Cart state

```kotlin
sealed interface CartUiState {
    data object Empty : CartUiState
    data class Open(val cart: CartProjection) : CartUiState
    data class Reserving(val cart: CartProjection) : CartUiState
    data class LockedForCheckout(val checkout: CheckoutProjection) : CartUiState
    data class PaymentInProgress(val payment: PaymentProjection) : CartUiState
    data class RecoveryRequired(val recovery: RecoveryProjection) : CartUiState
    data class Completed(val receipt: ReceiptProjection) : CartUiState
}
```

Illegal combinations (offline + terminal charging, locked + editable) are structurally impossible,
not guarded by booleans.

**Reserve-first is a backend dependency, not a UI decision.** There is no POS stock-reservation RPC
in `supabase/` today; checkout is a single `checkout_pos_cart_with_tenders` call. `Reserving` and
`LockedForCheckout` cannot ship until `@backend_agent` lands a reservation contract. Until then the
UI implements the states and transitions straight through, and the certification matrix marks the
reserve-first row **blocked**, not passed.

### 9.3 Semantic states

| Family | Treatment | Consequence |
|---|---|---|
| Positive | `success` + icon + label | Continue |
| Attention | `warning` + icon | Continue with awareness |
| Blocking | `error` (`#8E0F22`) + icon + explicit action | Progression blocked |
| Unknown | Neutral/attention hybrid, distinct icon | **Duplicate charge blocked** |
| Offline | `offline` amber | Tender set restricted |
| Selected | Brand red | Current context |

Colour is never the only signal — every row carries an icon or a label.

### 9.4 Copy

No tutorial paragraphs, no helper copy on obvious controls (D-008), verbs on buttons, errors state
what happened and the next permitted action, no engineering language in operator UI, no placeholder
or fake data in production builds. Deterministic preview data lives only in `src/debug` — the
pattern `packages/android-ui/src/debug/.../ui/shop` already establishes.

Every operator-facing string lives in a string resource. A copy catalogue keyed to the §9.5 error
taxonomy is a Phase 3 deliverable.

### 9.5 Errors

1. Transient → concise retry.
2. Correctable input → focus the offending control.
3. Business-rule rejection → state the constraint and the permitted next action.
4. **Payment unknown → block duplicate charge, open recovery.** `Unknown` is not failure; it means
   the system cannot prove whether funds moved.
5. Hardware unavailable → offer an approved alternative.
6. Offline restriction → name which tenders are unavailable.

Never surface exception names, HTTP codes, database terms or stack traces.

### 9.6 Offline

Aligned to [`ADR 2026-08-03`](../../decisions/2026-08-03-offline-sqlcipher-pos-cache.md) and to what
`PosViewModel` already enforces:

- Local catalogue search remains available.
- **Cash only.** Non-cash tenders are unavailable — already enforced at `PosViewModel.kt:1360`.
- **Walk-in only.** Named credit customers are online-only — already enforced; Rev 1.3 omitted this.
- Discount, void, refund, price override, quotations stay online (manager reauth needs a live RPC).
- Ambiguous prior payments stay blocked from retry.
- Sync state is explicit in the header (D-005).

Card and mobile-money requests are **never** queued as ordinary offline writes.

Note: the offline SQLCipher store is a **sale outbox**, not a catalogue package. Rev 1.3 §14.1
conflated the two. A read-only encrypted catalogue package with resumable download and rollback is
a separate, unbuilt concern.

### 9.7 Performance

Targets apply to the **supported baseline device** — named in the Path B SKU list the action plan
§11 still has open. Until that list lands, targets are measured on the lowest-spec tablet in the
pilot fleet and the figure recorded here.

- Touch feedback within one frame; local cart acknowledgement immediate.
- Indexed local catalogue lookup < 100 ms; first useful search results < 250 ms.
- Sustained 60 fps on cart and Quick Access scrolling.
- No blank intermediate frame on transition; no spinner for trivial local work.

Compose rules: stable keys, immutable models, `derivedStateOf` only where measured, isolate cart
recomposition from catalogue browsing, pre-size images, async decode, no I/O in composition.

### 9.8 Product imagery

Deterministic media contract per catalogue image: background treatment, bounding-box padding, crop
mode, aspect class, thumbnail variants, fallback, cache key. Adjacent cards must not show wildly
different object scales. Missing image → neutral placeholder, never a bright illustration that
steals emphasis. Hero imagery is treated separately from product imagery.

### 9.9 Accessibility

Contrast to WCAG AA; 48 dp minimum touch targets at every window class; focus order follows visual
order; predictable keyboard/D-pad; TalkBack labels describe action and state in operator language;
colour never the sole signal; reduced motion honoured; font scaling supported to a documented
maximum with layout adaptation rather than clipping.

Where accessibility scaling and benchmark proportion conflict, **accessibility wins** — and unlike
Rev 1.3, the scaled layout is still certified: §10.3 requires golden surfaces at maximum font scale.

---

## 10. Certification

Rev 1.3 gated on pixel equality. That gate was unsatisfiable (its own illustrative coordinates were
6–10 Rpx off the real ones, against a ±2 Rpx tolerance) and incompatible with an adaptive product.
Rev 1.4 certifies **proportion, grammar and behaviour**.

### 10.1 Gates

| Gate | Asserts | Fails when |
|---|---|---|
| **V1 Structure** | Required zones and components exist and nest correctly | A band or component is missing |
| **V2 Proportion** | Zone ratios within ±1.5%, band ratios ±3%, card aspect ±5%, at every reference size | Layout drifts from the measured composition |
| **V3 Grammar** | Every colour, radius, spacing, type style and icon resolves to a token | A raw literal appears in feature code |
| **V4 Typography** | Family, role, weight, line height, wrapping, tabular numerals | A role is substituted or numerals are proportional |
| **V5 Assets** | Icon family, hero crop, product-image treatment | A foreign icon set or inconsistent image scaling |
| **V6 Provenance** | Every benchmark difference maps to a registry row | An unregistered difference exists |
| **V7 Responsive identity** | Compact preserves tokens, grammar, semantics, state machines | The phone reads as a different product |
| **V8 Behaviour** | State machines, authorisation gates, offline restrictions | A gate is bypassable from the UI |
| **V9 Owner sign-off** | Final render approved at the canonical frame | — |

**V9's owner is the shop owner**, recorded as a dated row in the delta registry. Rev 1.3 referred to
"the owner" nine times without ever naming who signs.

### 10.2 Reference sizes

Every gate runs at `1280×800`, `1024×768`, `800×1280`, `412×915`, `360×800`.

### 10.3 Golden surfaces

Expanded: Home empty cart · Home populated · search active · vehicle cascade open · Quick Access
populated · Quick Access empty · cart locked · payment · offline · EPC browse.
Compact: Sell home · cart sheet · checkout review · tender step · offline · recovery.
Accessibility: Expanded Home and Compact cart at the documented maximum font scale.

Perceptual comparison uses a locked threshold with a difference heatmap, never a demand for
mathematically zero pixel difference — Android font rasterisation makes that unrealistic.

### 10.4 Design lint

CI fails the build on any of these inside `apps/android-management/feature/pos/`:

- raw colour literals outside token definitions;
- ad hoc `RoundedCornerShape` values outside the design system;
- direct Material default typography in canonical components;
- unapproved icon libraries, including `material-icons-extended` (§4.7);
- a `Reports` navigation destination;
- **any integer literal used as an item count in a lazy row** — counts are derived (§3.3);
- fake payment providers in the production source set;
- a duplicate canonical POS screen implementation;
- price or tax arithmetic inside a composable.

### 10.5 Harness

None of this exists yet — the repo has no screenshot tooling, no Macrobenchmark module, and two
test files in `feature/pos`. **The harness is a Phase 1 deliverable, not a Phase 9 one.** Standing
it up (screenshot runner, lint script, reference-size matrix) gates everything downstream.

Performance certification adds Macrobenchmark + Baseline Profiles for: cold launch into POS, Home
render, search typing, Quick Access fling, barcode add-to-cart, repeated quantity change, proceed to
payment, tender step, cart → receipt.

---

## 11. Migration contract

Rev 1.3's fatal omission: it read as if it governed an existing implementation. It does not. The
current `PosScreen.kt` is a 947-line two-pane catalogue/cart screen on Material 3 and the `ui.shop`
component library, with **no rail, no header cascade, no hero, no category row, no Quick Access row
and no recent searches**. None of the eight canonical destinations appear in it.

This is a UI rebuild. It is not a rewrite of the POS domain.

| Path | Disposition | Note |
|---|---|---|
| `PosScreen.kt` | **Rebuild** | Compose the new shell; behaviour ported per rows below, not deleted |
| `PosScreen.kt` — two-pane fallback logic | **Supersede** | Replaced by the §3.5 window-class system |
| `PosViewModel.kt` | **Keep** | Rebind to new intents. Do not split until `RpcClient` is split |
| `PosViewModel.kt` — split tender (`PosTenderDraft`, lines 45–69, 337–364) | **Keep** | Already correct; drives §8.3 tender steps |
| `PosViewModel.kt` — offline guards (line ~1360) | **Keep** | Cash-only and walk-in-only rules are canonical (§9.6) |
| `PosCartLineOps.kt` | **Keep** | Pure, tested |
| `PosEpcBrowseScreen.kt` | **Retheme** | Retokenise to §4; becomes a first-class destination |
| `offline/*` | **Keep** | Outbox, sync engine, SQLCipher store all unchanged |
| `ShopTheme` / `GtrTheme` wrapper | **Keep** | Already supplies the correct cool canvas, brand colours, vendored fonts and shape scale |
| `ui.shop` composables used by POS (`ShopProductCard`, `ShopListCard`, `ShopStatusChip`, …) | **Replace in POS only** | POS gets its own component set matching the benchmark's anatomy; the `Shop*` public API is frozen (Phase A) so it stays for its other consumers |
| `ShopWarmTheme` | **Untouched** | Customer app only (`CustomerShopTheme`); not in the POS path |
| `material-icons-extended` in `feature/pos` | **Remove** | Replaced by packaged Lucide (§4.7) |
| `apps/web/(staff)/staff/pos` | **Out of scope** | Different platform; explicitly not a "duplicate POS" to delete |

Nothing here deletes a capability. Under truth-protocol A0, silent feature thinning is forbidden —
if a behaviour in the current screen has no row above, it is an omission in this table, not a
licence to drop it.

### 11.1 Sequence

Reconciled with the action plan's phases rather than replacing them.

| Phase | Deliverable | Gate |
|---|---|---|
| **0 Freeze** | Benchmark committed ✓ · delta registry ✓ · reference spec ✓ · this document ✓ | Done in this change |
| **1 Harness** | Screenshot runner · design-lint CI · reference-size matrix · lossless benchmark resample | Harness runs green on an empty baseline |
| **2 Design system** | `PosPalette` · spacing/radius/elevation/type · Lucide vendoring · motion · adaptive layout primitives | V3 passes on a token showcase |
| **3 Expanded shell** | Rail · header + cascade · hero · category row · Quick Access · cart pane, debug data | V1, V2, V4, V5 pass at all Expanded sizes |
| **4 Catalogue binding** | Search · fitment context · category browse · EPC retheme · image pipeline | Search and cascade drive real results |
| **5 Cart binding** | Cart projection · quantity · customer · isolated recomposition | V8 cart behaviour |
| **6 Quick Access** | Long-press pin/unpin · reorder · heterogeneous cards · **backend pin RPCs** | Pins persist per operator across devices |
| **7 Compact** | Phone shell · cart sheet · checkout · tender steps · bottom nav | V7 + compact goldens |
| **8 Checkout** | Reserve-first *(blocked on backend)* · split tender · terminal · unknown/recovery | V8; reserve-first marked blocked until the RPC exists |
| **9 Hardware** | Scanner · CameraX · ESC/POS · terminal | Bridge-First verified |
| **10 Certification** | Full golden suite · Macrobenchmark · security · accessibility · owner sign-off | All gates green |

Phases 6 and 8 carry `@backend_agent` dependencies (§6.4, §9.2). Raise them now so they are
scheduled, not discovered.

---

## 12. Definition of done

- Expanded Home matches the benchmark's **proportions, grammar and hierarchy** at every reference
  size, within §10.1 tolerances.
- Every difference from the benchmark is registered with an owner reference.
- No `Reports` destination in POS navigation.
- Quick Access is operator-scoped, heterogeneous, unbounded, pinnable by long-press from anywhere,
  server-persisted, with **no literal item count in the codebase**.
- Vehicle cascade is permanent in the header at Expanded/Medium and recomposes at Compact, driving a
  visible, clearable fitment context.
- The phone performs every operation in the §8.3 parity table.
- Money is backend-authoritative with explicit currency; no `KSh`, no hardcoded VAT rate, no ZIMRA.
- Copy is correct — `GENUINE PARTS`, `Add Customer` — and lives in string resources.
- Error red is visually distinct from brand red.
- Offline restrictions (cash-only, walk-in-only, no queued card/mobile-money) are enforced and
  visible.
- Authorisation gates are RPC-enforced; hiding navigation is never the control.
- Scanner, CameraX and receipt printing are real bridge integrations.
- Golden, performance, accessibility and security gates pass; design lint is green.
- The owner signs off the canonical-frame render, dated in the registry.

Reserve-first and server-persisted pins may be **explicitly deferred** with their backend
dependency named — but never silently marked done.

---

## 13. Coherence

1. The benchmark supplies composition — within the scope §2 fixes.
2. Measured ratios supply adaptivity; pixels supply provenance only.
3. Industrial minimalism supplies the base language.
4. Soft borders and restrained depth supply hierarchy.
5. Modularity supplies organisation, never arbitrary reshaping.
6. Glass supplies transient focus, never permanent structure.
7. Progressive disclosure suppresses clutter.
8. Editorial typography and tabular numerals supply scanability.
9. Semantic colour supplies meaning — and brand red is not error red.
10. Motion supplies feedback, not decoration.
11. Recomposition supplies device fitness; tokens supply identity across devices.
12. State machines supply behavioural determinism.
13. The delta registry supplies governance.
14. Backend contracts supply truth; the UI renders, it does not calculate.

The result should not read as a set of combined trends, nor as a tablet layout squeezed onto a
phone. It should read as one mature product that happens to know what size screen it is on.

**clean, expensive, fast, obvious, trustworthy and unmistakably Nissan GTR Auto.**
