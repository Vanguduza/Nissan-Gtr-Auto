# Deploy `apps/web` on Vercel (continuous)

Monorepo root: GitHub `Vanguduza/Nissan-Gtr-Auto`.  
Vercel **Root Directory** must be `apps/web` (see `apps/web/vercel.json`).

## 1. Project settings

| Setting | Value |
|---------|--------|
| Framework | Next.js |
| Root Directory | `apps/web` |
| Install | `cd ../.. && pnpm install` |
| Build | `cd ../.. && pnpm --filter @gtr/web build` |
| Node | `20.x` |
| Production branch | `main` |
| Preview | all other branches / PRs |

## 2. Environment variables

Use the active hosted project `bicyjghgdnzlnjqxzoud`. Never put service-role or Cloudflare R2 credentials in Vercel browser/public variables.

| Name | Value |
|------|--------|
| `NEXT_PUBLIC_SUPABASE_URL` | `https://bicyjghgdnzlnjqxzoud.supabase.co` |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | replacement project publishable/anon key |
| `NEXT_PUBLIC_SITE_URL` | `https://nissangtrauto.co.zw` or the active `*.vercel.app` URL before DNS cutover |

Optional: `NEXT_PUBLIC_DEFAULT_WAREHOUSE_ID`, `NEXT_PUBLIC_ZIG_EXCHANGE_RATE`, `NEXT_PUBLIC_MAP_STYLE_URL`.

After changing Supabase public env values, **redeploy**. A URL change without the matching replacement-project publishable key will break Auth.

## 2b. Catalog runtime

The web app does not receive R2 credentials. Staff/customer catalog requests go through the authenticated Supabase Edge function `catalog-live-r2`, which joins lightweight Supabase hierarchy/commerce data to R2 technical objects.

Do not add the retired Supabase project's storage host as a catalog dependency. `apps/web/next.config.ts` permits normal app-owned public Supabase media without binding the app to a specific project ref; EPC diagram/part payloads use the gateway.

## 3. HTTPS / HSTS

Vercel terminates TLS and redirects HTTP→HTTPS on custom domains once SSL is issued. The app also provides:

| Layer | Behavior |
|-------|----------|
| `apps/web/middleware.ts` | Non-local HTTP → 308 HTTPS; HSTS on HTTPS responses |
| `apps/web/vercel.json` | HSTS platform backup |
| `publicSiteUrl()` | Coerces accidental non-local `http://` origins to HTTPS |

Local `localhost`, `127.0.0.1`, and `::1` remain exempt for development.

## 4. Live updates

1. Connect the GitHub repo in Vercel.
2. Push to the configured production branch → production deploy.
3. Push/open a PR → preview deploy when preview builds are enabled.

Local `pnpm --filter @gtr/web dev` does not update production.

## 5. Supabase Auth URLs

Configure the **replacement** Supabase project Authentication → URL configuration:

- `https://<project>.vercel.app/auth/callback`
- `https://nissangtrauto.co.zw/auth/callback`
- `https://www.nissangtrauto.co.zw/auth/callback`

If Google OAuth is enabled, the provider callback is:

`https://bicyjghgdnzlnjqxzoud.supabase.co/auth/v1/callback`

All hosted Auth Site URL / redirects must use HTTPS.

## 6. Production cutover gate

Do not point production DNS/users at a build solely because it compiled. Confirm:

- replacement Supabase URL + matching publishable key are present in Vercel;
- intended Auth users/roles exist on the replacement project;
- `catalog-live-r2` is active and its R2 secrets/manifests are configured;
- staff and customer critical E2E paths pass;
- no client bundle contains service-role or R2 credentials.

The retired project `gylrgwqyuiwkyykardwc` must not be deleted until these gates are green.

## 7. CLI (optional)

```bash
npx vercel link
npx vercel env pull
npx vercel --prod
```
