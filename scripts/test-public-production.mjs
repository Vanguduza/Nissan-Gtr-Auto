import assert from "node:assert/strict";
import { test } from "node:test";
import { checkPublicProduction } from "./check-public-production.mjs";

const env = { NEXT_PUBLIC_SUPABASE_URL: "https://bicyjghgdnzlnjqxzoud.supabase.co", NEXT_PUBLIC_SUPABASE_ANON_KEY: "sb_publishable_test_fixture_not_a_real_credential" };
function fixture(host = "bicyjghgdnzlnjqxzoud.supabase.co", google = true) {
  return async (url) => {
    const path = new URL(url).pathname;
    if (path === "/") return new Response('<script src="/_next/static/main.js"></script>', { headers: { "strict-transport-security": "max-age=31536000" } });
    if (path.startsWith("/_next/")) return new Response(`const project="https://${host}";`);
    if (path === "/staff") return new Response(null, { status: 307, headers: { location: "/login?next=%2Fstaff" } });
    if (path.endsWith("/settings")) return Response.json({ external: { google } });
    return Response.json({ healthy: true });
  };
}
test("public runtime receipt does not claim business acceptance", async () => {
  const r = await checkPublicProduction(env, fixture());
  assert.ok(r.checks.every((c) => c.passed));
  assert.equal(r.native_business_acceptance, "NOT_ESTABLISHED");
  assert.ok(!JSON.stringify(r).includes(env.NEXT_PUBLIC_SUPABASE_ANON_KEY));
});
test("retired served backend and disabled Google are failed observations", async () => {
  const r = await checkPublicProduction(env, fixture("gylrgwqyuiwkyykardwc.supabase.co", false));
  assert.equal(r.checks.find((c) => c.name === "served_backend_binding").passed, false);
  assert.equal(r.checks.find((c) => c.name === "customer_google_provider").passed, false);
});
test("missing credentials stop before any remote request", async () => {
  const r = await checkPublicProduction({}, () => { throw new Error("must not request"); });
  assert.equal(r.checks.length, 1);
  assert.equal(r.checks[0].passed, false);
});
