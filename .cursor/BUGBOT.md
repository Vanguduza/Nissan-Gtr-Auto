# Bugbot Review Rules — Nissan GTR Auto ERP

## Blocking Issues (must fix before merge)

### Security
- New database table without `ENABLE ROW LEVEL SECURITY` and policies
- `service_role` key referenced in client-side code (`apps/web/`, `apps/ios/`, `apps/android-*/`)
- Hardcoded secrets, API keys, or credentials in source code
- Auth bypass or missing authentication on API routes

### Hard Exclusions
- Any ZIMRA, FDMS, fiscalisation, or tax-authority integration code
- Payroll tax computation (PAYE, NSSA POBS/APWCS/ZIMDEF, P4/P4A forms)
- HTML5 or browser-based QR scanning libraries
- Direct item exchange in returns flow (must use Quarantine protocol)

### Data Integrity
- Journal entry UPDATE or DELETE (must use reversing entries)
- Money fields without explicit `currency` column
- Missing `exchange_rate_applied` on converted currency transactions
- Cart items with bundled core charges (must use parent-child schema)

### Architecture
- Hardware access (camera, printer, biometric, GPS) not routed through `bridges/`
- Business logic duplicated across apps instead of `packages/shared/`
- Data pipeline imported as build-time dependency of client apps
- Schema changes made only via Supabase dashboard (must be in migrations)

## Warnings (should fix)

- Missing indexes on columns used in RLS policies
- Missing tests for new business logic
- Full-file rewrites of existing files (prefer targeted diffs)
- Agent changes outside assigned lane in `rufler.yaml`
- Missing `posted_by` / `posted_at` on journal entries

## Info (suggestions)

- Consider adding integration tests for cross-module flows
- Document new environment variables in `AGENTS.md`
- Add trigger condition header to new skill files in `.claude/skills/`
