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
| ARCH-01 | Module split `pos-design` / `pos-domain` / `pos-data` / `pos-ui` | 10.1 | 1 | done — 2026-10-01: all four modules build in `:app:compileTabletDebugKotlin`; UI in `pos-ui`, pure logic in `pos-domain` |
| ARCH-02 | Hilt dependency injection | 10.1 | 1 | partial — Hilt plugin applied to `pos-ui`; `PosStore` is constructor-injected with `PosGateways` but no Hilt module binds them yet (Phase 5) |
| ARCH-03 | `pos-domain` has zero Android dependency — enforced in CI | 10.1 | 1 | done — 2026-10-01: `DomainPurityTest` passes and `:feature:pos-domain:test` now runs in `android-pos.yml` |
| ARCH-04 | Eight typed gateways replacing `RpcClient` | 10.2 | 3 | partial — Catalog, Fitment, Cart, Pin, Session gateways declared in `pos-domain`; Checkout, Tender, Till, Recovery, Quotation not yet; no `pos-data` implementations yet (Phase 5). **2026-10-03:** `TillGateway` declared and implemented (`RpcTillGateway`) |
| ARCH-05 | `PosResult`; no exceptions for business outcomes | 10.2 | 3 | partial — every new gateway returns `PosResult`; legacy `PosViewModel` still throws |
| ARCH-06 | Idempotency keys on every money- or stock-moving call | 10.2 | 3 | todo |
| ARCH-07 | `PosStore` over pure, total reducers | 10.3 | 3 | partial — `PosStore` over pure `reduce()` covers home: cascade, search, recent, Popular Items (D1), cart add/qty/remove, pins with rollback; 17 JVM reducer tests + 6 store tests. Checkout, customer, quotes, returns not yet |
| ARCH-08 | Screen-scoped immutable projections | 10.3 | 3 | partial — screens read `PosState` and derived `popularRow`; per-screen projections still to split as destinations land |
| ARCH-09 | Type-safe navigation, one graph for both form factors | 10.4 | 3 | todo |
| ARCH-10 | `PosError` taxonomy mapped to string resources | 10.11 | 3 | done — 2026-10-01: every `PosError` case maps to a string resource (`PosUiKit.errorText`); new taxonomy cases must add one |

## SHELL — Expanded shell

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| SHELL-01 | Nav rail, eight destinations, canonical order, no `Reports` | 6.1, D-001 | 4 | partial — built in `pos-ui` (eight destinations, canonical order, no Reports; lint DL-07 green); screenshots `docs/design/pos/tablet/2026-10-01-tablet-home-*.png`; §11.1 tolerance gate against the benchmark not yet automated |
| SHELL-02 | Rail active and inactive states | 6.1 | 4 | partial — brand-red filled active pill, muted inactive; same gate note as SHELL-01 |
| SHELL-03 | Rail logo lockup | 6.1 | 4 | partial — logo lockup from `brand/logo.png`; same gate note as SHELL-01 |
| SHELL-04 | Rail GT-R artwork and brand statement | 6.1 | 4 | partial — GT-R artwork flexes to remaining height, brand statement present at 1280×800; same gate note |
| SHELL-05 | Header four zones | 6.2 | 4 | partial — four zones; header spans canvas + cart as in the benchmark (`PosScaffold` fixed); Medium collapse (§8.4) not yet |
| SHELL-06 | Search field with scan affordance | 6.2 | 4 | partial — search field with scan affordance; scan routes to the host (bridge wiring in Phase 9) |
| SHELL-07 | Operator identity block | 6.2 | 4 | partial — operator block from `SessionGateway` |
| SHELL-08 | Date and time block | 6.2 | 4 | partial — date/time block; host supplies the time |
| SHELL-09 | Offline status surface | 6.2, D-005 | 4 | partial — offline label in the header when `online=false`; offline outbox wiring not yet |
| SHELL-10 | Catalogue version and freshness indicator | 10.9 | 5 | todo |
| SHELL-11 | Hero with collapse to `minDp` and drop at Compact | 6.3, D-009 | 4 | partial — hero follows §3.6 (`clamp(160, available×0.34, 280)`), live text over the text-free crop; drop at Compact not yet |
| SHELL-12 | Category row, derived count, horizontally scrollable | 6.4 | 4 | partial — category count derived by §3.3 formula (no literal), horizontally scrollable, long-press pins |
| SHELL-13 | Recent searches chips with clear | 6.4, D-004 | 4 | partial — recent searches chips with Clear All, newest first, bounded |
| SHELL-14 | Adaptive zone law verified at all five reference sizes | 3.3, 3.5 | 4 | partial — verified at 1280×800, 1536×1024, 1024×768 (light + dark); 800×1280 shows the icon rail but the Medium header/hero (§8.4) is not built; Compact sizes are Phase 7 |

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
| FIT-01 | Header cascade — Model → Generation → Engine | 8.1, D-002 | 4 | partial — Expanded inline Model → Generation → Engine (no Make, D2), selection resets levels to the right, late responses dropped; Medium popover and Compact sheet not yet |
| FIT-02 | Maker field when the multi-make catalogue is active | 8.1 | 4 | dropped — owner D2 2026-10-01: Make is never shown (D-013) |
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
| QACC-05 | Operator-scoped server persistence + RPCs | 7.4 | 6 | partial — `20260907140000_pos_operator_popular_pins.sql` on the canonical lineage provides list/upsert/delete with owner RLS; reorder RPC missing |
| QACC-06 | Unbounded `LazyRow`; no literal item count in code | 7.5 | 6 | todo |
| QACC-07 | Empty state inviting the first pin | 7.5 | 6 | todo |
| QACC-08 | Offline — cached pins render, mutations queue as pending | 7.5 | 6 | todo |
| QACC-09 | Server best sellers merged into the row after pins (minus pinned duplicates) | 7.3, D-014 | 6 | partial — web row merges best sellers after pins (`lib/pos/popular.ts`, mirrors `PosPopularItems.kt`); tablet UI pending rebuild |
| QACC-10 | Operator can remove any best seller; hides are operator-scoped, server-persisted and survive ranking refresh until re-added | 7.3, D-014 | 6 | partial — `20261001100000_pos_popular_hidden_bestsellers.sql` + smoke + web UI; migration not yet applied or smoked (no local database in this environment) |
| QACC-11 | Subcategory and model pin kinds (in addition to spare, category, vehicle) | 7.1, D-014 | 6 | partial — web: vehicle (model), category and EPC subcategory pins with long-press / right-click / pin buttons; tablet UI pending rebuild |

## CART — Current Sale

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| CART-01 | Pane anatomy and order | 6.5 | 4 | partial — header · scrolling lines · Add Customer · subtotal/discount · total · Proceed to Payment · Park; only the list scrolls |
| CART-02 | Row column grid and deterministic truncation | 5.8, 6.5 | 4 | partial — thumbnail · name (≤2 lines) · part number · unit price · remove · stepper |
| CART-03 | Unit price, tabular figures | 6.5, D-012 | 4 | partial — unit price per row in `numericPrice` (tnum) |
| CART-04 | Quantity stepper — optimistic, settles, animates on divergence | 5.4 | 6 | todo |
| CART-05 | Remove line with undo | 5.9 | 6 | todo |
| CART-06 | Customer association | 6.5, D-008 | 6 | todo |
| CART-07 | Totals block — right-aligned, backend values, explicit currency | 6.5, D-006 | 6 | todo |
| CART-08 | Zero-value discount row stays visible | 6.5 | 4 | partial — discount row always rendered, US$ 0.00 included |
| CART-09 | Clear cart behind confirmation naming the verb | 5.9 | 6 | todo |
| CART-10 | Park and resume sale | 12 | 6 | todo |
| CART-11 | Locked-for-checkout state; cart not editable when reserved | 10.5 | 8 | partial — web and tablet: lines, customer, park, resume and governed edits refused while a reservation exists; back to sale releases it (reducer + store tests) |
| CART-12 | Cart recomposition isolated from catalogue — verified by test | 10.15 | 6 | todo |

## PAY — Checkout and payment

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| PAY-01 | Reserve-first sequence | 10.6 | 8 | partial — web and tablet reserve on Pay (`prepare_pos_commerce_checkout` with a request key), settle against the order (`settle_pos_commerce_tenders`, key kept after a dropped answer) or through the provider webhook; not yet run end to end on a device against the hosted backend |
| PAY-02 | Reservation TTL and designed expiry behaviour | 10.6 | 8 | partial — web and tablet show "Stock held until" and close payment with a notice when the hold expires |
| PAY-03 | Tender capability model; blocked tenders disabled with reason | 10.7 | 8 | partial — web and tablet tender cards: providers probed (503 → not set up), offline, on account without a customer |
| PAY-04 | Adapters — cash, swipe terminal, EcoCash, Paynow, ContiPay | 10.7 | 8 | partial — cash, card/bank slip, EcoCash, Paynow, ContiPay (web and tablet); card machine (ECR) on the tablet via the `card-terminal` bridge: purchase, card part of a split payment, ask-again (status) and reversal, results signed by the paired device key and recorded by `card-terminal-result`; web lists, sets up (admin) and finishes approved card payments but cannot charge (no device key in a browser). Not yet run against a real acquirer app |
| PAY-05 | Five normalised terminal outcomes | 10.7 | 8 | partial — provider attempts (web and tablet) and card machine attempts (tablet): approved, declined, cancelled, error (failed), unknown; a lost machine answer or an approved charge that did not post is Unknown and goes to recovery |
| PAY-06 | Split tender legs; remaining balance always from backend | 10.5 | 8 | partial — web and tablet "Pay in parts" (`*_pos_split_*`): cash, card/bank (reference required) and store credit parts, each an idempotent step whose header is the server's balance; the sale posts when the parts cover it. Provider parts stay disabled with the reason (the deployed initiate functions do not start split legs); card-terminal parts come with phase 5 |
| PAY-07 | `Unknown` opens recovery and blocks duplicate charge | 10.7, 10.11 | 8 | partial — web and tablet: no answer in 3 minutes or money captured without a sale → Unknown; every tender and back-to-sale blocked; "Resolve payment" |
| PAY-08 | Recovery as a dedicated screen on both form factors | 10.4 | 8 | partial — web and tablet recovery screen: order status, exceptions, approver repair (badge, password or signed-in approver), release; list of payments to resolve |
| PAY-09 | Reduced basket after partial payment | 10.8 | 8 | partial — web and tablet: from the part-paid state only, the operator picks what the customer keeps, the customer's consent is required, the server computes the total (`accept_pos_split_affordable_items`) and any surplus becomes a refund for a manager |
| PAY-10 | Change due for cash tender | 6.6.1 | 8 | todo |
| PAY-11 | Manager reauth — discount, void, refund, price override | L2 | 8 | todo |
| PAY-12 | Refunds post through the finance pipeline | L3 | 8 | todo |
| PAY-13 | Quotations — create, send, convert | L4 | 8 | todo |

## RCPT — Receipt and print

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| RCPT-01 | `ReceiptDocument` model — one model, three renderers | 6.6.1 | 9 | partial — shared counter-receipt format v1 (`packages/shared/src/pos/receipt.ts`, Kotlin port `ReceiptFormat.kt`) drives web preview/print, tablet preview, ESC/POS 42-col and A4 80-col; conformance fixture `packages/shared/fixtures/pos-receipt/v1.json` checked by node and JVM tests, tablet string resources pinned to its labels; Edge `receipt_pdf` not yet on it |
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
| SEC-09 | **Staff portal** — POS → Settings → Staff portal; second credential login; role and `module_access` recalculated before any management module renders; management reached only this way from the tablet POS | owner D4 2026-10-01 | 3 | partial — implemented in `MainActivity.kt` on the canonical lineage; not yet on `PosTheme` or certified |

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
| CERT-01 | Roborazzi screenshot harness on the JVM | 11.5 | 1 | done — 2026-10-01: Roborazzi on the JVM green (`BaselineScreenshotTest`, `DesignSystemScreenshotTest`, `PosHomeScreenshotTest`); Robolectric runtime fetched via mirror locally |
| CERT-02 | Macrobenchmark module + Baseline Profile generation | 11.5 | 1 | todo |
| CERT-03 | Design-lint CI script with every §11.4 rule | 11.4 | 1 | todo |
| CERT-04 | Five-reference-size test matrix | 11.2 | 1 | partial — harness covers all five sizes; home certified at Expanded sizes only (Compact belongs to Phase 7) |
| CERT-05 | Lossless benchmark export resampled for colour | 11.5 | 1 | todo |
| CERT-06 | Complete golden surface set, both schemes, both glass treatments | 11.3 | 10 | todo |
| CERT-07 | Gates V1–V10 all passing | 11.1 | 10 | todo |
| CERT-08 | Component certification matrix — every component, three gates | 11.6 | 10 | todo |
| CERT-09 | Test layers 1–12 all present and running | 11.7 | 10 | todo |
| CERT-10 | Owner sign-off recorded, dated, in the delta registry | 11.1 | 10 | todo |

---

## WEB — Web POS parity (owner D7, `WEB_POS_PARITY.md`)

| ID | Feature | § | Phase | Status |
|---|---|---|---|---|
| WEB-01 | Web POS consumes generated `tokens.css` / `tokens.ts` only; no ad hoc colours | 4.1, D7 | 2 | done — `tokens.css` only; design-lint DL-12 + web typecheck + production build pass (2026-10-01) |
| WEB-02 | Expanded composition (rail, header, hero, categories, Popular Items, recent searches, Current Sale) at the benchmark ratios | 3, 6 | 3 | partial — home composition built and screenshot at 1536×1024 and 1280×800 with preview data (`docs/design/pos/web/`); real product imagery depends on live data |
| WEB-03 | Window classes and compact recomposition with the same breakpoints as the tablet | 3.5, 9 | 7 | partial — expanded, medium and compact (sticky sale bar + Pay); the designed phone recomposition (§9) is still PHONE work |
| WEB-04 | Typed `lib/pos/` gateway per RPC in the parity matrix | 10.2 | 5 | partial — gateway covers every parity-matrix capability (cart, setup, vehicle, pins + hide, customer + garage, manager discount/override/void/refund, checkout + EcoCash, park/resume, quotations, returns, EPC); not yet run against a live database |
| WEB-05 | Full capability parity per `WEB_POS_PARITY.md` §3 (cart, vehicle, pins, customer and garage, manager gate, quotations, returns, EPC) | 12 | 5–10 | partial — all screens exercised on preview data; 2026-10-01 live run against the real migrated schema (local Postgres + PostgREST 12, real RLS, staff sign-in through the login page): search → add → cash sale SINV-00002, EPC Navara D40 › Filters › Oil filter with real diagram image and callout at its seeded position → sale SINV-00003, pairing code created and claimed + phone QR scan landed; Realtime, Edge functions and hosted Storage not exercised |
| WEB-06 | W-001 online-only behaviour with the shared offline status surface | 10.12 | 9 | partial — offline banner; every sale mutation blocked while offline |
| WEB-07 | W-002 companion pairing + keyboard-wedge scanner; no browser camera | 10.18 | 9 | partial — scanner input into the search field; companion phone pairing screen not rebuilt on web yet |
| WEB-08 | W-003 browser print of the shared receipt document model (80 mm, A4) | 6.6 | 9 | done (code) — browser print at 80 mm and A4 renders the shared receipt format v1 (same text the tablet prints); screenshots `web-pos-receipt-*` |
| WEB-09 | Staff portal second login from POS → Settings (D4) | D4 | 3 | partial — Settings → Staff portal re-auth dialog; not yet run against a live database |
| WEB-10 | Playwright screenshots against the benchmark at 1536×1024 and each window class | 11 | 11 | partial — Playwright flows and screenshots run manually (`docs/design/pos/web/`); not yet an automated CI gate |
| WEB-11 | Pre-benchmark web POS UI deleted (`staff-pos-panel.tsx`, `staff-pos-shell.tsx`) | D7 | 3 | done — deleted 2026-10-01; online order prep moved to `/staff/pos/prep` |
| WEB-12 | Haptic feedback: add/qty tap, pin/remove select, long-press, sale complete, errors; Settings On/Off; honours reduced motion | D7 | 3 | done (web) — Vibration API in `lib/pos/haptics.ts`; Android browsers only, silent no-op on iOS/desktop. Tablet native haptics pending the tablet rebuild |

## Summary

| Area | Rows |
|---|---:|
| SYS | 21 |
| ARCH | 10 |
| SHELL | 14 |
| DISC | 8 |
| FIT | 6 |
| QACC | 11 |
| CART | 12 |
| PAY | 13 |
| RCPT | 11 |
| CAT | 9 |
| HW | 8 |
| OFF | 8 |
| SEC | 9 |
| A11Y | 8 |
| PHONE | 8 |
| CERT | 10 |
| WEB | 11 |
| **Total** | **177** |

Rows QACC-05, PAY-01 and PAY-02 were `blocked` on backend work. On 2026-10-01 they moved to
`partial`: the canonical lineage (`chatgpt/pos-reconcile-green-20260907`) already ships pin storage
and reserve-first commerce. Remaining gaps are noted per row. QACC-09 to QACC-11 and SEC-09 were
added for owner decisions D1 and D4 (`docs/decisions/2026-10-01-pos-owner-decisions.md`).

## TABLET-BUILD (2026-10-01, Phases 3–7 working slice — verified by JVM and Roborazzi tests, not yet on a device)

| ID | Feature | Blueprint ref | Phase | Status |
|---|---|---|---|---|
| TAB-01 | Tablet POS route renders the benchmark POS on live data (holding screen removed); Settings → Staff portal second login (D4) | 10.1, D4–D6 | 5 | partial — `PosTabletEntry` binds `pos-ui` to `pos-data` gateways over `RpcClient`; compiles in both flavours; not yet run on a device against Supabase |
| TAB-02 | Payment: split tender, cash change (display only), EcoCash push, receipt contacts; tenders must equal the server total | 6.6, 10.6 | 8 | partial — reducer + store tests; reserve-first (§10.6) contract still not on the backend |
| TAB-03 | Receipt preview = printed lines (80 mm ESC/POS, A4 document printer); failed print never blocks the sale | 6.6 | 9 | partial — preview, ESC/POS and A4 all render the shared receipt format v1 (conformance-tested); printer not exercised on hardware here |
| TAB-04 | Manager approval for discount, price override, void, refund (manager signs in for that action only) | D4, 10.x | 8 | partial — uses existing `withManagerApproval`; tests with fakes |
| TAB-05 | Customer search/create/edit, attach to sale, garage (0/1/many), save vehicle to garage | 9.3 | 6 | partial — screens + reducer tests |
| TAB-06 | Orders: parked sales (resume) and quotations (create, send, convert) | 9.3 | 6 | partial |
| TAB-07 | Returns: recent invoices, manager-approved refund | 9.3 | 8 | partial — superseded by RET-01…RET-07 (return by line, cores, warranty, card refund) |
| TAB-08 | EPC Browse drill-down (model → variant → section → diagram → parts, pin, find) | 9.3 | 5 | done (code) — diagram image with numbered callouts synced to the parts list, Add puts the stocked OEM in the cart; pixel or fraction boxes resolved by one rule shared with web (`lib/epc-box.ts`); fixture art matches seeded boxes; screenshots `tablet-epc*`; web checked in Chromium at 1536/1024/390 |
| TAB-09 | Focus dialogs: own window, page behind dimmed and (API 31+) blurred, back/outside dismiss, bottom sheet on phones | 5.6 | 4 | done (code) — screenshot `tablet-payment_dialog`; blur itself needs a device to observe |
| TAB-10 | Medium (icon rail, vehicle dialog, sale bar + cart sheet below 900 dp) and Compact (bottom navigation, stacked header, identity-strip hero, sale bar + sheet) | 3.5, 8.4, 9 | 7 | partial — rendered at 1024×768, 800×1280, 412×915, 360×800 |
| TAB-11 | Offline sale queue on the new shell | 10.12 | 8 | done (code) — restricted mode in the reducer (cash only, walk-in only, approvals/park/quotes online only, a sale started online is not converted); local cart from the snapshot; `PosOfflineOutbox` over the SQLCipher engine with WorkManager drain; reconnect moves an unpaid local cart to the server and replays; header queue count, Settings → Sync now; server replay checked idempotent and drift-refused (`supabase/tests/tablet_offline_replay_smoke.sql`); not yet run on a device |
| TAB-12 | Companion phone pairing on the new shell | 9 | 9 | done (code) — till: Pair phone (cart pane + Settings) shows the 6-digit code with expiry, polls session and cart every 3 s, ends with the sale; phone: Settings → Scan for a till claims the code (same staff account; server refuses cross-rep) and camera scans go to the till's cart via `add_cart_line_from_qr`; lifecycle and phone scan checked on the seeded DB (`supabase/tests/companion_pairing_smoke.sql`); not yet run on two devices |
| WEB-13 | Companion phone pairing on the web POS: pairing code with expiry, live cart lines and session status over Realtime, end pairing; no browser camera | 9, W-002 | 9 | partial — built and Playwright-checked on preview data; Realtime not yet run against a live project |
| WEB-14 | Adaptive layout (expanded / medium / compact) and focus dialogs over a blurred, inert page | 3.5, 5.6 | 7 | done (web) — Playwright at 1536×1024, 1280×800, 1024×768, 820×1180, 390×844 |

### 2026-10-01 live-run fixes

| ID | Finding | Fix |
|---|---|---|
| LIVE-01 | Web counter search without a vehicle only matched catalogue fitment rows: "oil filter" and even an exact stocked part number returned nothing | `search_pos_stock_items` (migration `20261001120000`): stock by part number and every description word, staff-only; web and tablet merge it with `search_catalog` hits |
| LIVE-02 | Out-of-stock parts could be added and failed only at checkout | Add disabled / refused with "out of stock" on web and tablet (online and offline) |
| LIVE-03 | Checkout showed "insufficient FIFO batch qty for item <uuid>" | Stock-ledger and privilege errors reworded for the cashier on both clients |

### 2026-10-01 full catalogue on every app

| ID | Change |
|---|---|
| CAT-01 | Migration `20261001130000_retire_demo_catalog_rows` removes the Navara D40 / X-Trail T31 fixture rows (variants, sections, diagrams, fitment, vehicle master) wherever the full catalogue is loaded; local fixture-only databases keep them for development |
| CAT-02 | Web POS and tablet POS EPC: diagram lists, parts and images come from `catalog-live-r2` (Supabase hierarchy + R2 part shards + signed R2 images); fail closed with "being published" / "not connected" states, never fixture data. R2 shards carry no callout boxes, so rows are matched by PNC |
| CAT-03 | Web POS and tablet POS vehicle-filtered search: the vehicle's R2 fitment shard (published vehicle-master id → `customer-search` / `customer-stock`) first; Supabase fitment rows only while R2 is not serving |
| CAT-04 | iOS: vehicle selector reads the full published vehicle master (`list_customer_vehicle_master`), vehicle parts come from the R2 shard first (same as Android). Not compiled here (no Swift toolchain). iOS customer EPC browse still uses the old diagram RPC: staff-only in the target architecture, needs an owner decision |
| CAT-05 | Live web check against the real schema with a `catalog-live-r2` stand-in: R2 off → "full catalogue is not connected yet"; R2 serving → signed image + R2 parts + Add |

## TILL — Till sessions (2026-10-03, owner request: wire the live POS operations backend; delta D-016)

| ID | Feature | Blueprint | Phase | Status |
|----|---------|-----------|-------|--------|
| TILL-01 | Open a till with a float (USD/ZiG) bound to this device; selling (add, resume, convert quote, pair phone, pay) is refused until a till is open and takes the operator to the Till screen; every new or resumed cart is attached to the till (`attach_pos_cart_till_session`) | 10.2 | 1 | partial — web: Playwright on preview data; tablet: reducer + store tests and Roborazzi captures; neither run against the live project yet |
| TILL-02 | Cash in (reason, notes when required) and manager-approved cash out / petty cash / bank drop / cash refund with governed reason codes (`list_pos_approval_reasons('cash_out')`) | 10.2, 10.10 | 1 | partial — as TILL-01 |
| TILL-03 | Blind denominated close: count by note and coin, server returns expected and variance; out counts need a variance reason; a variance waits for manager approval (`approve_pos_till_variance`) | 10.2, 10.10 | 1 | partial — as TILL-01 |
| TILL-04 | Manager-approved handover to another active sales/admin operator | 10.10 | 1 | partial — as TILL-01 |
| TILL-05 | Header operator block shows till state and opens the Till screen; recent tills list | 6.2 | 1 | partial — as TILL-01 |
| TILL-06 | Manager approval on the tablet always signs in as the manager, also through the offline-catalogue client wrapper (`ManagerApproval`) | 10.10 | 1 | done (code) — regression fixed 2026-10-03; was running approvals as the attendant since the offline catalogue wrapper landed |

## GOV — Governed actions and approval policies (2026-10-03, phase 2)

| ID | Feature | Blueprint | Phase | Status |
|----|---------|-----------|-------|--------|
| GOV-01 | Discount, price override, void and refund run through the `*_governed` RPCs with a configured reason (`list_pos_approval_reasons`); reasons that need notes cannot be submitted without them | 10.10 | 2 | partial — web: Playwright on preview data; tablet: reducer tests and Roborazzi captures; not yet run against the live project |
| GOV-02 | The approval policy (`pos_action_requires_manager`, value = discount % or price change %) decides whether a manager signs in; within policy the cashier confirms with a reason only; an unreadable policy asks for a manager (fail closed) | 10.10 | 2 | partial — as GOV-01 |
| GOV-03 | Settings → Approval policies: everyone sees the rules; admins edit threshold, always-manager and reason-required (`set_pos_approval_policy`, admin-only on the server); cash out and till variance stay manager-only | 10.10 | 2 | partial — as GOV-01 |
| GOV-04 | Web manager approval ends only its own session (`signOut({ scope: "local" })`), never the manager's sessions on other devices | 10.10 | 2 | done (code) — was global sign-out before 2026-10-03 |

## MGR — Manager approval by ID badge (2026-10-03, owner request)

| ID | Feature | Blueprint | Phase | Status |
|----|---------|-----------|-------|--------|
| MGR-01 | Approvers are employees, login optional (owner, 2026-10-03): any manager (grade A1/A2/B1, or an HR role that heads a team), an HR role flagged for approvals, or an employee an admin/HR assigns (`set_approver_assignment`); admins by role; web Settings → Approvers & ID badges | 10.10 | — | partial — backend live (migrations 20261003142638, 20261003144849), checked in rolled-back runs incl. an employee with no login approving at a cashier's till; web on preview data |
| MGR-02 | ID badges: QR `GTRMGR1:<id>:<secret>`, hash-only storage, shown once, ID-1 printable card, expiry, revocation, managers only | 10.10 | — | partial — as MGR-01 |
| MGR-03 | Badge approval: one call validates the badge, runs the governed action as approved by the holder, audits every outcome (approved, failed, rejected); 5 rejects in 15 min locks the operator | 10.10 | — | partial — as MGR-01 |
| MGR-04 | Tablet/phone: once a reason is chosen the front camera opens automatically for the badge (QR bridge, `CameraLens.FRONT`); password is the fallback; web uses a USB/Bluetooth scanner (no browser camera) | 10.10, 10.18 | — | partial — tablet: reducer/store tests and screenshot; not run on a device |
| MGR-05 | A signed-in manager approves with no badge or password prompt (`get_my_pos_approver_status`) | 10.10 | — | partial — as MGR-01 |
| MGR-06 | Approval audit trail: badge approvals, manager-session approvals and admin changes, append-only, admins and finance only | 10.10 | — | partial — as MGR-01 |

## RET — Returns, cores, warranty and stock by branch (2026-10-04, phase 6)

| ID | Feature | Blueprint | Phase | Status |
|----|---------|-----------|-------|--------|
| RET-01 | Returns opens the posted sale with what can still come back per line; core charges listed separately (`get_pos_invoice_detail`) | 10 | 6 | partial — web on preview data, tablet on the fake client and screenshots; hosted project has no posted invoices |
| RET-02 | Return by line with condition → cash refund (till), credit to account, store credit, swap for the same part, or warranty; outcomes that cannot apply are shown disabled with the reason; staff draft (`create_pos_return_case`), approver posts (`post_pos_return_case`, badge `return_post`); a failed post reuses the draft | 10, 10.10 | 6 | partial — as RET-01; backend credit-note path fixed (20261004003342) and checked in a rolled-back run |
| RET-03 | Old cores: take back and give the core charge back as cash, account credit or store credit (`post_pos_core_return`, badge `core_return`) | 10 | 6 | partial — as RET-01 |
| RET-04 | Warranty: open a claim from the sale (serial checked against the part), list by status, decide replace / credit / take back / reject (approver), close | 10 | 6 | partial — as RET-01 |
| RET-05 | Refund whole sale (`post_pos_refund_governed`) only while nothing from the sale was returned | 10.10 | 6 | partial — as RET-01 |
| RET-06 | Tablet: card-machine refund of a whole card sale — approver starts it (`begin_pos_card_terminal_refund`, badge `card_refund_begin`), the machine pays back (Refund operation, signed evidence), approver posts it (`finalize_pos_card_terminal_refund`); no clear answer → Unknown, recovery | 10.7, 10.11 | 6 | partial — reducer tests; no card machine set up on the hosted project |
| RET-07 | Stock by branch from part cards: on hand, held, free, on the way (`list_pos_stock_availability`) | 9 | 6 | partial — web preview and tablet screenshot |

## FUL — Fulfilment (2026-10-04, phase 7)

| ID | Feature | Blueprint | Phase | Status |
|----|---------|-----------|-------|--------|
| FUL-01 | From Stock by branch: hold here for collection, collect at another branch, bring it here (branch transfer), back-order (`create_pos_fulfillment_request`); a hold needs the part in the current sale and goes with it | 9, 10 | 7 | partial — web preview flow and tablet reducer tests/screenshots; hosted project has no stock levels, so a live hold is refused for stock |
| FUL-02 | A hold turns ready with its invoice when the sale is paid; a transfer when the warehouse posts it (server triggers) | 10 | 7 | partial — as FUL-01 |
| FUL-03 | Orders → Collections & transfers: status filter, send transfer (warehouse staff), mark a back-order ready, handed over / received here, release; a paid hold is never just released | 10 | 7 | partial — as FUL-01; back-order hand-over is a backend gap (no invoice link) |

## LTR — Payment letters, signature, business details (2026-10-04, phase 8)

| ID | Feature | Blueprint | Phase | Status |
|----|---------|-----------|-------|--------|
| LTR-01 | Recovery: Payment letter block per provider payment, card-machine attempt and split refund; letters already issued; Issue letter (signed-in manager / finance / admin) opens the letter (`create_payment_resolution_letter`, `get_payment_resolution_letter_render_data`, `list_payment_resolution_letters`) | 10.7, 10.11 | 8 | partial — web preview flow incl. print preview; tablet reducer tests and screenshot; no live letter issued (needs a manager with a signature) |
| LTR-02 | Printable A4 letter: business header, payment as observed, references, card details, note, the issuer's signature and name/title/code | 10.7 | 8 | partial — web print (A4 @page, dialogs hidden in print); tablet through `DocumentPrinterBridge.printSignedDocument` |
| LTR-03 | Settings → My signature: draw (web canvas / tablet touch) or upload (web); private per-user storage, sha256 registered (`register_my_manager_signature`) | 10.10 | 8 | partial — as LTR-01 |
| LTR-04 | Settings → Business details on documents (admin, `set_business_document_profile`; read via `get_business_document_profile`) | — | 8 | partial — as LTR-01; read checked live |

## COD — Cash and card on delivery, driver app (2026-10-04)

| ID | Feature | Blueprint | Phase | Status |
|----|---------|-----------|-------|--------|
| COD-01 | Stop → Proof of delivery → Payment: amount due, method and paid-so-far from `get_delivery_job_payment_context`; nothing shown for stops without an invoice | 10.7 | other apps | partial — unit tests and screenshots on the fake backend |
| COD-02 | Cash received (part or full, optional note), idempotent on a kept request id (`collect_delivery_cash`) | 10.7 | other apps | partial — as COD-01 |
| COD-03 | Card on delivery: machine assigned to this phone (`list_delivery_card_terminals`), pair with the Keystore key (`register_delivery_card_terminal_device_key`), charge (`begin_delivery_card_terminal_payment` → bridge → signed `card-terminal-result` → `finalize_delivery_card_terminal_payment`) | 10.7, 10.11 | other apps | partial — as COD-01; no machine assigned on the hosted project |
| COD-04 | Unknown answer or charged-not-posted → recovery (`get_delivery_card_terminal_recovery`): ask the machine again or post it; never a second charge | 10.7, 10.11 | other apps | partial — as COD-01 |
| COD-05 | "Complete delivery" held with the reason while money is due or a card charge is unresolved | 10.7 | other apps | partial — client-side only (server gap) |
