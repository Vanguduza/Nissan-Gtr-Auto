const fs = require("fs");
const path = "packages/supabase-client/src/database.types.ts";
let t = fs.readFileSync(path, "utf8");

function addJobCols(block) {
  let out = block;
  if (!out.includes("failure_reason_code:")) {
    out = out.replace(
      /(failure_reason: string \| null\n)/,
      `$1          failure_reason_code: Database["public"]["Enums"]["delivery_failure_reason"] | null\n          reattempt_of: string | null\n          route_sequence: number | null\n`,
    );
  }
  if ((out.match(/failure_reason_code\?:/g) || []).length < 2) {
    out = out.replace(
      /(failure_reason\?: string \| null\n)/g,
      `$1          failure_reason_code?: Database["public"]["Enums"]["delivery_failure_reason"] | null\n          reattempt_of?: string | null\n          route_sequence?: number | null\n`,
    );
  }
  if (!out.includes("delivery_jobs_reattempt_of_fkey")) {
    out = out.replace(
      /(foreignKeyName: "delivery_jobs_delivery_note_id_fkey"\n            columns: \["delivery_note_id"\]\n            isOneToOne: false\n            referencedRelation: "delivery_notes"\n            referencedColumns: \["id"\]\n          },)\n        \]\n      \}/,
      `$1\n          {\n            foreignKeyName: "delivery_jobs_reattempt_of_fkey"\n            columns: ["reattempt_of"]\n            isOneToOne: false\n            referencedRelation: "delivery_jobs"\n            referencedColumns: ["id"]\n          },\n        ]\n      }`,
    );
  }
  return out;
}

const start = t.indexOf("delivery_jobs: {");
const endMarker = "\n      delivery_track_tokens:";
const end = t.indexOf(endMarker, start);
if (start < 0 || end < 0) throw new Error("delivery_jobs bounds not found");
const jobsBlock = t.slice(start, end);
const newJobs = addJobCols(jobsBlock);
t = t.slice(0, start) + newJobs + t.slice(end);

const podOtpsTable = `      delivery_pod_otps: {
        Row: {
          attempts: number
          code_hash: string
          created_at: string
          delivery_job_id: string
          expires_at: string
          id: string
          max_attempts: number
          verified_at: string | null
        }
        Insert: {
          attempts?: number
          code_hash: string
          created_at?: string
          delivery_job_id: string
          expires_at: string
          id?: string
          max_attempts?: number
          verified_at?: string | null
        }
        Update: {
          attempts?: number
          code_hash?: string
          created_at?: string
          delivery_job_id?: string
          expires_at?: string
          id?: string
          max_attempts?: number
          verified_at?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "delivery_pod_otps_delivery_job_id_fkey"
            columns: ["delivery_job_id"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
        ]
      }
`;

const panicTable = `      panic_events: {
        Row: {
          acknowledged_at: string | null
          acknowledged_by: string | null
          created_at: string
          delivery_job_id: string | null
          driver_user_id: string
          id: string
          lat: number | null
          lng: number | null
        }
        Insert: {
          acknowledged_at?: string | null
          acknowledged_by?: string | null
          created_at?: string
          delivery_job_id?: string | null
          driver_user_id: string
          id?: string
          lat?: number | null
          lng?: number | null
        }
        Update: {
          acknowledged_at?: string | null
          acknowledged_by?: string | null
          created_at?: string
          delivery_job_id?: string | null
          driver_user_id?: string
          id?: string
          lat?: number | null
          lng?: number | null
        }
        Relationships: [
          {
            foreignKeyName: "panic_events_delivery_job_id_fkey"
            columns: ["delivery_job_id"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "panic_events_driver_user_id_fkey"
            columns: ["driver_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "panic_events_acknowledged_by_fkey"
            columns: ["acknowledged_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
`;

if (!t.includes("delivery_pod_otps:")) {
  t = t.replace(/(\n      driver_presence: \{)/, `\n${podOtpsTable}${panicTable}$1`);
}

const submitOld = `submit_delivery_pod: {
        Args: {
          p_delivery_job_id: string
          p_notes?: string
          p_pod_photo_path: string
          p_pod_signature_path: string
        }
        Returns: string
      }`;
const submitNew = `submit_delivery_pod: {
        Args: {
          p_delivery_job_id: string
          p_notes?: string
          p_otp_code: string
          p_pod_photo_path: string
          p_pod_signature_path: string
        }
        Returns: string
      }`;
if (t.includes(submitOld)) t = t.replace(submitOld, submitNew);
else if (!t.includes("p_otp_code: string")) {
  throw new Error("submit_delivery_pod block not matched");
}

const newRpcs = `      generate_delivery_pod_otp: {
        Args: { p_delivery_job_id: string; p_ttl?: string }
        Returns: string
      }
      verify_delivery_pod_otp: {
        Args: { p_delivery_job_id: string; p_code: string }
        Returns: boolean
      }
      delivery_geofence_suggestion: {
        Args: {
          p_arrive_radius_m?: number
          p_complete_radius_m?: number
          p_delivery_job_id: string
          p_lat: number
          p_lng: number
        }
        Returns: {
          distance_m: number | null
          suggest_arrive: boolean
          suggest_complete: boolean
        }[]
      }
      fail_delivery_job: {
        Args: {
          p_create_reattempt?: boolean
          p_delivery_job_id: string
          p_notes?: string
          p_reason: Database["public"]["Enums"]["delivery_failure_reason"]
        }
        Returns: string
      }
      raise_delivery_panic: {
        Args: {
          p_delivery_job_id?: string
          p_lat?: number
          p_lng?: number
        }
        Returns: string
      }
      optimize_driver_stops: {
        Args: { p_driver_user_id: string }
        Returns: {
          delivery_job_id: string
          distance_m: number | null
          route_sequence: number
        }[]
      }
`;

if (!t.includes("generate_delivery_pod_otp:")) {
  if (!t.includes("\n      get_delivery_track_point: {")) {
    throw new Error("get_delivery_track_point anchor missing");
  }
  t = t.replace("\n      get_delivery_track_point: {", `\n${newRpcs}\n      get_delivery_track_point: {`);
}

if (!t.includes("delivery_failure_reason:")) {
  t = t.replace(
    'delivery_eta_source: "haversine" | "osrm" | "manual"\n',
    'delivery_eta_source: "haversine" | "osrm" | "manual"\n      delivery_failure_reason:\n        | "customer_absent"\n        | "refused"\n        | "wrong_address"\n        | "damaged"\n        | "other"\n',
  );
  t = t.replace(
    'delivery_eta_source: ["haversine", "osrm", "manual"],\n',
    'delivery_eta_source: ["haversine", "osrm", "manual"],\n      delivery_failure_reason: [\n        "customer_absent",\n        "refused",\n        "wrong_address",\n        "damaged",\n        "other",\n      ],\n',
  );
}

fs.writeFileSync(path, t);
console.log("patched ok", {
  pod: t.includes("delivery_pod_otps:"),
  panic: t.includes("panic_events:"),
  otpRpc: t.includes("generate_delivery_pod_otp:"),
  failEnum: t.includes("delivery_failure_reason"),
  otpArg: t.includes("p_otp_code: string"),
  reattempt: t.includes("reattempt_of: string | null"),
  route: t.includes("route_sequence: number | null"),
});
