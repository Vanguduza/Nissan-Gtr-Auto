# Cursor + Open-Source Adopt-First Playbook for ERP / SaaS

**Audience:** product owners and AI coding agents building multi-module SaaS/ERP.  
**Goal:** discover and integrate **open source** before inventing; keep one system of record.  
**Evidence base:** [`docs/plans/2026-08-02-cursor-oss-strategy-research-plan.md`](plans/2026-08-02-cursor-oss-strategy-research-plan.md) → [`docs/plans/2026-08-02-cursor-oss-strategy-findings.md`](plans/2026-08-02-cursor-oss-strategy-findings.md) (2026-08-02).  
**Policy:** **OSS only** in recommendations (prefer MIT / Apache-2.0 / BSD; **flag AGPL/GPL/BSL**). No proprietary “buy” catalogs (no Stripe/Clerk/Auth0/Vercel-as-product pitches). Self-hosted OSS and free protocols are in scope. Commercial options only if a human **explicitly** asks.  
**Always-on rule:** [`.cursor/rules/adopt-first.mdc`](../.cursor/rules/adopt-first.mdc).

GTR Auto ERP is a **short appendix** only — copy rituals, not a vendor shopping list.

---

## Table of contents

1. [Philosophy](#1-philosophy-oss-discover--integrate--fork--invent)
2. [What top AI-assisted developers do](#2-what-top-ai-assisted-developers-do-20252026-evidence)
3. [Pre-flight Cursor workspace](#3-pre-flight-cursor-workspace)
4. [Claude Code, Ruflo/rufler, Goose, memory](#4-claude-code-ruflorufler-goose-and-memory)
5. [OSS discovery ritual](#5-mandatory-oss-discovery-ritual)
6. [OSS capability catalog](#6-oss-capability-catalog-for-erp--saas)
7. [Multi-agent execution](#7-multi-agent--lane-execution)
8. [Evaluating GitHub repos](#8-evaluating-github-repos)
9. [Satellite wiring](#9-wiring-oss-satellites-one-system-of-record)
10. [Anti-patterns](#10-anti-patterns)
11. [Checklists](#11-checklists)
12. [Appendix A — GTR (short)](#12-appendix-a--gtr-short-case-study)
13. [Appendix B — sources](#13-appendix-b--source-map)

---

## 1. Philosophy: OSS discover → integrate → fork → invent

Agents default to **scaffolding**. ERP/SaaS then grows a second auth stack, homemade search, and a fake ledger. Invert that:

> Search GitHub, awesome-lists, and skill/MCP registries **before** opening a new package.

```text
Differentiator for OUR product?
├─ YES → Build (or Fork permissive base) — still steal OSS patterns
└─ NO  → Fit self-hostable / library OSS?
          ├─ Satellite beside us → INTEGRATE + thin adapter
          ├─ Library inside us → INTEGRATE + wrapper
          ├─ Need durable control → FORK
          └─ No fit after honest search → BUILD minimum slice
```

| Path | Meaning |
|------|---------|
| **Integrate** | Compose service or dependency; **your** DB stays SoR |
| **Fork** | Own an MIT/Apache/BSD tree long-term |
| **Build** | Domain logic, hard exclusions, hardware bridges |

There is **no Buy path** in this playbook.

**Customize, don’t absorb:** Medusa/Twenty/Meilisearch may index or serve UX; customers, stock, and money stay in one SoR (usually Postgres + Auth + policies). Dual SoR is the failure mode.

---

## 2. What top AI-assisted developers do (2025–2026 evidence)

Synthesized from [agents.md](https://agents.md/), [Cursor docs/forum](https://forum.cursor.com/t/cursor-setup-with-rules-mdc-agents-md-and-hooks/161005), [DVC2/cursor-agent-configs](https://github.com/DVC2/cursor-agent-configs), HN threads on AGENTS.md / progressive disclosure, and 2026 Claude Code↔Cursor comparisons — **not** from one monorepo’s habits.

### 2.1 Three complementary layers

| Layer | Job | Load when |
|-------|-----|-----------|
| **`AGENTS.md`** | Ambient “how we code” (stack, commands, exclusions) | Session / directory scope |
| **Skills** (`SKILL.md`) | Invokable workflows (“ship a release”, “write ADR”) | On relevance / slash |
| **MCP** | Live tools (DB, GitHub, docs) | Connected servers |

Industry consensus (buildbetter, morphllm, Spillwave): these are **layers, not competitors**. AAIF/Linux Foundation now stewards AGENTS.md alongside MCP and Goose-class agents.

### 2.2 Progressive disclosure

- Always-on text must stay **short**. Giant always-apply `.mdc` packs are a documented anti-pattern ([DVC2 migration notes](https://github.com/DVC2/cursor-agent-configs)).  
- Skills expose ~name+description until triggered; full body loads later.  
- HN practitioners: keep AGENTS.md light, link out to docs; put **non-obvious** gotchas in AGENTS.md — not “we use TypeScript” ([Evaluating AGENTS.md](https://news.ycombinator.com/item?id=47034087)).  
- Stale paths in AGENTS.md cause **context rot** (agents-lint / ICSE 2026 narrative on HN) — treat the file as living docs.

### 2.3 Rules vs skills vs hooks (Cursor forum)

Per Cursor staff guidance on the forum:

| Primitive | Behavior |
|-----------|----------|
| **Rules** | Style/context; model *can* ignore |
| **Skills** | Multi-step workflows; optional |
| **Hooks** | Deterministic scripts on lifecycle events; model **cannot** ignore; no reasoning tokens |

Use **hooks** for format, exclusion greps, prod guards. Use **skills** for occasional playbooks.

### 2.4 Nested AGENTS.md for monorepos

[agents.md](https://agents.md/): place root + per-package files; **nearest wins**. OpenAI’s monorepo is cited with ~88 nested files. Pair with Cursor glob `.mdc` for language-specific style (`**/*.ts`, `**/*.kt`).

### 2.5 Dual-tool workflow is normal

2026 blogs converge: **Cursor** = interactive IDE (inline, diffs, guided agent); **Claude Code** = autonomous multi-file / tests / CI / worktrees. Share one SoT: root `AGENTS.md`, and `CLAUDE.md` containing only `@AGENTS.md` when needed ([HN Claude Code feature thread](https://news.ycombinator.com/item?id=45786738)).

### 2.6 Adopt modules; don’t rebase SoR mid-flight

awesome-selfhosted and ERP suites are **menus**. Teams that swap a shipping Postgres SoR for ERPNext/Odoo mid-project lose apps, policies, and agent lanes. Prefer satellites and libraries unless greenfield **chooses** a suite as SoR.

---

## 3. Pre-flight Cursor workspace

### 3.1 Skeleton

```text
repo/
├── AGENTS.md                 # portable ambient context (+ nested under packages/)
├── lanes.yaml | rufler.yaml  # optional path→agent map (§4.3)
├── .cursor/
│   ├── rules/*.mdc           # scoped + short always-on
│   ├── skills/*/SKILL.md
│   ├── agents/               # subagent personas
│   ├── hooks*                # deterministic gates
│   ├── mcp.json.example
│   └── mcp.json              # gitignored
├── docs/decisions/           # ADRs
├── docs/plans/
└── apps/ | packages/ | infra/
```

### 3.2 AGENTS.md

Plain Markdown ([spec](https://agents.md/)): overview, build/test commands, style, security, PR norms, **hard exclusions**, where decisions live, OSS-first policy. Keep lean; link to ADRs. Update in the same PR as convention changes.

### 3.3 Rules intensity

| Mode | Use |
|------|-----|
| Always-on (short) | Exclusions, adopt-first, session discipline |
| Glob-scoped `.mdc` | Language / app conventions |
| Agent-requestable | YAGNI / deep reviews |

Migrate legacy `.cursorrules` → `AGENTS.md` + `.cursor/rules/`.

### 3.4 Skills

```bash
# https://skills.sh/  — find-skills is the top install (~2.7–3M as of mid-2026 reports)
npx skills find <query>
npx skills add <owner/repo> --skill <name> -a cursor -y
```

Prefer high install counts + reputable owners; **audit license and exclusion conflict** before install. Never `add --all`.

### 3.5 MCP

Catalog: [punkpeye/awesome-mcp-servers](https://github.com/punkpeye/awesome-mcp-servers) (~90k+★). Curate **5–10**, prefer read-only, pin versions, gitignore secrets. Each server = privileged microservice.

### 3.6 Hooks & CI

Hooks enforce what prose cannot. Optional: lint AGENTS.md freshness. Agents commit **only when asked**.

---

## 4. Claude Code, Ruflo/rufler, Goose, and memory

**Not mandatory for every greenfield.** Recommended when work is multi-lane, multi-session, or multi-host.

| Piece | Day-1? | When |
|-------|--------|------|
| `AGENTS.md` + short rules | **Yes** | Always |
| `docs/decisions/` | **Yes** | Always |
| Path lanes YAML | No | ≥2 apps / specialists |
| Episodic memory (claude-mem / ADRs) | No | Multi-week builds |
| Claude Code | No | Autonomy / CI / worktrees |
| Full Ruflo | No | Claude Code–primary swarms |
| Goose | No | Apache-2.0 local/CI agent |

### 4.1 Name map (research clarification)

| Name | Meaning | URL |
|------|---------|-----|
| **[Ruflo](https://github.com/ruvnet/ruflo)** | Agent **meta-harness** (ex Claude Flow); swarms, plugins, MCP for Claude Code/Codex | ruvnet/ruflo |
| **[rufler](https://github.com/pramodtoraskar/rufler)** (also lib4u/rufler) | Python wrapper: `rufler_flow.yml` + `rufler run` over Ruflo | “Docker Compose for swarms” |
| **Lane file** (`rufler.yaml` / `lanes.yaml`) | Path-routed agent ownership for Cursor/`AGENTS.md` prompts | Project-local — **no Ruflo required** |

Community shorthand “ruflo” ≠ “our lane YAML.” Document which you mean. **Do not** run Ruflo orchestration and Cursor’s manager pipeline on the **same dirty tree**.

### 4.2 Claude Code vs Cursor

| | Cursor | Claude Code |
|--|--------|-------------|
| Strength | Inline edits, visual diffs, guided Agent | Autonomous multi-file, shell loops, CI/headless |
| Config | AGENTS.md, `.cursor/rules`, skills, MCP, hooks | CLAUDE.md / `@AGENTS.md`, skills, optional Ruflo |
| Parallelism | Subagents / cloud agents | Worktrees + Task agents |

### 4.3 Lane YAML without Ruflo

```yaml
version: 1
agents:
  web_agent:
    paths: [apps/web/**, packages/ui/**]
    shared_read: [packages/shared/**, docs/decisions/**, AGENTS.md]
  backend_agent:
    paths: [supabase/**, packages/db-client/**]
    shared_read: [packages/shared/**, docs/decisions/**, AGENTS.md]
```

Prompts name the owner; nested `AGENTS.md` reinforces package boundaries.

### 4.4 Memory — three layers

| Layer | Store | Contents |
|-------|-------|----------|
| Durable policy | Rules / skills / AGENTS.md | Exclusions, SoR, discipline |
| Durable decisions | `docs/decisions/` ADRs | Accepted schema, adopt/skip |
| Episodic | [claude-mem](https://github.com/thedotmack/claude-mem) (OSS) | Session observations |

**Rules ≠ episodic memory.** Claude-mem progressive pattern: `search` → `timeline` → `get_observations` (~10× savings vs dumping history). Works with Cursor hooks and Claude Code. **Docs-as-memory** alone is valid: force ADR search before inventing tables.

### 4.5 Copy-paste — memory + lane

```text
Before app code:
1) State lane from lanes/rufler.yaml or AGENTS.md.
2) Search docs/decisions/; quote ADR titles.
3) If memory tools exist: search → timeline → get_observations only.
4) Name ≥2 OSS options considered for any commodity capability.
Stop if an ADR conflicts.
```

---

## 5. Mandatory OSS discovery ritual

**When:** new module, epic, or non-trivial feature.  
**Output:** Discovery note — Integrate / Fork / Build / Skip + license + SoR impact.

### 5.1 Order

1. In-repo ADRs + plans + progressive memory  
2. Project skills → skills.sh / `npx skills find`  
3. MCP catalog (ops only)  
4. OSS market — GitHub topics, [awesome-selfhosted](https://github.com/awesome-selfhosted/awesome-selfhosted), domain lists  
5. Score (§8)  
6. Decide Integrate / Fork / Build (**never default Buy**)  
7. Scaffold adapters only after decision  

### 5.2 Epic kickoff prompt

```text
Epic: <NAME>
Hard constraints: <exclusions, SoR, accepted licenses>

Before coding:
1) OSS ritual in docs/CURSOR_ERP_SAAS_SETUP_GUIDE.md §5.
2) Search docs/decisions/, skills.sh, awesome-mcp-servers, awesome-selfhosted,
   GitHub topics: <list>.
3) ≥3 OSS options with licenses; verdict Integrate|Fork|Build|Skip.
   Do NOT pitch proprietary SaaS unless I explicitly ask.
4) Plan in docs/plans/ with adapter boundaries.
5) One coding lane → security → verify.
```

### 5.3 Discovery note template

```markdown
## Discovery note — <capability>
| Option | License | Path | SoR impact | URL |
|--------|---------|------|------------|-----|
| … | … | Integrate/Fork/Build/Skip | … | … |

### Decision
### Adapter boundary
### Risks (license, dual-write, exclusions)
```

---

## 6. OSS capability catalog for ERP / SaaS

Menu only — re-check license and activity on adoption day. **No commercial columns.**

### 6.1 Identity & AuthZ

| Need | Integrate | Build when |
|------|-----------|------------|
| OIDC/SAML IdP | Keycloak (Apache-2.0), Authentik (MIT core), Ory, Authelia | Exotic offline/device-bound |
| App AuthZ | Casbin (+ language ports) | Domain entitlement graphs |

### 6.2 Billing & documents

| Need | Integrate | Notes |
|------|-----------|-------|
| Usage/subscriptions | Lago, Kill Bill | Self-host metering |
| PDFs / invoices | Permissive PDF libs; self-host invoice apps (verify license) | Respect fiscal exclusions |
| Card capture | Own thin adapters to gateways you choose — this guide does **not** recommend paid PSPs | Don’t invent card vaults |

### 6.3 Finance & BI

| Need | Integrate | License watch |
|------|-----------|---------------|
| Ledger ideas | Study ERPNext/Dolibarr/OFBiz — prefer patterns | Often **Build** immutability/multi-currency |
| BI | **Apache Superset** (Apache-2.0) | Prefer over Metabase (**AGPL**) for product embed |

### 6.4 Inventory & warehouse

Stock in **your** SoR. Use suite modules as patterns; quarantine/lot rules often first-party.

### 6.5 CRM, commerce, scheduling

| Need | Integrate | License |
|------|-----------|---------|
| CRM | [Twenty](https://github.com/twentyhq/twenty) | **AGPL-3.0** — isolate / counsel |
| Headless commerce | Medusa (MIT), Saleor (BSD-3) | Keep money/stock in SoR |
| Scheduling / PM | Cal.com, Plane | Verify current license |

### 6.6 HR / payroll

Thin attendance/gross-pay on your SoR is common. Honor product **hard exclusions** (e.g. no tax engines where forbidden).

### 6.7 Search & data

Meilisearch, Typesense, OpenSearch; Gorse for recs; open public APIs (e.g. NHTSA vPIC) via adapters.

### 6.8 Notifications

Self-host mail (Postal, Mailu, etc.) or SMTP you control; Matrix/Mattermost for ops chat (check licenses).

### 6.9 Analytics, flags, queues, obs

| Need | Integrate | License |
|------|-----------|---------|
| Product analytics/flags | PostHog (MIT), Unleash, GrowthBook | Prefer MIT/Apache |
| Web analytics | Umami; Plausible **AGPL** | Flag AGPL |
| Queues | Redis/BullMQ, RabbitMQ, NATS | — |
| Errors/metrics | GlitchTip, Prometheus/Grafana | — |

### 6.10 Admin UI & design skills

OSS admin kits (verify license); design skills from skills.sh — **explicit-invoke** only.

### 6.11 Mobile, hardware, logistics

| Need | Integrate / build |
|------|-------------------|
| Fleet GPS | Traccar |
| Routing | OSRM, Valhalla |
| Device QR/printer/biometric/GPS | **Native bridges** — never browser/WebView shortcuts |

### 6.12 Automation

n8n (fair-code — read license), Activepieces, Node-RED, Playwright. Avoid AGPL vision-RPA inside product paths unless isolated.

### 6.13 Full ERP suites (caution)

| Suite | Typical license | Role |
|-------|-----------------|------|
| ERPNext | GPLv3 | Ideas or deliberate greenfield SoR |
| Dolibarr | GPLv3 | SME reference |
| OFBiz | Apache-2.0 | Heavy framework |
| Odoo | Verify edition | Same mid-project rebase caution |

---

## 7. Multi-agent / lane execution

```text
Orchestrator → Planner (docs/plans) → One coding lane → Security review → Verifier
```

- Single agent: typos, one-file bugs, docs.  
- Multi-agent: DB + API + ≥2 clients; auth; money; parallel platforms (**one lane per worktree**).  
- Nested AGENTS.md + lane YAML prevent cross-app edits.  
- **One** orchestration story — skip CrewAI-as-IDE-manager; skip Ruflo + Cursor manager dual-run.

---

## 8. Evaluating GitHub repos

| Criterion | Good | Red flag |
|-----------|------|----------|
| License | MIT, Apache-2.0, BSD | None; AGPL embed without plan; BSL production clauses |
| Activity | Releases / commits ≤90d | Years stale |
| Fit | Same stack or clean HTTP API | Second runtime without ROI |
| SoR | Adapter through your API | Wants to own customers + GL |
| Ops | Compose + healthcheck | Cluster day 1 |

**Teaching example:** Metabase = AGPL (+ many embed features behind commercial tiers in practice); Superset = Apache-2.0 for engineering-led self-host embed ([2026 comparisons](https://ossalt.com/guides/metabase-vs-apache-superset-2026)).

> **Verdict:** Integrate | Fork | Build | Skip — because \<one sentence\>.

---

## 9. Wiring OSS satellites (one system of record)

```text
Clients → Your API / policies / SoR
            ├─ events → Search / Recs / GPS / BI / Queues
            └─ webhooks ← Gateways / Mail / iPaaS you control
```

Compose profiles (`search`, `gps`, `bi`, `idp`); pin images; `127.0.0.1` binds; thin adapters own ID maps and authz. Dual-read behind flags; ADR the cutover.

---

## 10. Anti-patterns

| Don’t | Do |
|-------|----|
| Mega always-on rules | Progressive AGENTS + scoped mdc + skills |
| Conflicting AGENTS.md / CLAUDE.md / .cursorrules | One SoT + `@AGENTS.md` |
| Obvious LLM-generated AGENTS.md | Non-obvious gotchas only |
| Enable all MCP servers | Allowlist 5–10 |
| Dual orchestrators | One pipeline |
| Mid-project ERP suite rebase | Satellites / libraries |
| Pitch proprietary SaaS in discovery | OSS unless user asks |
| Skills for format/lint | Hooks |
| Schema without ADR/memory pass | Search first |
| Ignore AGPL on embed | Prefer Apache/MIT or isolate |

---

## 11. Checklists

### 11.1 Greenfield

- [ ] Choose SoR deliberately  
- [ ] AGENTS.md (lean) + exclusions + OSS-first  
- [ ] Optional nested AGENTS.md / lane YAML  
- [ ] adopt-first rule + hooks for hard gates  
- [ ] `docs/decisions/` + `docs/plans/`  
- [ ] Curated skills + MCP allowlist  
- [ ] Satellite compose sketches  
- [ ] Money/currency/ledger rules if applicable  
- [ ] CI: lint, tests, exclusion greps  
- [ ] First epic runs §5 ritual  

### 11.2 New module

- [ ] §5.2 prompt  
- [ ] ADRs + memory + lane  
- [ ] skills.sh + awesome-selfhosted + awesome-mcp  
- [ ] ≥3 OSS options + §8 rubric  
- [ ] Discovery note (**no Buy list**)  
- [ ] One lane → security → verify  

### 11.3 Weekly refresh

- [ ] `npx skills update` (review changelogs)  
- [ ] Skim catalogs for **felt gaps only**  
- [ ] Prune MCP/skills  
- [ ] CVE/images for running satellites  
- [ ] ADR if posture changed  

---

## 12. Appendix A — GTR (short case study)

Illustrative only:

| Topic | Example |
|-------|---------|
| SoR | Supabase (Postgres + RLS + Auth) |
| Lanes | Path agents in `rufler.yaml` + `AGENTS.md` |
| Pipeline | manager → planner → one lane → security → verifier |
| Memory | `docs/decisions/` + optional claude-mem |
| Satellites | Meilisearch, Traccar profiles |
| Exclusions | No ZIMRA/FDMS; no payroll tax; Bridge-First hardware |

Copy **discipline**, not every dependency.

---

## 13. Appendix B — source map

| Topic | URL |
|-------|-----|
| Research plan | `docs/plans/2026-08-02-cursor-oss-strategy-research-plan.md` |
| Findings | `docs/plans/2026-08-02-cursor-oss-strategy-findings.md` |
| AGENTS.md | https://agents.md/ |
| Cursor rules | https://cursor.com/docs/rules.md |
| Cursor forum (rules/skills/hooks) | https://forum.cursor.com/t/cursor-setup-with-rules-mdc-agents-md-and-hooks/161005 |
| Cursor config patterns | https://github.com/DVC2/cursor-agent-configs |
| HN AGENTS.md | https://news.ycombinator.com/item?id=44957443 |
| HN evaluating AGENTS.md | https://news.ycombinator.com/item?id=47034087 |
| HN progressive AGENTS.md | https://news.ycombinator.com/item?id=48299687 |
| Awesome MCP | https://github.com/punkpeye/awesome-mcp-servers |
| Awesome selfhosted | https://github.com/awesome-selfhosted/awesome-selfhosted |
| Skills | https://skills.sh/ · https://github.com/vercel-labs/skills |
| Ruflo | https://github.com/ruvnet/ruflo |
| rufler | https://github.com/pramodtoraskar/rufler |
| claude-mem | https://github.com/thedotmack/claude-mem |
| Goose | https://github.com/aaif-goose/goose |
| Twenty CRM | https://github.com/twentyhq/twenty |
| Medusa / Saleor | https://github.com/medusajs/medusa · https://github.com/saleor/saleor |
| Superset vs Metabase (license) | https://ossalt.com/guides/metabase-vs-apache-superset-2026 |

---

*License notes are operational heuristics, not legal advice. Re-read each LICENSE before embedding or distributing.*
