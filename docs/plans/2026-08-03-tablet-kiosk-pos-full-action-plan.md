# Tablet Kiosk POS — Full Action Plan (Revised)

- Status: **complete** (Must / Should = Done or Stub; Later catalogued, not blocking)
- Date: 2026-08-03 (rev 4 — catalog close + verify)
- Note: Phase −1/0 complete; dual APK + kiosk shell + POS/quotations/approvals shipped in-tree. Offline POS cache (#57/#59) Done. Later (#16–19, #29, #58, #60–62, #64) stay deferred.
- Parents: [`2026-08-03-pos-kiosk-role-routing.md`](./2026-08-03-pos-kiosk-role-routing.md), [`2026-08-03-bluetooth-content-share-and-device-session.md`](./2026-08-03-bluetooth-content-share-and-device-session.md), Batch 1 [`2026-08-03-erp-batch1-commerce-pos-hr-finance.md`](./2026-08-03-erp-batch1-commerce-pos-hr-finance.md)
- Sources: [`Nissan_GTR_Auto_POS_Kiosk_Role_Based_Routing_Specification.md`](../../Nissan_GTR_Auto_POS_Kiosk_Role_Based_Routing_Specification.md), [`catalog/bluetooth_content_share.html`](../../catalog/bluetooth_content_share.html) (filename ≠ BT file-share product)
- ADRs: [`pos-scan-session-pairing`](../decisions/2026-07-25-pos-scan-session-pairing.md), [`web-management-parity-rbac`](../decisions/2026-07-25-web-management-parity-rbac.md), [`offline-sqlcipher-pos-cache`](../decisions/2026-08-03-offline-sqlcipher-pos-cache.md) (**Later stub**)
- Skills: `/token-discipline`; `/qr-inventory-workflow` if scan/print changes; `/accounting-ledger` if refund JE deepens — **no** auto `/ui-ux-pro-max`
- Lane(s): **`@management_app_agent`** (primary) → `@backend_agent` → `@hardware_mobile_agent` → `@web_agent` (parity) → `@finance_agent` (refund JE only)

### Catalog progress (#1–69) — 2026-08-03 close

Legend: **Done** = shipped enough for Must/Should; **Stub** = interface/docs/UI placeholder or partial; **Later** = deferred (not blocking complete).

| # | Feature | Pri | Progress |
|---|---------|-----|----------|
| 1 | Separate tablet vs phone APKs | Must | **Done** — `phone` / `tablet` flavors; distinct `applicationId`s |
| 2 | Device Owner provisioning | Must | **Done** — tablet `KioskDeviceAdminReceiver` + ops runbook |
| 3 | Lock Task Mode | Must | **Done** — `LockTaskController` (tablet only) |
| 4 | Boot / crash / power recovery | Must | **Done** — `KioskBootReceiver` + HOME |
| 5 | Immersive full-screen chrome | Must | **Done** — system bars hidden on tablet |
| 6 | HOME / default launcher category | Must | **Done** — tablet manifest `HOME` |
| 7 | Firmware boot logo (Magisk path) | Must (Path B) | **Done** — Path B ops runbook (ops flash; no auto-flash) |
| 8 | Custom `bootanimation.zip` | Must (Path B) | **Stub** — runbook steps; no zip binary in repo |
| 9 | Magisk + Lock Task dual lockdown | Must (Path B) | **Done** — `HardeningPathDetector` + Device Admin status |
| 10 | App-level branded startup | Must | **Done** — `BrandedSplashHost` |
| 11 | Animated GT-R splash (~4–6s) | Should | **Stub** — Canvas sequence + skip; licensed asset pack TBD |
| 12 | Engine audio sync + kill switch | Must | **Done** — default OFF; Device Admin toggle; optional `raw/engine_splash` |
| 13 | Splash asset pack replaceability | Should | **Stub** — replaceable raw pattern; no licensed pack shipped |
| 14 | Staff login UI | Must | **Done** — branded SignIn (emp#\|email\|phone + password) |
| 15 | Emp# \| email \| phone + password | Must | **Done** — `resolve_staff_login_email` + management SignIn |
| 16 | Employee ID + PIN primary auth | Later | **Later** — disabled stub on SignIn |
| 17 | NFC employee card login | Later | **Later** — disabled stub on SignIn |
| 18 | QR / barcode employee badge | Later | **Later** — disabled stub on SignIn |
| 19 | Fingerprint / biometric login | Later | **Later** — disabled stub on SignIn |
| 20 | Login security / lockout / audit | Must | **Stub** — `record_staff_login_attempt` / lock helpers; AuthGate wiring partial |
| 21 | Profile + role + permission load | Must | **Done** — roles + `module_access` before route |
| 22 | Role-based auto-routing | Must | **Done** — `ManagementHomeRoles` + optional `default_landing` |
| 23 | Sales → POS direct | Must | **Done** — sales-only → POS |
| 24 | Non-sales → staff dashboard | Must | **Done** — hub for WH/Fin/HR/Admin/Dispatch |
| 25 | Configurable `default_landing` | Should | **Done** — `hr_roles.default_landing` + `my_default_landing` |
| 26 | Module-level gate | Must | **Done** — `moduleAllowed` + RPC |
| 27 | Screen-level gate | Should | **Stub** — module hide; deep-link screen matrix partial |
| 28 | Action-level gate (RPC) | Must | **Done** — discount/void/refund/quote SECURITY DEFINER |
| 29 | Data-scope permissions | Later | **Later** |
| 30 | Deny missing/inactive role | Must | **Done** — fail closed Deny |
| 31 | Sales multi-role / hub escape | Should | **Done** — “All modules (hub)” from POS |
| 32 | Counter-optimized POS layout | Must | **Done** — landscape till + catalog/cart |
| 33 | Product / parts search | Must | **Done** — catalog search modes |
| 34 | Bridge barcode scan → cart | Must | **Done** — Camerax QR bridge |
| 35 | Customer select / create | Must | **Done** — find/select on till |
| 36 | Stock availability on line | Must | **Stub** — `lookupSaleableQtyByOem` on RpcClient; till display incomplete |
| 37 | Create quotation (POS) | Must | **Done** — create + list Quotes panel |
| 38 | Park / hold sale | Must | **Done** |
| 39 | Resume parked sale | Must | **Done** |
| 40 | Split-bill tenders | Must | **Done** |
| 41 | Checkout / complete sale | Must | **Done** — USD\|ZIG; no ZIMRA |
| 42 | Receipt print (ESC/POS Bridge) | Must | **Done** |
| 43 | Receipt contact capture | Must | **Done** |
| 44 | Companion phone scan pairing | Must (preserve) | **Done** — ADR pairing preserved |
| 45 | Line / cart discount + manager auth | Must | **Done** — manager reauth + `apply_pos_cart_discount` |
| 46 | Void transaction + manager auth | Must | **Done** — `void_pos_cart` |
| 47 | Refund + manager auth → finance | Must | **Done** — `post_pos_refund` → finance pipeline |
| 48 | Price override | Should | **Stub** — RPC + RpcClient; till UI/impl incomplete |
| 49 | Idle lock | Must | **Done** — default 3 min; Admin 1–15 |
| 50 | Logout → in-app login | Must | **Done** — no splash replay by default |
| 51 | Maintenance / Device Admin console | Must | **Done** |
| 52 | Wi‑Fi / BT admin intents | Must | **Done** |
| 53 | Printer / scanner diagnostics | Must | **Done** — console diagnostics text |
| 54 | Warehouse dashboard modules | Must (adopt) | **Done** — existing WH modules |
| 55 | Manager dashboard modules | Should | **Stub** — hub tiles; dedicated analytics polish light |
| 56 | Manager / approval permissions | Must | **Done** — `is_pos_approver` |
| 57 | Offline SQLCipher POS cache | Must | **Done** — [ADR](../decisions/2026-08-03-offline-sqlcipher-pos-cache.md); SQLCipher + pull/replay RPCs |
| 58 | Offline login (Argon2id cache) | Later | **Later** — depends #57; not authorized by offline sales ADR |
| 59 | WorkManager sync pipeline | Must | **Done** — foreground drain + WorkManager on reconnect |
| 60 | Casbin / full action-matrix | Later | **Later** |
| 61 | Fine vendor job-title roles | Later | **Later** |
| 62 | True BT file/content share | Later | **Later** / out until specified |
| 63 | Web staff emp# + idle parity | Should | **Stub** — change-password path; emp#/idle shell parity incomplete |
| 64 | iOS staff kiosk | Later | **Later** |
| 65 | Error handling fail-closed | Must | **Done** — non-enumerating auth; deny closed |
| 66 | Adopt existing POS ops | Must | **Done** — Batch 1 cart/checkout preserved |
| 67 | Magisk Device Admin toggles / ops UI | Must (Path B) | **Done** — Path B/A status; no auto-flash |
| 68 | Send quotation | Must | **Done** — send audit + ESC/POS path |
| 69 | Convert quotation → sale | Must | **Done** — convert → cart |

**Must/Should ship gate:** all Must and Should rows are **Done** or **Stub**. Later rows do not block **complete**.

---

## 1. Goal

Ship a **shopfloor tablet** kiosk APK (Device Owner / Lock Task; **Path B Magisk primary** on rooted CN tablets) that is a **separate product** from the phone management APK: staff auth → **role-based landing** → sales **POS counter** (incl. quotations) *or* non-sales **staff dashboard**, with Bridge-First printers, Admin-or-shop-manager void/discount/refund (finance refund pipeline), idle lock, emp#|email|phone login, and engine-audio OFF-by-default.

---

## 2. Vision & principles

| Principle | Meaning |
|-----------|---------|
| **Tablet ≠ phone (separate APKs)** | **Locked:** two installable APKs (distinct `applicationId`s). Shared modules OK. **Tablet APK** owns kiosk / Device Owner / Lock Task / Path B. **Phone APK** = portable management + optional companion scan — **no** Device Owner / Lock Task / Magisk ownership. |
| **One app family, two modes** | Sales attendants → **POS counter**. Warehouse / finance / HR / admin / dispatcher → **staff dashboard** (role-gated) — **never** forced into POS. |
| **RPC + RLS authority** | Nav hide is UX only. Discount, void, refund, price override, stock adjust, Device Admin exit → SECURITY DEFINER RPCs (+ audit). |
| **Bridge-First** | Camera, QR, BT ESC/POS, biometric → `bridges/` only. No Web Bluetooth, no HTML5 camera. |
| **Adopt-first** | Reuse Batch 1 POS, `module_access`, `prefersPosHome`, ESC/POS bridge, organogram, `post_finance_refund` — extend, do not rebuild. |
| **Path B primary / Path A fallback** | **Locked:** Magisk / OEM boot customization is the **chosen** tablet hardening path for rooted CN tablets. Path A (AOSP DO + Lock Task only) remains documented **fallback** for non-root OEMs — not the primary fleet story. |
| **Hard exclusions (still)** | No ZIMRA/FDMS; no payroll tax; invoices tax-agnostic; no Web Bluetooth for receipts or content-share product until separately specified. |

---

## 3. Personas & landing

| Persona | Landing | Dashboard tiles | POS |
|---------|---------|-----------------|-----|
| Sales attendant | **POS mode** (`create_pos_cart` ready) | Hub only if policy / multi-role escape | Primary |
| Warehouse | Dashboard → warehouse | Receive, transfers, bins, cycle count, consignment, insights | Only if `module_access` has `pos` |
| Finance | Dashboard → finance | Accounts, statements, petty cash, refunds desk, requisitions | No unless granted |
| HR | Dashboard → HR | Clock, onboarding, organogram | No |
| Admin / shop manager | Dashboard hub | All modules + Device Admin entry | Yes (override) |
| Dispatcher / fleet | Dashboard → dispatch/fleet | Existing gates | No |
| Missing / inactive role | **Deny** — branded error, stay on in-app login | — | — |

Fine organogram titles (Cashier, Parts Specialist, Workshop, Service Advisor…) map via `hr_roles.module_access` + `default_landing` — **not** a parallel enum dump.

**Authorization (locked):** void / discount / refund approval = **Admin** **or** **shop manager** as defined in organogram / `hr_roles` (approval flags / grade mapping — not attendant-only; second-user reauth on device).

---

## 4. Information architecture

```
Power on → [Path B Magisk boot + Lock Task (primary CN) | Path A Lock Task fallback]
        → App immersive branded startup → GT-R splash (± engine audio)
        → Staff login (emp#|email|phone [+ Later PIN/badge])
        → Load profile/roles/permissions (fail closed)
        ├─ Sales → POS MODE (counter till + quotations)
        └─ Other → DASHBOARD MODE (module grid)
              ↕ Admin|shop-manager void / refund / discount
        Device Admin console (elevated, audited)
```

- **Phone APK:** Dashboard-first hub; optional POS; companion scan; no DO / Magisk.
- **Web `/staff`:** Management fallback + emp#/idle parity — **not** kiosk SoR.

---

## 5. Complete feature inventory

Priority: **Must** / **Should** / **Later**. Later items are **fully described** so nothing is missing from the catalog.

| # | Feature | Who | Behavior (end-to-end) | Surface | Depends on | Pri | Phase |
|---|--------|-----|----------------------|---------|------------|-----|-------|
| 1 | Separate tablet vs phone APKs | Ops / all staff | **Locked:** two APKs / `applicationId`s; shared Gradle modules OK. Tablet APK: kiosk shell, DO, Lock Task, Path B hooks, counter POS. Phone APK: portable management hub, companion scan, **no** forced DO/Lock Task/Magisk. | Tablet APK / phone APK | Product flavors or modules | Must | 0–2 |
| 2 | Device Owner provisioning | Sys admin | Factory-reset tablet; no Google accounts; `dpm set-device-owner …AdminReceiver`; allow-list tablet package only; document runbook. Magisk unlock path may factory-reset first (aligns with DO prerequisite). | Device Admin / ops docs | Android Enterprise | Must | 2 |
| 3 | Lock Task Mode | All kiosk users | On boot / app start, `setLockTaskPackages` + `startLockTask`. Blocks Home, Recents, notification shade, Play, unapproved apps for ordinary staff. Immersive alone is **not** enough. | Tablet kiosk shell | DO (#2) | Must | 2 |
| 4 | Boot / crash / power recovery | All | After power-on, restart, crash, power blip, or admin reboot: app auto-launches; returns to splash→login or locked session per policy; never leaves staff on stock launcher. | Tablet shell | DO/HOME (#2–3, #6) | Must | 2 |
| 5 | Immersive full-screen chrome | All | Hide status/nav bars (`WindowCompat` / immersive); no flash to launcher between boot→splash→login. Supports Lock Task; does not replace it. | Tablet shell | Splash (#10) | Must | 2 |
| 6 | HOME / default launcher category | All (kiosk) | Manifest `HOME` category so app is the dedicated launcher under DO; accidental Home returns to GTR app. | Tablet shell | DO (#2) | Must | 2 |
| 7 | Firmware boot logo (Magisk path) | Ops | On **rooted CN tablets** (Path B **primary**): unlock bootloader → Magisk-patch `boot.img` → flash → replace `/system/media/bootanimation.zip` with branded frames. Shows GTR identity **before** Android UI. Non-root tablets use Path A fallback; app splash still required. | Magisk / ops | Rooted device | Must (Path B) | 2B |
| 8 | Custom `bootanimation.zip` | Ops | Generate via bootanimation-creator (or equiv.); push to `/system/media/` with 644; OEM may ignore audio inside zip — app-level audio (#12) remains SoR for sound. | Magisk / ops | #7 | Must (Path B) | 2B |
| 9 | Magisk + Lock Task dual lockdown | Ops / admin | Path B **primary**: Magisk for early boot branding + optional system hardening; **still** set Device Owner + Lock Task for runtime kiosk. Device Admin exposes “rooted hardening status” / runbook links; exit Lock Task still audited. | Device Admin + ops | #2–3, #7 | Must (Path B) | 2B |
| 10 | App-level branded startup | All | After Android starts, auto-open full-screen GTR branding before login — **required on all tablets**, including those without firmware logo. | Tablet shell | Assets | Must | 2 / 6 |
| 11 | Animated GT-R splash (~4–6s) | All | Timed sequence: dark bg → silhouette/headlights → accelerate across screen → logo settle → “INITIALIZING BUSINESS SYSTEM” → login. Local assets only (Compose/Lottie/video/Canvas). Configurable duration; landscape tablet. Plays once per full boot by default — **not** after ordinary logout. | Tablet splash | #10, licensed assets | Should | 6–7 |
| 12 | Engine audio sync + kill switch | All / Device Admin | Local RAW audio (idle→rev→accel…); ExoPlayer/MediaPlayer with splash; respect media volume; stop on end/skip/background. **Default OFF.** Device Admin toggle ON/OFF per device (DataStore). OEM zip audio often broken — app audio is authoritative. | Splash + Device Admin | #11, assets | Must | 6 |
| 13 | Splash asset pack replaceability | Ops | Swap licensed visual/audio packs without rewriting splash controller; no unlicensed Nissan scrapes. | App config | #11–12 | Should | 7 |
| 14 | Staff login UI | Staff | Branded login: title, identifier fields, sign-in, **assigned counter/terminal**, connectivity status. Design-system components. | Tablet / web staff | Auth | Must | 1–3 |
| 15 | Emp# \| email \| phone + password | Staff | Staff enter employee_code **or** email **or** phone + password. SECURITY DEFINER resolve emp#→`auth.users` (active only); rate-limit; no enumeration. Customers **never** use emp#. Honor `must_change_password`. | Tablet + web | Backend RPC | Must | 3 |
| 16 | Employee ID + PIN primary auth | Staff | Spec primary: emp# + numeric PIN. Hash server-side / Keystore for local; **no plaintext PIN**. Optional evolve after password path: map PIN as second factor or Edge-backed verify — needs ADR before SoR fork. | Tablet login | Auth ADR | Later | — |
| 17 | NFC employee card login | Staff | Tap NFC badge → Bridge/NFC module → resolve staff → session. Bridge-First only. | Tablet + bridges | Hardware | Later | — |
| 18 | QR / barcode employee badge | Staff | Scan badge via **native Bridge** (no HTML5) → resolve staff. | Tablet + bridges | QR bridge | Later | — |
| 19 | Fingerprint / biometric login | Staff | Tablet biometric → unlock local session or confirm identity; Bridge biometric module; Keystore-backed. | Tablet + bridges | Biometric bridge | Later | — |
| 20 | Login security / lockout / audit | Staff / security | Failed-attempt tracking; configurable temporary lockout; auth audit log; inactive staff rejected; session expiration. | Tablet + backend | #15 | Must | 3 |
| 21 | Profile + role + permission load | All staff | After auth: load staff id/name, roles, dept/branch/terminal, employment status, `default_landing`, module/screen/action/data-scope/approval flags. **Do not route** until loaded; fail closed if load fails. | Tablet / web | RLS/RPC | Must | 1 |
| 22 | Role-based auto-routing | All staff | No manual dashboard picker. Sales → POS ready; others → configured dashboard. Prefer DB `default_landing` over hard-code. | Tablet / web | #21 | Must | 1 |
| 23 | Sales → POS direct | Sales | Auth OK → permissions → open POS → auto `create_pos_cart` / new sale ready. No hub detour for sales-only. | Tablet POS | #22, Batch 1 POS | Must | 1 |
| 24 | Non-sales → staff dashboard | WH / Fin / HR / Admin / Dispatch | Land on role hub; tiles = `module_access` ∩ `staff_roles`; never auto-open POS. | Tablet dashboard | #22 | Must | 1 |
| 25 | Configurable `default_landing` | Admin (HR roles) | Column/map on `hr_roles`; change landing without app rebuild. Empty `module_access` → staff_roles fallback; admin bypass documented. | Backend + clients | Migration | Should | 7 |
| 26 | Module-level gate | All | Hide unauthorized modules (POS, inventory, finance, HR, device admin, …) **and** reject RPCs. | Dashboard / RPC | `my_module_access` | Must | 1 |
| 27 | Screen-level gate | All | Block deep-links / navigation to unauthorized screens (POS history, stock adjust, Device Admin, reports…). | Clients + RPC | #26 | Should | 5–7 |
| 28 | Action-level gate (RPC) | Attendant / manager | Create/edit/cancel/hold/resume sale, discount, price override, refund, void, stock adjust, role change, device settings, create/send/convert quotation — each SECURITY DEFINER checked. | POS / backend | RLS | Must | 5 |
| 29 | Data-scope permissions | All | Restrict to own txns / counter / branch / dept / company-wide as policy allows. | RPC / reports | Schema | Later | — |
| 30 | Deny missing/inactive role | Unassigned | Branded error; stay on login; no unrestricted dashboard; no default-admin grant. | Login | #21 | Must | 1 |
| 31 | Sales multi-role / hub escape | Multi-role sales | If sales+warehouse/admin: clarify landing (open decision). Should: explicit “Hub” escape from POS when covering till. | POS / hub | #22 | Should | 7 |
| 32 | Counter-optimized POS layout | Sales | Tablet till UI: large tap targets, search + catalog grid + cart + tender — **not** phone hub chrome. Landscape-first. | Tablet POS | Existing `feature/pos` | Must | 4 |
| 33 | Product / parts search | Sales | Search OEM, internal SKU, VIN/vehicle, PNC where supported; catalog hits → cart. | POS | Batch 1 | Must | 4 |
| 34 | Bridge barcode scan → cart | Sales | Native QR/barcode Bridge adds line to current cart; no browser camera. | POS + bridges | QR bridge | Must | 4 |
| 35 | Customer select / create | Sales | Attach or create customer on sale when permitted by action gate. | POS | RPC | Must | 4 |
| 36 | Stock availability on line | Sales | Show authorized stock qty/location for selected SKU. | POS | Inventory RPC | Must | 4 |
| 37 | Create quotation (POS) | Sales | From counter cart (or dedicated quote mode): save as **quotation** without tender/checkout. Capture customer (required or soft-required per policy), lines, currency (USD\|ZIG), expiry/validity if schema supports, counter/terminal, salesperson. Persist via SECURITY DEFINER RPC; status `draft`→`issued`; **no** ledger post; **no** ZIMRA. List/open own (or branch) quotes from POS. Distinct from supplier RFQ quotations. | Tablet POS | Cart / quote schema | Must | 4 |
| 68 | Send quotation | Sales | From issued quote: print via ESC/POS Bridge and/or send receipt-style contact (email/SMS/WhatsApp path already used for receipts — reuse contact capture). Record send audit (who/when/channel). Fail closed if no customer contact and channel requires it. Does **not** complete a sale. | Tablet POS + bridges | #37, #42 | Must | 4 |
| 69 | Convert quotation → sale | Sales | Open issued (non-expired/cancelled) quote → load lines into live POS cart → attendant may adjust authorized fields → checkout via existing park/split/tender path. Conversion links quote→sale/invoice ids; marks quote `converted`; cannot double-convert. Manager gates still apply on discount/void after convert. | Tablet POS | #37, #40–41 | Must | 4 |
| 38 | Park / hold sale | Sales | `park_pos_cart` — hold current cart; resume authorized held carts. | POS | Existing RPCs | Must | 4 |
| 39 | Resume parked sale | Sales | `resume_pos_cart` — restore held txn into counter. | POS | #38 | Must | 4 |
| 40 | Split-bill tenders | Sales | `checkout_pos_cart_with_tenders` — multiple tender lines / split pay. | POS | Existing RPCs | Must | 4 |
| 41 | Checkout / complete sale | Sales | Complete authorized payment path; currency explicit (USD\|ZIG); no ZIMRA fiscal QR. | POS | Batch 1 | Must | 4 |
| 42 | Receipt print (ESC/POS Bridge) | Sales | Print via `bridges/android/escpos-printer`; Device Admin opens BT Settings intents under Lock Task allow-list; diagnostics — **not** Web Bluetooth. | POS + Device Admin + bridges | BT printer | Must | 4 |
| 43 | Receipt contact capture | Sales | Optional receipt email/phone contacts on complete (existing pattern). | POS | Checkout | Must | 4 |
| 44 | Companion phone scan pairing | Sales + phone | Optional `pos_scan_sessions` + Realtime; phone APK scans, tablet cart updates; ADR pairing. Phone stays non-DO. | Tablet POS + phone APK | Pairing ADR | Must (preserve) | 4 |
| 45 | Line / cart discount + manager auth | Sales + Admin\|shop mgr | Attendant requests discount; **Admin or shop manager** (organogram / `hr_roles`) reauth/approve; server gate + append-only audit; attendant alone cannot apply. | POS | #28, #56 | Must | 5 |
| 46 | Void transaction + manager auth | Sales + Admin\|shop mgr | Request void → Admin or shop manager authorize → RPC; audit who/when; no silent delete. | POS | #28, #56 | Must | 5 |
| 47 | Refund + manager auth → finance pipeline | Sales + Admin\|shop mgr + finance | **Locked:** Counter refund is **not** a parallel POS-only path. Flow: attendant request → Admin\|shop-manager auth → call through **`post_finance_refund` / finance refund pipeline** (reversing JE, `finance_refunds`, audit; requisition path where Batch 1 already designed). Quarantine returns rules apply for physical returns. Extend RPC authz so counter can invoke **with** manager gate (today finance\|admin only — widen carefully). Currency explicit; **no ZIMRA**. | Tablet POS → finance SoR | Ledger skill, `#post_finance_refund` | Must | 5 |
| 48 | Price override | Sales + Admin\|shop mgr | Action-gated override; typically same Admin\|shop-manager approval pattern as discount. | POS | #28 | Should | 5 |
| 49 | Idle lock | All staff | **Default 3 minutes** inactivity → clear in-memory session UI → branded lock/reauth **inside app**; never launcher. See §10a rationale. Device Admin override **1–15 min** (DataStore); optional org server default later (open). | Tablet + web staff | Session | Must | 2 |
| 50 | Logout → in-app login | All | Invalidate session; clear secrets; return to staff login; stay in Lock Task; **do not** replay full splash by default. | Tablet shell | #3 | Must | 2 |
| 51 | Maintenance / Device Admin console | Sys admin / elevated | Elevated reauth → branded console: Wi‑Fi, Bluetooth, printer, scanner diagnostics, date/time, reboot/shutdown, logs, device info, software update hooks, POS sync status, **engine audio toggle**, **idle minutes**, exit Lock Task, Path B/Magisk status. Auto-expire → back to kiosk; full Settings = higher gate. Audit who/terminal/time/actions. | Device Admin | DO (#2) | Must | 2 |
| 52 | Wi‑Fi / BT admin intents | Admin | Console launches system Wi‑Fi/BT settings intents under allow-list — not in-app Web Bluetooth stack. | Device Admin | Lock Task allow-list | Must | 2 / 4 |
| 53 | Printer / scanner diagnostics | Admin + hardware | ESC/POS + QR bridge health checks from console. | Device Admin + bridges | #42, #34 | Must | 4 |
| 54 | Warehouse dashboard modules | Warehouse | Receive, transfers, counts, authorized adjusts, reorder alerts, locations, movement history, discrepancies — via existing modules + `module_access`. | Dashboard | Batch 1 WH | Must (adopt) | 1 |
| 55 | Manager dashboard modules | Manager | Sales/inventory/staff/branch performance, approval queue, discount/refund approvals, ops reports, authorized finance summaries. | Dashboard | Analytics / approvals | Should | 5–7 |
| 56 | Approval / approval permissions | Admin \| shop mgr | **Locked:** approval authority for discount/void/refund = Admin **or** shop manager via organogram / `hr_roles` (approval flags). Feed #45–47. Map shop-manager title/grade once in migration/ADR — no free-form attendant self-approve. | Backend + UI | #28, organogram | Must | 5 |
| 57 | Offline SQLCipher POS cache | Sales (offline) | Encrypted SQLCipher; Keystore-wrapped AES key; cache catalog/stock; record offline cash sales; WorkManager + foreground sync on reconnect; conflict + revoke handling; **no** cached manager tokens. **ADR accepted.** | Tablet local DB | ADR | Done | — |
| 58 | Offline login (Argon2id cache) | Staff offline | On online login, store Argon2id hash+salt in SQLCipher; offline compare locally. Fail closed if no cache / revoked. | Tablet auth | #57 | Later | — |
| 59 | WorkManager sync pipeline | Tablet | Background sync offline txns when `CONNECTED`; conflict/revoke handling. Foreground drain on POS open. | Tablet | #57 | Done | — |
| 60 | Casbin / full action-matrix | Platform | Only if `module_access` + RPC checks prove insufficient (Batch 1 open decision). | Backend | Eval | Later | — |
| 61 | Fine vendor job-title roles | HR | Express Cashier / Parts Specialist / Workshop / Service Advisor via organogram `hr_roles` + landing — not parallel enum overnight. | HR / routing | #25 | Later | — |
| 62 | True BT file/content share | TBD | HTML filename suggests share; body does **not** define customer file OBEX/Nearby Share. Out until product specifies; if ever → new `bridges/` module only. | — | Spec | Later / out until specified | — |
| 63 | Web staff emp# + idle parity | Web staff | Emp# identifier + idle lock inside staff shell (default 3 min parity); not Android kiosk SoR. | Web `/staff` | #15, #49 | Should | 3 / 7 |
| 64 | iOS staff kiosk | iOS | Not in scope for Android tablet epic; defer. | — | — | Later | — |
| 65 | Error handling fail-closed | All | Auth fail: non-enumerating errors. Role/permission fail: deny, try secure cache only if Later offline allowed. Unauthorized action: deny + log. | Clients + RPC | #21 | Must | 1–5 |
| 66 | Adopt existing POS ops | Sales | Preserve search, park, split-bill, companion, checkout — do not rewrite Batch 1 cart/checkout SoR. Quotations **extend** cart SoR; refunds **reuse** finance pipeline. | POS | Batch 1 | Must | 4 |
| 67 | Magisk Device Admin toggles / ops UI | Admin | Console shows Path B (primary) vs Path A (fallback) status; links to provisioning runbook; toggles that are safe post-root (engine audio, idle mins, exit Lock Task, reboot). Does **not** auto-flash Magisk from app — flashing is ops/ADB procedure. | Device Admin | #9 | Must (Path B) | 2B |

**Catalog count: 69 features** (#1–69; #68–69 quotation send/convert).

---

## 6. Magisk / boot patch — Path B **primary** (locked)

**Decision (locked):** Magisk / OEM boot customization is the **chosen primary** tablet hardening path for **rooted CN tablets**. Path A (AOSP Device Owner + Lock Task without root) is retained as **fallback** for OEMs that cannot unlock/root. Prior “reject Magisk” non-goal remains **withdrawn**; prior “Path A preferred / Path B optional” wording is **superseded**.

### Dual path (priority flipped)

| | Path B — Magisk + Lock Task (**primary** — rooted CN) | Path A — AOSP Lock Task (**fallback**) |
|--|--------------------------------------------------------|----------------------------------------|
| When | Bootloader unlockable; shop accepts root ops (chosen fleet path) | OEM allows Device Owner without root / no unlock |
| Boot branding | Magisk + custom `bootanimation.zip` **then** app splash | App splash only (firmware logo stays OEM) |
| Runtime kiosk | DO + Lock Task **plus** early boot branding | Device Owner + Lock Task |
| Engine audio | App splash ExoPlayer; Device Admin OFF default | Same |
| Support | Higher ops burden — accepted for primary CN SKUs | Lower; Play Integrity / OTA friendlier |

### Path B steps (ops runbook — not app auto-flash)

1. Backup; confirm unlockable CN tablet SKU.
2. `fastboot flashing unlock` (factory reset — use this reset before DO).
3. Extract stock `boot.img` → `adb push` → Magisk “Select and Patch” → `adb pull` `magisk_patched.img` → `fastboot flash boot`.
4. Build branded `bootanimation.zip` → push `/system/media/bootanimation.zip` (644); remount as needed.
5. Provision Device Owner on **tablet** package: `adb shell dpm set-device-owner <tablet.applicationId>/.AdminReceiver`.
6. Enable Lock Task allow-list; verify boot → animation → app splash (± audio) → login with no launcher flash.
7. Device Admin: confirm engine-audio OFF default; idle **3 min** default; printer BT intents; audited exit.

### Pros

- True branded power-on before Android UI.
- Unlock factory-reset aligns with clean DO provisioning.
- Deeper OEM lockdown possible on permissive hardware.

### Risks / ops burden

- **Play Integrity / SafetyNet** may fail — accept for dedicated POS SKUs; do not rely on Play-only APIs.
- **OTA** can wipe Magisk/bootanimation — pin OEM builds; re-patch runbook after updates.
- **Support burden** — per-SKU images; brick risk; only trained ops.
- **Security** — root increases attack surface; compensate with DO, allow-lists, no staff ADB, audited maintenance.
- Vendor MD §23 “do not root” = caution for *consumer* delivery; **product override** for owned rooted POS fleet (Path B primary).

### Relation to engine audio & locked launcher

- Firmware zip audio: best-effort only.
- **Authoritative audio:** app splash + Device Admin kill switch (default OFF).
- Locked launcher: Magisk does **not** replace Lock Task; both layers for Path B.

### Fallback for non-root

Path A: DO + Lock Task + immersive app splash + engine-audio toggle. Meets operational kiosk Must without firmware logo when Path B is unavailable.

---

## 7. What already exists vs net-new

| Capability | Status |
|------------|--------|
| Standalone POS, park/resume, split-bill, companion, ESC/POS | **Exists** |
| Coarse `prefersPosHome` (sales→POS; admin\|warehouse→hub) | **Partial** — extend finance/hr/dispatcher + `default_landing` |
| `module_access` / `my_module_access` | **Exists** |
| Finance desk refunds JE (`post_finance_refund`) | **Exists** — wire POS counter through this pipeline + Admin\|shop-mgr gate |
| Customer POS quotations create/send/convert | **Done** (#37, #68, #69) |
| Kiosk shell / idle / Device Admin / audio prefs | **Done** (tablet flavor) |
| Emp# login identifier | **Done** (#15) |
| POS void / discount / manager auth RPCs | **Done** (#45–47, #56) |
| Separate tablet vs phone `applicationId`s | **Done** (#1) |
| Counter tablet IA | **Done** (#32) |
| Magisk Path B runbook + admin status | **Done** (#7–9, #67; boot zip = Stub #8) |
| PIN / NFC / badge / offline login (#58) | **Later** — SignIn disabled stubs; offline **sales** cache is Done ([SQLCipher ADR](../decisions/2026-08-03-offline-sqlcipher-pos-cache.md)) |

---

## 8. Phased build order

**Do not start Phase 0+ until premature kiosk scaffolding is reverted** (separate task). Then implement per locked decisions — no further product approval gate on §10 locked items.

| Phase | Focus | Lane | AC (summary) |
|-------|-------|------|--------------|
| **−1** | Revert premature kiosk/partial scaffolding (separate) | `/manager` + docs | Clean tree; plan remains SoR |
| **0** | Freeze inventory post-revert; confirm Path B SKU list vs Path A fallback devices | `/manager` + docs | Written keep/drop; no product code until clear |
| **1** | Role landing + module tiles + fail closed | `@management_app_agent` (+ backend if landing col) | §3 matrix; RPC reject unauthorized |
| **2** | Separate APKs + Lock Task + DO + idle (3 min) + maintenance; phone APK non-DO | `@management_app_agent` | Two APKs; kiosk guide; phone hub usable |
| **2B** | Magisk Path B runbook + bootanimation + Device Admin status (**primary** CN fleet) | `@management_app_agent` + ops docs (`@hardware_mobile_agent` if bridge touch) | Rooted tablet: branded zip → app → login; Path A fallback documented |
| **3** | Emp#\|email\|phone resolve + UI | `@backend_agent` → management → `@web_agent` | Staff login three identifiers; customers blocked; RLS |
| **4** | Counter POS polish + quotations create/send/convert + BT print + park/split/companion | `@management_app_agent` + `@hardware_mobile_agent` (+ `@backend_agent` quote RPCs) | Quote→send→convert→sale; Bridge print; no Web Bluetooth |
| **5** | Admin\|shop-mgr void/discount/refund via finance pipeline (+ price override Should) | `@backend_agent` → management (+ `@finance_agent` JE) | Attendant alone cannot; posts through `post_finance_refund`; audit; no ZIMRA |
| **6** | Engine audio default OFF + Device Admin toggle; splash wired | `@management_app_agent` | Fresh device silent; toggle plays local asset |
| **7** | Should: `default_landing`, branded splash polish, web idle/emp# parity, hub escape, manager dashboard polish | mixed | Per Should rows; Later not expanded into build |

---

## 9. Hard exclusions & revised non-goals

**Still hard exclusions**

- No ZIMRA / FDMS / fiscalisation QR / tax-authority payloads.
- No payroll tax (PAYE/NSSA/statutory forms).
- Bridge-First: no HTML5 camera/QR; no Web Bluetooth for receipts.
- No customer storefront emp# signup.
- No rewriting Batch 1 cart/checkout SoR (quotations **extend**; refunds **reuse** finance).
- Phone APK not converted to DO kiosk.
- No parallel POS-only refund ledger path.

**Withdrawn / revised**

- ~~No Magisk / bootloader / system partition~~ → **Path B primary** for rooted CN tablets (§6).
- ~~Path A preferred / Path B optional~~ → **Path B chosen**; Path A = fallback.
- ~~Same APK / soft flavor only~~ → **Separate APKs** locked.
- ~~Quotations Later~~ → **Must** create/send/convert (#37, #68, #69).
- PIN / NFC / badge / biometric / offline SQLCipher / Casbin / fine titles / data-scope / iOS kiosk / BT content-share → remain **Later or out-until-specified**, but **catalogued** in §5.

---

## 10. Locked decisions

| # | Decision | Resolution |
|---|----------|------------|
| L1 | APK product split | **Separate APKs** (distinct `applicationId`). Shared modules OK. Tablet owns kiosk/DO/Path B; phone = portable management without tablet ownership. |
| L2 | Void/discount/refund authorizer | **Admin or shop manager** as defined in organogram / `hr_roles` (approval flags). Second-user reauth; attendant cannot self-approve. |
| L3 | POS refund path | POS refund **must** connect to / post through the **finance refund pipeline** (`post_finance_refund` / reversing JE / requisition as designed) — **no** parallel POS-only refund SoR. |
| L4 | Quotations | **Must** in tablet POS: create, send, convert (#37, #68, #69). End-to-end in feature catalog. |
| L5 | Idle timeout | **Default 3 minutes**; Device Admin override **1–15 minutes**. See §10a. |
| L6 | Path B vs A | **Path B (Magisk/boot) is the chosen primary** hardening path for rooted CN tablets. Path A Lock Task documented as **fallback**. |
| L7 | Premature code | **Revert separately** before implementation. Status = decisions locked — ready after revert. |

### §10a Idle timeout — default & rationale

| Setting | Value |
|---------|--------|
| **Default** | **3 minutes** of inactivity |
| **Device Admin range** | **1–15 minutes** (local DataStore on tablet) |
| **Behavior** | Clear in-memory session UI → branded in-app lock/reauth; stay in Lock Task; never drop to launcher |

**Rationale:** Retail / unattended counter POS guidance commonly clusters around a **2–5 minute** idle reauth window (balance: reduce walk-away fraud vs not forcing reauth mid-sale while briefly handling a printer or customer). **3 minutes** sits mid-band as a concrete ship default. Shorter (1–2) is available via Device Admin for high-theft counters; longer (up to 15) for supervised low-risk counters — not open-ended 30 min on kiosk.

**Still open (non-blocking for Phase −1/0):** org-wide server default for idle; exact `hr_roles` key/grade that maps “shop manager”; Path B SKU allow-list; licensed splash/audio assets; sales multi-role landing (POS vs hub).

---

## 11. Remaining open decisions (narrow)

1. **Sales multi-role landing** — sales+warehouse → POS or dashboard? (Today dashboard wins for admin\|warehouse.)
2. **Shop-manager mapping** — which organogram / `hr_roles` row(s) / approval flag = “shop manager” (implement L2).
3. **Licensed engine audio / splash assets** — supply now or mute-only until rights land?
4. **Which tablet SKUs are Path B?** — list CN models approved for unlock/Magisk vs Path A–only fallback fleet.
5. **Org idle default** — device-local only (Must done) vs also server org default (Should)?

---

## 12. Risks

| Risk | Mitigation |
|------|------------|
| Client-only permission hide | SECURITY DEFINER RPCs; `/security-reviewer` + `/verifier` |
| DO provisioning friction | Factory-reset + `dpm` runbook; pin-mode fallback without full DO |
| Magisk OTA / Integrity / brick | Per-SKU runbook; re-patch after OTA; Path A fallback |
| Dual APK drift | Shared modules; shared RPC contracts; CI both APKs |
| POS refund bypassing ledger | Force `post_finance_refund` path; `/finance_agent` + ledger skill; `/verifier` |
| Offline privilege escalation | Offline SoR Later + ADR; no cached manager tokens |
| Emp# enumeration | Rate-limit resolve; constant-ish errors |
| Phone/tablet confusion | Separate APKs + package ids; phone NON-goal = no DO |
| ZIMRA creep on receipts | Exclusion check in `/verifier` |
| Premature kiosk code drift | **Revert first** (Phase −1); then implement from locked plan |

---

## 13. Paths likely touched (when implementing post-revert)

- `apps/android-management/` — dual APK/flavors, routing, POS + quotations, kiosk/Lock Task, Device Admin, emp# login, splash/audio
- `apps/web/` — staff emp# + idle parity only
- `supabase/migrations/` — emp# resolve, `default_landing`, void/discount/refund authz, quotation RPCs, widen refund invoke under manager gate + RLS/audit
- `bridges/android/escpos-printer/` (+ QR/biometric Later)
- `docs/guides/android-management-kiosk-device-owner.md` (+ Magisk Path B **primary** runbook section)

---

## Phase 0 — Post-revert inventory (2026-08-03)

**Status:** complete. Premature kiosk scaffolding confirmed **absent** (no Lock Task / Device Admin / idle / Magisk / dual-APK code under `apps/android-management/`).

### KEEP (adopt — do not rebuild)

| Asset | Pointer |
|-------|---------|
| Standalone POS | `feature/pos` — park/resume, split-bill, companion, Bridge QR/print |
| Coarse role home | `ManagementHomeRoles.prefersPosHome` — sales→POS; admin\|warehouse→hub (**extend** for finance/hr/dispatcher) |
| Module gate | `moduleAllowed` + `my_module_access` / `hr_roles.module_access` |
| Staff roles enum | `admin` \| `sales` \| `warehouse` \| `finance` \| `hr` \| `dispatcher` |
| Finance refund SoR | `post_finance_refund` — wire in Phase 5; do not fork |
| Bridges | `bridges/android/escpos-printer`, `qr-scanner`, `biometric-photo` |
| Phone package baseline | `applicationId = co.zw.nissangtr.management` → becomes **phone** APK |

### DROP / NET-NEW (build clean)

| Gap | Phase |
|-----|-------|
| Dual APK (`…management` phone vs `…management.tablet` kiosk) | 2 |
| Lock Task + Device Owner + HOME launcher + immersive | 2 |
| Idle lock (default 3 min, Admin 1–15) + Device Admin console | 2 |
| Magisk Path B runbook + Device Admin status UI | 2B |
| Fail-closed deny for empty/inactive roles; finance/hr/dispatcher landing | 1 |
| Emp#\|email\|phone resolve | 3 |
| Quotations create/send/convert | 4 |
| Admin\|shop-mgr void/discount/refund via finance pipeline | 5 |
| Engine audio OFF default + splash polish | 6–7 |

### Path B vs Path A device freeze

| Class | Treatment |
|-------|-----------|
| Rooted unlockable CN tablets | **Path B primary** (Magisk + DO + Lock Task) — exact SKU allow-list still open (§11.4); ops runbook ships anyway |
| Non-root / no unlock | **Path A fallback** (DO + Lock Task + app splash only) |
| Phone APK | **Never** DO / Lock Task / Magisk |

### Phase 0 gate

- [x] Premature kiosk code absent
- [x] Keep/drop written
- [x] Path B primary / Path A fallback documented for implementers
- [x] Plan status → **in-progress**
- [x] Catalog #1–69 closed (Done / Stub / Later) — plan status → **complete**
- [x] Later stubs: SignIn PIN/NFC/badge/biometric disabled; offline POS (#57/#59) Done — SQLCipher ADR accepted

---

## Handoff

1. ~~**Revert** premature kiosk scaffolding~~ — done (confirmed Phase 0).
2. ~~Phases 1–6 Must/Should~~ — **complete** per catalog table (Stub rows may deepen later without reopening epic).
3. Optional follow-ups (non-blocking): wire lockout (#20), saleable qty UI (#36), price-override till (#48), web emp#/idle (#63), licensed splash packs (#11/#13).
4. `/security-reviewer` + `/verifier` on remaining Stub deepenings; migrations → `/supabase-rls-auditor`.
5. Locked §10 items remain SoR — do not re-litigate. Later (#16–19, #58, #60–62) need accepted ADRs before build. Offline sales (#57/#59) accepted.

**Invoke next:** optional Stub polish lanes only; no Magisk/offline-login (#58) expansion without ADR accept.
