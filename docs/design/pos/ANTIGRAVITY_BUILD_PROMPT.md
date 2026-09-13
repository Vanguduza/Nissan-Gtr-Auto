# Antigravity build prompt — Nissan GTR Auto POS

Paste **§A** into Antigravity as the opening message. The rest of this file is reference the agent
reads from the repo.

---

## A. The brief (paste this)

You are rebuilding the point-of-sale interface for Nissan GTR Auto, an automotive spare-parts
business in Zimbabwe. It runs on a counter tablet and on staff phones.

This is **not a greenfield project and not a free design exercise.** A complete specification already
exists in this repository and it is binding. Your job is to execute it faithfully, not to reinterpret
it.

**Read these before writing any code, in this order:**

1. `docs/design/pos/README.md` — orientation
2. `docs/design/pos/POS_FRONTEND_BLUEPRINT_REV_1_5.md` **§0.1** — how to work from this document.
   Read this section twice. It defines forbidden shortcuts and what a completion report must contain.
3. `docs/design/pos/FEATURE_REGISTER.md` — 162 numbered features. **This register, not your reading
   of the prose, defines scope.**
4. `docs/design/pos/POS_FRONTEND_BLUEPRINT_REV_1_5.md` — the full specification
5. `docs/design/pos/VisualReferenceSpec.json` — measured geometry
6. `docs/design/pos/APPROVED_VISUAL_DELTAS.md` — the 12 sanctioned differences from the reference
7. `docs/design/pos/reference/benchmark-home-expanded-2026-09-07.jpg` — the owner-approved design.
   **Open this image and look at it.** It is the visual target.

Then read `AGENTS.md` and `docs/plans/2026-08-03-tablet-kiosk-pos-full-action-plan.md` §10 for the
constraints that outrank the blueprint.

**Do not start Phase 1 until you have produced an implementation plan and a human has approved it.**

Your first deliverable is a plan artifact covering Phase 1 only (blueprint §12.1), listing every
feature-register ID Phase 1 owns, the files you will create, and how you will prove it works.

---

## B. Environment facts you will otherwise get wrong

| Fact | Consequence |
|---|---|
| Android Gradle root is `apps/android-management/`, with its own wrapper | Run `./gradlew` from there, not from the repo root |
| `minSdk = 26`, `compileSdk = 34` | Blur is API 31+. The **fallback** treatment in blueprint §5.3 is what most devices render. Build it first |
| Compose BOM is `2024.06.00` | Predates `SharedTransitionLayout`. Shared-element transitions (§5.5) need a BOM bump — propose it, do not silently upgrade |
| No version catalog (`libs.versions.toml`) exists | Dependencies are hardcoded strings across modules. Introducing a catalog is a reasonable Phase 1 proposal |
| No dependency injection exists — `PosModule` is a 3-line stub | Hilt is specified (ARCH-02) and must be added |
| No screenshot, benchmark, or lint tooling exists | Phase 1 builds it. Everything downstream depends on it |
| `feature/pos` has exactly 2 test files | You are not extending a tested codebase. Assume nothing works until a test says so |
| Supabase RPC layer, ledger and RLS are the system of record | **Never modify them.** You are replacing the client, not the backend |

The existing `feature/pos` implementation is a **wrong implementation** and has no authority. You may
replace any of its client code. What you may not do is lose a capability — blueprint §12 lists what
must still exist when you are finished.

---

## C. How to work in Antigravity specifically

### C.1 Load the knowledge base first

Add all six documents in §A to your knowledge base before starting, so they persist across agent
sessions. The single most likely failure of a long build is an agent in session 9 that no longer
remembers a constraint from session 1.

### C.2 Plan artifact before code, every phase

Produce an implementation plan artifact and get human approval before each phase. The plan names:
the register IDs in scope, the files to be created or changed, the gate that will prove it, and any
blocker. A plan that does not enumerate register IDs is not a plan for this project.

### C.3 Task list seeded from the register — not from your own decomposition

Seed your task list directly from `FEATURE_REGISTER.md` IDs. One task per register row.

Do not invent your own task breakdown. The register exists precisely so that scope cannot quietly
shrink between planning and delivery, and a parallel task list defeats it. When a register row is
too large for one task, add sub-tasks **under** that ID — never replace it.

### C.4 Verification: there is no browser here

**This is an Android Compose application. You cannot open it in a browser, and you should not try.**
Your browser tooling is not the verification path for this project. Substitute this loop:

```bash
cd apps/android-management

# Unit and reducer tests — fast, run constantly
./gradlew :feature:pos-domain:test

# Screenshot tests — these produce PNGs
./gradlew :feature:pos-ui:testDebugUnitTest

# Output lands here:
#   apps/android-management/feature/pos-ui/build/outputs/roborazzi/
```

Then — and this is the part that matters — **open the generated PNGs and look at them.** Compare
against `docs/design/pos/reference/benchmark-home-expanded-2026-09-07.jpg`. Attach them to your
walkthrough artifact as evidence.

A screenshot test that runs green proves a file was written. It does not prove the screen looks
right. You have image-viewing capability; use it. This is the closest equivalent to the browser
verification you would normally do on a web project, and it is the intended substitute.

Reserve emulator runs for Phase 9 (hardware) and Phase 10 (performance). They are slow and flaky;
JVM screenshot tests are the primary loop.

### C.5 The walkthrough artifact is the completion report

Blueprint §0.1.3 requires four things in a completion report. Your walkthrough artifact must contain
all four, or the phase is not complete:

1. **Register delta** — which IDs moved to `done`, with the commit for each
2. **Gate output** — the actual output of the gates that ran. Not "tests pass" — paste the output
3. **Deltas registered** — new rows in `APPROVED_VISUAL_DELTAS.md`, or an explicit "none"
4. **Known gaps** — anything specified but not delivered, why, and what unblocks it

Screenshots belong in the walkthrough. A walkthrough for a UI phase with no screenshots is not
evidence of anything.

### C.6 Running agents in parallel

Phase dependencies. Phases on the same line may run concurrently in separate agents; a line may not
start until the line above is complete and approved.

```
Phase 1  Foundations                          ← alone. Everything depends on it
Phase 2  Design system    ∥  Phase 3  Domain core
Phase 4  Expanded shell   ∥  Phase 5  Data binding
Phase 6  Cart + Quick Access  ∥  Phase 7  Compact  ∥  Phase 9  Hardware + receipt
Phase 8  Checkout
Phase 10 Certification                        ← alone
```

Phases 2 and 3 are genuinely independent — `pos-design` has no domain types and `pos-domain` has no
Compose dependency. That separation is the whole reason for the module split, and it is what makes
parallel agents safe here.

Phase 9 can start once Phase 3 has published the gateway interfaces, because the bridges implement
against interfaces, not against UI.

**Two agents must never edit the same module concurrently.** If a phase needs a change in a module
another agent owns, raise it — do not reach into it.

### C.7 Model choice

Use the strongest reasoning model available for Phases 1, 3, 8 and 10 — module architecture, the
state machine, payment correctness and certification are where a cheaper model costs you most. UI
assembly phases (4, 6, 7) tolerate a faster model, provided the screenshot loop in §C.4 is actually
being run and looked at.

---

## D. Non-negotiables

Condensed from blueprint §1 and §0.1.2. Violating any of these makes a completion report false.

**Authority, highest first:** repository truth protocol → `AGENTS.md` hard exclusions → the locked
action plan (§10 L1–L7) → the kiosk specification → the benchmark image → the delta registry → the
blueprint → RPC contracts → platform constraints → your preference (lowest).

**Absolute prohibitions:**

- **No ZIMRA, FDMS, fiscalisation or tax-authority payloads.** Not optional, not configurable
- **No payroll tax**
- **Hardware only through `bridges/`.** No WebView, no HTML5 camera, no Web Bluetooth — not for
  scanning, not for printing
- **Never modify the cart/checkout system of record** — Postgres RPCs, ledger, RLS
- **Never cache a manager approval token.** Discount, void, refund and price override need a live
  reauth RPC
- **Never queue a card or mobile-money payment offline.** Offline is cash-only, walk-in only
- **Never compute a remaining balance, tax or total client-side.** Backend values only
- **Never define a brand colour outside `packages/ui/brand-tokens.json`**
- **Never ship `KSh` or a hardcoded 16% VAT.** Those are mock content in the benchmark image — the
  deployment is Zimbabwe, USD/ZiG (blueprint §2, delta D-006)

**Forbidden shortcuts** — full table at blueprint §0.1.2. The ones most likely to tempt you:

- A `TODO`, stub, or "simplified for now" implementation in anything reported as done
- Mock or sample data outside `src/debug` or `src/test`
- Skipping or `@Ignore`-ing a test to get a green build
- Suppressing a design-lint rule instead of fixing what it caught
- **Deleting or rewording a row in the feature register.** A feature that will not be built is marked
  `dropped` with an owner reference, so the decision is visible
- Marking a register row `done` before its gate has actually run

**When you are blocked:** finish everything that does not depend on the blocker, then state
precisely what is needed and who owns it. Three register rows are already known to be blocked on
backend work that is specified but unbuilt — QACC-05, PAY-01, PAY-02. Never substitute a local
simulation for a missing backend and report the feature as working.

---

## E. Phase missions

Full detail in blueprint §12.1. Exit criteria below are the bar for approval.

| # | Mission | Exits when |
|---|---|---|
| 1 | **Foundations.** Split `feature/pos` into `pos-design` / `pos-domain` / `pos-data` / `pos-ui`. Add Hilt. Style Dictionary token pipeline. Roborazzi + Macrobenchmark + design-lint CI. Resample colours from a lossless benchmark export | Harness runs green on an empty baseline; tokens generate for Kotlin, CSS/TS and Swift; CI proves `pos-domain` has no Android dependency |
| 2 | **Design system.** `PosTheme`, both colour schemes, type with tracking and `tnum`, Lucide vendored, `material-icons-extended` removed, motion, adaptive primitives, glass capability pair | A component gallery screenshot-tested at all five reference sizes, both schemes, both glass treatments |
| 3 | **Domain core.** `PosState`, pure reducers, projections, the eight gateway interfaces, fakes | Reducer suite covers cart, reserve, split tender and recovery — running on the JVM in seconds |
| 4 | **Expanded shell.** Rail, header with cascade, hero, category row, Quick Access, cart pane — against fakes | Gates V1, V2, V4, V5 pass at every Expanded size. Screenshots compared to the benchmark by eye and attached |
| 5 | **Data binding.** Gateway implementations, search, fitment, categories, EPC, image pipeline, catalogue package lifecycle | Contract test per gateway; catalogue activates atomically and rolls back |
| 6 | **Cart and Quick Access.** Cart flows, long-press pin/unpin, reorder, heterogeneous cards | Recomposition test proves cart edits do not recompose the catalogue. QACC-05 blocked and reported |
| 7 | **Compact.** Phone shell, cart sheet, checkout, tender steps, bottom navigation | **Every row of the §9.3 parity table demonstrably reachable on the phone.** Compact goldens pass |
| 8 | **Checkout.** Split tender, terminal adapters, unknown → recovery, reduced basket | No duplicate charge reachable under fault injection. Reserve-first blocked and reported |
| 9 | **Hardware and receipt.** Scanner profiles, CameraX, ESC/POS, terminal, `ReceiptDocument`, print preview | Preview and ESC/POS renderers produce identical output on the same fixtures; print failure never blocks sale completion |
| 10 | **Certification.** Full golden suite, Macrobenchmark, security, accessibility, owner sign-off | Gates V1–V10 green; component matrix (§11.6) complete; test layers 1–12 present |

---

## F. Definition of done

Blueprint §13 is the full bar. The short form:

Every one of the 162 register rows is `done` with its gate passed, or `dropped` with an owner
reference. No row is `partial`. No row was deleted. Both form factors pass their gates. The owner has
signed off the canonical-frame render.

Before claiming any phase complete, run the drift audit at blueprint §0.1.4 across the **whole**
register — not only the rows you touched. Drift is not visible in the current diff; it is only
visible in re-checking what was already declared finished.

---

## G. Your first action

Do not write code. Do not scaffold. Do not create a module.

1. Read everything in §A
2. Open the benchmark image and study it
3. Produce a Phase 1 implementation plan artifact
4. In that plan, report anything in the specification you believe is wrong, ambiguous, or
   unbuildable as written — **before** you start, not after
5. Wait for approval
