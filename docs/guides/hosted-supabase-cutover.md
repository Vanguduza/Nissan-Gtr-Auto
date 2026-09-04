# Hosted Supabase cutover

Point clients at the active hosted control/commerce project instead of local Docker (`http://127.0.0.1:54321`) or the retired hosted project.

## Active project

| Field | Value |
|-------|--------|
| Ref | `bicyjghgdnzlnjqxzoud` |
| API URL | `https://bicyjghgdnzlnjqxzoud.supabase.co` |
| Heavy EPC data | Cloudflare R2 through `catalog-live-r2` |

Retired project `gylrgwqyuiwkyykardwc` must remain intact until Auth identities/roles, R2 serving manifests, client environments, and end-to-end flows are verified on the replacement project.

**Never commit** publishable/anon keys, `service_role`, database passwords, OAuth secrets, or Cloudflare R2 credentials. Put them only in gitignored files, deployment secret stores, Supabase Dashboard, or `supabase secrets`.

Related: [`docs/CUSTOMER_OAUTH_SETUP.md`](../CUSTOMER_OAUTH_SETUP.md), [`docs/LOCAL_DEVELOPMENT.md`](../LOCAL_DEVELOPMENT.md), root [`.env.example`](../../.env.example).

---

## 1. Get active-project credentials

1. Open Supabase Dashboard → project **bicyjghgdnzlnjqxzoud**.
2. **Project Settings → API**:
   - Project URL → `SUPABASE_URL` / `NEXT_PUBLIC_SUPABASE_URL`
   - publishable/anon key → browser + mobile client vars
   - `service_role` → server / Edge / tooling only; never mobile or `NEXT_PUBLIC_*`
3. **Project Settings → Database** only when a direct Postgres connection is genuinely required.

Local Docker JWT keys and the retired project's keys are not valid on the replacement project. Changing the URL without changing the matching client key will break Auth.

---

## 2. Env vars per client

### Web (`apps/web/.env.local` — gitignored)

| Variable | Source |
|----------|--------|
| `NEXT_PUBLIC_SUPABASE_URL` | `https://bicyjghgdnzlnjqxzoud.supabase.co` |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | replacement project publishable/anon key |
| `NEXT_PUBLIC_SITE_URL` | production `https://nissangtrauto.co.zw` or local `http://127.0.0.1:3000` |

Restart/redeploy after any public env change.

### Android customer (`apps/android-customer/local.properties` — gitignored)

| Variable | Source |
|----------|--------|
| `SUPABASE_URL` | replacement Project URL |
| `SUPABASE_ANON_KEY` | replacement publishable/anon key |
| `GOOGLE_WEB_CLIENT_ID` | Google Cloud Web OAuth client ID, when Google sign-in is enabled |

Do not set `rpc.forceFake=true` for a live cutover.

### iOS (`apps/ios/Secrets.xcconfig` — gitignored)

| Variable | Source |
|----------|--------|
| `SUPABASE_URL` | replacement Project URL |
| `SUPABASE_ANON_KEY` | replacement publishable/anon key |

Leave `STOREFRONT_FORCE_FAKE` unset/off for Live.

### Root / tooling (`.env` — gitignored)

Use replacement `SUPABASE_URL`, matching client key, and server-only service role. Never copy the service role into app/mobile/public variables.

---

## 3. Cloudflare R2 serving plane

Heavy catalog data is not restored to Supabase. The replacement project stores only lightweight hierarchy/release metadata plus `catalog_r2_serving_objects` registrations.

`catalog-live-r2` expects these **Supabase Edge Function secrets**:

- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_R2_ACCESS_KEY_ID`
- `CLOUDFLARE_R2_SECRET_ACCESS_KEY`
- `CLOUDFLARE_R2_BUCKET`
- optional `CLOUDFLARE_R2_ENDPOINT`

The R2 serving manifest is generated/uploaded from the local catalog pipeline, then registered through the service-role-only manifest ingest path. Do not invent manifest rows and do not repopulate heavy EPC tables in Supabase as a substitute.

Required live serving kinds include `vehicle_search`, `vehicle_fitment`, `section_parts`, `diagram_parts`, and `diagram_image`. `catalog-live-r2?action=health` must report the required kinds present and R2 configured before declaring live catalog serving green.

---

## 4. Link CLI and apply only pending migrations

```bash
npx supabase login
npx supabase link --project-ref bicyjghgdnzlnjqxzoud
npx supabase migration list
npx supabase db push
```

Then regenerate linked types only when required:

```bash
pnpm db:types:linked
```

Do **not** run `supabase db reset` against either hosted project. Do not recreate legacy `staff_catalog_v2_*`, full-Postgres catalog delivery, or retired customer catalog-fitment RPCs.

---

## 5. Auth is a cutover gate

The replacement project must contain the intended production identities and role/profile rows before staff E2E can pass. Do not create fake production users merely to make a smoke test green.

Hosted `supabase/config.toml` settings do not configure production GoTrue. Configure providers and URLs in the replacement project's Dashboard.

### Providers

1. Authentication → Providers → Google — enable and configure when used.
2. Authentication → Providers → Apple — enable when used.

Google Cloud hosted callback for the replacement project:

`https://bicyjghgdnzlnjqxzoud.supabase.co/auth/v1/callback`

### URL configuration

- Site URL: `https://nissangtrauto.co.zw`
- Redirect allow-list should include:
  - `https://nissangtrauto.co.zw/auth/callback`
  - `https://www.nissangtrauto.co.zw/auth/callback`
  - `http://127.0.0.1:3000/auth/callback` for hosted-backend development
  - `gtrcustomer://auth/callback`
  - `gtr-customer://auth/callback`

Keep the signup gate/hooks described in [`CUSTOMER_OAUTH_SETUP.md`](../CUSTOMER_OAUTH_SETUP.md) fail-closed.

---

## 6. Staff catalog validation

The supported staff path is:

`StaffCatalogBrowser → catalogGatewayGet → catalog-live-r2 → lightweight Supabase hierarchy + R2 technical objects`

The hierarchy actions are:

- `staff-families`
- `staff-variants`
- `staff-sections`
- `staff-diagrams`

Part/image actions are:

- `staff-section-parts`
- `staff-diagram-parts`
- `diagram-image`

There must be no app dependency on `staff_catalog_v2_*` RPCs.

---

## 7. Final cutover gates

Do not delete the retired project until all of the following are green:

- [ ] Replacement migrations applied and schema verified.
- [ ] `catalog-live-r2` active on `bicyjghgdnzlnjqxzoud`.
- [ ] R2 Edge secrets configured on replacement project.
- [ ] R2 serving objects/manifests registered from authoritative local output.
- [ ] `catalog-live-r2` health reports live browsing ready.
- [ ] Intended Auth users/profiles/staff roles exist on replacement project.
- [ ] Staff hierarchy → diagram → part/image path passes authenticated E2E.
- [ ] Customer auth/catalog/cart/order/payment critical paths pass on replacement project.
- [ ] Web production/preview env uses replacement URL + matching publishable key.
- [ ] Android/iOS live config uses replacement URL + matching publishable key.
- [ ] No service-role or R2 secrets exposed client-side.
- [ ] Build/typecheck/test gates pass, or any CI infrastructure outage is independently resolved and rerun.

Only after those gates pass may `gylrgwqyuiwkyykardwc` be deleted.
