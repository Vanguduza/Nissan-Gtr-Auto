# Nissan GTR Auto ERP — Agent Instructions

## Project Overview

Composable ERP for Nissan spare-parts distribution. Polyglot monorepo with one Supabase backend and four client surfaces. See `README.md` for architecture and `rufler.yaml` for agent lane assignments.

## Hard Exclusions (Standing Rules)

- **NO ZIMRA** — no FDMS, fiscalisation, mTLS fiscal devices, tax-authority payloads.
- **NO payroll tax** — no PAYE, NSSA, statutory remittance forms. Gross pay + manual deductions only.

## Before You Start

1. Check **claude-mem** and `docs/decisions/` for prior schema decisions, naming conventions, and exclusions.
2. Identify your agent lane in `rufler.yaml` — stay within it unless explicitly routed.
3. Load domain skills from `.cursor/skills/` only when trigger conditions match (see skill descriptions). Prefer `/token-discipline` over reloading blueprints.
4. Read path-specific rules in `.cursor/rules/*.mdc` for the directory you're editing.
5. For UI design on web/mobile, invoke `/ui-ux-pro-max` explicitly — do not auto-load the full design suite. Motion polish: Emil skills (`emil-design-eng` / `review-animations`) explicit-invoke only, after brand/layout.
6. Tooling install status: `docs/TOOLING_SETUP.md`. Cursor + OSS setup playbook: `docs/CURSOR_ERP_SAAS_SETUP_GUIDE.md`.

## Agent team (quality × speed ÷ tokens)

**Roadmap source of truth:** [`docs/plans/2026-07-23-master-erp-development.md`](docs/plans/2026-07-23-master-erp-development.md)

Use **on-demand** specialists — do not load all roles every turn. Full playbook: `docs/AGENT_TEAM.md` / `/sdlc-pipeline`.

```
/manager → /planner → @coding_lane → /security-reviewer → /verifier
```

| Role | Invoke | Notes |
|------|--------|-------|
| Manager | `/manager` | Sequences; no product code |
| Planning | `/planner` | Writes `docs/plans/` only |
| Coding | `@web_agent` / `@backend_agent` / … | One lane per task |
| Security | `/security-reviewer` | Diff-scoped; RLS deep-dive via `/supabase-rls-auditor` |
| Testing | `/verifier` | Tests + exclusions + lane checks |
| Hardware | `/hardware-bridge-specialist` | QR / printer / biometric / GPS |

## Agent Lanes

| Agent | Paths | Invoke |
|-------|-------|--------|
| `@web_agent` | `apps/web/`, `packages/ui/` | Storefront, catalog, B2B |
| `@ios_agent` | `apps/ios/` | iOS customer app |
| `@android_agent` | `apps/android-customer/` | Android customer app |
| `@management_app_agent` | `apps/android-management/` | POS, warehouse, HR, finance |
| `@android_delivery_agent` | `apps/android-delivery/` | Driver-only delivery (GPS FGS, POD, presence) |
| `@hardware_mobile_agent` | `bridges/` | QR, printer, biometric, GPS |
| `@backend_agent` | `supabase/`, `packages/supabase-client/` | Schema, migrations, edge functions |
| `@data_pipeline_agent` | `data-pipeline/` | Scraping, FAST parsing |
| `@finance_agent` | Ledger, accounts, finance | Journal entries, reports |

## Global Laws (from `.cursorrules`)

- **Bridge-First:** Hardware access only through `bridges/` — never browser/WebView APIs.
- **RLS Mandate:** Every table gets RLS policies before being "done".
- **Ledger Immutability:** Append-only journal entries; corrections via reversing entries.
- **Multi-Currency:** Explicit currency on all money fields; store exchange rate at transaction time.

## Run Commands

> Apps not yet scaffolded. Commands will be added per-app as they are created.

```bash
# Supabase local dev
supabase start
supabase db reset          # Apply all migrations locally
supabase gen types typescript --local > packages/supabase-client/src/database.types.ts

# Data pipeline
cd data-pipeline && python -m pytest

# Web (when scaffolded)
cd apps/web && pnpm dev

# Android (when scaffolded)
cd apps/android-customer && ./gradlew assembleDebug
cd apps/android-management && ./gradlew assembleDebug
cd apps/android-delivery && ./gradlew assembleDebug   # Fake if no SUPABASE_* in local.properties

# iOS (when scaffolded)
cd apps/ios && xcodebuild -scheme GTRCustomer -destination 'platform=iOS Simulator,name=iPhone 16'
```

## Testing Expectations

- Backend: migration tests, RLS policy tests, edge function unit tests.
- Web: component tests (Vitest), E2E (Playwright) for critical flows.
- Mobile: unit tests for ViewModels, instrumented tests for bridges.
- Finance: journal entry balance validation, statement generation accuracy.

## PR Checklist

Before opening a PR, verify:
- [ ] No ZIMRA or payroll tax references introduced
- [ ] No HTML5/browser QR scanning added
- [ ] New tables have RLS policies in the migration
- [ ] Changes stay within agent lane (or cross-cutting agent was invoked)
- [ ] Targeted diffs, not full-file rewrites of existing code
- [ ] Shared logic in `packages/shared/`, not duplicated per app

## Local-First (Default)

Primary development happens in **Cursor Desktop** on a developer's machine.
See `docs/LOCAL_DEVELOPMENT.md` for clone, open, and toolchain steps.

When working locally:
- Use Agent / Composer in the IDE against this repo root.
- Prefer `/manager` for multi-step features; invoke lane agents and `/planner`, `/security-reviewer`, `/verifier` by phase.
- Run Supabase, Node, Xcode, and Android Studio toolchains on the host.
- Commit on `cursor/<descriptive-name>-ad25` branches and open PRs as usual.

## Cloud Agent Instructions (Optional)

Only when intentionally using a remote Cloud Agent:
- Use `.cursor/environment.json` for environment setup.
- Prefer Cloud Agents for long schema/docs/backend passes — not for iOS/Android/hardware.
- Run tests before marking work complete.
- Commit and push to `cursor/<descriptive-name>-ad25` branches.
- Create draft PRs via the PR management tool.
