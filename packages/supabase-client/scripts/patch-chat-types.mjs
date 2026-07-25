import fs from "fs";

const path = "packages/supabase-client/src/database.types.ts";
let t = fs.readFileSync(path, "utf16le");

const chatTables = `
      chat_messages: {
        Row: {
          body: string
          created_at: string
          id: string
          sender_kind: Database["public"]["Enums"]["chat_sender_kind"]
          sender_user_id: string
          thread_id: string
        }
        Insert: {
          body: string
          created_at?: string
          id?: string
          sender_kind: Database["public"]["Enums"]["chat_sender_kind"]
          sender_user_id: string
          thread_id: string
        }
        Update: {
          body?: string
          created_at?: string
          id?: string
          sender_kind?: Database["public"]["Enums"]["chat_sender_kind"]
          sender_user_id?: string
          thread_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "chat_messages_sender_user_id_fkey"
            columns: ["sender_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "chat_messages_thread_id_fkey"
            columns: ["thread_id"]
            isOneToOne: false
            referencedRelation: "chat_threads"
            referencedColumns: ["id"]
          },
        ]
      }
      chat_participants: {
        Row: {
          joined_at: string
          last_read_at: string | null
          role: Database["public"]["Enums"]["chat_participant_role"]
          thread_id: string
          user_id: string
        }
        Insert: {
          joined_at?: string
          last_read_at?: string | null
          role: Database["public"]["Enums"]["chat_participant_role"]
          thread_id: string
          user_id: string
        }
        Update: {
          joined_at?: string
          last_read_at?: string | null
          role?: Database["public"]["Enums"]["chat_participant_role"]
          thread_id?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "chat_participants_thread_id_fkey"
            columns: ["thread_id"]
            isOneToOne: false
            referencedRelation: "chat_threads"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "chat_participants_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      chat_threads: {
        Row: {
          assigned_at: string | null
          assigned_to: string | null
          closed_at: string | null
          closed_by: string | null
          created_at: string
          customer_id: string | null
          customer_user_id: string
          id: string
          kind: Database["public"]["Enums"]["chat_thread_kind"]
          last_message_at: string | null
          status: Database["public"]["Enums"]["chat_thread_status"]
          subject: string | null
          updated_at: string
        }
        Insert: {
          assigned_at?: string | null
          assigned_to?: string | null
          closed_at?: string | null
          closed_by?: string | null
          created_at?: string
          customer_id?: string | null
          customer_user_id: string
          id?: string
          kind?: Database["public"]["Enums"]["chat_thread_kind"]
          last_message_at?: string | null
          status?: Database["public"]["Enums"]["chat_thread_status"]
          subject?: string | null
          updated_at?: string
        }
        Update: {
          assigned_at?: string | null
          assigned_to?: string | null
          closed_at?: string | null
          closed_by?: string | null
          created_at?: string
          customer_id?: string | null
          customer_user_id?: string
          id?: string
          kind?: Database["public"]["Enums"]["chat_thread_kind"]
          last_message_at?: string | null
          status?: Database["public"]["Enums"]["chat_thread_status"]
          subject?: string | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "chat_threads_assigned_to_fkey"
            columns: ["assigned_to"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "chat_threads_closed_by_fkey"
            columns: ["closed_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "chat_threads_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "chat_threads_customer_user_id_fkey"
            columns: ["customer_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
`;

if (!t.includes("chat_threads:")) {
  const marker = "      customer_garage_vehicles:";
  const i = t.indexOf(marker);
  if (i < 0) throw new Error("customer_garage_vehicles not found");
  t = t.slice(0, i) + chatTables + t.slice(i);
  console.log("inserted tables at", i);
} else {
  console.log("tables already present");
}

const enumBlock = `      chat_participant_role: "customer" | "staff"
      chat_sender_kind: "customer" | "staff" | "system"
      chat_thread_kind: "support" | "parts"
      chat_thread_status: "open" | "assigned" | "closed"
`;
if (!t.includes('chat_thread_kind: "support"')) {
  const cart = '      cart_channel: "pos" | "storefront"\n';
  const ci = t.indexOf(cart);
  if (ci < 0) throw new Error("cart_channel enum not found");
  t = t.slice(0, ci + cart.length) + enumBlock + t.slice(ci + cart.length);
  console.log("inserted Enums");
} else {
  console.log("Enums already present");
}

const constBlock = `      chat_participant_role: ["customer", "staff"],
      chat_sender_kind: ["customer", "staff", "system"],
      chat_thread_kind: ["support", "parts"],
      chat_thread_status: ["open", "assigned", "closed"],
`;
if (!t.includes('chat_thread_kind: ["support"')) {
  const cartC = '      cart_channel: ["pos", "storefront"],\n';
  const cj = t.indexOf(cartC);
  if (cj < 0) throw new Error("cart_channel constants not found");
  t = t.slice(0, cj + cartC.length) + constBlock + t.slice(cj + cartC.length);
  console.log("inserted Constants");
} else {
  console.log("Constants already present");
}

if (!t.includes("claim_chat_thread:")) {
  const chatFn = `
      chat_unread_count: { Args: { p_thread_id?: string }; Returns: number }
      claim_chat_thread: { Args: { p_thread_id: string }; Returns: undefined }
      close_chat_thread: { Args: { p_thread_id: string }; Returns: undefined }
      mark_chat_thread_read: { Args: { p_thread_id: string }; Returns: undefined }
      post_chat_message: {
        Args: { p_body: string; p_thread_id: string }
        Returns: string
      }
      start_chat_thread: {
        Args: {
          p_body?: string
          p_kind?: Database["public"]["Enums"]["chat_thread_kind"]
          p_subject?: string
        }
        Returns: string
      }
`;
  let a = t.indexOf("      claim_sms_outbox_batch:");
  if (a < 0) a = t.indexOf("      assign_staff_role:");
  if (a < 0) throw new Error("functions anchor not found");
  t = t.slice(0, a) + chatFn + t.slice(a);
  console.log("inserted Functions near", a);
} else {
  console.log("Functions already present");
}

fs.writeFileSync(path, t, "utf16le");
console.log("done, length", t.length);
