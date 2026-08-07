/**
 * One-shot: push local-dev seed Auth users + staff roles to hosted Supabase.
 * Reads root .env (SUPABASE_URL, SUPABASE_SERVICE_KEY). Never commits secrets.
 *
 * Usage: node scripts/seed-hosted-dev-users.mjs
 */
import { createClient } from "@supabase/supabase-js";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

function loadEnv(path) {
  const text = readFileSync(path, "utf8");
  const out = {};
  for (const line of text.split(/\r?\n/)) {
    if (!line || line.trim().startsWith("#")) continue;
    const i = line.indexOf("=");
    if (i < 1) continue;
    const k = line.slice(0, i).trim();
    let v = line.slice(i + 1).trim();
    if (
      (v.startsWith('"') && v.endsWith('"')) ||
      (v.startsWith("'") && v.endsWith("'"))
    ) {
      v = v.slice(1, -1);
    }
    out[k] = v;
  }
  return out;
}

const env = loadEnv(resolve(process.cwd(), ".env"));
const url = env.SUPABASE_URL || env.NEXT_PUBLIC_SUPABASE_URL;
const serviceKey = env.SUPABASE_SERVICE_KEY;
if (!url || !serviceKey) {
  console.error("Missing SUPABASE_URL or SUPABASE_SERVICE_KEY in root .env");
  process.exit(1);
}
if (/127\.0\.0\.1|localhost/.test(url)) {
  console.error("Refusing to run against local URL — set hosted SUPABASE_URL in .env");
  process.exit(1);
}

const admin = createClient(url, serviceKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

/** @type {{ id: string, email: string, password: string, fullName: string, role?: string, customer?: { id: string } }[]} */
const USERS = [
  {
    id: "a0000000-0000-4000-8000-000000000001",
    email: "admin@gtr.local",
    password: "local-dev-admin",
    fullName: "Local Admin",
    role: "admin",
  },
  {
    id: "a0000000-0000-4000-8000-000000000002",
    email: "finance@gtr.local",
    password: "local-dev-finance",
    fullName: "Local Finance",
    role: "finance",
  },
  {
    id: "a0000000-0000-4000-8000-000000000003",
    email: "warehouse@gtr.local",
    password: "local-dev-warehouse",
    fullName: "Local Warehouse",
    role: "warehouse",
  },
  {
    id: "c0000000-0000-4000-8000-0000000000a1",
    email: "storefront-a@gtr.local",
    password: "local-dev-customer",
    fullName: "Storefront A",
    customer: { id: "c1000000-0000-4000-8000-0000000000a1" },
  },
  {
    id: "c0000000-0000-4000-8000-0000000000b2",
    email: "storefront-b@gtr.local",
    password: "local-dev-customer",
    fullName: "Storefront B",
    customer: { id: "c1000000-0000-4000-8000-0000000000b2" },
  },
];

async function findUserIdByEmail(email) {
  // Paginate lightly — hosted projects are small for this seed set.
  for (let page = 1; page <= 20; page++) {
    const { data, error } = await admin.auth.admin.listUsers({
      page,
      perPage: 200,
    });
    if (error) throw error;
    const hit = data.users.find(
      (u) => (u.email || "").toLowerCase() === email.toLowerCase(),
    );
    if (hit) return hit.id;
    if (data.users.length < 200) break;
  }
  return null;
}

async function upsertAuthUser(u) {
  const via = u.role ? "hr_onboarding" : "auth_otp";
  const existingId = await findUserIdByEmail(u.email);
  if (existingId) {
    const { data, error } = await admin.auth.admin.updateUserById(existingId, {
      password: u.password,
      email_confirm: true,
      user_metadata: { full_name: u.fullName },
      app_metadata: {
        provider: "email",
        providers: ["email"],
        gtr_provisioned_via: via,
      },
    });
    if (error) throw error;
    return { id: data.user.id, created: false };
  }

  const { data, error } = await admin.auth.admin.createUser({
    id: u.id,
    email: u.email,
    password: u.password,
    email_confirm: true,
    user_metadata: { full_name: u.fullName },
    app_metadata: {
      provider: "email",
      providers: ["email"],
      gtr_provisioned_via: via,
    },
  });
  if (error) throw error;
  return { id: data.user.id, created: true };
}

async function ensureProfile(userId, fullName) {
  const { error } = await admin.from("profiles").upsert(
    { id: userId, full_name: fullName, is_staff: false },
    { onConflict: "id" },
  );
  if (error) throw error;
}

async function ensureStaffRole(userId, role) {
  const { error } = await admin.from("staff_roles").upsert(
    { user_id: userId, role },
    { onConflict: "user_id,role" },
  );
  if (error) throw error;
}

async function ensureCustomer(userId, customerId, email, displayName) {
  const { error } = await admin.from("customers").upsert(
    {
      id: customerId,
      display_name: displayName,
      email,
      currency: "USD",
      profile_id: userId,
    },
    { onConflict: "id" },
  );
  if (error) throw error;
}

async function main() {
  console.log(`Seeding hosted Auth users on ${url}`);
  for (const u of USERS) {
    process.stdout.write(`- ${u.email} … `);
    const { id, created } = await upsertAuthUser(u);
    await ensureProfile(id, u.fullName);
    if (u.role) await ensureStaffRole(id, u.role);
    if (u.customer) {
      await ensureCustomer(id, u.customer.id, u.email, u.fullName);
    }
    console.log(created ? `created (${id})` : `updated (${id})`);
  }

  // Verify admin password grant with anon (same as the web app).
  const anon = env.SUPABASE_ANON_KEY || env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!anon) {
    console.warn("No anon key in .env — skip login probe");
    return;
  }
  const probe = createClient(url, anon, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { data, error } = await probe.auth.signInWithPassword({
    email: "admin@gtr.local",
    password: "local-dev-admin",
  });
  if (error || !data.session) {
    console.error("Admin login probe FAILED:", error?.message || "no session");
    process.exit(1);
  }
  console.log("Admin login probe OK (hosted)");
  await probe.auth.signOut();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
