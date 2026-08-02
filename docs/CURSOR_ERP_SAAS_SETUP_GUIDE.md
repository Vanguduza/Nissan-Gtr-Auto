# Cursor + Adopt-First Strategy Guide for ERP / SaaS

**Audience:** product owners and Cursor agents building (or extending) multi-module SaaS/ERP systems.  
**Goal:** ship by **adopting and customizing** — skills, MCP, premade repos, SaaS APIs, and OSS satellites — and **invent only** what differentiates the product or what hard exclusions forbid buying.  
**Companion research (this repo):** [`AGENTS.md`](../AGENTS.md), [`docs/AGENT_TEAM.md`](AGENT_TEAM.md), [`docs/TOOLING_SETUP.md`](TOOLING_SETUP.md), [`docs/IMPLEMENTATION-AND-DESIGN-GUIDE.md`](IMPLEMENTATION-AND-DESIGN-GUIDE.md), [`docs/plans/2026-08-02-dev-tooling-skills-research.md`](plans/2026-08-02-dev-tooling-skills-research.md), [`docs/plans/2026-08-02-open-source-erp-toolkit-audit.md`](plans/2026-08-02-open-source-erp-toolkit-audit.md), [`infra/satellites/README.md`](../infra/satellites/README.md).  
**Always-on agent rule:** [`.cursor/rules/adopt-first.mdc`](../.cursor/rules/adopt-first.mdc).

Nissan GTR Auto ERP is a **worked example** in the appendix — not the ceiling of this guide. Future systems will have **more modules** than GTR has today; the rituals scale with module count.

---

## Table of contents

1. [Philosophy: adopt → customize → invent](#1-philosophy-adopt--customize--invent)
2. [Pre-flight Cursor workspace setup](#2-pre-flight-cursor-workspace-setup)
3. [Mandatory discovery ritual (with copy-paste prompts)](#3-mandatory-discovery-ritual-with-copy-paste-prompts)
4. [Capability catalog for a full ERP/SaaS](#4-capability-catalog-for-a-full-erpsaas)
5. [Execution playbook (multi-agent vs single)](#5-execution-playbook-multi-agent-vs-single)
6. [How to evaluate a GitHub repo](#6-how-to-evaluate-a-github-repo)
7. [Wiring satellites without replacing the system of record](#7-wiring-satellites-without-replacing-the-system-of-record)
8. [Anti-patterns](#8-anti-patterns)
9. [Checklists](#9-checklists)
10. [Appendix: GTR case study](#10-appendix-gtr-case-study)

---

## 1. Philosophy: adopt → customize → invent

### 1.1 The default bias of AI coding agents

Left alone, Cursor agents **scaffold**. That is useful for greenfield files and dangerous for ERP/SaaS: you get a second auth stack, a home-grown queue, a faux accounting engine, and a “temporary” search that never dies. Serious teams invert the default:

> **Search the market (skills → MCP → SaaS/API → OSS) before opening a new package.**

Adopt-first is not laziness. It is how experienced developers already work: Clerk/Auth0/Supabase Auth instead of rolling sessions; Stripe/Paystack/local PSPs instead of card vaults; Meilisearch/Typesense instead of reinventing ranking; Resend/Postmark instead of SMTP yak-shaves.

### 1.2 Decision tree: Buy / Integrate / Fork / Build

Use this tree **for every capability area** in §4. Write the choice into `docs/decisions/` (or the epic plan) so the next agent does not re-litigate it.

```text
Is this capability a competitive differentiator for OUR product?
├─ YES → prefer Build (or Fork a permissive base you will own long-term)
│         still: steal patterns, schemas, and UI kits — do not invent UX from vacuum
└─ NO  → Is there a SaaS/API with acceptable cost, data residency, and lock-in?
          ├─ YES → BUY (thin adapter in our codebase)
          └─ NO  → Is there OSS that fits our stack + license?
                    ├─ YES, as a service beside us → INTEGRATE (satellite + adapter)
                    ├─ YES, as a library inside us → INTEGRATE (dependency + wrapper)
                    ├─ YES, but we need durable control → FORK (budget merge cost)
                    └─ NO fit after honest search → BUILD the minimum slice
```

| Path | Meaning | Typical examples |
|------|---------|------------------|
| **Buy** | Pay a vendor; own only the adapter and domain mapping | Auth (Clerk/Auth0/WorkOS), email (Resend), payments (Stripe), feature flags (LaunchDarkly/PostHog), error tracking (Sentry) |
| **Integrate** | Run OSS or call public APIs; keep **our** DB as system of record | Meilisearch, Traccar, OSRM, Superset, Casbin, NHTSA vPIC |
| **Fork** | Copy a permissive codebase we will maintain | Rare — only when upstream velocity ≠ our needs and license allows |
| **Build** | First-party domain logic | Industry pricing rules, ledger semantics, fitment/VIN catalogs, Bridge-First hardware, jurisdiction-specific exclusions |

### 1.3 Customize, don’t absorb

Adopting is not “replace our ERP with theirs.”

| Healthy | Unhealthy |
|---------|-----------|
| Satellite search indexes **our** catalog rows | Dual write into Medusa *and* our inventory as competing truths |
| Casbin policies evaluated at **our** API boundary | Keycloak becomes a second user directory beside Auth |
| PSP webhooks update **our** payment + ledger tables | Commerce platform owns AR, COGS, and returns |
| BI tool **reads** a replica / warehouse view | Analysts edit production via the BI SQL editor |

**Rule:** the system of record (usually Postgres + Auth + RLS/RPC) stays singular. Everything else is a **lens, index, or worker**.

### 1.4 How other Cursor-heavy teams actually set this up (2026 patterns)

Industry practice has converged on **layered agent config**, not one mega-prompt:

| Layer | Purpose | Examples |
|-------|---------|----------|
| **AGENTS.md** | Portable repo context (stack, lanes, exclusions, commands) | Root + optional nested `apps/*/AGENTS.md` |
| **Rules** (`.cursor/rules/*.mdc`) | Scoped or always-on constraints | Globs for web/Android; short always-on discipline |
| **Skills** (`SKILL.md`) | On-demand expertise / workflows | Domain skills, `find-skills`, design packs |
| **MCP** | Live tools (DB, GitHub, docs, browser) | Curated allowlist, prefer read-only first |
| **Subagents / lanes** | Isolated roles (planner, reviewer, verifier) | `/manager` → `/planner` → lane → security → verify |

Cross-tool consensus (Cursor, Codex, Copilot, etc.): put shared truth in **AGENTS.md**; keep Cursor-only globs in `.mdc`; load skills only when triggered; treat MCP as privileged microservices.

**Token discipline:** always-on text must stay short. Long catalogs belong in this guide and in skills — not in every chat’s system prompt.

---

## 2. Pre-flight Cursor workspace setup

Do this **once per product** (and re-audit quarterly). Skip nothing marked required.

### 2.1 Repository skeleton (required)

```text
repo/
├── AGENTS.md                 # lanes, exclusions, run commands, team pipeline
├── .cursorrules              # optional short global laws (or migrate fully into AGENTS.md + rules)
├── .cursor/
│   ├── rules/                # *.mdc — prefer scoped + short always-on
│   ├── skills/               # project skills (domain > generic)
│   ├── agents/               # subagents (verifier, RLS auditor, …)
│   ├── mcp.json.example      # committed template
│   └── mcp.json              # gitignored local secrets
├── docs/
│   ├── decisions/            # ADRs — agents must search here first
│   ├── plans/                # epic plans from /planner
│   └── CURSOR_ERP_SAAS_SETUP_GUIDE.md  # this file
├── apps/ | packages/ | supabase/ | bridges/ | infra/
└── .gitignore                # secrets, mcp.json, .env*, local overlays
```

### 2.2 AGENTS.md (keep under ~200 lines)

Must answer:

1. What is the **system of record**?
2. What are **hard exclusions**?
3. What are **agent lanes** (path → owner)?
4. What is the **default pipeline** (plan → code → security → verify)?
5. Where do decisions and plans live?
6. How do we run tests / migrations locally?

Nested `AGENTS.md` files (e.g. under `apps/web/`) may refine lane detail; root file **routes**.

### 2.3 Rules intensity

| Intensity | Use for | Avoid for |
|-----------|---------|-----------|
| **Always-on** (short) | Exclusions, adopt-first, session/token discipline | Essays, full module catalogs |
| **Agent-requestable** | Ponytail/YAGNI, deep design reviews | Things that must never be skipped |
| **Glob-scoped** | Next.js / Kotlin / Swift / finance ledger conventions | Cross-cutting policy that every agent needs |

Prefer **agent-requestable or path-scoped** over always-on ultra packs. Heavy design/motion skills remain **explicit-invoke only**.

### 2.4 Skills directories

| Location | Role |
|----------|------|
| `.cursor/skills/` | Project domain skills (ERP modules, token-discipline, brand) |
| `~/.cursor/skills/` | Global craft skills (e.g. motion polish) shared across repos |
| Nested `apps/*/.cursor/skills/` | Deploy/test skills scoped to one app |

**Install tooling:**

```bash
# Browse ecosystem
# https://skills.sh/

# Search
npx skills find <query>

# Install project-scoped (preferred for domain)
npx skills add <owner/repo> --skill <name> -a cursor -y

# Install global craft (use sparingly)
npx skills add <owner/repo> --skill <name> -g -a cursor -y

# Optional meta-skill so agents know how to search
npx skills add vercel-labs/skills --skill find-skills -g -a cursor -y
```

CLI source: [vercel-labs/skills](https://github.com/vercel-labs/skills). Prefer skills with strong install counts, reputable owners (`vercel-labs`, `anthropics`, known vendors), and licenses that match your product. **Audit every skill** against hard exclusions before install.

### 2.5 MCP curation (required process)

Catalogs: [punkpeye/awesome-mcp-servers](https://github.com/punkpeye/awesome-mcp-servers), [glama.ai/mcp/servers](https://glama.ai/mcp/servers).

**Process:** browse → shortlist 5–10 → check license / auth / network egress → pin versions → add to gitignored `mcp.json` from example → enable in Cursor Settings → document in tooling docs → prefer **read-only** until trusted.

Treat each MCP server like a **privileged microservice**:

- Least privilege (read-only DB/Supabase until write is justified)
- Secrets in env / vault — never committed
- Internal **allowlist** of approved servers
- Do not map every REST endpoint 1:1 into tools (token bloat); prefer high-level use cases
- Never add fiscal/tax-authority or browser-QR “hardware” MCP that fights Bridge-First policies

### 2.6 Secrets & git

- `.env`, `.env.local`, `.cursor/mcp.json`, service-role keys, PSP secrets → **gitignored**
- Commit `.env.example` / `mcp.json.example` with placeholders only
- Prefer host OS secret stores or CI OIDC for production
- Rotate keys that ever appear in chat logs

### 2.7 Git / branch hygiene for agents

- Feature branches: `cursor/<descriptive-name>-ad25` (or your team convention)
- Agents commit **only when asked**
- Hooks (format, exclusion greps, RLS checks) catch what prompts miss
- Do not run two orchestration stacks (e.g. CrewAI + Cursor `/manager`) on the same dirty tree

### 2.8 Optional sidecars

| Tool | Role | Do not |
|------|------|--------|
| **Goose** (or similar CLI agent) | Headless recipes, nightly checklists | Replace Cursor as primary IDE agent |
| **Cloud Agents** | Long schema/docs passes | Mobile/hardware/Bridge work that needs device SDKs |
| **LLM gateways** | Cost experiments | Pipe production PII / service-role through unreviewed proxies |

---

## 3. Mandatory discovery ritual (with copy-paste prompts)

**When:** every new module, epic, or non-trivial feature (anything beyond a one-file bugfix).  
**Who:** human product owner starts the chat with a discovery prompt; agents must not skip steps.  
**Output:** a short “Discovery note” in the plan or PR: options found, decision (Buy/Integrate/Fork/Build), license, risks.

### 3.1 Ritual steps (fixed order)

1. **In-repo reuse** — search `docs/decisions/`, packages, similar modules, claude-mem / prior plans.
2. **Skills** — project skills → [skills.sh](https://skills.sh/) → `npx skills find …`.
3. **MCP** — awesome-mcp / Glama for operational leverage (not as a substitute for product architecture).
4. **Market** — GitHub topics, awesome-lists, SaaS category leaders, public APIs.
5. **Score** — use §6 evaluation rubric.
6. **Decide** — Buy / Integrate / Fork / Build; record ADR if non-obvious.
7. **Only then** scaffold adapters or first-party code.

### 3.2 Copy-paste: product owner → Cursor (start of epic)

```text
/manager

Epic: <NAME>
Goal: <one paragraph outcome>

Hard constraints: <exclusions, stack, SoR, compliance>

Before any coding lane:
1) Run the Adopt-First discovery ritual from docs/CURSOR_ERP_SAAS_SETUP_GUIDE.md §3.
2) Search: docs/decisions/, .cursor/skills/, skills.sh / `npx skills find <keywords>`,
   awesome-mcp-servers, GitHub topics (<topic list>), and mainstream SaaS/API options for this capability.
3) Produce a Discovery note with ≥3 options scored Buy/Integrate/Fork/Build.
4) Then /planner writes docs/plans/<date>-<slug>.md with the chosen path and adapter boundaries.
5) One coding lane only after the plan is accepted. Then /security-reviewer → /verifier.

Do NOT invent a greenfield implementation until discovery shows no acceptable adopt path.
```

### 3.3 Copy-paste: force skill + MCP search

```text
Before scaffolding <FEATURE>, perform discovery only (no product code):

A. Skills
- List matching skills already in .cursor/skills/ and ~/.cursor/skills/
- Run: npx skills find "<FEATURE keywords>"
- Check https://skills.sh/ leaderboard for the same keywords
- Recommend at most 1–2 skills to install; justify license + exclusion fit

B. MCP
- Search punkpeye/awesome-mcp-servers and Glama for servers that help implement or operate <FEATURE>
- Propose an allowlist delta (additions only); prefer read-only; note secrets required
- Reject anything that implies browser hardware access or excluded tax/fiscal domains

C. Output a markdown table: Option | Type (skill/MCP/SaaS/OSS) | License | Fit | Verdict
```

### 3.4 Copy-paste: force GitHub + SaaS API search

```text
Capability area: <e.g. Inventory reservations / Feature flags / Transactional email>

Search and compare (WebSearch + WebFetch as needed):
1) GitHub topics: <erp>, <inventory>, <saas-boilerplate>, etc. — top 5 active repos
2) Category SaaS APIs: list 3 vendors with pricing model and data residency notes
3) OSS satellites that can run beside our Postgres SoR (not replace it)

For each candidate fill:
- Stars / last commit / open issues signal
- License (flag AGPL/GPL/BSL)
- Stack fit to our monorepo languages
- Customization cost (days): adapter vs rewrite
- Recommendation: Buy | Integrate | Fork | Build | Skip

End with a single recommended path and the thinnest adapter sketch (files/interfaces only).
```

### 3.5 Copy-paste: mid-feature “are we inventing again?”

```text
Stop implementation. Diff the current approach against Adopt-First.

Questions:
1) What did we invent that already exists as SaaS, OSS, skill, or in-repo package?
2) Is our SoR still singular?
3) What can we delete or replace with an adapter this PR?

Return a punch-list; do not continue coding until I approve the revised approach.
```

### 3.6 Discovery note template (paste into plan / PR)

```markdown
## Discovery note — <capability>

- Date / agent:
- In-repo reuse:
- Skills considered:
- MCP considered:
- SaaS / API options:
- OSS options:
- License / AGPL risks:
- Decision: Buy | Integrate | Fork | Build
- Adapter boundary (what we own vs vendor):
- Explicit non-goals:
- Follow-up ADR path (if needed): docs/decisions/<date>-<slug>.md
```

---

## 4. Capability catalog for a full ERP/SaaS

Use this as a **menu**. For each row: run the ritual, then pick Buy/Integrate/Fork/Build. Examples are illustrative — always re-check licenses and activity on the day you adopt.

### 4.1 Identity, auth, tenancy

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| AuthN (email, OAuth, magic link) | Clerk, Auth0, WorkOS, Supabase Auth, Firebase Auth | Keycloak, Authentik, Ory | Exotic device-bound auth; regulated offline |
| Org / B2B SSO | WorkOS, Auth0 Organizations | Keycloak | Deep custom IdP mapping |
| AuthZ / RBAC / ABAC | Permit.io, Oso Cloud | Casbin, OpenFGA, Cedar | Domain policies that *are* the product |
| Multi-tenant isolation | — | RLS patterns, schema-per-tenant guides | Your tenancy model is the moat |

### 4.2 Billing, payments, tax documents

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| Card / global PSP | Stripe, Adyen, PayPal | — | — |
| Regional / mobile money | Paystack, Flutterwave, local PSPs (e.g. Paynow) | — | Settlement rules unique to you |
| Subscriptions / entitlements | Stripe Billing, Chargebee, Lago (OSS+cloud) | Kill Bill, Lago self-host | Complex hybrid B2B contracts |
| Invoicing PDFs | Stripe Invoicing, QuickBooks API | Invoice generators (MIT libs) | Fiscalisation mandates (**respect exclusions**) |

**Exclusions reminder:** some products forbid tax-authority fiscal device integrations or payroll-tax engines — do not “discover” those in.

### 4.3 Finance & accounting

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| General ledger | QuickBooks/Xero APIs, Pilot | BigCapital, Akaunting, ERPNext *as satellite only* | Append-only multi-currency ledger is core IP |
| AP/AR workflows | Bill.com, Melio | Module slices from Tryton/Dolibarr | Distributor credit, core charges, quarantine returns |
| FX rates | Open Exchange Rates, ECB feeds | — | Official daily rate authority is you |

Prefer **libraries + your schema** over adopting a full second ERP for GL.

### 4.4 Inventory, warehouse, procurement

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| WMS | ShipBob, Fulfil | Odoo Inventory *patterns*, custom on Postgres | Spare-parts fitment + multi-warehouse quirks |
| Barcode / QR | Hardware vendors | ZXing, ML Kit, vision-camera plugins | Must stay Bridge-First on device |
| Procurement / RFQ | — | ERPNext/Odoo ideas; your tables | Approval graphs tied to your RBAC |
| Stock ledger | — | — | Usually **Build** — correctness is trust |

### 4.5 CRM, sales, POS, commerce

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| CRM | HubSpot, Attio, Twenty (OSS) | Twenty, EspoCRM | Parts-counter CRM tightly bound to stock |
| Headless commerce | Shopify, Medusa Cloud | Medusa, Saleor (check license), Vendure (GPL care) | POS already exists; avoid second catalog |
| POS | Square, Lightspeed | — | Shop-floor + bridges + offline are differentiators |

### 4.6 HR & payroll

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| HRIS | BambooHR, Factorial, Folk | Frappe HR, OrangeHRM | Simple attendance only |
| Payroll / statutory | Local payroll vendors | — | **Often Build-avoid:** buy local compliance; or gross-pay-only if tax filing is excluded |

### 4.7 Search, recommendations, data

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| Product search | Algolia, Typesense Cloud | Meilisearch CE, Typesense, OpenSearch, pgvector | Ranking IP is the product |
| Recommendations | — | Gorse, LightFM-style services | Cold-start domain events only you have |
| ETL / pipelines | Fivetran, Airbyte Cloud | Airbyte, dlt, custom Python | Scraping proprietary catalogs |
| Warehouse / analytics DB | BigQuery, Snowflake, MotherDuck | ClickHouse, DuckDB | — |

### 4.8 Notifications, messaging, docs

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| Transactional email | Resend, Postmark, SES | Postal, Mailpit (dev) | — |
| SMS | Twilio, Africa’s Talking, local | — | — |
| Push | OneSignal, FCM/APNs | — | — |
| WhatsApp / chatbots | Meta Cloud API vendors | — | Domain “parts finder” bot logic |
| Internal wiki / notices | Notion, Outline Cloud | Outline (BSL care), Wiki.js (AGPL care) | Tiny notice-board CRUD |

### 4.9 Analytics, flags, queues, observability

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| Product analytics | PostHog Cloud, Mixpanel | PostHog self-host, Plausible, Umami | — |
| Feature flags | LaunchDarkly, PostHog, Statsig | Unleash, Flagsmith, GrowthBook | — |
| Queues / jobs | Inngest, Trigger.dev, SQS | BullMQ, River, Celery, pg-boss | — |
| Errors / APM | Sentry, Datadog | GlitchTip | — |
| Logs | Axiom, Datadog | Loki, OpenSearch | — |

### 4.10 Admin UI, design systems, boilerplates

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| Admin CRUD | Retool, Appsmith, Forest | Refine, react-admin, AdminJS | Staff IA is product-critical |
| SaaS boilerplate | MakerKit, Supastarter, Ship-family kits | Open SaaS (Wasp), create-t3-turbo | You already have a monorepo SoR |
| Design / motion skills | — | ui-ux-pro-max, Emil Kowalski skills | Brand system is owned |

### 4.11 Mobile, hardware, logistics

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| Customer apps | — | Expo/RN templates; native scaffolds | Offline POS, bridges |
| GPS fleet | Samsara, etc. | **Traccar** | Dispatch UX |
| Routing / ETA | Google Routes, Mapbox | **OSRM**, Valhalla | Map data ops willingness |
| Printers / scanners | Vendor SDKs | ZXing, ESC/POS libs | **Bridge modules** mandatory in Bridge-First orgs |

### 4.12 Integrations & automation

| Need | Buy / SaaS | Integrate OSS | Build when |
|------|------------|---------------|------------|
| iPaaS / workflows | Zapier, Make, n8n Cloud | **n8n**, Temporal, Windmill | — |
| MCP for agents | Official vendor MCPs | Community MCP (allowlist) | Internal MCP for your SoR |
| Partner APIs | — | — | Thin anti-corruption adapters |

### 4.13 Full “ERP suite” products (special caution)

| Product | Typical license | Strategy |
|---------|-----------------|----------|
| ERPNext / Frappe | GPLv3 | **Patterns/satellites only** if you already have a SoR; do not mid-project rebase |
| Odoo | Community vs Enterprise | Same — module ideas, not fork-as-core unless greenfield chose it day 0 |
| Dolibarr, Tryton, OFBiz | Mixed | Mine workflows; rarely absorb whole suite into a TS/Kotlin monorepo |
| Medusa / Bagisto | MIT (Bagisto PHP) | Commerce front **if** gap exists; never dual SoR |

---

## 5. Execution playbook (multi-agent vs single)

### 5.1 Default pipeline (serious repos)

```text
/manager  →  /planner  →  @one_coding_lane  →  /security-reviewer  →  /verifier
```

| Phase | Actor | Writes |
|-------|-------|--------|
| Sequence / kill scope | Manager | Nothing (or checklist only) |
| Discovery + design | Planner (+ adopt-first ritual) | `docs/plans/…` |
| Implement | **One** lane agent | Code in lane paths only |
| Security | Security reviewer / RLS auditor | Findings (readonly) |
| Prove | Verifier | Test runs + exclusion scans |

**Do not** load every role every turn — that burns tokens without raising quality. Specialists run **once per phase**.

### 5.2 When a single agent is enough

- Typo / copy / one-component fix
- Document-only edits
- Narrow bug with known file
- Dependency bump with existing pattern

Still run adopt-first **lightly**: grep in-repo before adding a package.

### 5.3 When multi-agent is mandatory

- New module spanning DB + API + ≥2 clients
- Auth, payments, RLS, bridges, public APIs
- Anything touching ledger immutability or multi-currency
- Parallel platform work (use worktrees — one lane per worktree)

### 5.4 Monorepo lane pattern

Inspired by boundary tools and nested `AGENTS.md` practice:

1. Map directories → named agents in `rufler.yaml` / `AGENTS.md`.
2. Coding agents may **read** shared packages; they **write** only in-lane.
3. Cross-cutting changes (shared money math, authz) go through an explicit owner (`packages/shared/`, backend lane).
4. Hardware → dedicated bridge agent — never “quick WebView scan.”

### 5.5 Plan Mode vs Agent Mode

- **Plan Mode** for epics: force discovery note + file list + adapter boundaries before edits.
- **Agent Mode** after plan acceptance: implement the thinnest slice.
- Save plans **in-repo** (`docs/plans/`) so Cloud Agents and future chats share memory.

### 5.6 Do not duplicate orchestration

Skip secondary multi-agent frameworks (CrewAI-as-IDE-orchestrator, “omni routers” that reimplement manager/lanes) unless they run **outside** coding (batch research) and never fight `AGENTS.md`.

---

## 6. How to evaluate a GitHub repo

Score candidates during discovery. Reject early on license or SoR conflict.

### 6.1 Rubric (1–5 each; weight as you like)

| Criterion | What good looks like | Red flags |
|-----------|----------------------|-----------|
| **License** | MIT, Apache-2.0, BSD | AGPL if you embed/modify as SaaS; GPL if you distribute forks; BSL “source available”; no license |
| **Activity** | Commits ≤90 days; releases; responsive issues | Years stale; drive-by README |
| **Adoption** | Stars + real dependents + production stories | Star count alone; empty discussions |
| **Stack fit** | Same languages as monorepo **or** clean HTTP API | Forces second runtime (e.g. PHP in a TS shop) without huge ROI |
| **Docs / API** | OpenAPI, docker-compose, migration story | “Edit our core” as the only extension path |
| **Customization cost** | Adapter in days | Must fork deeply to change one workflow |
| **Ops cost** | One compose service + healthcheck | Multi-node consensus, custom JVM tuning day 1 |
| **Security** | Clear auth model; no default secrets | RCE history; unpinned `@latest` installs |
| **SoR conflict** | Reads/writes through **your** API | Wants to own customers, stock, and GL |

### 6.2 AGPL / GPL / BSL traps (not legal advice)

- **AGPL** (e.g. many Metabase/Wiki.js/Skyvern-class tools): fine as **internal** isolated services for some teams; risky if you modify and offer as network service without compliance plan. Prefer Apache/MIT alternatives (e.g. Superset vs Metabase) when shipping commercial SaaS.
- **GPLv3** (ERPNext, many suites): internal self-host often OK; shipping modified code to customers needs a compliance plan. Using as **ideas** is fine; absorbing as SoR is a company-level decision.
- **BSL / SSPL / “source available”**: read the production clause — may block offering the software as a competing hosted service.

### 6.3 Stars are a weak signal

Prefer: recent releases, CI green, security policy, number of **production** references, and whether the API matches your adapter plan. A 2k★ focused library beats a 40k★ monolith you must gut.

### 6.4 Decision output

Every evaluation ends with one line:

> **Verdict:** Buy | Integrate | Fork | Build | Skip — because \<one sentence\>.

---

## 7. Wiring satellites without replacing the system of record

### 7.1 Satellite pattern

```text
[ Clients: web / mobile / POS ]
            │
            ▼
[ Your API / RLS / RPC ]  ◄── system of record (Postgres + Auth)
            │
            ├── sync / events ──► [ Search / Recs / GPS / BI / Queue workers ]
            └── webhooks ◄────── [ PSP / Email / SMS / iPaaS ]
```

Satellites may **fail** without corrupting money or stock. Degrade gracefully (e.g. fall back to Postgres FTS if Meilisearch is down).

### 7.2 Docker / compose practice

- One compose file (or profiled services) under `infra/` or repo root
- Profiles per concern: `search`, `gps`, `routing`, `bi`
- Document env vars in README; never commit real keys
- Pin image digests or minor versions
- Healthchecks + named volumes
- Local ports bound to `127.0.0.1` where possible

### 7.3 Thin adapters (what “customize” means)

Own a small module that:

1. Maps **your** IDs ↔ satellite IDs  
2. Translates domain events → satellite documents/commands  
3. Enforces authz using **your** roles  
4. Hides vendor SDK quirks behind stable interfaces in `packages/shared` or `packages/<vendor>-adapter`

Do **not** sprinkle raw vendor clients across apps.

### 7.4 Sync strategies

| Strategy | Use when |
|----------|----------|
| Dual-write in API | Low volume; strong consistency needs |
| Outbox / queue worker | Reliable eventual sync |
| Cron reindex | Catalog-like data; OK with lag |
| Read replica → BI | Analytics; no write path |

### 7.5 Feature-flag the cutover

Ship adapter behind a flag (PostHog/Unleash/GrowthBook or config table). Dual-read until quality wins, then remove the legacy path deliberately (ADR).

---

## 8. Anti-patterns

| Anti-pattern | Why it hurts | Do this instead |
|--------------|--------------|-----------------|
| **Rewrite onto ERPNext/Odoo mid-project** | Burns SoR, lanes, and mobile apps | Satellite/library adoption only |
| **Auto-install every skill** | Context pollution; conflicting advice | Curate; explicit-invoke heavy packs |
| **Enable every MCP from awesome-lists** | Token burn; huge blast radius | Allowlist 5–10; read-only first |
| **Browser GPS/QR/printer “just for now”** | Violates Bridge-First; security theater | Native `bridges/` only |
| **Second orchestration stack** (CrewAI managing Cursor lanes) | Dual managers, drift | One pipeline in `AGENTS.md` |
| **Invent auth/billing/email** | Weeks lost; subtle security bugs | Buy commodity |
| **AGPL embed without counsel** | License surprise at customer ship | Prefer permissive BI/search; isolate AGPL |
| **Cards-everywhere admin UI** | Slow ERP UX; agent bikeshedding | Follow product design system; invoke UI skills explicitly |
| **PDF toolkit as mandate** | Someone else’s SoR choice | Treat PDFs as **menus**, not architecture |
| **Skipping discovery because “AI is fast”** | Fast wrong | Ritual is cheaper than rewrite |
| **Dual system of record** | Inventory/finance drift | One SoR; satellites are projections |
| **Ponytail ultra during greenfield scaffold** | Agents refuse necessary structure | Lite / requestable; allow scaffolds when planned |
| **Fiscal/payroll-tax “helpful” modules** | Hard exclusion violations | Reject at discovery |

---

## 9. Checklists

### 9.1 New greenfield SaaS / ERP

- [ ] Choose **system of record** deliberately (do not default to “full ERP suite”)
- [ ] Write **hard exclusions** and **lanes** into `AGENTS.md` before feature coding
- [ ] Add short always-on rules: session discipline + **adopt-first**
- [ ] Create `docs/decisions/` and `docs/plans/`
- [ ] Install minimal skills (token discipline + domain); optional `find-skills`
- [ ] Curate MCP allowlist (GitHub, DB/Supabase read-only, docs, Playwright as needed)
- [ ] Pick commodity Buy list: auth, email, errors, flags, analytics, hosting
- [ ] Sketch satellite compose profiles (search/GPS/BI) — wire only with ROI
- [ ] Define money rules (currency fields, ledger immutability) up front
- [ ] Seed CI: lint, tests, exclusion greps, RLS checks
- [ ] First epic uses full discovery ritual and records an ADR

### 9.2 New module in an existing ERP

- [ ] Paste §3.2 manager prompt with module name
- [ ] Search `docs/decisions/` + similar modules for reuse
- [ ] Run skills.sh / `npx skills find` + awesome-mcp pass
- [ ] Score ≥3 external options with §6 rubric
- [ ] Decide Buy/Integrate/Fork/Build; write Discovery note
- [ ] `/planner` → plan path with adapter boundaries
- [ ] One coding lane; shared logic in shared packages
- [ ] Migrations include RLS in the **same** file when DB changes
- [ ] `/security-reviewer` (and RLS auditor if schema) → `/verifier`
- [ ] Update IMPLEMENTATION / tooling docs if a satellite was added

### 9.3 Weekly tooling refresh (30–60 minutes)

- [ ] `npx skills update` (review changelogs; don’t blind-update critical skills)
- [ ] Skim skills.sh leaderboard for **gaps you already felt this week**
- [ ] Skim awesome-mcp / Glama for **one** candidate; evaluate or skip
- [ ] Confirm MCP allowlist still least-privilege; rotate any exposed secrets
- [ ] Re-read hard exclusions with any new skill/MCP proposal
- [ ] Prune unused MCP servers and global skills (token hygiene)
- [ ] Check satellite image updates / CVEs for compose services you actually run
- [ ] Note decisions in `docs/decisions/` if tooling posture changed

---

## 10. Appendix: GTR case study

GTR illustrates adopt-first on a **real** multi-app ERP. Future products should copy the *rituals*, not necessarily the same vendor picks.

### 10.1 System of record

| Layer | Choice |
|-------|--------|
| SoR | Supabase (Postgres + RLS + RPC + Auth + Storage + Realtime + Edge) |
| Web | Next.js (`apps/web`) |
| Mobile | Android management / customer / delivery + iOS customer |
| Hardware | `bridges/` only (Bridge-First) |
| Catalog ingest | `data-pipeline/` (Python) |

Hard exclusions: **no ZIMRA/FDMS/fiscalisation**; **no payroll tax** (gross pay + manual deductions only).

### 10.2 Agent execution model

```text
/manager → /planner → @web_agent | @backend_agent | @management_app_agent | …
         → /security-reviewer → /verifier
```

Lanes live in `rufler.yaml` / `AGENTS.md`. Hardware → `@hardware_mobile_agent`. Migrations → `/supabase-rls-auditor`.

### 10.3 Cursor tooling adopted

| Piece | Role |
|-------|------|
| `.cursor/rules/adopt-first.mdc` | Always-on discovery bias |
| `.cursor/rules/session_discipline.mdc` | Token/lane discipline |
| `.cursor/rules/ponytail.mdc` | Agent-requestable YAGNI (lite) |
| Project `.cursor/skills/` | Domain + ui-ux-pro-max + token-discipline |
| Emil Kowalski skills (global) | Motion polish — explicit-invoke after brand |
| MCP example allowlist | GitHub, Supabase, Postgres, Context7, Playwright, Sentry, optional n8n |
| Satellites compose | Meilisearch CE; optional Traccar; OSRM stub |

### 10.4 Buy / Integrate map (compressed)

| Capability | GTR posture |
|------------|-------------|
| Auth | **Keep** Supabase Auth (+ OTP Edge) — skip Clerk |
| Payments | **Regional PSP** (ContiPay/Paynow); Stripe not primary |
| Email / SMS | Resend-shaped Edge + SMS providers |
| Search today | Postgres FTS; **Integrate** Meilisearch CE when ranking/typo ROI proven |
| Fleet GPS | **Integrate** Traccar; dispatch UI stays first-party |
| Routing | **Integrate** OSRM when map data ready |
| AuthZ deepening | **Integrate** Casbin later; RLS remains foundation |
| Recs | **Integrate** Gorse (phase-2) |
| BI | Prefer **Superset** (Apache-2.0) over Metabase AGPL |
| Commerce platform | Medusa **only if** true headless gap — not a second ERP |
| Full suites | **Skip** ERPNext/Odoo/Frappe HR as SoR |

### 10.5 What GTR builds first-party (differentiators)

- Append-only multi-currency ledger and distributor finance workflows  
- Parts catalog, VIN/fitment, diagrams/hotspots  
- POS + quarantine returns + core charges  
- Bridge-First scanning/printing/GPS on device  
- Staff IA and role-separated Android apps  

### 10.6 Lessons for larger future ERPs

1. **More modules ⇒ stricter discovery**, not more inventing.  
2. Write exclusions and lanes **before** the twentieth module appears.  
3. Satellites scale sideways; a second SoR does not.  
4. Keep always-on rules tiny; put catalogs in this guide.  
5. Re-run §9.3 weekly or tooling rot will recreate invent-first habits.

---

## Quick reference links

| Resource | URL |
|----------|-----|
| Skills CLI | https://github.com/vercel-labs/skills |
| skills.sh directory | https://skills.sh/ |
| Awesome MCP servers | https://github.com/punkpeye/awesome-mcp-servers |
| Glama MCP | https://glama.ai/mcp/servers |
| MCP best practices (community) | https://github.com/lirantal/awesome-mcp-best-practices |
| Emil motion skills | https://github.com/emilkowalski/skills |
| Ponytail | https://github.com/DietrichGebert/ponytail |
| AGENTS.md ecosystem guidance | Search current “AGENTS.md Guide 2026” / Cursor docs for Rules & Skills |

---

*License notes in this guide are operational heuristics, not legal advice. Re-read each project’s LICENSE before commercial distribution or embedding.*
