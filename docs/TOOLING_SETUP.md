# External Tooling Setup — Claude-Mem, UI/UX Pro Max, MCP, satellites

Install order from the setup prompt (§4): **claude-mem first** (context discipline backbone), then **ui-ux-pro-max**, then MCP servers as needed.

**Master playbook (Cursor + OSS for ERP/SaaS):** [`docs/CURSOR_ERP_SAAS_SETUP_GUIDE.md`](CURSOR_ERP_SAAS_SETUP_GUIDE.md)

---

## Status in this repo

| Tool | Repo status | Where it lives |
|------|-------------|----------------|
| Domain skills + token discipline | **Installed** | `.cursor/skills/` |
| ui-ux-pro-max (+ design suite) | **Installed** (explicit-invoke) | `.cursor/skills/ui-ux-pro-max/` etc. |
| Emil Kowalski motion skills | **Installed globally** (explicit-invoke) | `~/.cursor/skills/` + `~/.agents/skills/` — `emil-design-eng`, `review-animations`, `improve-animations` (skip `apple-design` / `pick-ui-library`) |
| Ponytail (YAGNI lite) | **Installed** (agent-requestable) | `.cursor/rules/ponytail.mdc` |
| Path rules / agents / hooks | **Installed** | `.cursor/rules/`, `.cursor/agents/`, `.cursor/hooks*` |
| Ruflo lanes | **Configured** | `rufler.yaml` |
| MCP shortlist | **Example ready** | `.cursor/mcp.json.example` → copy to `.cursor/mcp.json` |
| Satellites (Meilisearch + stubs) | **Compose scaffold** | `docker-compose.satellites.yml`, `infra/satellites/` |
| claude-mem | **Needs host install** | User-level hooks + worker (see below) |
| n8n-mcp | **Optional** (in MCP shortlist) | Enable only if you use n8n |

Heavy UI / motion skills must stay **explicit-invoke** so they do not auto-load and burn tokens. Invoke `/ui-ux-pro-max` for layout/brand; Emil skills for motion polish after brand.

---

## 1. Claude-Mem (required for long builds)

Persistent memory across Cursor sessions. Prefer **user-level** install so it does **not** overwrite this project's production-guard hooks.

### Windows (Cursor-only)

```powershell
# Prerequisites: Git, Node (installed), Bun
powershell -c "irm bun.sh/install.ps1 | iex"

# Fresh shell after Bun install
git clone https://github.com/thedotmack/claude-mem.git $env:USERPROFILE\src\claude-mem
cd $env:USERPROFILE\src\claude-mem
bun install
bun run build

# Interactive wizard — choose Gemini free tier unless you have Claude Code
bun run cursor:setup
```

Or run the helper script:

```powershell
.\scripts\windows\install-claude-mem.ps1
```

### After install

1. Confirm worker: `bun run worker:status` (from the claude-mem clone)
2. Restart Cursor
3. Open memory viewer (URL printed by worker, often `http://localhost:37777`)
4. **Token-efficient search:** `search` → `timeline` → `get_observations` (never fetch all observations first)

### Hook conflict note

This repo's `.cursor/hooks.json` owns format / prod-guard / tests. Claude-mem hooks should live in **`%USERPROFILE%\.cursor\hooks.json`** (user scope). If the wizard installs project-level hooks, merge carefully — keep `guard-prod.ps1` with `failClosed: true`.

---

## 2. UI/UX Pro Max (installed)

Already generated under `.cursor/skills/`. Requires **Python 3** for the search scripts:

```powershell
python --version
# If missing: install from https://www.python.org/downloads/ (and tick "Add to PATH")
```

Usage:

```text
/ui-ux-pro-max
Design the Nissan GTR Auto storefront for spare-parts B2B + consumer — intentional typography/color/motion, not Inter+purple defaults.
```

Or CLI search from repo root:

```powershell
python .cursor\skills\ui-ux-pro-max\scripts\search.py "automotive parts storefront" --design-system -p "Nissan GTR Auto"
```

---

## 3. MCP shortlist (from awesome-mcp-servers)

Catalog: [punkpeye/awesome-mcp-servers](https://github.com/punkpeye/awesome-mcp-servers). Full enable steps: [`CURSOR_ERP_SAAS_SETUP_GUIDE.md`](CURSOR_ERP_SAAS_SETUP_GUIDE.md) §3.

Curated for this ERP (placeholders only — never commit secrets):

| Server | Purpose |
|--------|---------|
| github | PRs / issues |
| supabase | Project/schema (example uses `--read-only`) |
| postgres | SQL via `DATABASE_URL` |
| context7 | Library docs for agents |
| playwright | Web E2E / UI checks |
| sentry | Prod errors (OAuth URL) |
| n8n-mcp | Optional ops workflows |

1. Copy `.cursor/mcp.json.example` → `.cursor/mcp.json`
2. Fill tokens / project ref / `DATABASE_URL` via env or edit locally
3. **Cursor Settings → MCP** (or Skills and Integrations): enable servers; complete Sentry OAuth if used
4. Restart Agent / window if tools missing
5. Leave unused servers disabled to save tokens

Filesystem + IDE browser are already native — do not add browser-QR MCPs.

### n8n-MCP specifically

Skip if n8n is not part of your ops stack. Set `N8N_API_URL` / `N8N_API_KEY` only for live workflow CRUD.

Never commit real API keys. `.cursor/mcp.json` is gitignored.

## 3b. Ponytail + Emil skills

- **Ponytail:** agent-requestable rule `.cursor/rules/ponytail.mdc` (lite YAGNI). Does not override RLS / Bridge-First / hard exclusions.
- **Emil:** invoke `emil-design-eng` / `review-animations` / `improve-animations` only for storefront motion after `/ui-ux-pro-max`. Reinstall if needed:

```powershell
npx skills@latest add emilkowalski/skills --skill emil-design-eng -g -a cursor -y
npx skills@latest add emilkowalski/skills --skill review-animations -g -a cursor -y
npx skills@latest add emilkowalski/skills --skill improve-animations -g -a cursor -y
```

## 3c. Satellites (Meilisearch)

```powershell
docker compose -f docker-compose.satellites.yml --profile search up -d
```

See `infra/satellites/README.md`. Postgres FTS remains production search until a dual-read indexer is built.

---

## 4. Token-efficiency checklist (best practices)

| Practice | How this repo enforces it |
|----------|---------------------------|
| Path-routed agents | `rufler.yaml` + `AGENTS.md` |
| Lean always-on rules | `.cursor/rules/*.mdc` with `alwaysApply: false` |
| Explicit heavy skills | `disable-model-invocation: true` on design suite |
| Index hygiene | `.cursorignore` |
| Episodic memory | claude-mem + `docs/decisions/` |
| Plan before big work | `docs/plans/` + Plan Mode |
| Verify after implement | `/verifier`, `/supabase-rls-auditor` |
| Prod safety | `guard-prod.ps1` failClosed |
| Progressive disclosure | claude-mem 3-layer search |

Invoke `/token-discipline` at the start of large sessions if context starts drifting.

---

## 5. Verify setup

```text
[ ] Lane dirs exist under apps/, packages/, bridges/, supabase/, data-pipeline/
[ ] .cursor/skills includes domain + ui-ux-pro-max + token-discipline
[ ] .cursor/agents has verifier, supabase-rls-auditor, hardware-bridge-specialist
[ ] Hooks still allow shell (Windows PowerShell hooks)
[ ] claude-mem worker running (after host install)
[ ] Optional: MCP shortlist enabled (github/supabase/…); n8n only if needed
[ ] Optional: Emil skills visible; Ponytail rule present
[ ] Optional: Meilisearch via compose --profile search
[ ] Python available for ui-ux search scripts
```
