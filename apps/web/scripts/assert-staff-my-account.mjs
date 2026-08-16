/**
 * Assert Staff My Account nav/gate + photo / business-card polish.
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
assert(auth.includes("moduleIdForPath"), "moduleIdForPath for path/module_access gates");
assert(
  auth.includes("ctx.moduleAccess") || auth.includes("moduleAccess"),
  "canAccessPath must consider moduleAccess",
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
assert(lib.includes("uploadMyStaffPhoto"), "self photo upload helper");
assert(lib.includes("EMPLOYEE_PHOTOS_BUCKET"), "employee-photos bucket");
assert(lib.includes("downloadMyBusinessCard"), "business card download");

const panel = readFileSync(
  join(root, "apps/web/components/staff-account-panel.tsx"),
  "utf8",
);
assert(panel.includes("uploadMyStaffPhoto"), "panel wires photo upload");
assert(panel.includes("downloadMyBusinessCard"), "panel wires business card");
assert(panel.includes('type="file"'), "file input for photo (no HTML5 QR)");

const mig = readFileSync(
  join(root, "supabase/migrations/20260816020000_staff_my_account.sql"),
  "utf8",
);
assert(mig.includes("ADD COLUMN IF NOT EXISTS address"), "address column");
assert(mig.includes("update_my_staff_profile"), "update RPC");
assert(mig.includes("list_my_payslip_history"), "history RPC");

const photoMig = readFileSync(
  join(root, "supabase/migrations/20260816030000_employee_photos_self_upload.sql"),
  "utf8",
);
assert(photoMig.includes("employee-photos"), "employee-photos bucket");
assert(photoMig.includes("p_photo_storage_path"), "photo path on update RPC");

console.log("assert-staff-my-account: ok");
