import { writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { validateWebEnvironment, APPROVED_SUPABASE_HOST } from "./web-environment.mjs";

/** Public runtime checks only: this cannot certify payments, staff RLS or devices. */
export async function checkPublicProduction(env, request = fetch) {
  const receipt = { scope: "unauthenticated_public_runtime", observed_at_utc: new Date().toISOString(), checks: [], native_business_acceptance: "NOT_ESTABLISHED" };
  const check = (name, passed, detail) => receipt.checks.push({ name, passed, detail });
  const errors = validateWebEnvironment(env);
  check("approved_project_configuration", errors.length === 0, errors.length ? errors.join(" ") : APPROVED_SUPABASE_HOST);
  if (errors.length) return receipt;
  const get = (url, options = {}) => request(url, { signal: AbortSignal.timeout(20_000), ...options });
  const site = "https://www.nissangtrauto.co.zw";
  try {
    const home = await get(site + "/");
    check("homepage_https", home.ok && new URL(home.url || site).protocol === "https:", `HTTP ${home.status}`);
    check("hsts", (home.headers.get("strict-transport-security") || "").includes("max-age="), "HTTPS response policy");
    const html = await home.text();
    const urls = [...new Set([...html.matchAll(/<script[^>]+src="([^"]+)"/g)].map((m) => new URL(m[1].replaceAll("&amp;", "&"), site)).filter((u) => u.origin === site && u.pathname.startsWith("/_next/static/")).map(String))];
    if (urls.length > 80) throw new Error("Unexpected asset inventory size; manual review required");
    const hosts = new Set();
    await Promise.all(urls.map(async (url) => {
      const asset = await get(url);
      if (!asset.ok) throw new Error("A runtime asset could not be read");
      for (const m of (await asset.text()).matchAll(/https:\/\/([a-z0-9]+\.supabase\.co)/g)) hosts.add(m[1]);
    }));
    check("served_backend_binding", urls.length > 0 && hosts.has(APPROVED_SUPABASE_HOST) && !hosts.has("gylrgwqyuiwkyykardwc.supabase.co"), { assets: urls.length, observed_hosts: [...hosts].sort() });
    const staff = await get(site + "/staff", { redirect: "manual" });
    const destination = new URL(staff.headers.get("location") || "/", site);
    check("staff_requires_signin", [302,303,307,308].includes(staff.status) && destination.origin === site && destination.pathname === "/login", `HTTP ${staff.status}`);
    const headers = { apikey: env.NEXT_PUBLIC_SUPABASE_ANON_KEY };
    const health = await get(`https://${APPROVED_SUPABASE_HOST}/auth/v1/health`, { headers, redirect: "error" });
    check("public_key_native_auth", health.ok, `HTTP ${health.status}`);
    const settings = await get(`https://${APPROVED_SUPABASE_HOST}/auth/v1/settings`, { headers, redirect: "error" });
    const config = settings.ok ? await settings.json() : {};
    check("customer_google_provider", settings.ok && config.external?.google === true, "Provider enablement; login and redirect acceptance remain separate");
  } catch {
    check("runtime_observation_completed", false, "A bounded public request failed; inspect native provider/network access without exposing credential values");
  }
  return receipt;
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  const receipt = await checkPublicProduction(process.env);
  const serialized = JSON.stringify(receipt, null, 2) + "\n";
  const index = process.argv.indexOf("--output");
  if (index >= 0 && process.argv[index + 1]) await writeFile(process.argv[index + 1], serialized);
  process.stdout.write(serialized);
  process.exitCode = receipt.checks.every((c) => c.passed) ? 0 : 1;
}
