/**
 * Assert /staff hub tiles use module_access filtering (parity with sidebar).
 * Run: node apps/web/scripts/assert-staff-hub-module-access.mjs
 */

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "../../..");

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

const hub = readFileSync(
  join(root, "apps/web/app/(staff)/staff/page.tsx"),
  "utf8",
);
assert(
  hub.includes("filterNavTreeForModuleAccess"),
  "hub must filter with filterNavTreeForModuleAccess",
);
assert(
  !hub.includes("filterNavTreeForRoles("),
  "hub must not use roles-only filterNavTreeForRoles",
);
assert(
  hub.includes("ctx?.moduleAccess") || hub.includes("ctx.moduleAccess"),
  "hub must pass moduleAccess into filter",
);

const nav = readFileSync(join(root, "apps/web/components/staff-nav.tsx"), "utf8");
assert(
  nav.includes("filterNavTreeForModuleAccess"),
  "sidebar must keep module_access filter",
);

console.log("assert-staff-hub-module-access: ok");
