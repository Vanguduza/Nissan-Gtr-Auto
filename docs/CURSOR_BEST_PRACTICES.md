# Cursor Best Practices — Nissan GTR Auto ERP

Extended playbook beyond the core setup prompt. These practices complement `rufler.yaml`, `.cursorrules`, domain skills, and the three referenced repos (claude-mem, ui-ux-pro-max, n8n-mcp).

---

## 1. Context Management at Scale

### Intelligent Rule Application
Path-specific rules in `.cursor/rules/*.mdc` use `alwaysApply: false` with descriptive `description` fields. Cursor loads them only when the agent judges them relevant — keeping web sessions lean even with 30+ module rules.

### Scoped Skills with `paths`
As apps are scaffolded, add nested skills under each app directory:
```
.cursor/skills/                    # repo-wide (release, ERP domain)
apps/web/.cursor/skills/           # deploy-web, supabase-types
apps/ios/.cursor/skills/           # xcode-test, app-store
```
Use `disable-model-invocation: true` on heavy skills (ui-ux-pro-max) so they load only via explicit `/skill-name` invocation.

### `.cursorignore` for Indexing Hygiene
Excludes build artifacts, caches, generated types, and large binary assets from Cursor's codebase index. Reindex after changes: Command Palette → "Reindex".

### claude-mem as Episodic Memory
Durable conventions → Rules/skills. Episodic decisions (schema choices, naming debates) → claude-mem. Avoid double-loading the same facts in both.

### Plan Mode for Large Features
Multi-file ERP features (new module across web + API + mobile) should start in Plan Mode (`Shift+Tab`). Save plans to `docs/plans/` in-repo rather than relying on chat context.

---

## 2. Multi-Agent Development

### Agents Window as Control Plane
`Cmd+Shift+P` → "Open Agents Window". Pin long-running agents per platform. Complements `rufler.yaml` with a native UI for parallel local + cloud agents.

### Subagents (`.cursor/agents/`)
Three specialist subagents are pre-configured:
- **`/supabase-rls-auditor`** — readonly RLS security audit on migrations
- **`/verifier`** — skeptical post-implementation verification (tests, exclusions, lane compliance)
- **`/hardware-bridge-specialist`** — native bridge implementation (QR, printer, biometric, GPS)

Add more as needed: `erp-domain-verifier`, `ios-ui-reviewer`, `test-runner`.

### Orchestrator Pattern
For complex features, use the sequence:
1. **Planner** — Plan Mode, save to `docs/plans/`
2. **Implementer** — lane-scoped agent from `rufler.yaml`
3. **Verifier** — `/verifier` subagent runs tests and exclusion scans

### Parallel Execution
- `/multitask` — run independent subagent tasks concurrently
- Plan Mode → "Build in Parallel" for independent plan steps
- `/in-cloud` — hand long tasks to Cloud Agent VMs
- `/babysit` — monitor PRs until merge-ready (review comments, CI, conflicts)

### Worktrees for Parallel Platform Work
`.cursor/worktrees.json` configures per-worktree setup. Use `/worktree` in IDE or Agents Window to let one agent fix web inventory while another works on Android sync.

---

## 3. Code Quality & CI/CD

### Bugbot PR Review
`.cursor/BUGBOT.md` defines ERP-specific blocking rules:
- Missing RLS on new tables
- ZIMRA/payroll tax/HTML5 QR references
- Journal entry mutations
- Hardware access outside `bridges/`

Enable via Dashboard → Bugbot → connect GitHub → require `Cursor Bugbot` check on `main`.

### Pre-Push Review
Before pushing: `/review-bugbot` then `/review-security` to catch issues before CI.

### Hooks (`.cursor/hooks.json`)
| Hook | Script | Purpose |
|------|--------|---------|
| `afterFileEdit` | `format.sh` | Auto-format edited files |
| `beforeShellExecution` | `guard-prod.sh` | Block prod DB commands (failClosed) |
| `subagentStop` | `run-changed-tests.sh` | Run targeted tests after subagent work |

### Agent Review (Local)
Settings → Agents → Agent Review. Quick/Deep review of local diff before push. Use `/agent-review` or Source Control tab vs `main`.

### Permissions & Sandbox
- `.cursor/permissions.json` — allow/deny shell commands, block instructions for prod/ZIMRA/tax
- `.cursor/sandbox.json` — network allowlist (npm, Supabase, PyPI, Gradle, Apple, ContiPay)

---

## 4. Cloud Agents & Automations

### Cloud Environment (`.cursor/environment.json`)
Reproducible VM config for Cloud Agents. Add install commands as apps are scaffolded (pnpm install, supabase start, etc.).

Dashboard → Cloud Agents → Environments for secrets and snapshots.

### Cursor Automations
Complement n8n (business workflows) with repo-native automations:
- **CI failure triage** — auto-fix failing tests on PR
- **Security scan** — weekly vulnerability scan
- **Linear issue → PR** — `[repo=owner/nissan-gtr-auto]` labels

Create at [cursor.com/automations](https://cursor.com/automations) or via `/automate` skill.

### Automation Memories
Enable Memories tool on automations for recurring triage patterns (e.g., "when Android CI fails, check Gradle lockfile").

---

## 5. Integrations

| Integration | Purpose | Setup |
|-------------|---------|-------|
| **GitHub** | PRs, Bugbot, CI triggers | Dashboard → Integrations |
| **Linear** | Issue → agent delegation | `@Cursor` in Linear, `[repo=...]` labels |
| **Slack/Teams** | Trigger agents from chat | Dashboard → Integrations |
| **Sentry/PagerDuty** | Incident → investigate agent | Automation triggers |
| **Supabase MCP** | Direct DB queries from agent | Dashboard → MCP → Team Marketplace |
| **n8n-mcp** | Business workflow validation | Optional — see README |

---

## 6. Team Distribution

### Internal Plugin / Team Marketplace
Bundle rules, skills, subagents, MCP, and hooks into one distributable plugin:
1. Create from [plugin template](https://github.com/cursor/plugin-template)
2. Teams/Enterprise: Dashboard → Plugins → Team Marketplace
3. Set to **Required** for ERP repos

### Team Rules
Org-enforced prompts via Dashboard → Rules with globs (`supabase/**`, `**/auth/**`). Precedence: Team → Project → User.

### `@Docs` for External References
Index Supabase docs, ContiPay API, and internal OpenAPI specs via `@Docs` in chat. Reduces hallucination on RLS patterns and RPC signatures.

---

## 7. Suggested Priority Stack

### Phase 1 — Foundation (current)
- [x] `rufler.yaml` swarm topology
- [x] `.cursorrules` global laws
- [x] `.cursor/rules/*.mdc` path-specific rules
- [x] `.claude/skills/` domain skills
- [x] `AGENTS.md` repo-wide instructions
- [x] `.cursorignore` indexing hygiene
- [x] Subagents (RLS auditor, verifier, hardware specialist)
- [x] Bugbot rules, hooks, permissions, sandbox
- [x] Cloud environment and worktrees config

### Phase 2 — As Apps Are Scaffolded
- [ ] Install claude-mem (persistent session memory)
- [ ] Install ui-ux-pro-max (design-direction skill)
- [ ] Nested `AGENTS.md` per app directory
- [ ] Nested `.cursor/skills/` per platform
- [ ] Enable Bugbot on GitHub with required check
- [ ] Configure Cursor Automations (CI triage, security scan)

### Phase 3 — Scale
- [ ] Internal plugin with Nissan GTR standards bundle
- [ ] Team Rules for security/compliance
- [ ] Linear integration with repo labels
- [ ] Cursor CLI in GitHub Actions for headless triage
- [ ] Approval Agents with `APPROVAL_POLICY.md` per module
- [ ] `/split-to-prs` for cross-cutting ERP changes

---

## 8. How This Fits the Core Setup

| Core Setup (from prompt) | Extended Layer (this doc) |
|--------------------------|---------------------------|
| `rufler.yaml` swarm | Agents Window + subagents + `/multitask` |
| `.cursorrules` / `.mdc` | Nested `AGENTS.md` + Team Rules + `BUGBOT.md` |
| Domain skills | Nested `.cursor/skills/` + `paths` scoping |
| claude-mem | Rules for durable facts; claude-mem for episodic |
| n8n-mcp | Cursor Automations for repo-native events |
| Token discipline | Intelligent rules, scoped skills, Plan Mode |
