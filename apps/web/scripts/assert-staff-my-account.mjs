/**
 * Assert Staff My Account nav/gate wiring (no test runner in apps/web yet).
 * Run: node apps/web/scripts/assert-staff-my-account.mjs
 */

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "../../..");

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

const auth = readFileSync(join(root, "apps/web/lib/staff-auth.ts"), "utf8");
assert(auth.includes('href: "/staff/account"'), "STAFF_NAV_TREE missing /staff/account");
assert(auth.includes('label: "My Account"'), "My Account label missing");
assert(
  auth.includes('path === "/staff/account"') ||
    auth.includes('path.startsWith("/staff/account/")'),
  "pathAccessFor must allow /staff/account",
);

const page = readFileSync(
  join(root, "apps/web/app/(staff)/staff/account/page.tsx"),
  "utf8",
);
assert(page.includes("StaffAccountPanel"), "account page must render panel");

const lib = readFileSync(join(root, "apps/web/lib/staff-account.ts"), "utf8");
assert(lib.includes("get_my_staff_profile"), "RPC get_my_staff_profile");
assert(lib.includes("update_my_staff_profile"), "RPC update_my_staff_profile");
assert(lib.includes("list_my_payslip_history"), "RPC list_my_payslip_history");
assert(lib.includes("updateUser"), "email sync via GoTrue updateUser");

const mig = readFileSync(
  join(root, "supabase/migrations/20260816020000_staff_my_account.sql"),
  "utf8",
);
assert(mig.includes("ADD COLUMN IF NOT EXISTS address"), "address column");
assert(mig.includes("update_my_staff_profile"), "update RPC");
assert(mig.includes("list_my_payslip_history"), "history RPC");

console.log("assert-staff-my-account: ok");
