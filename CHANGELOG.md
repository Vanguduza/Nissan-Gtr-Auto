# Changelog

## Unreleased — 2026-08-12

### Added

- `docs/DIAL_SPARE_ADOPTION_PLAN.md` — Dial-a-Spare architecture adoption plan (finance, WMS, delivery, jobs, AI/CRM, Resend/Brevo).
- `docs/decisions/2026-08-12-principal-vs-dial-agency.md` — commercial model ADR (principal distributor ≠ DIAL agency).
- `@gtr/notifications` — Resend (transactional) + Brevo (promo) adapter contracts.
- `@gtr/delivery` — `DeliveryDispatchWorkflow` contracts + OSRM client helpers (D-44/D-45 alignment).
- Edge `brevo_send.ts`; `process-crm-promos` prefers Brevo for CRM email.
- Android `OsrmRouteFetcher`; delivery app prefers `OSRM_URL` over Google Directions.
- `@gtr/shared` `MoneyMinor` / `amountMinor` helpers (dual with legacy `Money`).

### Changed

- Living docs introduced (`CHANGELOG.md`, `ENHANCEMENTS.md`, `BUGS.md`); README surfaces delivery app + adoption plan.
