/**
 * Assert /staff hub tiles + path gates use module_access (parity with sidebar).
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

const auth = readFileSync(join(root, "apps/web/lib/staff-auth.ts"), "utf8");
assert(auth.includes("moduleIdForPath"), "moduleIdForPath helper");
assert(
  auth.includes("moduleHrefForFilteredChildren"),
  "filterNavTreeForRoles rewrites inaccessible module href",
);
assert(
  /canAccessPath[\s\S]*moduleAccess/.test(auth),
  "canAccessPath must gate on moduleAccess",
);

const finance = readFileSync(
  join(root, "apps/web/components/staff-finance-dashboard.tsx"),
  "utf8",
);
assert(
  finance.includes("filterNavTreeForModuleAccess"),
  "finance desk tiles must use module_access filter",
);
assert(
  !finance.includes("filterNavTreeForRoles("),
  "finance desk must not use roles-only filter",
);

console.log("assert-staff-hub-module-access: ok");
