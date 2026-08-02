# MCP enable checklist (Cursor UI)

1. Open **Cursor Settings → MCP** (or **Skills and Integrations → MCP**) and enable `github`, `supabase`, `postgres`, `context7`, `playwright` (leave `sentry` / `n8n-mcp` off until keys exist).
2. Paste secrets into gitignored `.cursor/mcp.json` where still `REPLACE_WITH_*` (GitHub PAT; Supabase access token from dashboard Account → Access Tokens). Do not commit this file.
3. Reload the window / restart Agent chat so tools appear; complete Sentry OAuth in-browser only if you enable Sentry — do not fake OAuth.
