/**
 * Patch database.types.ts for dedicated delivery app P0 schema.
 * Prefer `pnpm db:types` after local supabase reset when Docker is available.
 */
import fs from "node:fs";

const path = new URL("../src/database.types.ts", import.meta.url);
let t = fs.readFileSync(path, "utf8");

const deliveryJobsBlock = `      delivery_jobs: {
        Row: {
          assignee_user_id: string | null
          completed_at: string | null
          completed_via: Database["public"]["Enums"]["delivery_completed_via"] | null
          created_at: string
          created_by: string | null
          delivery_note_id: string
          dispatched_at: string | null
          document_number: string | null
          dropoff_lat: number | null
          dropoff_lng: number | null
          eta_at: string | null
          eta_seconds: number | null
          eta_source: Database["public"]["Enums"]["delivery_eta_source"] | null
          eta_updated_at: string | null
          failed_at: string | null
          failure_reason: string | null
          id: string
          notes: string | null
          pickup_lat: number | null
          pickup_lng: number | null
          pod_photo_path: string | null
          pod_signature_path: string | null
          status: Database["public"]["Enums"]["delivery_job_status"]
          updated_at: string
        }
        Insert: {
          assignee_user_id?: string | null
          completed_at?: string | null
          completed_via?: Database["public"]["Enums"]["delivery_completed_via"] | null
          created_at?: string
          created_by?: string | null
          delivery_note_id: string
          dispatched_at?: string | null
          document_number?: string | null
          dropoff_lat?: number | null
          dropoff_lng?: number | null
          eta_at?: string | null
          eta_seconds?: number | null
          eta_source?: Database["public"]["Enums"]["delivery_eta_source"] | null
          eta_updated_at?: string | null
          failed_at?: string | null
          failure_reason?: string | null
          id?: string
          notes?: string | null
          pickup_lat?: number | null
          pickup_lng?: number | null
          pod_photo_path?: string | null
          pod_signature_path?: string | null
          status?: Database["public"]["Enums"]["delivery_job_status"]
          updated_at?: string
        }
        Update: {
          assignee_user_id?: string | null
          completed_at?: string | null
          completed_via?: Database["public"]["Enums"]["delivery_completed_via"] | null
          created_at?: string
          created_by?: string | null
          delivery_note_id?: string
          dispatched_at?: string | null
          document_number?: string | null
          dropoff_lat?: number | null
          dropoff_lng?: number | null
          eta_at?: string | null
          eta_seconds?: number | null
          eta_source?: Database["public"]["Enums"]["delivery_eta_source"] | null
          eta_updated_at?: string | null
          failed_at?: string | null
          failure_reason?: string | null
          id?: string
          notes?: string | null
          pickup_lat?: number | null
          pickup_lng?: number | null
          pod_photo_path?: string | null
          pod_signature_path?: string | null
          status?: Database["public"]["Enums"]["delivery_job_status"]
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "delivery_jobs_delivery_note_id_fkey"
            columns: ["delivery_note_id"]
            isOneToOne: false
            referencedRelation: "delivery_notes"
            referencedColumns: ["id"]
          },
        ]
      }
      delivery_track_tokens: {
        Row: {
          created_at: string
          delivery_job_id: string
          expires_at: string
          id: string
          revoked_at: string | null
          token_hash: string
        }
        Insert: {
          created_at?: string
          delivery_job_id: string
          expires_at: string
          id?: string
          revoked_at?: string | null
          token_hash: string
        }
        Update: {
          created_at?: string
          delivery_job_id?: string
          expires_at?: string
          id?: string
          revoked_at?: string | null
          token_hash?: string
        }
        Relationships: [
          {
            foreignKeyName: "delivery_track_tokens_delivery_job_id_fkey"
            columns: ["delivery_job_id"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
        ]
      }
      driver_presence: {
        Row: {
          capacity: number
          last_lat: number | null
          last_lng: number | null
          last_seen_at: string | null
          shift_ends_at: string | null
          shift_starts_at: string | null
          status: Database["public"]["Enums"]["driver_presence_status"]
          updated_at: string
          user_id: string
        }
        Insert: {
          capacity?: number
          last_lat?: number | null
          last_lng?: number | null
          last_seen_at?: string | null
          shift_ends_at?: string | null
          shift_starts_at?: string | null
          status?: Database["public"]["Enums"]["driver_presence_status"]
          updated_at?: string
          user_id: string
        }
        Update: {
          capacity?: number
          last_lat?: number | null
          last_lng?: number | null
          last_seen_at?: string | null
          shift_ends_at?: string | null
          shift_starts_at?: string | null
          status?: Database["public"]["Enums"]["driver_presence_status"]
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "driver_presence_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: true
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }`;

const jobsRe =
  /      delivery_jobs: \{[\s\S]*?Relationships: \[[\s\S]*?\]\n      \}\n      delivery_locations:/;
if (!jobsRe.test(t)) {
  throw new Error("delivery_jobs block not found");
}
t = t.replace(jobsRe, `${deliveryJobsBlock}\n      delivery_locations:`);

const rpcInsert = `      assign_delivery_job: {
        Args: {
          p_assignee_user_id: string
          p_delivery_job_id: string
          p_override?: boolean
        }
        Returns: string
      }
      assign_staff_role:`;

if (!t.includes("assign_delivery_job:")) {
  t = t.replace("      assign_staff_role:", rpcInsert);
}

const moreRpcs = `      get_delivery_track_point: {
        Args: { p_delivery_job_id?: string; p_token?: string }
        Returns: {
          delivery_job_id: string
          eta_at: string | null
          eta_seconds: number | null
          lat: number
          lng: number
          recorded_at: string
          status: Database["public"]["Enums"]["delivery_job_status"]
        }[]
      }
      has_staff_role:`;

if (!t.includes("get_delivery_track_point:")) {
  t = t.replace("      has_staff_role:", moreRpcs);
}

const mintRpc = `      mint_delivery_track_token: {
        Args: { p_delivery_job_id: string; p_ttl?: string }
        Returns: string
      }
      revoke_staff_role:`;

if (!t.includes("mint_delivery_track_token:")) {
  t = t.replace("      revoke_staff_role:", mintRpc);
}

const setPresence = `      set_driver_presence: {
        Args: {
          p_capacity?: number
          p_last_lat?: number
          p_last_lng?: number
          p_shift_ends_at?: string
          p_shift_starts_at?: string
          p_status: Database["public"]["Enums"]["driver_presence_status"]
        }
        Returns: string
      }
      set_loyalty_program_settings:`;

if (!t.includes("set_driver_presence:")) {
  t = t.replace("      set_loyalty_program_settings:", setPresence);
}

const suggestRpc = `      submit_delivery_pod: {
        Args: {
          p_delivery_job_id: string
          p_notes?: string
          p_pod_photo_path: string
          p_pod_signature_path: string
        }
        Returns: string
      }
      suggest_delivery_assignees: {
        Args: { p_delivery_job_id: string; p_limit?: number }
        Returns: {
          capacity: number
          distance_m: number | null
          last_lat: number | null
          last_lng: number | null
          last_seen_at: string | null
          open_jobs: number
          status: Database["public"]["Enums"]["driver_presence_status"]
          user_id: string
        }[]
      }
      update_delivery_job_status:`;

if (!t.includes("suggest_delivery_assignees:")) {
  t = t.replace("      update_delivery_job_status:", suggestRpc);
}

// Enums (union)
t = t.replace(
  `      staff_role:
        | "admin"
        | "finance"
        | "warehouse"
        | "sales"
        | "dispatcher"
        | "hr"`,
  `      delivery_completed_via: "pod" | "manual" | "admin"
      delivery_eta_source: "haversine" | "osrm" | "manual"
      driver_presence_status: "available" | "on_duty" | "break" | "offline"
      staff_role:
        | "admin"
        | "finance"
        | "warehouse"
        | "sales"
        | "dispatcher"
        | "hr"
        | "driver"`,
);

// Enums (const arrays)
t = t.replace(
  `      staff_role: [
        "admin",
        "finance",
        "warehouse",
        "sales",
        "dispatcher",
        "hr",
      ],`,
  `      delivery_completed_via: ["pod", "manual", "admin"],
      delivery_eta_source: ["haversine", "osrm", "manual"],
      driver_presence_status: ["available", "on_duty", "break", "offline"],
      staff_role: [
        "admin",
        "finance",
        "warehouse",
        "sales",
        "dispatcher",
        "hr",
        "driver",
      ],`,
);

if (t.includes("driver_presence_status: \"available\"") && t.includes("| \"driver\"")) {
  // ok
} else if (!t.includes('"driver"')) {
  throw new Error("failed to add driver to staff_role enum");
}

fs.writeFileSync(path, t);
console.log("patched", path.pathname);
