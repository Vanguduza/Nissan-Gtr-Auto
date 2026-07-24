# Exclusion grep evidence (Phase 15)

- Date: 2026-07-24
- Aligns with: `.github/workflows/ci.yml` job `exclusions`, `docs/HARDENING.md` §6, `.cursor/BUGBOT.md`
- Result: **CLEAN** (no product-path hits)

Standing exclusions are never Done-as-feature. Migrations/tests may mention denials; this gate scans **client / bridge / pipeline / edge SDK-shaped** paths per CI.

## Commands (CI-equivalent)

Run from repo root (ripgrep). Skip missing roots if any.

```bash
# Roots (same as CI)
CLIENT_ROOTS=(apps packages bridges data-pipeline/data_pipeline data-pipeline/tests data-pipeline/schemas)

# ZIMRA / FDMS / fiscalisation (client)
rg -n -i --glob '!**/*.md' --glob '!**/.venv/**' --glob '!**/node_modules/**' \
  'zimra|fdms|fiscali[sz]' "${CLIENT_ROOTS[@]}"

# Payroll tax (client)
rg -n -i --glob '!**/*.md' --glob '!**/.venv/**' --glob '!**/node_modules/**' \
  'paye\b|nssa|pobs|apwcs|zimdef|\bp4a?\b' "${CLIENT_ROOTS[@]}"

# HTML5 / browser QR (client)
rg -n -i --glob '!**/*.md' --glob '!**/.venv/**' --glob '!**/node_modules/**' \
  'html5-qrcode|Html5Qrcode|barcode-detector|jsqr\b|@zxing/browser' "${CLIENT_ROOTS[@]}"

# Edge: ZIMRA/FDMS SDK/URL shapes
rg -n -i --glob '!**/*.md' \
  'zimra\.(gov|com)|fdms\.|/fiscali[sz]|@zimra/' supabase/functions

# Edge + migrations: HTML5 QR libs
rg -n -i --glob '!**/*.md' \
  'html5-qrcode|Html5Qrcode|@zxing/browser' supabase/functions
rg -n -i \
  'html5-qrcode|Html5Qrcode|@zxing/browser' supabase/migrations
```

Empty output / exit code 1 from `rg` (no matches) = pass. Any hit = fail.

## Results (2026-07-24 audit)

| Scan | Paths | Result |
|------|-------|--------|
| `zimra\|fdms\|fiscali[sz]` | `apps`, `packages`, `bridges`, `data-pipeline/{data_pipeline,tests,schemas}` | OK — no matches |
| `paye\b\|nssa\|pobs\|apwcs\|zimdef\|\bp4a?\b` | same | OK — no matches |
| `html5-qrcode\|Html5Qrcode\|barcode-detector\|jsqr\b\|@zxing/browser` | same | OK — no matches |
| `zimra\.(gov\|com)\|fdms\.\|/fiscali[sz]\|@zimra/` | `supabase/functions` | OK — no matches |
| `html5-qrcode\|Html5Qrcode\|@zxing/browser` | `supabase/functions` | OK — no matches |
| `html5-qrcode\|Html5Qrcode\|@zxing/browser` | `supabase/migrations` | OK — no matches |

## CI cross-check

- Workflow: `.github/workflows/ci.yml` → job `exclusions`
- Local ops note: `docs/HARDENING.md` §4–6
