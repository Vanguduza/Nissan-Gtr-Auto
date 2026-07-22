# Nissan GTR Auto — Composable ERP

Multi-platform, composable ERP for Nissan spare-parts distribution. One Supabase (PostgreSQL) backend, four client surfaces:

| Surface | Stack | Path |
|---------|-------|------|
| Customer Web App | Next.js (App Router) | `apps/web/` |
| Customer Mobile (iOS) | Swift/SwiftUI + native bridges | `apps/ios/` |
| Customer Mobile (Android) | Kotlin + native bridges | `apps/android-customer/` |
| Management & Sales (Android) | Kotlin (POS, warehouse, HR, finance) | `apps/android-management/` |

Shared packages live under `packages/`. Supabase schema, migrations, and edge functions live under `supabase/`. Catalog scraping/parsing infrastructure lives under `data-pipeline/` (independent of client builds).

## Hard Exclusions

These are **permanently out of scope** — no scaffolding, no TODOs, no references:

- **No ZIMRA integration** — no FDMS, fiscalisation QR codes, mTLS fiscal device logic, or tax-authority payloads in checkout/invoicing/receipts.
- **No payroll tax computation** — no PAYE, NSSA POBS/APWCS/ZIMDEF, statutory remittance forms (P4/P4A), or tax brackets. Payroll computes gross pay from attendance/hours/salary only, with manual/custom deduction line items.

## Repository Layout

```
.
├── apps/
│   ├── web/                    # Next.js storefront, My Garage, visual catalog, B2B portal
│   ├── ios/                    # iOS customer app + AVFoundation bridges
│   ├── android-customer/       # Android customer app + CameraX bridges
│   └── android-management/     # Internal POS, receiving, warehouse, HR, finance, dispatch
├── packages/
│   ├── shared/                 # Cart/pricing math, core-charge splitting, ledger helpers
│   ├── supabase-client/        # Typed Supabase client (generated + hand-written)
│   └── ui/                     # Cross-platform design tokens (web-first)
├── bridges/
│   ├── ios/                    # QR scanner, ESC/POS printer, biometric, GPS
│   └── android/                # QR scanner, ESC/POS printer, biometric, GPS
├── supabase/
│   ├── migrations/             # Version-controlled schema (RLS on every table)
│   └── functions/              # Edge functions
├── data-pipeline/              # Nissan FAST parsing, catalog scraping (Python)
├── .cursor/                    # Cursor orchestration (rules, agents, hooks, skills)
├── .claude/skills/             # Deep domain skills (trigger-conditional loading)
├── rufler.yaml                 # Multi-agent swarm topology
└── .cursorrules                # Global laws for all agents
```

## Cursor AI Orchestration

This repo is designed for **path-routed polyglot multi-agent development** in Cursor. Key files:

| File | Purpose |
|------|---------|
| `rufler.yaml` | Swarm topology — named agents with exclusive lane boundaries |
| `.cursorrules` | Global laws (Bridge-First, RLS mandate, ledger immutability, exclusions) |
| `.cursor/rules/*.mdc` | Path-specific rules (auto-applied per directory) |
| `.claude/skills/*.md` | Deep domain skills (loaded only when trigger conditions match) |
| `AGENTS.md` | Repo-wide agent instructions (run commands, conventions) |
| `.cursor/agents/` | Specialist subagents (RLS auditor, verifier, hardware bridge) |
| `.cursor/BUGBOT.md` | PR review rules for Bugbot |
| `.cursor/hooks.json` | Lifecycle hooks (format, prod guards, test gates) |

### Agent Lanes

| Agent | Scope |
|-------|-------|
| `@web_agent` | `apps/web/`, `packages/ui/` |
| `@ios_agent` | `apps/ios/`, `bridges/ios/` |
| `@android_agent` | `apps/android-customer/`, `bridges/android/` (customer) |
| `@management_app_agent` | `apps/android-management/` |
| `@hardware_mobile_agent` | `bridges/` (cross-cutting, invoked explicitly) |
| `@backend_agent` | `supabase/`, `packages/supabase-client/` |
| `@data_pipeline_agent` | `data-pipeline/` |
| `@finance_agent` | Ledger schema, financial statements (cross-cutting) |

### External Tooling

| Tool | Status | Purpose |
|------|--------|---------|
| [claude-mem](https://github.com/thedotmack/claude-mem) | **Recommended** | Persistent memory across sessions — schema decisions, exclusions, lane boundaries |
| [ui-ux-pro-max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) | **Recommended** | Design-direction skill for storefront, My Garage, visual catalog |
| [n8n-mcp](https://github.com/czlonkowski/n8n-mcp) | Optional | Backend workflow automations (low-stock triggers, SMS marketing) — skip if n8n not in ops stack |
| ECC + Ruflo | **Configured** | Base orchestration layer (`rufler.yaml`, path-routed lanes) |

Install claude-mem early — it is the backbone of context discipline across long multi-session builds.

### Additional Cursor Best Practices

Beyond the core setup prompt, this repo also includes:

- **`.cursorignore`** — excludes build artifacts, caches, and generated files from indexing
- **Nested `AGENTS.md`** — per-app run/test instructions (add as apps are scaffolded)
- **Subagents** — `supabase-rls-auditor`, `verifier`, `hardware-bridge-specialist`
- **Bugbot rules** — ERP-specific PR review gates (RLS required, no ZIMRA references)
- **Hooks** — format-on-edit, production URL guards, migration test triggers
- **Cloud environment** — `.cursor/environment.json` for reproducible Cloud Agent VMs
- **Worktrees** — `.cursor/worktrees.json` for parallel platform development
- **Permissions/sandbox** — safe agent execution boundaries

See `docs/CURSOR_BEST_PRACTICES.md` for the full extended playbook.

## Development

> Apps are not yet scaffolded. Follow the execution order in the setup prompt:
>
> 1. Orchestration files (done)
> 2. Install external tooling (claude-mem, ui-ux-pro-max)
> 3. Supabase schema (Chart of Accounts, ledger, vehicle_master, inventory)
> 4. Financial module
> 5. Hardware bridges (QR + printer)
> 6. Data pipeline + visual catalog
> 7. ERPNext parity checklist
> 8. Supporting capabilities (ContiPay, GPS, offline sync, etc.)

## License

Proprietary — Nissan GTR Auto.
