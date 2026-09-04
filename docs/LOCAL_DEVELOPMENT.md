# Local Development — Switch from Cloud Agents

This project is configured for **local Cursor Desktop** as the primary workflow.
Cloud Agents (`.cursor/environment.json`) remain optional for overnight/long tasks only.

## Why switch to local?

| Cloud Agents | Local Cursor Desktop |
|--------------|----------------------|
| Remote VM, limited native toolchains | Full Xcode, Android Studio, Docker, simulators |
| Good for schema/docs/backend | Required for iOS, Android, hardware bridges |
| Session ends when run ends | Persistent IDE, terminals, indexing |
| No physical device access | QR printers, biometrics, GPS testing |

For Nissan GTR Auto ERP (polyglot + native bridges), **local is the right default**.

---

## 1. Get the code on your machine

```bash
# Clone (if you don't have it yet)
git clone https://github.com/Vanguduza/Nissan-Gtr-Auto.git
cd Nissan-Gtr-Auto

# Check out the orchestration branch (or main after PR #1 is merged)
git fetch origin
git checkout cursor/erp-cursor-setup-ad25
# OR after merge:
# git checkout main && git pull
```

## 2. Open in Cursor Desktop (not Cloud)

1. Install Cursor Desktop if needed.
2. **File → Open Folder** → select the `Nissan-Gtr-Auto` repo root.
3. Wait for indexing to finish (`.cursorignore` already excludes noise).
4. Confirm Agent mode works: open Chat/Composer and ask it to summarize `AGENTS.md`.

You are now local. Do **not** start a new Cloud Agent for day-to-day work unless you intentionally want a remote VM.

## 3. Install local prerequisites

| Tool | Purpose | Install |
|------|---------|---------|
| Node.js 22+ | Web app / monorepo | nodejs.org |
| pnpm | Workspace package manager | `npm i -g pnpm` |
| Docker Desktop | Supabase local stack | docker.com |
| Supabase CLI | Migrations, local DB | Supabase CLI docs |
| Python 3.11+ | Data pipeline | system / pyenv |
| Xcode (macOS) | iOS customer app | App Store |
| Android Studio | Android apps + bridges | developer.android.com/studio |

Minimal for backend/orchestration work: **Node + pnpm + Docker + Supabase CLI**.

## 4. Verify Cursor picks up the agent team

In the opened repo, these should auto-load:

| Path | What it does locally |
|------|----------------------|
| `.cursorrules` | Global laws for every local agent session |
| `.cursor/rules/*.mdc` | Path-scoped rules when you edit those dirs |
| `.cursor/agents/*.md` | Subagents: `/verifier`, `/supabase-rls-auditor`, `/hardware-bridge-specialist` |
| `.claude/skills/*.md` | Domain skills (loaded when triggers match) |
| `rufler.yaml` | Lane map — reference in prompts with `@web_agent` etc. |
| `AGENTS.md` | Run commands & conventions |

## 5. Local agent workflow

| Instead of (Cloud) | Do this (Local) |
|--------------------|-----------------|
| Start a Cloud Agent run | Open Composer/Agent in Cursor Desktop |
| Wait for remote VM setup | Work immediately against local files |
| Push-only feedback loop | Run tests/terminals locally, then commit |
| Cloud-only long tasks | Use local Agents Window; use cloud only intentionally |

Recommended loop:

1. Pick a lane from `rufler.yaml`.
2. Open Agent chat, attach `@AGENTS.md` + the relevant folder.
3. Implement → run `/verifier` → commit on a scoped branch.
4. Push and open a PR when ready.

## 6. What to leave alone

- Keep `.cursor/environment.json` for optional cloud runs.
- Keep hooks/permissions/sandbox protections.
- Do not delete orchestration files.

## 7. Quick checklist

- [ ] Repo cloned / pulled on your machine
- [ ] Opened in Cursor Desktop at repo root
- [ ] Indexing complete; `AGENTS.md` readable by Agent
- [ ] Node, pnpm, Docker, Supabase CLI installed as needed
- [ ] Correct working branch checked out
- [ ] Tests/builds run locally before merge when CI infrastructure is unavailable

---

## 8. Auth seed + typed client

**Local reset** (Docker required) loads `supabase/seed.sql` automatically (`[db.seed]` in `config.toml`):

| Email | Password | Role |
|-------|----------|------|
| `admin@gtr.local` | `local-dev-admin` | admin |
| `finance@gtr.local` | `local-dev-finance` | finance |
| `warehouse@gtr.local` | `local-dev-warehouse` | warehouse |
| `storefront-a@gtr.local` | `local-dev-customer` | customer |
| `storefront-b@gtr.local` | `local-dev-customer` | customer |

```bash
pnpm db:start
pnpm db:reset
pnpm db:types
# or after linking the active hosted project:
pnpm db:types:linked
```

Local edge functions: `npx supabase functions serve` after `supabase start`.

**Active hosted project** (ref `bicyjghgdnzlnjqxzoud`): switch client env off `127.0.0.1:54321`, use the matching replacement-project publishable key, link with `npx supabase link --project-ref bicyjghgdnzlnjqxzoud`, apply only pending migrations, and configure Auth providers in Dashboard. See [`docs/guides/hosted-supabase-cutover.md`](./guides/hosted-supabase-cutover.md). Never run `db reset` against hosted.

The retired hosted project `gylrgwqyuiwkyykardwc` is recovery-only during cutover and must not be deleted until replacement Auth, R2 serving manifests, app environments, and E2E gates are green.

Commit `packages/supabase-client/src/database.types.ts` whenever migrations change public schema. Never put `service_role` in client packages.

RLS seed smoke: `psql … -f supabase/tests/phase2_rls_smoke.sql`. CI RLS gate + secrets checklist: `docs/HARDENING.md`.

### Catalog data locally vs hosted

Local fixture diagrams may still be loaded for deterministic development/testing:

```bash
pnpm db:reset && node supabase/seed_catalog_diagrams.mjs --docker
```

That local fixture workflow does **not** define hosted architecture. Hosted heavy EPC fitment/parts/diagram bytes belong in Cloudflare R2 and are accessed through `catalog-live-r2`; do not restore the heavy catalog into hosted Supabase.

---

## Next work after switching

Once local, follow the current versioned source-of-truth plans and architecture decisions. For hosted cutover details, use `docs/guides/hosted-supabase-cutover.md` rather than historical project-ref documentation.
