# Dev Tooling & Skills Research — Nissan GTR Auto ERP

**Date:** 2026-08-02  
**Scope:** research + recommendations only. No product code changes.  
**Audience:** Cursor Desktop workflow for this monorepo (Supabase + Next.js + Android/iOS, agent lanes in `AGENTS.md` / `rufler.yaml`, MCP, skills).  
**Hard exclusions (unchanged):** no ZIMRA; no payroll tax; Bridge-First for QR/printer/biometric/GPS.

---

## Implemented (2026-08-02 follow-through)

| Item | Status | Where |
|------|--------|-------|
| **Ponytail** | Done — agent-requestable **lite** rule | `.cursor/rules/ponytail.mdc` (+ note in `session_discipline.mdc`) |
| **Emil Kowalski** | Done — global install; explicit-invoke | `emil-design-eng`, `review-animations`, `improve-animations` under `~/.cursor/skills/` (skip `apple-design` / `pick-ui-library`) |
| **Awesome MCP shortlist** | Done — curated example (7 servers) | `.cursor/mcp.json.example` (+ local gitignored `.cursor/mcp.json`); docs in `TOOLING_SETUP.md` / guide |
| **Master playbook** | Done | `docs/CURSOR_ERP_SAAS_SETUP_GUIDE.md` |
| **Satellites** | Done — Meilisearch compose + Traccar profile + OSRM stub; Casbin/Gorse Phase-2 docs | `docker-compose.satellites.yml`, `infra/satellites/` |
| Goose / CrewAI / Skyvern / Omni | Unchanged — optional / skip per verdicts below | — |

Manual enable still required: fill MCP secrets, Cursor Settings → MCP, `docker compose … --profile search up -d`.

---

## Executive summary

| Rank | Project | Verdict | Why |
|------|---------|---------|-----|
| 1 | **Awesome MCP servers** | **Integrate** (as catalog) | Highest leverage for existing Cursor MCP setup; discover vetted servers without replacing lanes |
| 2 | **Ponytail** | **Integrate** (Cursor rule, careful) | Reinforces YAGNI / minimal diffs already in `.cursorrules`; MIT; one-file Cursor install |
| 3 | **Goose** | **Optional** | Mature Apache-2.0 agent for CLI/CI recipes; do not replace Cursor Desktop as primary IDE agent |
| 3b | **find-skills** (`npx skills`) | **Optional** | Ecosystem skill discovery CLI + meta-skill; useful gap-fill only — does not replace curated `.cursor/skills/` |
| 3c | **Emil Kowalski skills** (motion / craft) | **Optional** | Complementary to `/ui-ux-pro-max` for animation polish — not a brand/layout system; keep explicit-invoke |
| 4 | **Omni router** (name collision) | **Optional / mostly skip** | **OmniRoute** = optional LLM gateway (security caution); **Omnis** router = skip (duplicates `/manager`); SGLang router = skip |
| 5 | **CrewAI** | **Skip** (coding path) | Mature multi-agent framework that **duplicates** `/manager` → `/planner` → lane → `/security-reviewer` → `/verifier` |
| 6 | **Skyvern** | **Skip** (product); narrow optional | Browser RPA conflicts with Bridge-First for hardware; AGPL; only maybe for brittle supplier-site scraping in `data-pipeline/` |

**Top 3 to adopt now:** Awesome MCP servers (catalog discipline) → Ponytail (scoped Cursor rule) → Goose (optional sidecar for headless recipes).  
**Optional discovery (not adopt-now):** find-skills / `npx skills` when hunting a *generic* skill gap — after checking repo `.cursor/skills/` and `AGENTS.md`.  
**Optional design craft:** Emil Kowalski skills (`emilkowalski/skills`) for *motion* polish after `/ui-ux-pro-max` sets layout/brand — never auto-load; never replace Staff steel/red.  
**Skip for core ERP coding:** CrewAI as a second orchestration stack; Omnis/SGLang “omni router”; Skyvern inside POS/apps/bridges.

---

## Context: what this repo already has

- **Primary IDE:** Cursor Desktop (`docs/LOCAL_DEVELOPMENT.md`, `AGENTS.md`).
- **Orchestration:** on-demand `/manager` → `/planner` → one coding lane → `/security-reviewer` → `/verifier` (`docs/AGENT_TEAM.md`).
- **Token discipline:** `/token-discipline`, session rules, progressive claude-mem search.
- **Skills today:** curated set under `.cursor/skills/` (ERP domain + design suite + token-discipline); no `find-skills` / skills.sh installer in-tree yet.
- **MCP today:** example at `.cursor/mcp.json.example` (n8n-mcp optional); Glama/awesome lists not yet used as a formal discovery process.
- **Hardware:** `bridges/` only — no HTML5/browser QR.

Any external multi-agent product that re-implements “route to specialist squads” competes with lanes already encoded in `rufler.yaml` and burns tokens if run in parallel with Cursor’s own team.

---

## Ranked comparison

| Rank | Tool | What it is | License | Maturity (approx.) | Cursor Desktop fit | GTR verdict |
|------|------|------------|---------|--------------------|--------------------|-------------|
| 1 | [punkpeye/awesome-mcp-servers](https://github.com/punkpeye/awesome-mcp-servers) (+ [glama.ai/mcp](https://glama.ai/mcp)) | Curated MCP server directory | MIT (list) | Very high (~90k★, active, web mirror) | Native — Cursor is an MCP client | **Integrate** |
| 2 | [DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail) | YAGNI / “lazy senior” rules + skills | MIT | Very high (~94k★, 2026) | Copy `.cursor/rules/ponytail.mdc` (rules only; no slash commands) | **Integrate** (agent-requestable preferred) |
| 3 | [aaif-goose/goose](https://github.com/aaif-goose/goose) (ex Block `block/goose`) | Local desktop/CLI/API agent + MCP extensions | Apache-2.0 | High (~52k★, AAIF/Linux Foundation) | Parallel runtime; can call Cursor Agent CLI as provider | **Optional** |
| 3b | [vercel-labs/skills](https://github.com/vercel-labs/skills) (`find-skills` + [skills.sh](https://skills.sh/)) | Open agent-skills CLI (`npx skills`) + meta-skill that teaches agents to search/install | **MIT** (README / GitHub); each *installed* skill has its own license | High (~28k★ CLI; `find-skills` ~2.8M installs on skills.sh) | Native — installs into `.cursor/skills/` or `~/.cursor/skills/` | **Optional** |
| 3c | [emilkowalski/skills](https://github.com/emilkowalski/skills) ([skills.sh](https://skills.sh/emilkowalski/skills)) | Design-engineer skills: motion craft, animation review/audit, Apple motion, UI library pick, prototype switcher | **MIT** (repo LICENSE, © 2026 Emil Kowalski) | High (~24k★; ~488k total installs on skills.sh) | Native via `npx skills add` → `.cursor/skills/` or `~/.cursor/skills/` | **Optional** (motion layer) |
| 4a | [diegosouzapw/OmniRoute](https://github.com/diegosouzapw/OmniRoute) | Local OpenAI-compatible multi-provider gateway | MIT | High stars (~37k★); young project; **security caveats** | Point Cursor chat Base URL at `localhost:20128/v1` (Composer/Tab often stay on Cursor) | **Optional / caution** |
| 4b | [blouargant/omnis](https://github.com/blouargant/omnis) “Omnis router” | Vendor-neutral agent harness with squad routing | MIT | Immature (0★ public; develop branch) | Separate harness, not Cursor-native | **Skip** |
| 4c | SGLang `omni_router` | HTTP load balancer for Omni V1 model workers | (SGLang project) | Infra / serving | Irrelevant to IDE coding | **Skip** |
| 5 | [crewAIInc/crewAI](https://github.com/crewAIInc/crewAI) | Python multi-agent Crews + Flows | MIT (+ commercial AMP) | Mature (~56k★, PyPI 1.x) | Can scaffold via skills; not the IDE | **Skip** for ERP coding lanes |
| 6 | [Skyvern-AI/skyvern](https://github.com/Skyvern-AI/skyvern) | LLM+vision browser automation (Playwright+) | **AGPL-3.0** | Mature OSS + cloud (~22k★) | MCP-ready, but separate stack | **Skip** product; optional pipeline only |

---

## Per-project findings

### 1. Omni router (name collision — three different things)

#### A. Omnis router (`blouargant/omnis`)

- **What:** Go agent harness: mount tools/skills/MCP, define **squads**, default **Omnis router** hands chats to the best squad and re-routes on topic drift.
- **License:** MIT.
- **Maturity:** Early / low adoption (public GitHub shows ~0 stars).
- **vs Cursor:** Parallel product. Conceptually similar to `/manager` + lane agents, but outside Cursor’s rule/skill/MCP surface.
- **GTR fit:** **Skip.** Would fork orchestration away from `AGENTS.md` / `rufler.yaml` / existing subagents. No benefit that outweighs dual-stack confusion.

#### B. OmniRoute (`diegosouzapw/OmniRoute`)

- **What:** Local MIT AI gateway — one OpenAI-compatible endpoint, multi-provider fallback, token compression claims, MCP/A2A exposure. Explicit Cursor setup (`omniroute setup-cursor`); chat Base URL override only (Composer / inline / autocomplete often remain on Cursor’s backend).
- **License:** MIT.
- **Maturity:** Popular and fast-moving; also the subject of public security write-ups (default JWT secrets / key-handling patterns inherited from fork lineage — treat as **untrusted until audited**).
- **GTR fit:** **Optional** for cost/fallback experiments on BYOK chat only. **Do not** put production Supabase service-role or customer PII through an unreviewed local proxy. Prefer Cursor’s native model picker unless gateway ROI is proven.

#### C. SGLang Omni Router

- **What:** Worker-pool HTTP router for Omni V1 inference deployments.
- **GTR fit:** **Skip.** Model-serving infra, not monorepo/ERP development.

---

### 2. Ponytail

- **What:** Rules/skills that force agents through a YAGNI ladder (reuse → stdlib → native → dep → minimal new code) without dropping safety/validation.
- **License:** MIT.
- **Maturity:** Extremely popular (~94k★); strong Cursor adapter (`.cursor/rules/ponytail.mdc`). In Cursor, always-on rules only — `/ponytail-review` etc. need Claude Code/Codex-class hosts.
- **vs Cursor / GTR:** Aligns with “Minimize scope — simplest correct diff wins,” token discipline, and targeted diffs. Risk: if always-on at `ultra`, may under-scaffold greenfield phases (new apps, migrations that legitimately need more structure).
- **Verdict:** **Integrate** as an **agent-requestable** or coding-lane rule (or `lite`/`full`), not blindly global at ultra. Prefer `/ponytail-review`-style diff reviews when using a host that supports commands; otherwise invoke spirit via `/verifier` + existing conventions.
- **Use cases:** Feature PRs that over-abstract; UI wrappers around native controls; “should this package exist?”
- **Conflicts:** None with hard exclusions. Mild tension with deliberate scaffolding phases — keep off during greenfield app bootstrap if agents refuse necessary files.

---

### 3. Awesome MCP servers

- **What:** Community “awesome list” of MCP servers; mirrored/enriched at Glama (scores, scanning).
- **License:** MIT for the list; **each server has its own license/trust model**.
- **Maturity:** De-facto discovery hub (~90k★).
- **vs Cursor:** Direct fit — Cursor already consumes MCP; repo already documents optional n8n-mcp.
- **Verdict:** **Integrate as process**, not as “install everything.”
  - Browse → shortlist → security review (permissions, secrets, network) → add one server to user or project MCP config → document in `docs/TOOLING_SETUP.md`.
  - High-value candidates to *evaluate* (not auto-approved): official Supabase MCP, GitHub, Playwright (web E2E only), docs search — never random browser-QR or tax/fiscal MCPs.
- **Conflicts:** Over-enabling MCP servers increases tool-surface and token use — contradicts token discipline. No ZIMRA/payroll MCP should be added even if listed.

---

### 4. CrewAI

- **What:** Python framework for multi-agent **Crews** and event-driven **Flows**; optional commercial AMP control plane. Skills pack teaches coding agents how to scaffold CrewAI apps.
- **License:** MIT (core); AMP commercial.
- **Maturity:** High; production-oriented ecosystem.
- **vs Cursor / GTR:** Orthogonal runtime. Using CrewAI to “manage” ERP coding recreates `/manager` + `/planner` + lanes with worse IDE integration and no knowledge of `rufler.yaml` exclusions.
- **Verdict:** **Skip** for GTR **development** orchestration.
- **Optional niche (outside coding lanes):** one-off Python research/batch jobs if a future analytics experiment wants autonomous crews — still prefer `@data_pipeline_agent` and existing Python tooling first.
- **Conflicts:** Direct conceptual conflict with `docs/AGENT_TEAM.md` (“on-demand specialists, not five agents every turn”). Installing `crewaiinc/skills` would bias agents toward scaffolding CrewAI projects instead of Supabase/Next/Android work.

---

### 5. Skyvern

- **What:** LLM + computer-vision browser automation on Playwright; workflows, SDK, cloud; MCP-ready.
- **License:** **AGPL-3.0** (network copyleft if you modify and offer as a service; commercial cloud escape hatch).
- **Maturity:** Strong OSS + product (~22k★, frequent releases).
- **vs Bridge-First:** Skyvern automates **browsers**. GTR mandates QR / printer / biometric / GPS via native `bridges/`, not WebView/HTML5. Using Skyvern (or any browser agent) for store POS scanning, warehouse QR, or fiscal-device UIs would violate project law and invite ZIMRA-shaped mistakes.
- **vs Cursor:** Cursor already has IDE browser MCP for **dev verification** of web UI. Skyvern is a separate RPA product for production-style site workflows.
- **Verdict:** **Skip** for apps, POS, bridges, customer flows.
- **Narrow optional:** `@data_pipeline_agent` only — scraping supplier catalogs when CSS/XPath scrapers are too brittle. Even then: keep AGPL out of shipped product binaries; isolate as an internal tool; prefer existing Playwright/pytest in-pipeline if sufficient.
- **Conflicts:** Bridge-First; AGPL embedding risk; overlaps Cursor browser tools for storefront QA.

---

### 6. Goose (Block → AAIF)

- **What:** Native open-source agent (desktop + CLI + API), Rust core, 15+ LLM providers, 70+ MCP extensions, recipes. Can use `GOOSE_PROVIDER=cursor-agent` to drive Cursor’s CLI agent from Goose sessions.
- **License:** Apache-2.0.
- **Maturity:** High; moved to Agentic AI Foundation (Linux Foundation).
- **vs Cursor:** Complementary sidecar, not a drop-in for Cursor Desktop’s monorepo rules/hooks/subagents. Dual agents on the same tree risk conflicting edits and ignored `AGENTS.md` nuances unless Goose is pointed at the same instructions.
- **Verdict:** **Optional.**
  - Good for: headless recipes (e.g. nightly “run verifier checklist”), local BYOK experiments, MCP playground outside the IDE.
  - Bad for: replacing `/manager` pipeline or primary Android/iOS/hardware work that needs Cursor’s path rules and production guards.
- **Conflicts:** Process duplication if both Cursor Agent and Goose edit the repo concurrently. No conflict with hard exclusions if instructions are shared.

---

### 7. find-skills / Skills CLI (Vercel Labs) — skills discovery

> User follow-up (2026-08-02): “what about the repo find skills?”

#### Exact repos / surfaces

| Piece | URL | Role |
|-------|-----|------|
| **Skills CLI** (canonical) | https://github.com/vercel-labs/skills | `npx skills` package manager for Agent Skills; Cursor-supported |
| **find-skills skill** | Bundled in that repo: `skills/find-skills/SKILL.md`; catalog https://skills.sh/vercel-labs/skills/find-skills | Meta-skill: teaches the agent *when/how* to search & install skills |
| **Directory / leaderboard** | https://skills.sh/ | Browse installs, owners, skill pages |
| **Related / forks** | `vercel-labs/add-skill` (older add flow); third-party mirrors (e.g. explainx / random GitHub copies) | Prefer **vercel-labs/skills** + skills.sh over unofficial republishers |
| **Spec context** | Agent Skills open format (SKILL.md); ecosystem also used by Claude Code, Codex, Goose, etc. | Portable skill packages — not Cursor-exclusive |

#### What it does

- **`npx skills find [query]`** — search the skills.sh index (interactive or keyword; aliases `search` / `f` / `s`).
- **`npx skills add <owner/repo[@skill]>`** — install into agent skill dirs (project `.cursor/skills/` or global `~/.cursor/skills/` with `-g`; target `-a cursor`).
- **`find-skills` SKILL.md** — agent trigger on “find a skill for X” / “how do I do X”; prefers skills.sh leaderboard, then CLI search; quality heuristics (install count, owner reputation, stars); offers `npx skills add … -g -y`.

#### License & maturity

- **CLI repo:** MIT (declared on GitHub / README; ~28k★ as of research date).
- **Per-skill licenses vary** — Anthropic and others may ship restrictive skill licenses; audit before vendoring into the monorepo.
- **Maturity:** High ecosystem traction (`find-skills` shows ~2.8M installs on skills.sh); CLI young (created 2026-01) but widely adopted across agents.

#### How to use with Cursor (this monorepo)

```bash
# Optional: install the meta-skill only (global preferred so it does not clutter the ERP tree)
npx skills add vercel-labs/skills --skill find-skills -g -a cursor -y

# Or search without installing the meta-skill
npx skills find supabase
npx skills find "react performance"

# Install a *specific* vetted skill after human review (prefer -g unless the skill is deliberately repo-owned)
npx skills add vercel-labs/agent-skills@react-best-practices -g -a cursor
```

Restart / reopen Agent chat so Cursor re-discovers skills. Invoke via `/find-skills` or natural language (“is there a skill for …”).

#### Does it help GTR?

**Marginally — optional gap-fill, not a core workflow.**

Already in place:

- Curated domain skills under `.cursor/skills/` (`token-discipline`, `accounting-ledger`, `qr-inventory-workflow`, `nissan-fast-parser`, `parts-catalog-ingestion`, `erpnext-feature-parity`, `sdlc-pipeline`, design suite, …).
- Lane routing via `AGENTS.md` / `rufler.yaml` / `/manager` pipeline.
- Explicit invoke + token-discipline rules that **discourage** loading every skill every turn.

find-skills **helps** when hunting a *generic* upstream skill the repo does not own (e.g. Vercel React/Next performance guidelines for `apps/web/`).  
It **does not** replace ERP domain skills, Bridge-First hardware skills, or `/token-discipline`. Blind `npx skills add --all` or always-on auto-install **hurts** token budget and can pull in browser-QR / fiscal / tax-shaped skills that violate hard exclusions.

#### Verdict vs Ponytail / Awesome MCP / Goose

| Tool | Layer | GTR action |
|------|-------|------------|
| Awesome MCP servers | MCP **server** discovery | **Integrate** (process) — still #1 |
| Ponytail | Coding **diff** discipline | **Integrate** (scoped rule) — still #2 |
| Goose | Alternate **agent runtime** | **Optional** sidecar |
| **find-skills / `npx skills`** | Agent **skill package** discovery | **Optional** — below the top 3; use CLI ad hoc or global meta-skill only; **do not** commit random ecosystem skills into `.cursor/skills/` without review |

**Recommendation: Optional / skip-as-default.** Prefer repo-owned skills + AGENTS.md. Use `npx skills find` (or a **global** `find-skills` install) only when a clear generic gap remains after checking `.cursor/skills/`. Never auto-install into the project tree; never adopt skills that imply ZIMRA, payroll tax, or HTML5 QR.

---

### 8. Emil Kowalski skills — motion / design craft

> User follow-up (2026-08-02): Emil Kowalski Cursor Agent Skills for frontend design.

#### Exact repos / surfaces

| Piece | URL | Role |
|-------|-----|------|
| **Canonical skills repo** | https://github.com/emilkowalski/skills | 8 Agent Skills packages (SKILL.md); ~24k★ |
| **skills.sh catalog** | https://skills.sh/emilkowalski/skills | Install counts / per-skill pages (~488k total installs) |
| **Author site (older install string)** | https://emilkowal.ski/skill | Same skill set; still shows `npx skills add emilkowalski/skill` (singular) — prefer **`emilkowalski/skills`** |
| **Install CLI** | `npx skills` via [vercel-labs/skills](https://github.com/vercel-labs/skills); discover via `npx skills find animation` / find-skills | Same path as §7 |

#### What they cover

| Skill | Focus |
|-------|--------|
| **emil-design-eng** | Main craft skill: animation decision framework, custom easing, duration &lt;300ms, springs, popovers/origin-aware motion, CSS transforms, gestures, Sonner-style component polish, review checklist |
| **review-animations** | Diff-scoped strict motion review (justify motion, frequency, easing, interruptibility, a11y); read-only critic |
| **improve-animations** | Codebase-wide motion audit → prioritized findings + self-contained plans under `plans/` (does not edit source) |
| **find-animation-opportunities** | Propose where motion helps *and* what not to animate |
| **animation-vocabulary** | Name effects (“pop in”, rubber-banding, …) so prompts are precise — not a design system |
| **apple-design** | WWDC-derived Apple motion/materials/depth/typography translated for web |
| **pick-ui-library** | Prefer Emil’s trusted libs over hand-rolled/abandoned packages |
| **prototype** | Multiple live UI variants + picker to choose a winner |

**Stack flavor:** Web / React / Next-oriented craft (CSS transitions, springs, `@starting-style`, interruptible UI). Not Android/iOS native design systems; not ERP domain logic.

#### License & maturity

- **License:** **MIT** (`LICENSE` in `emilkowalski/skills`, © 2026 Emil Kowalski).
- **Maturity:** High — popular author (animations.dev / Vercel / Linear lineage), large skills.sh install volume, active GitHub presence.

#### How to install (Cursor)

```bash
# Prefer global so project .cursor/skills/ stays ERP-curated; target Cursor
npx skills@latest add emilkowalski/skills --skill emil-design-eng -g -a cursor -y

# Motion review / audit helpers (same flags)
npx skills@latest add emilkowalski/skills --skill review-animations -g -a cursor -y
npx skills@latest add emilkowalski/skills --skill improve-animations -g -a cursor -y

# Discover without installing
npx skills find "emil animation"
```

Restart / reopen Agent chat after install. Treat like `/ui-ux-pro-max`: **explicit-invoke only** (do not leave always-on / model-auto-invoke).

#### vs GTR `/ui-ux-pro-max` + brand rules

| Layer | `/ui-ux-pro-max` (repo) | Emil Kowalski skills |
|-------|-------------------------|----------------------|
| Role | Broad design-system search (styles, palettes, fonts, UX, charts, 22 stacks) | Narrow **motion craft** + some component polish |
| GTR policy | Explicit-invoke only (`AGENTS.md` / session_discipline); already installed under `.cursor/skills/ui-ux-pro-max/` | Should match the same discipline if adopted |
| Brand | Can be steered (`--design-system -p "Nissan GTR Auto"`) but still generic catalogs | Does **not** encode Staff `--gtr-red` / `--gtr-steel`, AutoDoc IA, or anti-purple-slop rules |
| Motion | CSV guidelines (150–300ms, reduced-motion, limit animations) | Deeper easing/physicality/frequency review; overlaps and occasionally **disagrees** (e.g. pro-max “ease-in for exit” vs Emil “avoid ease-in for UI”) |

**Brand / user-rule conflicts to watch**

- **Staff chrome** (steel/red, dense utility tabs) must win over Apple translucent materials / glass depth from `apple-design`.
- **Frontend design user rules** (no purple AI slop, brand-first heroes, restrained cards) still apply; Emil’s “agents with taste” helps *against* slop on micro-interactions, but does not replace those rules.
- **`pick-ui-library`:** risk of pulling toast/UI libs that fight `packages/ui/` / existing Next stack — skip unless intentionally evaluating a dependency.
- **POS / warehouse / finance desks:** high-frequency UIs — Emil’s own frequency rules say *less* motion; do not use these skills to decorate every staff screen.
- **Token discipline:** same as other heavy design skills — never co-load with `/ui-ux-pro-max` by default.

#### Verdict

**Optional** — adopt as a **global, explicit-invoke motion layer**, not as a replacement for `/ui-ux-pro-max` and not as project-default for all frontend work.

| When | Use |
|------|-----|
| New storefront page / landing composition, palette, type, IA | **`/ui-ux-pro-max`** first (brand-steered) |
| Micro-interactions feel sluggish / wrong easing / over-animated | **`emil-design-eng`** or **`review-animations`** |
| Roadmap of motion fixes without implementing yet | **`improve-animations`** / **`find-animation-opportunities`** |
| Naming a motion effect for a clearer prompt | **`animation-vocabulary`** |
| Staff ERP chrome, RBAC desks, Android management | Prefer existing chrome + user rules; **skip** Emil unless a specific transition is broken |
| Picking a new UI library wholesale | **Skip** `pick-ui-library` by default |
| Apple-like materials on Staff or marketing that fights steel/red | **Skip** `apple-design` unless explicitly wanted |

**Recommendation: Optional.** Highest value for `apps/web/` storefront / marketing polish *after* brand and layout are set. Keep install **global (`-g`)**; do not vendor the whole pack into monorepo `.cursor/skills/` without review. Same explicit-invoke bar as ui-ux-pro-max.

---

## Concrete use cases vs conflicts (quick matrix)

| Need | Prefer | Avoid |
|------|--------|-------|
| Route work to `@backend_agent` / `@web_agent` | Existing `/manager` + lanes | Omnis router, CrewAI crews for coding |
| Discover MCP for Supabase/GitHub/docs | Awesome MCP + Glama + security pass | Installing unvetted servers en masse |
| Stop over-engineered diffs | Ponytail rule (lite/full) + `/verifier` | Ultra mode during greenfield scaffold |
| Multi-provider chat cost/fallback | Cursor native models first; OmniRoute only if audited | Blind OmniRoute with prod secrets |
| Browser QA of Next.js storefront | Cursor browser MCP / Playwright tests | Skyvern in product path |
| Supplier site scrape (pipeline) | Existing pipeline + Playwright; Skyvern only if justified | Skyvern for QR/POS/hardware |
| Headless agent recipes | Goose CLI (optional) | Second full IDE agent as default |
| Discover a *generic* Cursor skill (React, PR review, …) | `npx skills find` / skills.sh after checking `.cursor/skills/` | Mass-installing ecosystem skills into the monorepo; skills that fight token-discipline or hard exclusions |
| Storefront layout / palette / type / UX checklist | `/ui-ux-pro-max` (explicit) + Staff/storefront brand vars | Auto-loading design suite every turn |
| Motion polish / animation review on web UI | Emil `review-animations` / `emil-design-eng` (global, explicit) *after* brand | `apple-design` or `pick-ui-library` overriding steel/red or `packages/ui/` |
| Payroll tax / ZIMRA automation | **Never** | Any MCP/agent that pulls fiscal/tax stacks |

---

## Top recommendations (actionable)

### Adopt now

1. **Awesome MCP servers (process)**  
   - Bookmark [github.com/punkpeye/awesome-mcp-servers](https://github.com/punkpeye/awesome-mcp-servers) and [glama.ai/mcp/servers](https://glama.ai/mcp/servers).  
   - For each candidate: license, auth model, whether it needs secrets, whether it expands network beyond `.cursor/sandbox.json` allowlists.  
   - Document approved MCPs in `docs/TOOLING_SETUP.md` (alongside n8n-mcp / claude-mem).

2. **Ponytail (Cursor rule)**  
   - Copy upstream `.cursor/rules/ponytail.mdc` as an **agent-requestable** rule (or always-on at `lite`/`full` only after a one-week trial).  
   - Do not let it override RLS, ledger immutability, multi-currency, or Bridge-First — those stay in `.cursorrules`.

3. **Goose (optional sidecar)**  
   - Install only if you want CLI recipes / local BYOK outside Cursor.  
   - Point it at the same `AGENTS.md` exclusions; do not run Goose + Cursor Agent on the same dirty tree without coordination.

### Optional (discovery only — not adopt-now)

4. **find-skills / `npx skills` (Vercel Labs)**  
   - Use when a capability gap is **not** covered by `.cursor/skills/` / `AGENTS.md`.  
   - Prefer `npx skills find …` or a **global** (`-g`) install of `find-skills`; keep project `.cursor/skills/` curated.  
   - Review license + content of any candidate skill before install; reject anything fiscal/tax/browser-QR.  
   - Does **not** displace Awesome MCP (servers) or Ponytail (YAGNI diffs).

5. **Emil Kowalski skills (`emilkowalski/skills`)**  
   - Optional **motion craft** companion to `/ui-ux-pro-max` — not a brand system.  
   - Install globally (`-g -a cursor`); explicit-invoke only; start with `emil-design-eng` + `review-animations`.  
   - Skip `apple-design` / `pick-ui-library` unless deliberately evaluating; never let them override `--gtr-red` / `--gtr-steel` or user anti-slop rules.  
   - Do **not** commit the pack into project `.cursor/skills/` without a conscious ADR.

### Skip (and why)

| Skip | Why |
|------|-----|
| **CrewAI for ERP coding** | Duplicates manager/planner/lane/security/verifier; pulls agents toward Python crew scaffolds instead of this monorepo |
| **Omnis router / SGLang omni_router** | Immature or wrong layer; duplicates lane routing |
| **Skyvern in apps/bridges/POS** | Bridge-First + AGPL; browser RPA ≠ native hardware bridges |
| **OmniRoute as default** | Until security posture is verified; limited Cursor surface (chat-only); key/proxy risk |
| **find-skills as always-on project skill + auto-install** | Token bloat; dilutes curated ERP skills; risk of unvetted ecosystem packages |
| **Emil pack as always-on / replacing ui-ux-pro-max** | Wrong layer for brand/layout; token cost; Apple/material bias can fight Staff chrome |

### Explicitly out of scope (do not “helpfully” add)

- Any MCP or agent workflow for ZIMRA / FDMS / fiscal QR.  
- Any CrewAI/Skyvern “payroll tax filing” automation.  
- HTML5/browser QR libraries justified by Skyvern-style browser automation.

---

## Suggested follow-ups (docs only unless approved)

1. ~~Add a short “MCP shortlist” subsection to `docs/TOOLING_SETUP.md`~~ **Implemented** — MCP table + enable steps; example at `.cursor/mcp.json.example`.  
2. ~~Trial Ponytail on one coding lane PR~~ **Installed** as agent-requestable lite — trial intensity on next feature PR; keep or adjust.  
3. Do **not** open product PRs for CrewAI/Skyvern/Omnis unless a future `docs/decisions/` ADR overturns this plan.  
4. ~~Optional: skill discovery note~~ **Implemented** in `docs/CURSOR_ERP_SAAS_SETUP_GUIDE.md` + TOOLING_SETUP.  
5. ~~Emil install + invoke-after-ui-ux~~ **Implemented** (global skills + AGENTS / session_discipline one-liners).  
6. **Remaining manual:** enable MCP servers in Cursor Settings; start Meilisearch profile when ready to dual-read; Casbin/Gorse still Phase-2 only.

---

## Sources (retrieved 2026-08-02)

- https://github.com/punkpeye/awesome-mcp-servers  
- https://github.com/DietrichGebert/ponytail  
- https://github.com/crewAIInc/crewAI / https://crewai.com  
- https://github.com/Skyvern-AI/skyvern / https://www.skyvern.com  
- https://github.com/aaif-goose/goose / https://goose-docs.ai  
- https://github.com/diegosouzapw/OmniRoute / https://omniroute.online  
- https://github.com/blouargant/omnis  
- https://github.com/vercel-labs/skills / https://skills.sh/ / https://skills.sh/vercel-labs/skills/find-skills  
- https://vercel.com/changelog/introducing-skills-the-open-agent-skills-ecosystem  
- https://github.com/emilkowalski/skills / https://skills.sh/emilkowalski/skills / https://emilkowal.ski/skill  
- Repo: `AGENTS.md`, `docs/AGENT_TEAM.md`, `.cursorrules`, `docs/TOOLING_SETUP.md`, `.cursor/skills/`
