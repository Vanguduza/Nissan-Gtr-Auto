# Structural Critique — Modules Built From Scratch vs Proven Patterns

> Read-only audit. This is the harsh, specific pass the user asked for. Every claim below
> is either a direct file:line citation (produced by two dedicated code-exploration
> workstreams — Android architecture and web architecture — plus direct reads performed
> in this session) or explicitly marked **[reference knowledge]** where it draws on
> general/public knowledge of Medusa, Bagisto, or Odoo's real module boundaries (a live
> external-research workstream against the actual GitHub repos was in progress but had
> not returned before this document was finalized — using own knowledge here rather than
> stalling, as instructed).

## 0. Reference patterns used for comparison (stated plainly, so claims can be checked)

- **Medusa** [reference knowledge]: commerce split into independent modules (product,
  inventory, pricing, promotion, cart, order, fulfillment) behind a workflows-SDK
  orchestration layer; storefront/admin are pure API consumers with zero direct DB
  access; each module has its own service class with a narrow public interface.
- **Bagisto** [reference knowledge]: Laravel package-per-domain (`packages/Webkul/Product`,
  `.../Sales`, `.../Customer`, `.../Inventory`, `.../Shop`, `.../Admin`), with the shop
  storefront and admin panel as structurally separate route/theme layers even though
  they share the same Laravel app.
- **Odoo** [reference knowledge]: `addons/<module>` per domain, with models like
  `stock.move`/`stock.quant`, `purchase.order`, `account.move`, `res.partner` as the
  stable public contracts other modules depend on.
- **OmniCart** [reference knowledge — unverified against a specific GitHub repo in this
  pass]: a Jetpack Compose commerce sample generally structured as
  `data/` (repositories + remote/local sources) → `domain/` (use cases) →
  `presentation/` (ViewModel + Compose UI) per feature, i.e. a **repository/use-case
  layer always sits between ViewModel and network client**. **Caveat**: the exact
  canonical repo behind "OmniCart" as referenced in the master design plan was not
  confirmed via live GitHub search in this pass (the external-research workstream had
  not returned); this comparison uses the well-known generic Android
  "Now in Android"/Compose clean-architecture pattern that OmniCart-style samples
  follow, not a verified read of one specific repository. Treat the *comparison point*
  (repository/use-case layer as standard practice) as solid; treat "OmniCart
  specifically does X" as unverified.

---

## 1. `apps/android-management/feature/pos` — the sharpest problem in this codebase

**Verdict: God ViewModel + God Screen, zero test coverage on the highest-risk file in
the repo.** This is the single module most in need of a structural refactor before
further features are added.

### The evidence

- `PosViewModel.kt` is **1,550 lines**. `PosScreen.kt` is **860 lines**.
- `PosUiState` declares **51 distinct properties** (`PosViewModel.kt:50-106`) covering:
  till mode, warehouse/customer/currency/fulfillment selection, cart + search, receipts/
  tenders/EcoCash, companion-device pairing, printer state, manager-auth dialog fields,
  quotations/park-cart state, and offline-sync badges — seven or eight *unrelated*
  feature domains living in one state object.
- `PosViewModel` exposes **61 public functions** (event handlers), spanning offline sync
  (`pullOfflineSnapshot`, `syncOfflineQueue` — lines 198-244), catalog/cart mutation
  (`searchCatalog:506`, `addPartFromCatalog:576`), companion/QR/printer orchestration,
  manager-authorization prompts (`requestDiscount:980` ... `confirmManagerAction:1051`),
  quotations/park (`createQuotation:1194`), and checkout (`checkout:1361`).
- **There is no repository or use-case layer.** The ViewModel constructor takes
  `RpcClient` directly (`PosViewModel.kt:120-126`) and calls it inline throughout —
  `rpc.searchCustomers` (`:286`), `rpc.createPosCart` (`:468`), `rpc.checkoutPosCart...`
  (`:1445-1458`). `PosModule.kt:4-6` — the file whose name implies it should be the
  module's composition boundary — is a two-line marker object (`const val id = "pos"`)
  and does nothing architecturally.
- **Zero test files exist for `PosViewModel.kt` or `PosScreen.kt`.** By contrast, the
  *offline* subsystem it wraps (`OfflinePosSyncEngine`, tested in
  `OfflinePosSyncEngineTest.kt` with three real scenarios — idempotent replay, price
  conflict, insufficient-stock rejection) is well tested. The riskiest, largest,
  least-tested file in the two Android apps is the one every single till transaction in
  the business flows through.

### Why this is bad, specifically (not just "needs review")

A senior reviewer's objection to a 1,550-line ViewModel with 61 public methods is not
aesthetic — it is that **it is currently impossible to unit-test checkout logic in
isolation from companion-device pairing, printer state, or quotation logic**, because
they all share one `viewModelScope` and one `PosUiState`. Every future feature (a new
tender type, a new manager-approval flow, richer offline conflict UI) has to be added to
this same file, growing both the blast radius of any change and the cost of writing a
test that isolates it. This is precisely the anti-pattern OmniCart-style Compose
architecture (repository/use-case boundary per feature) and Medusa's per-module service
boundary both exist to prevent — a single "commerce module" that never grows past the
size where a single engineer can hold it in their head, because responsibilities are
split at module boundaries, not left to accumulate in one class.

### Concrete refactor recommendation (not to be executed without an approved plan, per
the master audit prompt's non-negotiable rule #2 — recorded here as the audit's
recommendation only)

Split `PosViewModel` along its already-visible internal seams (the `PosUiState` fields
already group naturally):
1. `PosCartViewModel` — cart/catalog/checkout (the actual till).
2. `PosCompanionViewModel` — QR/companion pairing + printer.
3. `PosManagerAuthViewModel` (or a shared `ManagerReauthController` usable by kiosk too,
   see Finding 5 in `security-findings.md`) — discount/void/refund/price-override prompts.
4. `PosQuotationViewModel` — quotations + park-cart.
5. Keep `OfflinePosSyncEngine` as-is (it is already correctly decoupled) and inject it
   into (1) rather than composing it inside `PosScreen.kt:71-92` — move that composition
   root into a small `PosOfflineComponent`/factory so `PosScreen` stops being a manual DI
   graph as well as a UI file.
Introduce a thin `PosRepository` (or per-domain repositories) between these ViewModels
and `RpcClient`, so checkout logic becomes unit-testable without a fake network client
wired through 61 methods worth of surface area.

---

## 2. `core/rpc` (both apps) — God interface, naming taxonomy collapse

**Verdict: The interface boundary this whole system depends on is itself the largest
anti-pattern in the Android codebase, and it is the *cause* of `PosViewModel`'s size, not
independent of it.**

### The evidence

- `RpcClient.kt` (management) declares **~102 methods** on one interface, backed by
  **79** `RpcNames` constants (`RpcNames.kt:10-150`) spanning HR, POS, warehouse,
  logistics/delivery, chat, procurement, bins, consignment, credit, fleet, and
  onboarding — separated only by `//` section comments (`RpcClient.kt:23,226,269,383`),
  not by type. `RpcModels.kt` is **721 lines**.
- The customer app's twin (`RpcClient.kt` in `android-customer`) is smaller (~39
  methods, 28 `RpcNames` constants) but is **the same pattern at smaller scale** — one
  interface, one models file (100 lines), covering catalog/cart/pay/garage/chat/track/
  wishlist/compare/reviews.
- **Naming taxonomy does not follow a domain-prefix convention.** Sampled evidence:
  `create_pos_cart`, `checkout_pos_cart` (POS infix, not prefix), `save_hr_onboarding_stage`
  vs `clock_attendance` (no `hr_` on the second), `post_stock_receipt`/`create_warehouse_bin`
  (no `wh_` prefix despite being warehouse RPCs), `post_finance_refund` vs `post_pos_refund`
  (mixed ownership signal for what should be one finance concept), and a literal
  **hyphen-vs-underscore split** — `hr-onboarding-create-auth` (an Edge Function path,
  `RpcNames.kt:16`) sits in the same constants file as underscore-separated Postgres RPC
  names, with no naming convention distinguishing "this hits an Edge Function" from
  "this is a direct Postgres RPC."
- **No DI framework exists to make this interface swappable/scoped.** Repo-wide search
  for `hilt|koin|@HiltViewModel|@Inject|dagger` across both Android apps returned **zero
  matches**. Composition is manual: `RpcClientFactory.create(...)` at the Activity
  (`MainActivity.kt:152-156`), passed down through Compose parameters, with
  `viewModel(factory = XxxViewModel.factory(rpc))` at each screen. This is internally
  *consistent* (not mixed frameworks) but means every feature module transitively
  depends on the entire 102-method interface just to get a `ViewModel` factory —
  there is no way to give `HrOnboardingViewModel` a narrower dependency than "the whole
  app's RPC surface."

### Why this is the root cause, not a separate issue

`PosViewModel` is 1,550 lines partly *because* there is no smaller-scoped interface it
could depend on instead of the God `RpcClient` — splitting the ViewModel (§1) without
also splitting this interface just moves the same 102-method dependency into four
smaller files that all still import the same god object. **Fix this one first, or the
ViewModel refactor in §1 will not actually reduce coupling.**

### Concrete refactor recommendation

Decompose `RpcClient` into domain-scoped interfaces (`PosRpc`, `HrRpc`, `WarehouseRpc`,
`LogisticsRpc`, `ChatRpc`, ...) that a single `SupabaseRpcClient` class can implement all
of simultaneously (so there is still one network client under the hood — this is not
proposing a rewrite of the transport layer, just the contract surface). Each
ViewModel/feature module then depends on only the interface(s) it needs. Normalize the
naming taxonomy opportunistically (prefix by domain) the next time each RPC name is
touched, rather than as a big-bang rename — a mass rename of 79+ constants without a
migration/compat plan is exactly the kind of "broad refactor without an approved plan"
the audit's non-negotiable rules warn against.

---

## 3. `apps/web` — three audiences, one deployable, security enforced only after JS loads

**Verdict: The architecture works today because RLS is genuinely solid (see
`security-findings.md` Finding 6), but the web app's own internal boundaries do not
reflect the Medusa/Bagisto separation this project explicitly named as a reference, and
the one layer that *should* be non-negotiable (edge-level auth) is missing entirely.**

### The evidence

- **No `middleware.ts` exists anywhere in `apps/web`.** Staff-only routes — including
  `/staff/*` **and** `/procurement/*`, which is staff-gated but lives under the `(b2b)`
  route group beside customer-facing `/b2b` (`app/(b2b)/procurement/layout.tsx` vs
  `app/(b2b)/b2b/layout.tsx`) — are protected exclusively by a `'use client'`
  `StaffGate` component (`staff-gate.tsx:1,70-176`) that runs **after** the page's JS has
  been shipped to the browser.
- **The supplier portal has no layout-level gate at all.** `(supplier)/layout.tsx:1-8`
  wraps supplier pages in `ShopChrome` — the *storefront's* header/cart/chat shell — with
  authorization deferred entirely to per-panel `requireSession()` calls
  (e.g. `supplier-rfq-list.tsx:32-37`). A supplier visiting `/supplier` sees the
  customer shopping chrome, not a supplier-specific shell — this is not just cosmetic,
  it means the "supplier" audience was never given its own layer, it was grafted onto
  the customer layer.
- **Staff's foundational session/result types are owned by the *customer* module.**
  `requireSession` and `StorefrontResult` are defined in `customer-storefront.ts:36-113`
  and *re-exported* by staff modules (`staff-pos.ts:3-13`, `staff-finance.ts:10-15`,
  `staff-warehouse.ts:2-8`, and the rest of the 16 `staff-*.ts` files). In a codebase
  that is supposed to separate customer-facing and staff-facing concerns, the staff
  layer's most basic primitive is structurally a dependent of the customer layer, not a
  sibling of it.
- **The client-side data-access layer mirrors backend RPC sprawl 1:1, with no shared
  abstraction beyond a thin client factory.** 16 files matching `lib/staff-*.ts`
  (`staff-analytics`, `staff-auth`, `staff-bins`, `staff-consignment`, `staff-credit`,
  `staff-delivery-tracking`, `staff-finance` [1,222 lines], `staff-fleet`, `staff-hr`,
  `staff-logistics`, `staff-ops`, `staff-pos`, `staff-pos-realtime`,
  `staff-stores-insights`, `staff-warehouse`, `staff-warranty`), plus cross-audience
  files that break the `staff-*` naming convention entirely (`rfq-portal.ts` serves
  both staff and supplier; `chat.ts` serves both customer and staff;
  `customer-reviews.ts:186-213` contains staff moderation functions despite its name).
  Each file independently repeats `createWebClient()` → `requireSession()` →
  `.rpc()/.from()` → `{ok, data|error}` — there is no shared data-fetch hook, no shared
  error mapper, and (confirmed directly) at least one checkout component bypasses the
  `lib` layer entirely and queries a table inline:
  `cart-checkout.tsx:183-187` calls `client.from("sales_invoices")` directly after
  calling `checkoutCustomerCart()` from `customer-storefront.ts`, rather than the
  checkout helper returning what the UI needs.
- **No Medusa-style headless separation exists.** Presentation and data access are
  fused in client components throughout — `catalog-browse.tsx:28-65` creates a Supabase
  client, checks the session, and calls a loader function all inside one `useEffect`;
  `add-to-cart-button.tsx:17-40` owns session-checking *and* cart mutation inside a
  single button component. Medusa's storefront, by contrast, never imports a database
  client — it only calls a versioned HTTP API. This repo's storefront has full,
  unmediated Supabase client access from React components.

### Why this matters given the reference patterns actually named in the project's own doc

The project's own master design plan explicitly names Medusa ("API-first patterns") and
Bagisto ("web administration patterns," implying admin/shop separation) as references.
Neither pattern requires a full rewrite to approximate — but today, `apps/web` has
**less** separation between audiences than either reference, and the one place that
*should* be architecturally trivial to add regardless of the rest (edge middleware for
`/staff` and `/procurement`) is simply absent, not partially implemented.

### Concrete refactor recommendations

1. Add `middleware.ts` (or a server-side session check in each staff/procurement/supplier
   layout using `@supabase/ssr`) that redirects unauthenticated/non-staff requests
   **before** rendering, independent of `StaffGate`. Highest-value, lowest-risk fix in
   this entire document — no schema change, no RLS change, purely additive.
2. Extract `requireSession`/`StorefrontResult` (and a shared error-mapper) into a
   neutral `lib/session.ts` or `lib/data-access-kernel.ts` that neither the customer nor
   staff modules "own" — both import from it as peers.
3. Give the supplier portal its own layout/chrome and an explicit `SupplierGate`,
   instead of inheriting `ShopChrome`.
4. Move `/procurement` under `(staff)` (or a dedicated `(staff-procurement)` group) so
   route-group boundaries actually match audience boundaries, which is the cheapest
   possible readability win here.
5. Split genuinely cross-audience files (`rfq-portal.ts`, `chat.ts`,
   `customer-reviews.ts`'s moderation functions) into audience-suffixed files even if
   they still call the same underlying RPCs — the current naming actively hides which
   audience a given exported function is for.

---

## 4. `apps/android-management/feature/kiosk` — the one module that held up well

**Verdict: This is the module in the "likely candidates to scrutinize hard" list that
did *not* turn out to have a God-object problem.** Worth stating plainly, since a harsh
audit should be equally precise about what is *not* broken.

### The evidence

- No single class inside `feature/kiosk` tries to own everything. Responsibilities are
  genuinely split: `HardeningPathDetector` (pure function, unit-testable, covered by
  `KioskDevicePrefsTest.kt:36-78`), `LockTaskController` (Android DO/Lock Task API calls,
  correctly thin — delegates status back to the detector, `LockTaskController.kt:52-61`),
  `KioskDevicePrefs` (DataStore-backed settings), `BrandedSplashHost`/`IdleSessionHost`
  (Compose lifecycle hosts), and `DeviceAdminConsoleScreen` (the ops UI). Composition
  happens correctly at the app level (`MainActivity.kt:167-199`), not hidden inside the
  feature module — this is the right place for an orchestrator to live for something
  this tied to `Activity` lifecycle.
- `KioskModule.kt:7-9` is, like `PosModule.kt`, just a marker object — but here it does
  not matter, because nothing in this module *needed* a DI graph; the pieces are already
  small enough to wire manually without becoming unmanageable.
- The one real issue found is a **security** one, not a structural one: no
  re-authentication gate at the point `exitLockTask()` is called
  (`security-findings.md` Finding 5) — this is a missing *feature* (a manager-reauth
  check), not evidence of an architectural anti-pattern in how the module is built.
- Testing coverage is thin (`LockTaskController`, `BrandedSplashHost`, `IdleSessionHost`,
  `DeviceAdminConsoleScreen`, `KioskDeviceAdminReceiver` all have zero tests), but this is
  a coverage gap common to Android framework-coupled classes generally (they require
  Robolectric/instrumentation to test meaningfully), not a sign the module needs a
  rewrite.

### Recommendation

Leave the structure alone. Add the manager-reauth gate from Finding 5. If test coverage
is prioritized, invest in Robolectric for `LockTaskController` before inventing any new
kiosk abstraction — there is no missing architecture layer here to build.

---

## 5. `apps/android-management/feature/hr` (onboarding) — sound, but its safety-net test is misleading

**Verdict: Structurally fine at current scope (~437 LOC ViewModel, readable stage
machine) — the actual finding is about test-suite honesty, not architecture.**

### The evidence

- `HrOnboardingViewModel.kt` follows the same `StateFlow` + direct-`RpcClient`-call
  pattern as everything else in this codebase (not yet a God object — no evidence of the
  scale problem `PosViewModel` has).
- `HrOnboardingFakeRpcParityTest.kt:14-70`, despite its name implying it verifies
  **Fake ↔ Live RPC parity**, only instantiates `FakeRpcClient()` and asserts its own
  fake return values. It never imports `SupabaseRpcClient`, never asserts that
  `RpcNames.SAVE_HR_ONBOARDING_STAGE` (or equivalent) is used identically by both
  implementations, and never checks that the Live path's Postgres/Edge entrypoints match
  what the Fake simulates. The only parity guarantee is that both classes compile against
  the same `RpcClient` interface (`RpcClient.kt:545-567`) — a type-level guarantee, not a
  behavioral one.

### Why this matters

A test named "parity test" that does not test parity is worse than no test at all,
because it gives false confidence that Fake/Live divergence would be caught in CI. If the
`SupabaseRpcClient` implementation of an HR onboarding RPC ever drifts from what
`FakeRpcClient` simulates (e.g. a payload field renamed on the server side), this test
suite will stay green.

### Recommendation

Either rename the test to reflect what it actually checks (fake-only smoke test), or add
a genuine parity assertion — e.g. a shared fixture asserting both implementations are
invoked with the same `RpcNames` constant for each onboarding stage transition, ideally
via a contract test that fails loudly if Live and Fake diverge on shape.

---

## 6. Backend/domain modules — see `domain-database-analysis.md` for the full comparison

Summarized here because the user asked about "modules built from scratch" broadly:

- **AI autonomous layer** (`20260803150000`, `20260803220000`) is the **best-integrated**
  bolt-on found anywhere in this audit — see `domain-database-analysis.md` §6. Do not
  lump it in with the Android/web findings above; it does not show the same anti-patterns.
- **POS offline sync** (`20260803270000`) is well-engineered on idempotency (double-layer
  protection) and appropriately conservative on scope (cash-only, walk-in-only), with one
  concrete integrity gap (exchange-rate trust, see `security-findings.md` Finding 1).
- **POS as a whole**, across the *client* (§1-2 above) is fragmented at the schema level
  too: `pos_carts`/`pos_cart_lines`/`sales_invoices` (2026-07-23) plus
  `pos_action_audit`, `pos_quotations`/`pos_quotation_lines`,
  `pos_offline_sale_receipts`, `pos_scan_sessions` added across **six separate
  migrations dated 2026-07-25 through 2026-08-03** — each addition is individually
  well-scoped (per `domain-database-analysis.md` §5), but the cumulative effect on the
  Android client is the 1,550-line `PosViewModel` in §1: the backend's incremental,
  additive migration style is *correct database practice* but has a direct, traceable
  cost on the client that absorbed each addition into the same file instead of a new
  module boundary.

---

## Cross-cutting pattern: incremental additions land in existing files instead of new module boundaries

The single throughline connecting §1 (POS ViewModel), §2 (RPC God interface), and §3
(web `lib/staff-*.ts` sprawl) is not that any one migration or feature was built badly in
isolation — each individual addition cited above is reasonably well-scoped on its own.
It is that **this codebase has no enforced convention for when a new capability should
become a new module/interface/file versus an addition to an existing one**, on either
client. The backend migration discipline (one migration per logical change, RLS in the
same file) is good and consistently followed. The client-side equivalent — "one
ViewModel/repository per bounded feature, one interface segment per domain" — does not
exist as an enforced convention, and the God ViewModel/God interface findings above are
the accumulated cost of that gap over roughly two weeks of continuous feature addition
(2026-07-23 → 2026-08-03 by migration timestamps).

**This is the one recommendation to prioritize above any individual refactor**: before
splitting `PosViewModel` or `RpcClient`, agree on (and write down, e.g. as a
`.cursor/rules/android_architecture.mdc`) the boundary rule itself — otherwise the split
proposed in §1-2 will simply re-accumulate the same way over the next two weeks of
feature work.
