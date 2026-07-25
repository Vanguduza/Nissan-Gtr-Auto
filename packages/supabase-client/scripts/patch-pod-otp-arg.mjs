import fs from "node:fs";

const p = "packages/supabase-client/src/database.types.ts";
let t = fs.readFileSync(p, "utf8");

if (t.includes("p_otp_code")) {
  console.log("types already include p_otp_code");
  process.exit(0);
}

const re =
  /submit_delivery_pod: \{\s*Args: \{[\s\S]*?\}\s*Returns: string\s*\}/;
const neu = `submit_delivery_pod: {
        Args: {
          p_delivery_job_id: string
          p_notes?: string
          p_otp_code: string
          p_pod_photo_path: string
          p_pod_signature_path: string
        }
        Returns: string
      }`;

if (!re.test(t)) {
  console.error("submit_delivery_pod block not found");
  process.exit(1);
}
t = t.replace(re, neu);
fs.writeFileSync(p, t);
console.log("patched submit_delivery_pod otp arg");
