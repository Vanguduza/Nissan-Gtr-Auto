import fs from "node:fs";

const t = fs.readFileSync("packages/supabase-client/src/database.types.ts", "utf8");
const checks = [
  "driver_presence:",
  "delivery_track_tokens:",
  "suggest_delivery_assignees",
  "get_delivery_track_point",
  "set_driver_presence",
  "submit_delivery_pod",
  "mint_delivery_track_token",
  "assign_delivery_job",
  "delivery_eta_source",
  "driver_presence_status",
  '| "driver"',
];
let failed = false;
for (const c of checks) {
  const ok = t.includes(c);
  console.log(ok ? "OK" : "MISS", c);
  if (!ok) failed = true;
}
process.exit(failed ? 1 : 0);
