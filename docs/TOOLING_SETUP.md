# External Tooling Setup — Claude-Mem, UI/UX Pro Max, n8n-MCP

Install order from the setup prompt (§4): **claude-mem first** (context discipline backbone), then **ui-ux-pro-max**, then **n8n-mcp** only if you use n8n.

---

## Status in this repo

| Tool | Repo status | Where it lives |
|------|-------------|----------------|
| Domain skills + token discipline | **Installed** | `.cursor/skills/` |
| ui-ux-pro-max (+ design suite) | **Installed** (explicit-invoke) | `.cursor/skills/ui-ux-pro-max/` etc. |
| Path rules / agents / hooks | **Installed** | `.cursor/rules/`, `.cursor/agents/`, `.cursor/hooks*` |
| Ruflo lanes | **Configured** | `rufler.yaml` |
| claude-mem | **Needs host install** | User-level hooks + worker (see below) |
| n8n-mcp | **Optional** | Copy from `.cursor/mcp.json.example` |

Heavy UI skills have `disable-model-invocation: true` so they do **not** auto-load and burn tokens. Invoke with `/ui-ux-pro-max` when designing storefront / My Garage / catalog UI.

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

## 3. n8n-MCP (optional)

Skip if n8n is not part of your ops stack (setup prompt §4.3).

1. Copy `.cursor/mcp.json.example` → `.cursor/mcp.json`
2. For docs-only tools, leave API env empty
3. For live workflow CRUD, set `N8N_API_URL` and `N8N_API_KEY`
4. Cursor Settings → MCP → enable **n8n-mcp**
5. Restart Cursor / enable the server when prompted

Never commit real API keys. `.cursor/mcp.json` is gitignored if present.

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
[ ] Optional: n8n-mcp enabled only if needed
[ ] Python available for ui-ux search scripts
```
