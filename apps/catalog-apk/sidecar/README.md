# Catalog APK — FlareSolverr sidecar

Real Chromium-based Cloudflare solver that runs **on the host** (Docker or native Windows binary). The APK does not embed a browser; it health-checks / auto-ensures this sidecar, then routes challenged fetches through FlareSolverr.

## Quick start (Windows)

```powershell
cd apps/catalog-apk/sidecar
.\start.ps1 -Agent          # Docker FlareSolverr :8191 + control agent :8192
# USB phone:
.\adb-reverse.ps1
```

Stop:

```powershell
.\stop.ps1 -Agent
```

Native (no Docker) fallback is automatic if `docker` is missing (uses `data-pipeline/scripts/start-flaresolverr-native.ps1`).

## Ports

| Port | Service |
|------|---------|
| `8191` | FlareSolverr API (`/v1` commands, `/` health) |
| `8192` | Control agent (`POST /v1/ensure` starts compose if down) |

Defaults bind **`127.0.0.1`**. For LAN phones, only on a trusted network:

```powershell
.\start.ps1 -Bind 0.0.0.0 -Agent -Token "your-long-token"
```

Then set in Catalog APK → Projects → FlareSolverr sidecar:

- Agent URL: `http://<pc-lan-ip>:8192`
- Token: same as `-Token`
- FlareSolverr URL on targets: `http://<pc-lan-ip>:8191/v1`

Prefer **USB + `adb-reverse.ps1`** over opening `0.0.0.0` when possible.

The Catalog APK needs **no FlareSolverr settings** — it auto-tries `127.0.0.1` and `10.0.2.2` agents/APIs and only calls ensure when a Cloudflare challenge is detected.

## Control agent API

- `GET /health` — agent up + whether FlareSolverr is healthy (no token)
- `GET /v1/status` — detailed status (`X-Sidecar-Token`)
- `POST /v1/ensure` — `docker compose up -d` if needed, wait until healthy
- `POST /v1/stop` — compose stop

Token header: `X-Sidecar-Token: catalog-apk-dev` (override with `SIDECAR_TOKEN`).

## Emulator

Use FlareSolverr URL `http://10.0.2.2:8191/v1` (Android emulator → host loopback). Agent: `http://10.0.2.2:8192`.

## Overnight

```powershell
.\start.ps1 -Agent
.\watchdog.ps1
```

## Security

- Do not expose agent/FlareSolverr to the public internet.
- Change `SIDECAR_TOKEN` when binding beyond loopback.
- Same ToS / robots obligations as the Catalog APK scrape targets.
