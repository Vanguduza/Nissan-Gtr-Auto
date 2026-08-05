# Findings: Cursor + OSS Strategy Research

**Date:** 2026-08-02  
**Plan:** [`2026-08-02-cursor-oss-strategy-research-plan.md`](2026-08-02-cursor-oss-strategy-research-plan.md)  
**Method:** WebSearch + WebFetch against plan checklist (Cursor forum, HN, engineering blogs, awesome lists, GitHub READMEs). Star counts / install figures are **as reported by sources on fetch date** — re-verify before adopting.  
**Policy filter:** OSS-only recommendations; no proprietary buy catalogs.

---

## 1. Checklist status (Phase 2)

### A. Developer strategy
- [x] Cursor AGENTS.md / rules / skills / MCP 2026  
- [x] HN AGENTS.md / Claude Code / Cursor threads  
- [x] Reddit r/cursor — weak direct hits; **Cursor forum** used as primary community source instead  
- [x] r/LocalLLaMA — weak direct hits; covered via LocalLLaMA-adjacent agent/memory discourse in blogs  
- [x] Claude Code vs Cursor 2026 blogs  
- [x] Monorepo + nested AGENTS.md (agents.md spec, guidebooks, Medium)  
- [x] Progressive disclosure / token efficiency (HN)  
- [x] DVC2/cursor-agent-configs + Cursor docs  
- [x] Cursor forum multi-agent / rules vs skills vs hooks  

### B. Trending / catalogs
- [x] Ruflo / claude-flow rename + activity  
- [x] rufler YAML wrappers  
- [x] claude-mem progressive memory  
- [x] awesome-selfhosted (CRM/analytics sections)  
- [x] skills.sh / find-skills popularity  
- [x] Twenty, Medusa, Meilisearch, Keycloak, PostHog ecosystem  
- [x] Metabase AGPL vs Superset Apache  
- [~] awesome-mcp-servers — fetched; page is large (~92k★); treat as **catalog to curate**, not enable-all  
- [~] open-mem — noted as alternate memory MCP family; less primary evidence than claude-mem  

### C. License / SoR
- [x] Superset vs Metabase license implications  
- [x] IdP self-host (Keycloak / Authentik) as OSS path  

---

## 2. Headline trends (5+)

1. **`AGENTS.md` is the cross-tool ambient standard** — stewarded under Linux Foundation AAIF; ~60k+ repos; nested files + nearest-wins for monorepos ([agents.md](https://agents.md/), HN).  
2. **Progressive disclosure beats mega always-on rules** — Skills/MCP load on demand; giant `.mdc` dumps are an anti-pattern ([DVC2/cursor-agent-configs](https://github.com/DVC2/cursor-agent-configs), HN autoresearch thread).  
3. **Hooks > skills for deterministic enforcement** — format/lint/guards must not depend on the model “remembering” ([Cursor forum](https://forum.cursor.com/t/cursor-setup-with-rules-mdc-agents-md-and-hooks/161005)).  
4. **Dual-tool workflows are normal** — Cursor for interactive IDE loop; Claude Code for autonomous multi-file / CI; share one SoT via `AGENTS.md` + `@AGENTS.md` in `CLAUDE.md` (multiple 2026 blogs + HN).  
5. **Episodic memory is a first-class OSS layer** — claude-mem’s search → timeline → get_observations pattern (~10× token savings) is the community reference ([thedotmack/claude-mem](https://github.com/thedotmack/claude-mem)).  
6. **Swarm harnesses are hot but risky to dual-run** — Ruflo (ex Claude Flow) is a major meta-harness for Claude Code; rufler wraps it in YAML; do not stack with a second IDE orchestrator on one dirty tree.  
7. **Skill registries act like package managers** — `find-skills` ~2.7–3M installs; discover before inventing workflows ([skills.sh](https://skills.sh/)).  
8. **Self-host satellites + license literacy** — Twenty (CRM, AGPL), PostHog (MIT), Superset (Apache) vs Metabase (AGPL) shape SaaS embedding choices; prefer permissive for product embed.

---

## 3. Strategy patterns → guide implications

| Pattern | Evidence | Guide implication |
|---------|----------|-------------------|
| Layer: AGENTS.md (ambient) + skills (verbs) + MCP (live data) | buildbetter, morphllm, Spillwave Medium | Structure §3–§5 of guide this way |
| Nested AGENTS.md nearest-wins | agents.md (OpenAI ~88 files example) | Monorepo lane section; path ownership |
| Glob `.mdc` for language/app style | Cursor forum (deanrie) | Prefer scoped rules over always-on essays |
| Hooks for must-run gates | Cursor forum | Checklist: hooks for format/exclusion greps |
| Context rot / stale AGENTS.md | HN agents-lint (ETH Zurich cited) | Treat AGENTS.md as living; CI lint optional |
| Non-obvious > obvious in AGENTS.md | HN “Evaluating AGENTS.md” | Teach: document *why* / gotchas, not “use TypeScript” |
| Worktrees for parallel agents | Claude Code vs Cursor blogs | Multi-agent section |
| Docs-as-memory + ADR search | HN monorepo/docs threads; engineering practice | Discovery ritual step 1 |
| Adopt OSS satellites, keep one SoR | awesome-selfhosted + ERP toolkit pattern | Capability catalog + satellite wiring |
| License first | Superset vs Metabase 2026 guides | Rubric §; flag AGPL |

---

## 4. Dev tooling clarifications (not GTR-only)

| Name | What research says | URL |
|------|--------------------|-----|
| **Claude Code** | Terminal/agent-first Anthropic tool; strong for multi-file autonomy, tests, CI; pair with Cursor IDE | Anthropic docs + 2026 comparison blogs |
| **Cursor Desktop** | AI IDE; rules/skills/MCP/subagents/hooks; best interactive loop | https://cursor.com/docs/rules.md · forum |
| **Ruflo** | Agent meta-harness (renamed from Claude Flow ~Feb 2026); swarms, memory, MCP surface for Claude Code/Codex | https://github.com/ruvnet/ruflo |
| **rufler** | Python wrapper: one `rufler_flow.yml` + `rufler run` over Ruflo CLI (repos: pramodtoraskar/rufler, lib4u/rufler) | GitHub / DEV.to |
| **Project `rufler.yaml` lane file** | Separate concept: path→agent map inspired by swarm naming; **does not require** Ruflo install | Document name collision in guide |
| **claude-mem** | OSS persistent memory; progressive MCP search; Cursor hooks + Claude Code | https://github.com/thedotmack/claude-mem |
| **Goose** | Apache-2.0 local agent under AAIF/Linux Foundation; MCP-native sidecar | aaif-goose / block goose lineage |
| **Agent Skills / skills.sh** | Open skill packages; CLI `npx skills`; progressive load of SKILL.md | https://skills.sh/ · vercel-labs/skills |

**Necessary?** Not for every greenfield. Recommended when multi-session / multi-lane / multi-host work scales.

---

## 5. Trending OSS inventory (capability → candidates)

| Capability | OSS candidates (prefer MIT/Apache/BSD; flag others) | Notes |
|------------|------------------------------------------------------|-------|
| IdP / AuthN | Keycloak (Apache-2.0), Authentik (MIT core), Ory, Authelia | Self-host |
| AuthZ lib | Casbin (+ ports) | In-process |
| CRM | Twenty — **AGPL-3.0** (~53k★, active 2026) | Isolate / counsel |
| Commerce | Medusa (MIT), Saleor (BSD-3) | Satellite; keep stock SoR |
| Search | Meilisearch, Typesense | Compose satellites |
| Product analytics / flags | PostHog (MIT), Unleash, GrowthBook | Self-host |
| Web analytics | Umami; Plausible **AGPL** | Flag AGPL |
| BI | Apache Superset (Apache-2.0); Metabase **AGPL** | Prefer Superset for product embed |
| Billing/metering | Lago, Kill Bill | OSS metering |
| GPS / routing | Traccar, OSRM | Logistics satellites |
| Scheduling / PM | Cal.com, Plane | Check current license |
| ERP suites | ERPNext GPLv3, Dolibarr GPLv3, OFBiz Apache | Patterns or greenfield SoR — not mid-rebase |
| MCP catalog | punkpeye/awesome-mcp-servers (~92k★) | Curate 5–10 |
| Skills discovery | find-skills (~2.7–3M installs) | Audit before install |
| Agent harness | Ruflo MIT family; Goose Apache-2.0 | Optional; one orchestrator |
| Memory | claude-mem; docs/decisions pattern | Progressive search |

---

## 6. Anti-patterns observed

- Always-on ultra rule packs (pre-2026 Cursor configs) — context burn  
- Duplicate AGENTS.md / CLAUDE.md / .cursorrules with conflicting commands  
- LLM-generated AGENTS.md full of obvious facts (HN)  
- Enabling entire MCP awesome-list  
- Dual orchestration (Ruflo swarm + Cursor manager on same dirty tree)  
- Replacing SoR with ERPNext/Odoo mid-project  
- Ignoring AGPL when embedding BI/CRM in commercial SaaS  
- Skills for deterministic format/lint (should be hooks)  
- Stale AGENTS.md paths (context rot; agents-lint narrative)  

---

## 7. Gaps / weak evidence

- Direct Reddit scrape via `site:reddit.com/r/cursor` returned empty in this run — used Cursor forum + HN + blogs instead.  
- Exact live star counts for every satellite not re-queried individually; Twenty AGPL + stargazers from awesome-selfhosted-data (2026-07).  
- Ruflo self-audit claims (tools wired vs marketing) appear in third-party writeups — guide should say “evaluate maturity; don’t dual-run.”  
- Copilot Workspace-specific workflows less documented than Cursor/Claude Code in this pass — treat as “same AGENTS.md portability.”  

---

## 8. Guide rewrite brief (Phase 3 instructions)

Rewrite `docs/CURSOR_ERP_SAAS_SETUP_GUIDE.md` to:

1. Lead with **OSS discover → integrate → fork → invent** (explicitly **no Buy column**).  
2. Open with **evidence-backed developer strategies** (§2 findings), citing agents.md, Cursor forum, HN, DVC2 — not GTR paths.  
3. Pre-flight: AGENTS.md nested, scoped mdc, skills progressive, MCP allowlist, hooks for gates.  
4. Dedicated tooling chapter: Claude Code, Ruflo vs rufler vs lane YAML, Goose, claude-mem — **general**.  
5. Discovery ritual + prompts forcing OSS search + memory/lane checks.  
6. Capability catalog = §5 inventory only (licenses flagged).  
7. Multi-agent: nested AGENTS / lanes / worktrees; one orchestrator.  
8. Repo evaluation rubric + AGPL traps (Superset vs Metabase as teaching example).  
9. Satellite wiring / one SoR.  
10. Anti-patterns from §6.  
11. Checklists.  
12. **GTR appendix ≤1 page.**  
13. Source map with URLs from this findings doc.

Update `.cursor/rules/adopt-first.mdc`: OSS-only; Integrate/Fork/Build; no proprietary pitches unless user asks; mention memory + lane search.

Mark plan Phase 2–3 complete when done.
