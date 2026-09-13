# Approved visual deltas — POS

Registry of every intentional difference between the shipped POS and the owner-approved
benchmark (`reference/benchmark-home-expanded-2026-09-07.jpg`).

**Rule:** a visual or behavioural difference from the benchmark that is not recorded here is a
regression and fails certification. Adding a row requires an owner decision reference.

`affectsGolden` — the delta changes a golden surface, so the golden must be re-baselined with it.
`responsiveOnly` — the delta exists only below the Expanded window class and does not change the
tablet composition.

| ID | Region | Benchmark behaviour | Approved behaviour | Reason | Owner ref | affectsGolden | responsiveOnly |
|----|--------|---------------------|--------------------|--------|-----------|---------------|----------------|
| D-001 | Nav rail | Rail item 7 is `Reports` | Rail item 7 is `EPC Browse` | Salespeople do not run reports at the counter; EPC browse is a first-class counter task and already implemented (`PosEpcBrowseScreen.kt`). Reporting stays in the dashboard/staff surfaces. | Rev 1.3 §6.1 | yes | no |
| D-002 | Header, zone 1 | Static taxonomy line `Spares · Service · Performance` | **Vehicle selection cascade fields** (Model → Generation → Engine, plus Maker when the multi-make catalog is active) | The taxonomy line was decorative. Fitment context is the single highest-value filter at the counter and belongs in permanent reach. Reuses `vehicle_master` per `docs/guides/vehicle-cascade-and-epc-browse.md`. | Owner, 2026-09-13 | yes | no |
| D-003 | Popular Spares row | Fixed row of server-ranked popular products with a `View All` link | **Quick Access panel** — operator-specific, horizontally scrollable, unbounded, heterogeneous (spare / category / vehicle), populated by long-press pin/unpin from anywhere in the app | The row evolves from a merchandising strip into the operator's own frequently-used working set. Removes the fixed visible count as a functional cap. | Owner, 2026-09-13 | yes | no |
| D-004 | Popular Spares row | `View All` navigates to a full list | Row scrolls horizontally; `View All` is retained only as an overflow into Quick Access management (reorder / unpin) | Scrolling replaces pagination as the primary continuation; management still needs a full surface. | Owner, 2026-09-13 | yes | no |
| D-005 | Header / status | No connectivity indicator | Subtle amber offline status surface in the header context zone | Offline is a restricted operational mode (`docs/decisions/2026-08-03-offline-sqlcipher-pos-cache.md`) and the operator must know before choosing a tender. | ADR 2026-08-03 | yes | no |
| D-006 | Cart money | `KSh` amounts, `VAT (16%)` | Currency and tax come from backend domain state (USD / ZiG; tax-agnostic invoices) | Benchmark mock content is Kenyan. Deployment is Zimbabwe; `AGENTS.md` mandates explicit currency per money field, and ZIMRA/fiscalisation is a hard exclusion. | AGENTS.md; action plan §9 | yes | no |
| D-007 | Hero value indicators | Reads `GNGUINE PARTS` | Reads `GENUINE PARTS` | Typographical defect in the benchmark raster. The benchmark is not authoritative for copy. | Blueprint §2 | yes | no |
| D-008 | Cart | `Add Customer (Optional)` | `Add Customer` | Parenthetical helper copy explaining an obvious optional control conflicts with the operational-copy rules. Optionality is conveyed by placement and by checkout succeeding without it. | Blueprint §2 | yes | no |
| D-009 | Hero | Marketing hero occupies a fixed dominant band | Hero band may collapse toward `bands.hero.minDp` as available height shrinks, and is replaced by a compact identity strip below the Expanded class | Adaptive height budget; the hero is the lowest-value band when vertical space is scarce. | Blueprint §3 | yes | partly |
| D-010 | Whole screen | Single fixed 1536×1024 composition | Ratio-driven adaptive composition across four window classes; a separate compact phone recomposition | The product must run on more than one counter tablet, and full POS operation is required from the phone APK. | Owner, 2026-09-13 | yes | yes |
| D-011 | Error colour | Error state and primary CTA are both brand red | Error resolves to a darker `#8E0F22`, always icon-paired; brand red stays reserved for CTA, active nav and brand emphasis | `GtrColors.Danger` and `GtrColors.Primary` are the same byte value `#C8102E`, and `GtrTheme` wires `error = Danger` — a blocking error is currently indistinguishable from the button the operator is meant to press. | ADR 2026-09-13 | yes | no |
| D-012 | Cart row price | Single price per row | Explicitly the **unit** price; line extension is implied by the quantity stepper | Confirmed arithmetically from the benchmark (2,500 + 18,000 + 3,800×2 = 28,100). Rev 1.3 left "unit or line price" ambiguous. | Blueprint §5 | no | no |

## Withdrawn from Rev 1.3

| Was | Status | Why |
|-----|--------|-----|
| "POS migrates from the warm surface to a cool canvas" | **Not a delta** | Measurement error on first pass. The POS renders through `ShopTheme` → `GtrTheme`, whose background is `GtrColors.Chalk` `#F4F5F7` — already cool and within JPEG error of the measured benchmark canvas `#F3F4F8`. `ShopWarmTheme` belongs to the customer app and was never in the POS path. Nothing to change. |
| "Vehicle cascade moved out of a permanent hero overlay into contextual search" | **Withdrawn** | The benchmark hero contains no cascade dropdowns, so there was nothing to move. Superseded by D-002, which places the cascade in the header. |
| "±2 Rpx critical geometry tolerance" | **Withdrawn** | Incompatible with an adaptive layout, and unsatisfiable against the illustrative coordinates Rev 1.3 shipped. Replaced by ratio tolerances in `VisualReferenceSpec.json`. |

## How to add a delta

1. Confirm the difference is intentional and traceable to an owner decision or a higher authority (blueprint §1).
2. Add a row with a new `D-0NN` id, both behaviours stated concretely, and the decision reference.
3. If `affectsGolden` is yes, re-baseline the affected golden surfaces in the same change.
4. Never edit a delta's meaning in place — supersede it with a new row and mark the old one withdrawn.
