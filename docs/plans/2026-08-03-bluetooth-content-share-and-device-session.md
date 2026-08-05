# Bluetooth / Device Session + Staff Identity Extensions

- Status: draft
- Date: 2026-08-03
- Source: [`catalog/bluetooth_content_share.html`](../../catalog/bluetooth_content_share.html) (body = POS kiosk blueprint; **filename ≠ product feature** — no OBEX/Nearby Share/file-transfer UX in the HTML)
- Parent: [`2026-08-03-pos-kiosk-role-routing.md`](./2026-08-03-pos-kiosk-role-routing.md); ADRs [`auth-otp-fail-closed`](../decisions/2026-07-25-auth-otp-fail-closed.md), [`web-management-parity-rbac`](../decisions/2026-07-25-web-management-parity-rbac.md)
- Lane(s): **`@management_app_agent`** (primary) → `@backend_agent` (employee_code login resolve + RLS) → `@hardware_mobile_agent` (BT printer/admin intents only) → `@web_agent` (staff idle lock + emp# login field)
- Skills: `/token-discipline`; `/qr-inventory-workflow` only if printer/scanner admin path changes

## Goal

Ship **required session idle lock**, **staff Employee ID as an additional login identifier**, and **engine-audio splash with per-device OFF** in Device Admin — while mapping the HTML’s Bluetooth mentions to Bridge-First printer/admin config (not inventing Web Bluetooth content share).

## A. Bluetooth “content share” → GTR map

| HTML asks | Adopt / Adapt / Defer |
|-----------|------------------------|
| Device Admin: Wi‑Fi / **Bluetooth** config via system intents | **Adapt** — maintenance console launches `Settings`/`BluetoothAdapter` intents under Lock Task allow-list; not Web Bluetooth |
| Barcode scanner + **printer** hardware config | **Adopt** existing `bridges/android/escpos-printer` (+ QR scanner); expose diagnostics in maintenance console |
| Magisk / unlock bootloader / `/system/media` bootanimation | **Defer / reject** — unsafe; kiosk plan already forbids OEM rooting |
| SQLCipher offline DB + Argon2 offline login + WorkManager sync | **Defer** — needs ADR; privilege-escalation risk (kiosk Later) |
| True customer **file/content share** over BT (diagrams, PDFs, media) | **Not in HTML body** — **defer** until product clarifies; if ever built → new `bridges/` module only |

**Bridge-First:** Web/staff UI may **display** pairing status and trigger bridge/admin actions; never `navigator.bluetooth` / WebView BT. Surfaces: **android-management** (SoR) + maintenance console; web = diagnostics copy only.

**Sequencing:** after Lock Task + maintenance console (kiosk slices B/C); hardware lane only for bridge surface polish.

## B. Employee ID as signup / login option

**Clean model (do not conflate):**

| Identity | Who | Identifiers | Auth |
|----------|-----|-------------|------|
| **Customer** | Storefront / OTP signup | email and/or phone (`auth-otp`); password login | Never `employees.employee_code` |
| **Staff** | HR-provisioned | email and/or phone **and** `employees.employee_code` (`GTR{grade}{seq}` from `next_employee_code_for_grade`) | Password (existing GoTrue); `must_change_password` after onboarding |

- **Alongside, not instead of:** staff login UI accepts emp# **or** email **or** phone → resolve to `auth.users` → password grant. Customers keep email/phone OTP signup unchanged.
- **Not** self-serve “signup with employee ID” for the public. Emp codes are minted only by `complete_hr_onboarding` / HR Edge (`hr-onboarding-create-auth`).
- **PIN:** remains deferred (kiosk Later). This plan = **emp# as identifier + password**, not a parallel PIN store.
- Backend: SECURITY DEFINER resolve `employee_code` → `user_id` (active employees only); rate-limit; no emp# enumeration leak; RLS unchanged on `employees`.

## C. Session idle lock (required)

Align with kiosk Must §4:

- Root activity / Compose `pointerInput` (Android) and staff shell activity listener (web) → configurable idle minutes → clear in-memory session → **in-app** lock/login (never Android launcher / never leave staff shell to public storefront).
- Config: device-local default **5 min** (kiosk); staff-admin override range e.g. 1–30; persist per-device (Android DataStore) + optional org default in staff settings later.
- Web staff: same UX (lock → reauth); iOS customer app **out** (not kiosk). iOS staff N/A today.

## D. Engine audio boot

- App splash (ExoPlayer / MediaPlayer on **local RAW** assets) as splash starts; stop on end, skip, or app background. Respect media volume; no OEM `bootanimation.zip` audio.
- **Must:** toggle **Engine audio: ON/OFF** in Device Administration console (maintenance / Device Owner gated). Persist **per device** (DataStore key `engine_audio_enabled`).
- **Default: OFF** until licensed assets land (licensing + YAGNI); ops can enable per tablet.
- Console home: Android management maintenance screen (same gate as Wi‑Fi/BT/exit Lock Task). Web staff admin may show read-only “tablet setting” note only — SoR is on-device.

## E. Non-goals / exclusions / deferral conflicts

**Hard exclusions:** no ZIMRA/FDMS; no payroll tax; Bridge-First; new tables + RLS same migration.

| Prior kiosk plan note | This plan |
|----------------------|-----------|
| Idle lock = Must | **Unchanged — build now** |
| Engine audio = Later | **Promote to Should/Must-lite** with default OFF + admin kill switch |
| Employee ID + PIN = Later | **Split:** emp# + password **build now**; PIN **still Later** |
| Firmware Magisk / system boot logo | Still **out** |
| Offline SQLCipher SoR | Still **Later** (ADR required) |
| BT content/file share product | **Out** until specified (HTML does not define it) |

## Acceptance checklist

- [ ] Staff can sign in with **employee_code** or email or phone + password (Android management + web staff); customers cannot use emp#
- [ ] `must_change_password` still gates first login after HR provision
- [ ] Idle timeout → lock screen inside app; logout never drops to launcher on kiosk
- [ ] Idle minutes configurable; persisted; sensible default
- [ ] Splash engine audio plays only when device toggle ON; OFF is default; toggle in Device Admin console
- [ ] Maintenance BT/Wi‑Fi/printer actions use system intents + existing ESC/POS bridge — no Web Bluetooth
- [ ] No ZIMRA, payroll tax, HTML5 QR, Magisk/OEM flash in deliverables
- [ ] Any new resolve RPC / settings table ships with RLS

## Paths in scope

- `apps/android-management/` — idle lock, splash audio, Device Admin toggle, emp# login field, Lock Task continuity
- `apps/web/` — staff login identifier + idle lock in staff shell; change-password already exists
- `supabase/` — emp# → user resolve RPC/Edge; optional org idle default (+ RLS)
- `bridges/android/escpos-printer/` — only if admin diagnostics need bridge hooks
- Docs: this plan; update kiosk plan “Later” rows when implementing

## Out of scope

- Customer storefront emp# signup; Employee PIN/NFC/biometric login
- Magisk, custom `bootanimation.zip`, SQLCipher offline POS SoR
- iOS customer idle lock / engine audio
- New Bluetooth file-share bridge
- Rewriting POS cart/checkout or parallel RBAC

## Phased build order

| Phase | Owner | Work |
|-------|-------|------|
| 1 | `@management_app_agent` (+ `@web_agent`) | Idle lock + in-app reauth (web + Android) |
| 2 | `@backend_agent` → clients | Resolve `employee_code` → auth user; login UI third identifier |
| 3 | `@management_app_agent` | Splash audio + per-device OFF toggle in Device Admin |
| 4 | `@management_app_agent` + `@hardware_mobile_agent` | Maintenance Wi‑Fi/BT/printer intents on top of kiosk Lock Task |
| 5 | — | Defer: PIN, offline cipher, true BT content share |

## Handoff

1. Implement Phase 1–3 in **`@management_app_agent`** / `@backend_agent` / `@web_agent` as tabled (one lane per dirty tree)
2. `/security-reviewer` (auth resolve, session lock, Device Admin)
3. Migrations → `/supabase-rls-auditor`
4. `/verifier` (exclusions + login/idle smokes)
5. `/manager` for done gate

**Invoke:** `/manager` to sequence, or Phase 1 `@management_app_agent` once approved.
