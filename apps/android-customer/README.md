# GTR Customer — Android

Customer shell with thin Compose scaffolds for **cart**, **orders**, **My Garage**,
**wishlist**, **compare**, **reviews**, **ContiPay / Paynow intent create**, **live chat**,
and **active delivery track** — mirroring web AuthZ RPCs in
`apps/web/lib/customer-storefront.ts`, wishlist/compare/reviews helpers, chat helpers,
and privacy-safe track in `apps/web/lib/customer-delivery-track.ts`.

## Prerequisites

- JDK 17+
- Android SDK (API 34)
- Wrapper jar (if missing): `gradle wrapper --gradle-version 8.7`

## Module layout

| Module | Package | Role |
|--------|---------|------|
| `:app` | `co.zw.nissangtr.customer` | Launcher + route shell + auth gate |
| `:core:rpc` | `…customer.rpc` | `RpcClient` + `FakeRpcClient` + `SupabaseRpcClient` + `RpcNames` |
| `:feature:auth` | `…customer.auth` | `SignInScreen` + `AuthGate` (GoTrue email/password + Google ID token) |
| `:feature:cart` | `…customer.cart` | Create / add line / checkout |
| `:feature:orders` | `…customer.orders` | Invoice list + `get_customer_order` |
| `:feature:garage` | `…customer.garage` | Upsert / delete / list vehicles |
| `:feature:wishlist` | `…customer.wishlist` | List / add / remove / notify / move-to-cart |
| `:feature:compare` | `…customer.compare` | Auth compare RPCs + `GuestCompareStore` |
| `:feature:reviews` | `…customer.reviews` | Submit / list / stats / photo attach |
| `:feature:pay` | `…customer.pay` | ContiPay + Paynow intent create |
| `:feature:chat` | `…customer.chat` | Live chat threads / messages / composer |
| `:feature:track` | `…customer.track` | Active delivery last-point + ETA (`get_delivery_track_point`) |
| `:pod-camera` | `bridges/android/pod-camera` | Bridge-First CameraX still capture (review photos) |

## Screens (scaffolds)

| Screen | Module | RPCs / role |
|--------|--------|-------------|
| `SignInScreen` / `AuthGate` | `:feature:auth` | GoTrue `signInWith(Email)` + Google Credential Manager → `signInWith(IDToken)`; then `ensure_own_customer` if needed |
| `CartScreen` | `:feature:cart` | `create_customer_cart`, `add_customer_cart_line`, `checkout_customer_cart` |
| `OrdersScreen` | `:feature:orders` | `get_customer_order` (+ own-invoice SELECT) |
| `GarageScreen` | `:feature:garage` | `upsert_customer_garage_vehicle`, `delete_customer_garage_vehicle` |
| `WishlistScreen` | `:feature:wishlist` | `add_customer_wishlist_item`, `remove_customer_wishlist_item`, `set_wishlist_notify_when_in_stock`, `wishlist_move_to_cart` (+ wishlist SELECT) |
| `CompareScreen` | `:feature:compare` | `list_customer_compare_items`, `add_customer_compare_item`, `remove_customer_compare_item` (guest: SharedPreferences) |
| `ReviewsScreen` | `:feature:reviews` | `submit_customer_product_review`, `get_product_review_stats`, `add_customer_product_review_photo` (+ Storage `review-photos`) |
| `PayIntentScreen` | `:feature:pay` | `create_customer_contipay_intent`, `create_customer_paynow_intent` |
| `ChatScreen` | `:feature:chat` | `start_chat_thread`, `post_chat_message`, `mark_chat_thread_read`, `chat_unread_count` (+ thread/message SELECT) |
| `DeliveryTrackScreen` | `:feature:track` | `get_delivery_track_point` (job id and/or share token) — last point + ETA only |

## Wishlist / compare / reviews

Home → **Wishlist** / **Compare** / **Reviews**.

### Wishlist

- List via PostgREST `customer_wishlist_items` (+ `stock_items` embed) / Fake in-memory
- Add / remove by OEM (or id)
- Toggle **Notify when back in stock** → `set_wishlist_notify_when_in_stock`
- **Move to cart** → ensures open cart then `wishlist_move_to_cart` (removes wishlist row)

### Compare

- Signed-in (Live) or Fake: `list` / `add` / `remove` RPCs + OEM/description matrix (subset)
- Guest (not signed in): `GuestCompareStore` SharedPreferences OEM list (web localStorage parity)
- On Live sign-in: guest OEMs are pushed via `add_customer_compare_item` then list mirrored locally
- Soft cap: 8 items (`RpcNames.MAX_COMPARE_ITEMS`)

### Reviews

- Own list + approved-by-OEM + `get_product_review_stats`
- Submit → `submit_customer_product_review`
- Photo: **Bridge-First** `PodCameraBridge` (`bridges/android/pod-camera`) when attached; else gallery picker → cache file → Storage bucket `review-photos` → `add_customer_product_review_photo`
- Never WebView / HTML5 camera

### Fake demo (no Supabase)

1. Leave `SUPABASE_URL` / key unset (or `rpc.forceFake=true`).
2. `.\gradlew.bat assembleDebug` → install debug APK.
3. Home → **Wishlist** — seed OEMs `15208-65F0C` / `16546-EB70A`; toggle notify; move to cart; add OEM.
4. Home → **Compare** — seed oil filter; add OEM; matrix when ≥2 items.
5. Home → **Reviews** — load stats for `15208-65F0C`; submit; attach gallery (or bridge camera on device).

## Active delivery track (privacy)

Home → **Track delivery**, or Orders → select `INV-SEED-DISPATCH` → **Track delivery**.

- Calls `get_delivery_track_point` only — **never** a GPS trail UI.
- Polls **~8s** while active; stops when terminal.
- Deep links: `gtrcustomer://track/{token}` or intent extras `track_token` / `track_job_id`.
- Auth callback: `gtrcustomer://auth/callback` (Supabase OAuth / email confirm; native Google uses ID token and does not require the browser redirect).

## Google Sign-In (local.properties)

| Key | Role |
|-----|------|
| `GOOGLE_WEB_CLIENT_ID` | **Web** OAuth client ID → BuildConfig `GOOGLE_WEB_CLIENT_ID` (Credential Manager `serverClientId`) |
| `GOOGLE_SERVER_CLIENT_ID` | Alias for the same Web client ID if `GOOGLE_WEB_CLIENT_ID` is unset |

Also required outside the app (human / Google Cloud):

1. **Android** OAuth client: package `co.zw.nissangtr.customer` + debug/release **SHA-1** (`.\gradlew.bat :app:signingReport`).
2. Supabase Dashboard → Auth → Google enabled; Web client ID (+ secret) + authorized native client IDs.
3. Redirect allow-list includes `gtrcustomer://auth/callback` (see `docs/CUSTOMER_OAUTH_SETUP.md`).

Never commit client secrets. The Android client ID is not embedded — only the Web client ID.

## RPC binding: Fake vs Live

| Mode | When | Implementation |
|------|------|----------------|
| **Live** | `SUPABASE_URL` + `SUPABASE_ANON_KEY` set and `rpc.forceFake` ≠ `true` | `SupabaseRpcClient` (postgrest-kt + auth-kt + storage-kt) |
| **Fake** | URL/key missing, or `rpc.forceFake=true` | `FakeRpcClient` (in-memory, including wishlist/compare/reviews) |

Put secrets in **`local.properties`** (gitignored). Fake treats session as signed-in for compare gating (iOS parity).

## Exclusions

- No ZIMRA / fiscal QR
- No HTML5 / WebView QR or camera — Bridge-First (`bridges/`) for review photos
- No payroll tax / ContiPay HMAC secrets
- No customer GPS trail UI

## Run / test

```bash
cd apps/android-customer
.\gradlew.bat assembleDebug
```

1. **Fake:** leave URL/key unset → Home → Wishlist / Compare / Reviews.
2. **Live:** set credentials, sign in as a **customer** user, exercise the same screens.

## Production release builds

All APK/AAB builds verify the canonical source lineage. Release variants additionally
run `:app:verifyAndroidReleaseConfiguration` and stop when Live configuration or a
non-debug signing identity is missing. Fake transport remains available only to
explicit debug callers (`BuildConfig.DEBUG`); it is unavailable in production.

Apps now target Android 16 / API 36 with AGP 8.9.1 and Gradle 8.11.1. Install SDK
platforms 34 (library/snapshot tests) and 36, plus build-tools 35.0.0. JDK 17 is the
CI baseline. Google Play's requirement page, updated 2026-10-28, requires API 36
for new apps/updates after 2026-08-31; an internal debug APK is not a store release:
https://developer.android.com/google/play/requirements/target-sdk

Provide public Supabase configuration for the canonical HTTPS project and these
signing inputs through environment variables, Gradle properties, or ignored
`local.properties` (never commit a keystore or passwords):

- `GTR_ANDROID_KEYSTORE_PATH` — existing production keystore; relative paths use this app root.
- `GTR_ANDROID_STORE_PASSWORD` — keystore password.
- `GTR_ANDROID_KEY_ALIAS` — production private-key alias, never `androiddebugkey`.
- `GTR_ANDROID_KEY_PASSWORD` — private-key password.

The gate checks certificate identity and private-key access using JDK `keytool`;
passwords travel through child environment variables, never process arguments.
No production signing identity is generated by the build. Configure and retain
that identity outside this repository before distributing updates.

`.github/workflows/android-apps.yml` tests/builds debug variants for all three
apps, preserving the locked customer screenshots. The manual
`android-release.yml` workflow requires the configured repository variables and
secrets and produces signed artifacts plus source/canonical-state/SHA-256
provenance; it does not publish to an app store. Device, OAuth redirect/SHA-1,
Android 16 inset behavior, hardware and offline/recovery acceptance still need
actual target-device evidence.

Customer Release also requires `GOOGLE_WEB_CLIENT_ID` (or `GOOGLE_SERVER_CLIENT_ID`) for native Google sign-in. Garage saves retain the published vehicle key and explicit chassis/model identity without exposing technical catalogue data.

Build artifacts also pass `scripts/android_native_alignment.py`: every 64-bit
native library must have 16KB ELF load alignment, and uncompressed APK native
entries must be 16KB ZIP-aligned. CameraX 1.4.2 supplies the compatible image-processing
library. The signed artifact workflow runs `verify-head` before release packaging;
opaque Supabase publishable keys still need authenticated tenant acceptance.
