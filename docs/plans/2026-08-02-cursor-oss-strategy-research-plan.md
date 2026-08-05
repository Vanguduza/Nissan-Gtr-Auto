# Plan: Cursor + OSS Strategy Research (ERP / SaaS playbook)

**Date:** 2026-08-02  
**Status:** Phases 1–3 complete (2026-08-02)  
**Findings:** [`2026-08-02-cursor-oss-strategy-findings.md`](2026-08-02-cursor-oss-strategy-findings.md)  
**Guide:** [`docs/CURSOR_ERP_SAAS_SETUP_GUIDE.md`](../CURSOR_ERP_SAAS_SETUP_GUIDE.md) rewritten from findings  
**Goal:** Produce evidence-backed research, then rewrite `docs/CURSOR_ERP_SAAS_SETUP_GUIDE.md` as a **general** Cursor + open-source adopt-first playbook (not a GTR inventory dump).  
**Hard policy:** **Open source only** in recommendations (MIT / Apache-2.0 / BSD preferred; flag AGPL/GPL/BSL). No Stripe / Clerk / Auth0 / Vercel-as-paid-product / Namecheap pitches. Self-hosted OSS and free protocols OK.  
**Out of scope:** product app code, commits, proprietary SaaS catalogs.

**Deliverables (ordered):**

| Phase | Artifact |
|-------|----------|
| 1 | This plan |
| 2 | Findings → `docs/plans/2026-08-02-cursor-oss-strategy-findings.md` |
| 3 | Rewritten guide + light `adopt-first.mdc` update |

---

## 1. Research themes (what we must learn)

### Theme A — Strategies of top AI-assisted developers

How serious Cursor / Claude Code / Copilot-style users actually structure work:

- Layered config: `AGENTS.md` vs rules vs skills vs MCP vs subagents  
- Progressive disclosure / token discipline (short always-on, heavy on demand)  
- Multi-agent / lane / swarm patterns without dual orchestration  
- Adopt-not-build / “don’t invent auth-search-email” habits  
- Monorepo vs polyrepo with AI agents  
- Docs-as-memory / ADRs before schema invention  
- Hooks, verifiers, deterministic gates vs prose-only rules  

**Source classes (must cite links in findings):**

- Cursor forum / docs / community config repos  
- Hacker News threads on AGENTS.md, Cursor, Claude Code  
- Reddit: `r/cursor`, `r/LocalLLaMA`, related agent threads  
- GitHub Discussions on MCP, skills, agent harnesses  
- Engineering blogs (2025–2026) comparing Cursor vs Claude Code  
- Stack Overflow / collective Q&A on monorepo + AI agents  

### Theme B — Trending / latest OSS (fast-changing)

Prefer **stars + recent commits + 2025/2026 releases**:

- GitHub trending (agents, MCP, self-hosted SaaS, ERP/CRM)  
- Awesome lists updated 2025–2026 (`awesome-selfhosted`, `awesome-mcp-servers`, ERP/CRM awesomes)  
- [skills.sh](https://skills.sh/) popular installs + Agent Skills format  
- New / hot agent frameworks (Goose/AAIF, Ruflo/claude-flow, open harnesses) — evaluate vs “skip duplicate orchestration”  
- Self-hosted SaaS building blocks: IdP, search, CRM, commerce, flags, BI, GPS, queues  
- OSS memory: claude-mem, open-mem, memory MCP servers  

### Theme C — Dev tooling names (research items, not GTR assumptions)

| Item | Research questions |
|------|--------------------|
| **Claude Code** | When teams use it vs Cursor; CLAUDE.md / AGENTS.md portability; headless/CI |
| **Ruflo** | What [ruvnet/ruflo](https://github.com/ruvnet/ruflo) is; swarm topologies; Claude Code coupling |
| **rufler** | What [pramodtoraskar/rufler](https://github.com/pramodtoraskar/rufler) is vs a project `rufler.yaml` lane file |
| **claude-mem** | Progressive disclosure; Cursor hooks; license; alternatives |
| **Other memory** | open-mem, docs/`decisions` patterns, MCP memory servers |

### Theme D — OSS capability map for ERP/SaaS modules

For each capability: ≥2 OSS candidates with license + self-host/lib note. Categories at minimum:

Identity/AuthZ · Billing/metering (OSS) · Finance/BI · Inventory · CRM/Commerce · HR (gross-pay patterns only where relevant) · Search/Recs · Notifications · Analytics/Flags · Queues/Obs · Admin UI kits · Mobile/hardware/GPS · Automation · Full ERP suites (caution)

---

## 2. Explicit research queries / checklist (executor must run)

Mark each done in Findings. Use **WebSearch** broadly, then **WebFetch** for high-signal pages (READMEs, HN threads, awesome list sections).

### A. Developer strategy queries

- [ ] `Cursor AGENTS.md rules skills MCP best practices 2026`  
- [ ] `site:news.ycombinator.com AGENTS.md OR Cursor rules 2025 OR 2026`  
- [ ] `site:reddit.com/r/cursor AGENTS.md OR .cursor/rules OR MCP`  
- [ ] `site:reddit.com/r/LocalLLaMA coding agent Claude Code Cursor 2026`  
- [ ] `Claude Code vs Cursor workflow 2026 engineering blog`  
- [ ] `monorepo AI coding agent Cursor Copilot Stack Overflow`  
- [ ] `adopt open source don't reinvent auth search Meilisearch Keycloak agent`  
- [ ] `DVC2 cursor-agent-configs OR progressive disclosure agent skills`  
- [ ] `Cursor forum multi-agent OR subagents OR worktrees` (or community mirrors)  

### B. Trending repo / catalog queries

- [ ] `GitHub trending AI agents MCP 2026`  
- [ ] `awesome-mcp-servers new servers 2026` + fetch https://github.com/punkpeye/awesome-mcp-servers  
- [ ] `awesome-selfhosted ERP CRM analytics commerce 2026` + fetch list  
- [ ] `skills.sh popular agent skills 2026`  
- [ ] `aaif goose agent Apache MCP`  
- [ ] `ruvnet ruflo swarm Claude Code` + `pramodtoraskar rufler`  
- [ ] `thedotmack claude-mem Cursor hooks progressive disclosure`  
- [ ] `open-mem MCP memory server OR agent memory MCP OSS`  
- [ ] `Medusa Saleor Twenty CRM Plane Meilisearch Lago Kill Bill Casbin Traccar stars 2026`  
- [ ] `frappe erpnext vs self-host satellites architecture 2025 2026`  

### C. License / SoR caution queries

- [ ] `AGPL Metabase vs Apache Superset SaaS embedding`  
- [ ] `ERPNext GPLv3 commercial SaaS compliance self-host`  
- [ ] `Zitadel Authentik Keycloak license 2026 comparison OSS`  

### D. WebFetch priority URLs (always attempt)

- [ ] https://github.com/punkpeye/awesome-mcp-servers  
- [ ] https://github.com/awesome-selfhosted/awesome-selfhosted (relevant sections)  
- [ ] https://github.com/ruvnet/ruflo  
- [ ] https://github.com/thedotmack/claude-mem  
- [ ] https://github.com/DVC2/cursor-agent-configs (or docs/quick-reference)  
- [ ] https://skills.sh/ (or vercel-labs/skills README)  
- [ ] https://github.com/aaif-goose/goose (or block/goose redirect)  
- [ ] At least 2 HN threads + 1 Reddit/SO thread from Theme A hits  

---

## 3. Findings capture format

Write `docs/plans/2026-08-02-cursor-oss-strategy-findings.md` with:

1. **Method** — queries run, date, limits  
2. **Headline trends** (5–8 bullets) — what changed / what elites do  
3. **Strategy patterns table** — pattern → evidence link → guide implication  
4. **Trending OSS inventory** — by capability; stars/activity/license when known  
5. **Dev tooling clarifications** — Claude Code, Ruflo, rufler, memory  
6. **Anti-patterns observed in the wild**  
7. **Gaps / weak evidence** — what we couldn’t verify  
8. **Guide rewrite brief** — section-by-section instructions for Phase 3  

---

## 4. Outline of final guide (after research — Phase 3 target)

Provisional TOC (adjust if findings demand it):

1. Philosophy — OSS discover → integrate → fork → invent (no Buy)  
2. What top AI-assisted developers do (evidence-backed)  
3. Pre-flight Cursor workspace (`AGENTS.md`, rules, skills, MCP, hooks)  
4. Claude Code, Ruflo/rufler, Goose, memory (general landscape)  
5. Mandatory OSS discovery ritual + copy-paste prompts  
6. OSS capability catalog (ERP/SaaS modules; licenses flagged)  
7. Multi-agent / lane execution playbook  
8. Evaluating GitHub repos (stars ≠ fit; license rubric)  
9. Satellite wiring without dual SoR  
10. Anti-patterns  
11. Checklists  
12. Appendix — short GTR case study only  
13. Source map (URLs from findings)  

**Quality bar:** every major recommendation should trace to a finding cite or a dated awesome-list entry. Prefer depth over GTR path dumps. Zero commercial buy columns.

---

## 5. Phase gates

| Gate | Pass criteria |
|------|----------------|
| Phase 1 done | This plan committed to `docs/plans/` (written) |
| Phase 2 done | Findings doc exists; checklist mostly checked; ≥5 headline trends; tooling names clarified with sources |
| Phase 3 start | Only after Phase 2 pass |
| Phase 3 done | Guide rewritten from findings; `adopt-first.mdc` says OSS-only discovery; GTR = short appendix |

---

## 6. Executor notes

- Prefer **WebSearch → WebFetch** for primary sources; don’t invent star counts — say “approx / as of fetch” if uncertain.  
- If Ruflo vs rufler confusion appears in community posts, document both names explicitly.  
- GTR hard exclusions remain in appendix / adopt-first hard stops only — they must not dominate the general guide.  
- No commits unless user asks.
