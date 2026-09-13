# POS design system

Canonical design and frontend-engineering documentation for the Nissan GTR Auto POS
(`apps/android-management/feature/pos`), tablet and phone.

## Read in this order

| # | Document | What it settles |
|---|----------|-----------------|
| 1 | [`../../decisions/2026-09-13-pos-adaptive-benchmark-fidelity.md`](../../decisions/2026-09-13-pos-adaptive-benchmark-fidelity.md) | The decisions and why, in ADR form |
| 2 | [`POS_FRONTEND_BLUEPRINT_REV_1_5.md`](POS_FRONTEND_BLUEPRINT_REV_1_5.md) | Authority, adaptive geometry, token pipeline, design language and interaction grammar, components, Quick Access, phone POS, application architecture, certification, capability contract |
| 3 | [`FEATURE_REGISTER.md`](FEATURE_REGISTER.md) | **The completeness ledger — 162 rows. This, not the prose, defines scope** |
| 4 | [`VisualReferenceSpec.json`](VisualReferenceSpec.json) | Measured geometry — ratios normative, dp canonical, Rpx provenance only |
| 5 | [`APPROVED_VISUAL_DELTAS.md`](APPROVED_VISUAL_DELTAS.md) | Every intentional difference from the benchmark |
| 6 | [`reference/`](reference/) | The owner-approved benchmark raster |

## Building from this

Read blueprint **§0.1 How to build from this document** first. It defines the agent protocol:
what to read before starting, the forbidden shortcuts, what a completion report must contain, and
the drift audit. The feature register is the ledger you work against — rows are never deleted, and a
row moves to `done` only when its gate has actually run.

## Before changing POS UI

1. Read the blueprint's authority hierarchy (§1). This document set sits **below** the truth
   protocol, `AGENTS.md` exclusions and the locked tablet kiosk action plan.
2. Check whether your change is already a registered delta.
3. If it is a new intentional difference from the benchmark, add a registry row with an owner
   reference **before** writing the code.
4. If it changes canonical geometry, answer blueprint §1.3's five guardrail questions first.

## Outside this folder

- Boot, splash, login, role routing, Lock Task → [kiosk / role-routing specification](../../../Nissan_GTR_Auto_POS_Kiosk_Role_Based_Routing_Specification.md)
- Feature inventory, locked product decisions → [tablet kiosk POS action plan](../../plans/2026-08-03-tablet-kiosk-pos-full-action-plan.md)
- Offline behaviour → [SQLCipher POS cache ADR](../../decisions/2026-08-03-offline-sqlcipher-pos-cache.md)
- Cascade and EPC data model → [vehicle cascade guide](../../guides/vehicle-cascade-and-epc-browse.md)
- Brand primitives → `packages/ui/brand-tokens.json`
