# POS feature register

The completeness ledger for the POS rebuild. **This register, not anyone's reading of the prose,
defines scope.** Every row is a feature that must exist in the finished product.

Read with [`POS_FRONTEND_BLUEPRINT_REV_1_5.md`](POS_FRONTEND_BLUEPRINT_REV_1_5.md) §0.1, which
governs how to work from it.

## Rules

1. **Rows are never deleted.** A feature that will not be built is marked `dropped` with an owner
   decision reference. Deleting a row is the exact failure this register exists to prevent.
2. **A row moves to `done` only when its gate has actually run and passed.** Not when the code is
   written. Not when it looks right.
3. **A row may not be marked `done` while it contains a forbidden shortcut** (§0.1.2) — a `TODO`, a
   stub, a simplified stand-in, mock data outside a debug or test source set.
4. **`partial` is a real status and must be used.** Silently leaving a row `done` when half of it
   ships is thinning.
5. **New requirements get new rows**, with a blueprint reference. A requirement with no row does not
   get built, and a row with no blueprint reference does not get built either.
6. **Audit the whole register** before each phase and before release certification (§0.1.4) — not
   only the rows you touched.

## Status values

`todo` · `in-progress` · `partial` (with a note) · `blocked` (with what blocks it) · `done` (gate
passed, commit referenced) · `dropped` (owner reference required)

---

## SYS — Design system and foundations

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| SYS-01 | Style Dictionary pipeline → Kotlin, CSS/TS, Swift from `brand-tokens.json` | 4.1 | 1 | done |
| SYS-02 | `PosTheme` over CompositionLocals; no Material identity | 4.1 | 2 | done |
| SYS-03 | `PosPalette` light scheme | 4.1.1, 4.2 | 2 | done |
| SYS-04 | `PosPalette` dark scheme (first-class, not inversion) | 5.11 | 2 | done |
| SYS-05 | `error` and `unknown` primitives separated from brand red | 4.2 | 2 | done |
| SYS-06 | Spacing scale | 4.3 | 2 | done |
| SYS-07 | Radius scale | 4.4 | 2 | done |
| SYS-08 | Border and elevation tokens | 4.5 | 2 | done |
| SYS-09 | Type roles incl. tracking and `tnum` | 4.6 | 2 | done |
| SYS-10 | Lucide vendored; `material-icons-extended` removed from POS | 4.7 | 2 | done |
| SYS-11 | Density scale (Comfortable / Operational / Compact) | 4.8 | 2 | done |
| SYS-12 | Motion tiers, springs, reduced-motion honouring | 4.9 | 2 | done |
| SYS-13 | `PosOptical` offset tokens, bounded ±2 dp | 5.8 | 2 | done |
| SYS-14 | Adaptive primitives — `PosScaffold`, clamp law, derived counts | 3.3 | 2 | done |
| SYS-15 | `PosWindowClass` derivation from available size | 3.5 | 2 | done |
| SYS-16 | Locale formatting contract; `Money` value type | 5.11 | 2 | done |
| SYS-17 | Single feedback surface + undo | 5.9 | 2 | done |
| SYS-18 | Keyboard shortcut map + `?` discoverability overlay | 5.10 | 2 | done |
| SYS-19 | Focus contract and visible focus ring | 5.10 | 2 | done |
| SYS-20 | Glass capability pair — API 31+ blur and pre-31 fallback | 5.3 | 2 | done |
| SYS-21 | Skeletons matching final geometry exactly | 5.7 | 2 | done |

## ARCH — Application architecture

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| ARCH-01 | Module split `pos-design` / `pos-domain` / `pos-data` / `pos-ui` | 10.1 | 1 | todo |
| ARCH-02 | Hilt dependency injection | 10.1 | 1 | todo |
| ARCH-03 | `pos-domain` has zero Android dependency — enforced in CI | 10.1 | 1 | todo |
| ARCH-04 | Eight typed gateways replacing `RpcClient` | 10.2 | 3 | todo |
| ARCH-05 | `PosResult`; no exceptions for business outcomes | 10.2 | 3 | todo |
| ARCH-06 | Idempotency keys on every money- or stock-moving call | 10.2 | 3 | todo |
| ARCH-07 | `PosStore` over pure, total reducers | 10.3 | 3 | todo |
| ARCH-08 | Screen-scoped immutable projections | 10.3 | 3 | todo |
| ARCH-09 | Type-safe navigation, one graph for both form factors | 10.4 | 3 | todo |
| ARCH-10 | `PosError` taxonomy mapped to string resources | 10.11 | 3 | todo |

## SHELL — Expanded shell

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| SHELL-01 | Nav rail, eight destinations, canonical order, no `Reports` | 6.1, D-001 | 4 | todo |
| SHELL-02 | Rail active and inactive states | 6.1 | 4 | todo |
| SHELL-03 | Rail logo lockup | 6.1 | 4 | todo |
| SHELL-04 | Rail GT-R artwork and brand statement | 6.1 | 4 | todo |
| SHELL-05 | Header four zones | 6.2 | 4 | todo |
| SHELL-06 | Search field with scan affordance | 6.2 | 4 | todo |
| SHELL-07 | Operator identity block | 6.2 | 4 | todo |
| SHELL-08 | Date and time block | 6.2 | 4 | todo |
| SHELL-09 | Offline status surface | 6.2, D-005 | 4 | todo |
| SHELL-10 | Catalogue version and freshness indicator | 10.9 | 5 | todo |
| SHELL-11 | Hero with collapse to `minDp` and drop at Compact | 6.3, D-009 | 4 | todo |
| SHELL-12 | Category row, derived count, horizontally scrollable | 6.4 | 4 | todo |
| SHELL-13 | Recent searches chips with clear | 6.4, D-004 | 4 | todo |
| SHELL-14 | Adaptive zone law verified at all five reference sizes | 3.3, 3.5 | 4 | todo |

## DISC — Discovery, search and catalogue browse

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| DISC-01 | Search by name, part number, fitment, barcode, EPC hierarchy | 12 | 5 | todo |
| DISC-02 | Debounced and cancellable typing; immediate exact match | 12 | 5 | todo |
| DISC-03 | Category filtering | 6.4 | 5 | todo |
| DISC-04 | Search results surface | 5.5 | 5 | todo |
| DISC-05 | Product detail overlay / sheet with shared-bounds transition | 5.5 | 5 | todo |
| DISC-06 | EPC browse as first-class destination, rebuilt on the design system | 12 | 5 | todo |
| DISC-07 | Deterministic product image media contract | 10.16 | 5 | todo |
| DISC-08 | Empty and no-results states | 5.7 | 5 | todo |

## FIT — Vehicle fitment

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| FIT-01 | Header cascade — Model → Generation → Engine | 8.1, D-002 | 4 | todo |
| FIT-02 | Maker field when the multi-make catalogue is active | 8.1 | 4 | todo |
| FIT-03 | Cascade bound to `vehicle_master` / `search_catalog` | 8.2 | 5 | todo |
| FIT-04 | Session fitment context — visible, dismissible chip | 8.3 | 5 | todo |
| FIT-05 | Responsive cascade forms — inline, popover, sheet | 8.4 | 7 | todo |
| FIT-06 | Vehicle pinnable to Quick Access | 7.1 | 6 | todo |

## QACC — Quick Access panel

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| QACC-01 | Heterogeneous row — spare, category, vehicle | 7.1, D-003 | 6 | todo |
| QACC-02 | Long-press pin/unpin from anywhere those entities render | 7.2 | 6 | todo |
| QACC-03 | Deterministic ordering — pin order, recency, stable id | 7.3 | 6 | todo |
| QACC-04 | Drag to reorder | 7.3 | 6 | todo |
| QACC-05 | Operator-scoped server persistence + RPCs | 7.4 | 6 | **blocked** — backend |
| QACC-06 | Unbounded `LazyRow`; no literal item count in code | 7.5 | 6 | todo |
| QACC-07 | Empty state inviting the first pin | 7.5 | 6 | todo |
| QACC-08 | Offline — cached pins render, mutations queue as pending | 7.5 | 6 | todo |

## CART — Current Sale

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| CART-01 | Pane anatomy and order | 6.5 | 4 | todo |
| CART-02 | Row column grid and deterministic truncation | 5.8, 6.5 | 4 | todo |
| CART-03 | Unit price, tabular figures | 6.5, D-012 | 4 | todo |
| CART-04 | Quantity stepper — optimistic, settles, animates on divergence | 5.4 | 6 | todo |
| CART-05 | Remove line with undo | 5.9 | 6 | todo |
| CART-06 | Customer association | 6.5, D-008 | 6 | todo |
| CART-07 | Totals block — right-aligned, backend values, explicit currency | 6.5, D-006 | 6 | todo |
| CART-08 | Zero-value discount row stays visible | 6.5 | 4 | todo |
| CART-09 | Clear cart behind confirmation naming the verb | 5.9 | 6 | todo |
| CART-10 | Park and resume sale | 12 | 6 | todo |
| CART-11 | Locked-for-checkout state; cart not editable when reserved | 10.5 | 8 | todo |
| CART-12 | Cart recomposition isolated from catalogue — verified by test | 10.15 | 6 | todo |

## PAY — Checkout and payment

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| PAY-01 | Reserve-first sequence | 10.6 | 8 | **blocked** — backend |
| PAY-02 | Reservation TTL and designed expiry behaviour | 10.6 | 8 | **blocked** — backend |
| PAY-03 | Tender capability model; blocked tenders disabled with reason | 10.7 | 8 | todo |
| PAY-04 | Adapters — cash, swipe terminal, EcoCash, Paynow, ContiPay | 10.7 | 8 | todo |
| PAY-05 | Five normalised terminal outcomes | 10.7 | 8 | todo |
| PAY-06 | Split tender legs; remaining balance always from backend | 10.5 | 8 | todo |
| PAY-07 | `Unknown` opens recovery and blocks duplicate charge | 10.7, 10.11 | 8 | todo |
| PAY-08 | Recovery as a dedicated screen on both form factors | 10.4 | 8 | todo |
| PAY-09 | Reduced basket after partial payment | 10.8 | 8 | todo |
| PAY-10 | Change due for cash tender | 6.6.1 | 8 | todo |
| PAY-11 | Manager reauth — discount, void, refund, price override | L2 | 8 | todo |
| PAY-12 | Refunds post through the finance pipeline | L3 | 8 | todo |
| PAY-13 | Quotations — create, send, convert | L4 | 8 | todo |

## RCPT — Receipt and print

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| RCPT-01 | `ReceiptDocument` model — one model, three renderers | 6.6.1 | 9 | todo |
| RCPT-02 | `EscPosRenderer` | 6.6.1 | 9 | todo |
| RCPT-03 | `PreviewRenderer` | 6.6.3 | 9 | todo |
| RCPT-04 | `PdfRenderer` with share and email | 6.6.1 | 9 | todo |
| RCPT-05 | `Paper58` / `Paper80` profiles; character-grid layout | 6.6.2 | 9 | todo |
| RCPT-06 | Preview surface — profile shown, printer changeable, actions | 6.6.3 | 9 | todo |
| RCPT-07 | Reprint marking, count, and audit | 6.6.4 | 9 | todo |
| RCPT-08 | Print failure states, job retained across restart | 6.6.5 | 9 | todo |
| RCPT-09 | Preview / ESC-POS renderer parity certified on fixtures | 11.7 L4 | 9 | todo |
| RCPT-10 | A failed print never blocks sale completion | 6.6.5 | 9 | todo |
| RCPT-11 | Customer and merchant copies as `copy` values, not documents | 6.6.4 | 9 | todo |

## CAT — Catalogue package

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| CAT-01 | Package manifest with version, counts, size | 10.9 | 5 | todo |
| CAT-02 | Checksum verified **before** activation | 10.9 | 5 | todo |
| CAT-03 | Resumable, cancellable, metered-aware download | 10.9 | 5 | todo |
| CAT-04 | Staging location; never written over the active database | 10.9 | 5 | todo |
| CAT-05 | Atomic activation | 10.9 | 5 | todo |
| CAT-06 | Rollback to the previous version | 10.9 | 5 | todo |
| CAT-07 | Startup integrity check with fallback and report | 10.9 | 5 | todo |
| CAT-08 | SQLCipher at rest, passphrase wrapped by Keystore | 10.9, 10.10 | 5 | todo |
| CAT-09 | Read-only in production | 10.9 | 5 | todo |

## HW — Hardware bridges

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| HW-01 | `ScannerBridge` with configurable `ScannerProfile` | 5.10, 10.18 | 9 | todo |
| HW-02 | Scanner does not swallow typing or raise the soft keyboard | 5.10 | 9 | todo |
| HW-03 | Ambiguous scan opens a disambiguation surface | 5.10 | 9 | todo |
| HW-04 | `CameraBridge` — CameraX, transient, permission handling | 10.18 | 9 | todo |
| HW-05 | `PrinterBridge` — discovery, profile query, retry | 10.18 | 9 | todo |
| HW-06 | `TerminalBridge` normalised to the five outcomes | 10.18 | 9 | todo |
| HW-07 | Capability queried, never assumed; disabled-with-reason | 10.18 | 9 | todo |
| HW-08 | Every bridge fakeable so full flows test without hardware | 10.18 | 9 | todo |

## OFF — Offline

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| OFF-01 | Local catalogue search available offline | 10.12 | 8 | todo |
| OFF-02 | Cash-only; other tenders disabled with reason, never failing | 10.12 | 8 | todo |
| OFF-03 | Walk-in only; named credit customers online-only | 10.12 | 8 | todo |
| OFF-04 | Discount, void, refund, override, quotations stay online | 10.12 | 8 | todo |
| OFF-05 | Encrypted outbox with idempotent replay | 10.12 | 8 | todo |
| OFF-06 | Price drift and stock shortfall surface as conflicts | 10.12 | 8 | todo |
| OFF-07 | Card and mobile-money requests never queued | 10.12 | 8 | todo |
| OFF-08 | Sync state explicit in the header | D-005 | 8 | todo |

## SEC — Security and privacy

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| SEC-01 | No card data in memory beyond the adapter, storage, logs or crash reports | 10.10 | 10 | todo |
| SEC-02 | Correlation references stored; secrets and auth data not | 10.10 | 10 | todo |
| SEC-03 | Keys and passphrases via Android Keystore | 10.10 | 10 | todo |
| SEC-04 | Manager approval tokens never cached | 10.10 | 10 | todo |
| SEC-05 | Log redaction is opt-out, not opt-in | 10.10 | 10 | todo |
| SEC-06 | Audit till, payment, recovery, void, discount, refund, override | 10.10 | 10 | todo |
| SEC-07 | Fake providers test-source-set only + release-build assertion | 10.10 | 10 | todo |
| SEC-08 | `FLAG_SECURE` on payment and recovery surfaces | 10.10 | 10 | todo |

## A11Y — Accessibility

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| A11Y-01 | WCAG AA contrast in both schemes | 10.17 | 10 | todo |
| A11Y-02 | 48 dp minimum targets at every window class | 10.17 | 10 | todo |
| A11Y-03 | Focus order follows visual order | 10.17 | 10 | todo |
| A11Y-04 | Complete keyboard and D-pad traversal | 5.10 | 10 | todo |
| A11Y-05 | TalkBack labels describe action and state | 10.17 | 10 | todo |
| A11Y-06 | Colour never the sole status signal | 10.13 | 10 | todo |
| A11Y-07 | Reduced motion honoured | 4.9 | 10 | todo |
| A11Y-08 | Font scaling to documented maximum without clipping | 10.17 | 10 | todo |

## PHONE — Compact product

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| PHONE-01 | Compact shell with bottom navigation and overflow | 9.2 | 7 | todo |
| PHONE-02 | Persistent cart bar expanding to a full-height sheet | 9.2 | 7 | todo |
| PHONE-03 | Checkout as navigation; one screen per tender leg | 9.4 | 7 | todo |
| PHONE-04 | **Every row of the §9.3 parity table reachable** | 9.3, D-010 | 7 | todo |
| PHONE-05 | Two transient layers maximum | 9.4 | 7 | todo |
| PHONE-06 | Primary actions within thumb reach | 9.4 | 7 | todo |
| PHONE-07 | Compact goldens at 412×915 and 360×800 | 11.3 | 7 | todo |
| PHONE-08 | No Device Owner, Lock Task or Magisk on the phone APK (L1) | 1.2 | 7 | todo |

## CERT — Certification apparatus

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| CERT-01 | Roborazzi screenshot harness on the JVM | 11.5 | 1 | todo |
| CERT-02 | Macrobenchmark module + Baseline Profile generation | 11.5 | 1 | todo |
| CERT-03 | Design-lint CI script with every §11.4 rule | 11.4 | 1 | todo |
| CERT-04 | Five-reference-size test matrix | 11.2 | 1 | todo |
| CERT-05 | Lossless benchmark export resampled for colour | 11.5 | 1 | todo |
| CERT-06 | Complete golden surface set, both schemes, both glass treatments | 11.3 | 10 | todo |
| CERT-07 | Gates V1–V10 all passing | 11.1 | 10 | todo |
| CERT-08 | Component certification matrix — every component, three gates | 11.6 | 10 | todo |
| CERT-09 | Test layers 1–12 all present and running | 11.7 | 10 | todo |
| CERT-10 | Owner sign-off recorded, dated, in the delta registry | 11.1 | 10 | todo |

---

## Summary

| Area | Rows |
|---|---:|
| SYS | 21 |
| ARCH | 10 |
| SHELL | 14 |
| DISC | 8 |
| FIT | 6 |
| QACC | 8 |
| CART | 12 |
| PAY | 13 |
| RCPT | 11 |
| CAT | 9 |
| HW | 8 |
| OFF | 8 |
| SEC | 8 |
| A11Y | 8 |
| PHONE | 8 |
| CERT | 10 |
| **Total** | **162** |

Three rows are `blocked` on backend work that is specified but not built: QACC-05 (operator pin
storage), PAY-01 and PAY-02 (reserve-first). Those contracts are in blueprint §7.4 and §10.6 so they
can be scheduled rather than discovered.
