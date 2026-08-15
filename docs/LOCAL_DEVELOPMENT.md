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

1. Install [Cursor Desktop](https://cursor.com/download) if needed.
2. **File → Open Folder** → select the `Nissan-Gtr-Auto` repo root.
3. Wait for indexing to finish (`.cursorignore` already excludes noise).
4. Confirm Agent mode works: open Chat/Composer and ask it to summarize `AGENTS.md`.

You are now local. Do **not** start a new Cloud Agent for day-to-day work unless you intentionally want a remote VM.

## 3. Install local prerequisites

| Tool | Purpose | Install |
|------|---------|---------|
| Node.js 22+ | Web app / monorepo | https://nodejs.org |
| pnpm | Workspace package manager | `npm i -g pnpm` |
| Docker Desktop | Supabase local stack | https://docker.com/products/docker-desktop |
| Supabase CLI | Migrations, local DB | https://supabase.com/docs/guides/cli |
| Python 3.11+ | Data pipeline | system / pyenv |
| Xcode (macOS) | iOS customer app | App Store |
| Android Studio | Android apps + bridges | https://developer.android.com/studio |

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

**Optional local plugins** (install in Cursor Desktop Settings → Plugins / Skills):

- [claude-mem](https://github.com/thedotmack/claude-mem) — persistent memory across local sessions
- [ui-ux-pro-max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) — design skill for storefront

## 5. Local agent workflow (replace Cloud Agent habits)

| Instead of (Cloud) | Do this (Local) |
|--------------------|-----------------|
| Start a Cloud Agent run on cursor.com | Open Composer/Agent in Cursor Desktop |
| Wait for remote VM setup | Work immediately against local files |
| Push-only feedback loop | Run tests/terminals locally, then commit |
| Cloud-only long tasks | Use local Agents Window; optionally `/in-cloud` only when needed |

**Recommended local loop:**

1. Pick a lane from `rufler.yaml` (e.g. `@backend_agent`).
2. Open Agent chat, attach `@AGENTS.md` + the relevant folder.
3. Implement → run `/verifier` → commit on a `cursor/<name>-ad25` branch.
4. Push and open a PR when ready.

## 6. What to leave alone

- **Keep** `.cursor/environment.json` — harmless for local; used only if someone starts a Cloud Agent later.
- **Keep** hooks/permissions/sandbox — they also protect local agent shell commands.
- **Do not** delete orchestration files — they are what make local multi-agent work.

## 7. Stop / archive the current Cloud run

1. Open the current run: https://cursor.com/agents/bc-b00ea338-fc33-4a85-a69a-3d4ae59ead25
2. Merge or leave open [PR #1](https://github.com/Vanguduza/Nissan-Gtr-Auto/pull/1) as you prefer.
3. Archive or stop the cloud agent when you no longer need the remote session.
4. Continue all new work from Cursor Desktop on your machine.

## 8. Quick checklist

- [ ] Repo cloned / pulled on your machine
- [ ] Opened in **Cursor Desktop** at repo root
- [ ] Indexing complete; `AGENTS.md` readable by Agent
- [ ] Node, pnpm, Docker, Supabase CLI installed (as needed)
- [ ] Branch `cursor/erp-cursor-setup-ad25` checked out (or `main` after merge)
- [ ] Cloud agent archived when done
- [ ] Next work started in local Agent chat (not a new cloud run)

---

## 9. Auth seed + typed client (Phase 2)

**Local reset** (Docker required) loads `supabase/seed.sql` automatically (`[db.seed]` in `config.toml`):

| Email | Password | Role |
|-------|----------|------|
| `admin@gtr.local` | `local-dev-admin` | admin |
| `finance@gtr.local` | `local-dev-finance` | finance |
| `warehouse@gtr.local` | `local-dev-warehouse` | warehouse |
| `storefront-a@gtr.local` | `local-dev-customer` | customer (`profiles.id` = `customers.profile_id` = `c0000000-0000-4000-8000-0000000000a1`) |
| `storefront-b@gtr.local` | `local-dev-customer` | customer (`profiles.id` = `customers.profile_id` = `c0000000-0000-4000-8000-0000000000b2`) |

```bash
pnpm db:start
pnpm db:reset
pnpm db:types          # local Docker
# or after linking remote:
pnpm db:types:linked
```

Local edge functions (after `supabase start`): `npx supabase functions serve` — serves all under `supabase/functions/` at `http://127.0.0.1:54321/functions/v1/<name>` (leave running in a second terminal).

**Hosted project** (ref `gylrgwqyuiwkyykardwc`): switch client env off `127.0.0.1:54321`, push migrations with `supabase link` + `supabase db push`, and configure Auth providers on Dashboard — see [`docs/guides/hosted-supabase-cutover.md`](./guides/hosted-supabase-cutover.md). Do **not** `db reset` remote.

Commit `packages/supabase-client/src/database.types.ts` whenever migrations change public schema. **Never** put `service_role` in client packages — only anon via `createBrowserClient`.

RLS seed smoke: `psql … -f supabase/tests/phase2_rls_smoke.sql`  
CI RLS gate + Bugbot/secrets checklist: `docs/HARDENING.md` (`phase14_ci_smoke.sql` via `docker exec … psql`).

### Secrets / Mac infra (ready — awaiting keys or Mac host)

Code paths and fail-closed stubs are in place. Do **not** invent production secrets. Checklist (names in root `.env.example`, app `.env.example` / `local.properties.example` / `Secrets.xcconfig.example`, Edge README, `powersync/.env.example`, `packages/delivery-dispatch-worker/.env.example`, `promptfoo/.env.example`):

| Area | Status |
|------|--------|
| ContiPay / Paynow / WhatsApp Cloud / SMS / Resend | Edge wired; fail-closed without secrets unless local stub flag |
| Brevo (CRM promos) | Prefer Brevo; Resend fallback until `BREVO_*` set |
| `WORKER_SHARED_SECRET` | Fail-closed outside local stub |
| Map tiles (`NEXT_PUBLIC_MAP_STYLE_URL` / `MAPLIBRE_STYLE_URL`) | Self-host: `infra/satellites/maptiles/` — smoke `$env:MAPTILES_SMOKE=1; powershell -File infra/satellites/maptiles/prepare.ps1` then `--profile maptiles` → `http://127.0.0.1:8081/styles/basic-preview/style.json`. Full ZW: `prepare.sh` (Planetiler; first run ~1GB+ sources). Unset → CARTO / demotiles |
| PowerSync (`POWERSYNC_URL`) | SDK wired; Fake when unset — awaiting cloud E2E keys |
| Temporal (`TEMPORAL_ADDRESS` + service role) | Worker refuse-without-config — awaiting live Temporal |
| Promptfoo real-provider | Optional — offline gate always; waiting on keys for real CI |
| H5-iOS Mac verify | Code Done — awaiting Mac: |

```bash
cd bridges/ios/MapsNav
xcodebuild -scheme MapsNav -destination 'platform=iOS Simulator,name=iPhone 16' test

cd apps/ios
xcodebuild -scheme GTRCustomer -destination 'platform=iOS Simulator,name=iPhone 16' -project GTRCustomer.xcodeproj build
```

**Catalog diagram bytes** (after reset; Navara + X-Trail fixture packs):

```bash
pnpm db:reset && node supabase/seed_catalog_diagrams.mjs --docker
```

See also `data-pipeline/fixtures/*/diagrams/README.md`. Script discovers every `fixtures/<vehicle>/diagrams/<storage-prefix>/*.png` pack idempotently.

---

## Next work after switching

Once local, follow `docs/plans/2026-07-23-master-erp-development.md` (Phase 2+).