# Remote Supabase project

- Date: 2026-07-23
- Lane: @backend_agent
- Status: accepted

## Decision

Primary hosted Supabase project URL:

`https://gylrgwqyuiwkyykardwc.supabase.co`

Project ref: `gylrgwqyuiwkyykardwc`

Env templates live in `.env.example`; secrets in `.env.local` (gitignored).

## Why

Cloud-hosted DB for shared schema/migrations across local Cursor agents; local `supabase start` remains optional for offline/dev.

## Consequences

- Migrations are still authored in `supabase/migrations/` and pushed via CLI (`supabase db push` / link).
- Clients use **anon** key only; **service_role** is server-only.
- Do not paste keys into chat or commit them.
- Human cutover checklist: [`docs/guides/hosted-supabase-cutover.md`](../guides/hosted-supabase-cutover.md).
