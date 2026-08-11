# Nissan GTR Auto — Composable ERP

**Production domain:** [nissangtrauto.co.zw](https://nissangtrauto.co.zw)  
(Decision: `docs/decisions/2026-07-23-company-domain.md`)

Multi-platform, composable ERP for Nissan spare-parts distribution (**principal / first-party distributor** — not a multi-vendor marketplace). One Supabase (PostgreSQL) backend, five client surfaces:

| Surface | Stack | Path |
|---------|-------|------|
| Customer Web App | Next.js (App Router) | `apps/web/` |
| Customer Mobile (iOS) | Swift/SwiftUI + native bridges | `apps/ios/` |
| Customer Mobile (Android) | Kotlin + native bridges | `apps/android-customer/` |
| Management & Sales (Android) | Kotlin (POS, warehouse, HR, finance, dispatch) | `apps/android-management/` |
| Delivery (Android) | Kotlin driver app (jobs, POD, GPS, routing) | `apps/android-delivery/` |

**Dial-a-Spare adoption:** engineering patterns from DIAL (money minor units, MapLibre+OSRM delivery SoR, Resend/Brevo split, Temporal dispatch contracts) — see [`docs/DIAL_SPARE_ADOPTION_PLAN.md`](docs/DIAL_SPARE_ADOPTION_PLAN.md) and ADR [`docs/decisions/2026-08-12-principal-vs-dial-agency.md`](docs/decisions/2026-08-12-principal-vs-dial-agency.md). Living docs: [`CHANGELOG.md`](CHANGELOG.md), [`ENHANCEMENTS.md`](ENHANCEMENTS.md), [`BUGS.md`](BUGS.md).

Shared packages live under `packages/` (`shared`, `supabase-client`, `ui`, `documents`, `notifications`, `delivery`). Supabase schema, migrations, and edge functions live under `supabase/`. Catalog scraping/parsing infrastructure lives under `data-pipeline/` (independent of client builds).

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
│   ├── android-management/     # Internal POS, receiving, warehouse, HR, finance, dispatch
│   └── android-delivery/       # Driver-only jobs, POD, GPS, OSRM/Google routing
├── packages/
│   ├── shared/                 # Cart/pricing math, amountMinor money, ledger helpers
│   ├── notifications/          # Resend (transactional) + Brevo (promo) adapters
│   ├── delivery/               # Dispatch workflow contracts + OSRM helpers
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
| `.cursor/agents/` | Specialist subagents (planner, manager, security, verifier, RLS, hardware) |
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
| [claude-mem](https://github.com/thedotmack/claude-mem) | **Install on host** | Persistent memory across sessions — run `scripts/windows/install-claude-mem.ps1` |
| [ui-ux-pro-max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) | **Installed** (explicit `/ui-ux-pro-max`) | Design-direction skill for storefront, My Garage, visual catalog |
| Domain skills | **Installed** | `.cursor/skills/` — ledger, QR, FAST parser, catalog, ERPNext parity, token-discipline |
| [n8n-mcp](https://github.com/czlonkowski/n8n-mcp) | Optional | Copy `.cursor/mcp.json.example` → `mcp.json` if you use n8n |
| ECC + Ruflo | **Configured** | Base orchestration layer (`rufler.yaml`, path-routed lanes) |

Full install steps: [`docs/TOOLING_SETUP.md`](docs/TOOLING_SETUP.md). Install claude-mem early — it is the backbone of context discipline across long multi-session builds.

### Development mode: Local-first

**Primary workflow is Cursor Desktop on your machine.** Cloud Agents are optional for long/overnight tasks only.

→ **Switch / set up local:** [`docs/LOCAL_DEVELOPMENT.md`](docs/LOCAL_DEVELOPMENT.md)

### Additional Cursor Best Practices

Beyond the core setup prompt, this repo also includes:

- **`.cursorignore`** — excludes build artifacts, caches, and generated files from indexing
- **Nested `AGENTS.md`** — per-app run/test instructions (add as apps are scaffolded)
- **Subagents** — `planner`, `manager`, `security-reviewer`, `verifier`, `supabase-rls-auditor`, `hardware-bridge-specialist` (see `docs/AGENT_TEAM.md`)
- **Bugbot rules** — ERP-specific PR review gates (RLS required, no ZIMRA references)
- **Hooks** — format-on-edit, production URL guards, migration test triggers
- **Cloud environment** — `.cursor/environment.json` (optional; used only by Cloud Agents)
- **Worktrees** — `.cursor/worktrees.json` for parallel platform development
- **Permissions/sandbox** — safe agent execution boundaries

See `docs/CURSOR_BEST_PRACTICES.md` for the full extended playbook.

## Development

> Phase 1 foundation is in place (`packages/*`, `supabase/migrations`).  
> Prefer **local Cursor Desktop** (see `docs/LOCAL_DEVELOPMENT.md`).
>
> ```powershell
> pnpm install
> supabase start   # requires Docker + Supabase CLI
> supabase db reset
> pnpm db:types
> ```
>
> Edge secrets (never commit values): see root `.env.example` and
> [`supabase/functions/README.md`](supabase/functions/README.md) — including
> WhatsApp Cloud (`WHATSAPP_*`, `SITE_URL`) for receipts + parts-finder bot.
>
> Execution order:
>
> 1. Orchestration files (done)
> 2. External tooling — ui-ux-pro-max + domain skills (done); run claude-mem host install (`docs/TOOLING_SETUP.md`)
> 3. Supabase schema foundation (done — apply with `supabase db reset`)
> 4. Scaffold Next.js `apps/web` **or** deepen finance posting flows
> 5. Hardware bridges (QR + printer) — **local only** (needs devices/SDK)
> 6. Data pipeline + visual catalog
> 7. ERPNext parity checklist
> 8. Supporting capabilities (ContiPay, GPS, offline sync, etc.)

## License

Proprietary — Nissan GTR Auto.
