import fs from "node:fs";

const p = "packages/supabase-client/src/database.types.ts";
let t = fs.readFileSync(p, "utf8");

const setGeo = `      set_delivery_job_geo: {
        Args: {
          p_delivery_job_id: string
          p_dropoff_lat?: number
          p_dropoff_lng?: number
          p_pickup_lat?: number
          p_pickup_lng?: number
        }
        Returns: string
      }
`;

if (!t.includes("set_delivery_job_geo:")) {
  if (!t.includes("set_driver_presence:")) {
    console.error("set_driver_presence anchor missing");
    process.exit(1);
  }
  t = t.replace("      set_driver_presence:", `${setGeo}      set_driver_presence:`);
  console.log("added set_delivery_job_geo");
} else {
  console.log("set_delivery_job_geo already present");
}

const oldStatus = `      update_delivery_job_status: {
        Args: {
          p_delivery_job_id: string
          p_status: Database["public"]["Enums"]["delivery_job_status"]
        }
        Returns: string
      }`;

const newStatus = `      update_delivery_job_status: {
        Args: {
          p_delivery_job_id: string
          p_status: Database["public"]["Enums"]["delivery_job_status"]
        }
        Returns: Json
      }`;

if (t.includes(oldStatus)) {
  t = t.replace(oldStatus, newStatus);
  console.log("updated update_delivery_job_status Returns → Json");
} else if (t.includes("update_delivery_job_status:") && t.includes("Returns: Json")) {
  console.log("update_delivery_job_status already Json");
} else {
  console.warn("update_delivery_job_status block not matched — check manually");
}

fs.writeFileSync(p, t);
console.log("done");
