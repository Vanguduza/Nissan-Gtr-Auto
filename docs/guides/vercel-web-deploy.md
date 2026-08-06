# Deploy `apps/web` on Vercel (continuous)

Monorepo root: GitHub `Vanguduza/Nissan-Gtr-Auto`.  
Vercel **Root Directory** must be `apps/web` (see `apps/web/vercel.json`).

## 1. Project settings (Dashboard or CLI)

| Setting | Value |
|---------|--------|
| Framework | Next.js |
| Root Directory | `apps/web` |
| Install | `cd ../.. && pnpm install` |
| Build | `cd ../.. && pnpm --filter @gtr/web build` |
| Node | `20.x` (repo `engines`) |
| Production branch | `main` (auto-deploy on push) |
| Preview | all other branches / PRs |

## 2. Environment variables (Production + Preview)

From hosted Supabase (Dashboard → API) — **never** put service keys in Vercel:

| Name | Value |
|------|--------|
| `NEXT_PUBLIC_SUPABASE_URL` | `https://gylrgwqyuiwkyykardwc.supabase.co` |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | anon / publishable key |
| `NEXT_PUBLIC_SITE_URL` | **Must be `https://…`** — `https://nissangtrauto.co.zw` or the `*.vercel.app` URL until DNS is cut over. Never `http://` for prod/preview. |

Optional (same as local web): `NEXT_PUBLIC_DEFAULT_WAREHOUSE_ID`, `NEXT_PUBLIC_ZIG_EXCHANGE_RATE`, `NEXT_PUBLIC_MAP_STYLE_URL`.

After env changes: **Redeploy**.

## 2b. HTTPS / HSTS (defense-in-depth)

Vercel already terminates TLS and redirects HTTP→HTTPS on custom domains once SSL is issued. The app adds:

| Layer | Behavior |
|-------|----------|
| `apps/web/middleware.ts` | Non-local HTTP → **308** to HTTPS; sets `Strict-Transport-Security: max-age=31536000; includeSubDomains` on HTTPS responses |
| `apps/web/vercel.json` `headers` | Same HSTS value on all routes (platform backup) |
| `publicSiteUrl()` (`lib/site-url.ts`) | Coerces mis-set `http://` env origins to `https://` for non-local hosts (OAuth, metadata, PSP return URLs) |

**Local exempt:** `localhost`, `127.0.0.1`, and `::1` skip redirect and HSTS so `http://127.0.0.1:3000` keeps working. Use `NEXT_PUBLIC_SITE_URL=http://127.0.0.1:3000` only in `.env.local`.

`includeSubDomains` is intentional: apex + `www` are both on HTTPS ([company-domain decision](../decisions/2026-07-23-company-domain.md)). No HSTS `preload` flag (requires an explicit preload-list submission).

## 3. Live updates

1. Connect the GitHub repo in Vercel (Project → Settings → Git).
2. Push to `main` → Production deploy.
3. Push / open PR on other branches → Preview URL.

Local `pnpm --filter @gtr/web dev` does **not** update production; only Git pushes (or `npx vercel --prod`) do.

## 4. Supabase Auth URLs

Add the Vercel URL(s) to Supabase **Authentication → URL configuration** redirect allow-list, e.g.:

- `https://<project>.vercel.app/auth/callback`
- `https://nissangtrauto.co.zw/auth/callback`
- `https://www.nissangtrauto.co.zw/auth/callback` (if www is live)

All Auth **Site URL** / redirect entries for hosted envs must use **`https://`**, not `http://`.

## 5. CLI (optional)

```bash
npx vercel link          # root Directory: apps/web
npx vercel env pull      # optional
npx vercel --prod        # one-off prod deploy from local tree
```
