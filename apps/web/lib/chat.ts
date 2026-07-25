import type { RealtimeChannel, SupabaseClient } from "@gtr/supabase-client";
import {
  CHAT_RPC,
  claimChatThreadArgs,
  closeChatThreadArgs,
  markChatThreadReadArgs,
  postChatMessageArgs,
  startChatThreadArgs,
  chatUnreadCountArgs,
  type ChatMessage,
  type ChatThread,
  type ChatThreadKind,
  type ChatThreadStatus,
  type StartChatThreadInput,
} from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";

export type { ChatMessage, ChatThread, ChatThreadKind, ChatThreadStatus };

export type StaffChatFilter = "open" | "mine" | "closed";

function isChatThread(row: unknown): row is ChatThread {
  if (!row || typeof row !== "object") return false;
  const r = row as Record<string, unknown>;
  return typeof r.id === "string" && typeof r.status === "string";
}

function isChatMessage(row: unknown): row is ChatMessage {
  if (!row || typeof row !== "object") return false;
  const r = row as Record<string, unknown>;
  return (
    typeof r.id === "string" &&
    typeof r.thread_id === "string" &&
    typeof r.body === "string" &&
    typeof r.sender_kind === "string"
  );
}

export async function listCustomerThreads(
  client: SupabaseClient,
): Promise<StorefrontResult<ChatThread[]>> {
  const { data, error } = await client
    .from("chat_threads")
    .select("*")
    .order("last_message_at", { ascending: false, nullsFirst: false });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data ?? []) as ChatThread[] };
}

export async function listStaffThreads(
  client: SupabaseClient,
  filter: StaffChatFilter,
  userId: string,
): Promise<StorefrontResult<ChatThread[]>> {
  let q = client
    .from("chat_threads")
    .select("*")
    .order("last_message_at", { ascending: false, nullsFirst: false });

  if (filter === "open") {
    q = q.eq("status", "open");
  } else if (filter === "mine") {
    q = q.eq("assigned_to", userId).neq("status", "closed");
  } else {
    q = q.eq("status", "closed");
  }

  const { data, error } = await q;
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data ?? []) as ChatThread[] };
}

export async function listThreadMessages(
  client: SupabaseClient,
  threadId: string,
): Promise<StorefrontResult<ChatMessage[]>> {
  const { data, error } = await client
    .from("chat_messages")
    .select("*")
    .eq("thread_id", threadId)
    .order("created_at", { ascending: true });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data ?? []) as ChatMessage[] };
}

export async function startChatThread(
  client: SupabaseClient,
  input?: StartChatThreadInput,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc(
    CHAT_RPC.start,
    startChatThreadArgs(input),
  );
  if (error) return { ok: false, error: error.message };
  if (!data || typeof data !== "string") {
    return { ok: false, error: "No thread id returned" };
  }
  return { ok: true, data };
}

export async function claimChatThread(
  client: SupabaseClient,
  threadId: string,
): Promise<StorefrontResult<true>> {
  const { error } = await client.rpc(
    CHAT_RPC.claim,
    claimChatThreadArgs(threadId),
  );
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

export async function closeChatThread(
  client: SupabaseClient,
  threadId: string,
): Promise<StorefrontResult<true>> {
  const { error } = await client.rpc(
    CHAT_RPC.close,
    closeChatThreadArgs(threadId),
  );
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

export async function postChatMessage(
  client: SupabaseClient,
  threadId: string,
  body: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc(
    CHAT_RPC.post,
    postChatMessageArgs(threadId, body),
  );
  if (error) return { ok: false, error: error.message };
  if (!data || typeof data !== "string") {
    return { ok: false, error: "No message id returned" };
  }
  return { ok: true, data };
}

export async function markChatThreadRead(
  client: SupabaseClient,
  threadId: string,
): Promise<StorefrontResult<true>> {
  const { error } = await client.rpc(
    CHAT_RPC.markRead,
    markChatThreadReadArgs(threadId),
  );
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

export async function fetchChatUnreadCount(
  client: SupabaseClient,
  threadId?: string | null,
): Promise<StorefrontResult<number>> {
  const { data, error } = await client.rpc(
    CHAT_RPC.unreadCount,
    chatUnreadCountArgs(threadId),
  );
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: typeof data === "number" ? data : Number(data) || 0 };
}

/** Realtime INSERTs on messages for one thread. Caller removes the channel. */
export function chatMessagesInsertChannel(
  client: SupabaseClient,
  threadId: string,
  onInsert: (msg: ChatMessage) => void,
): RealtimeChannel {
  return client
    .channel(`chat_messages:${threadId}`)
    .on(
      "postgres_changes",
      {
        event: "INSERT",
        schema: "public",
        table: "chat_messages",
        filter: `thread_id=eq.${threadId}`,
      },
      (payload) => {
        if (isChatMessage(payload.new)) onInsert(payload.new);
      },
    );
}

/** Staff inbox: thread inserts/updates. Caller removes the channel. */
export function chatThreadsChangeChannel(
  client: SupabaseClient,
  onChange: (thread: ChatThread, event: "INSERT" | "UPDATE") => void,
): RealtimeChannel {
  return client
    .channel("chat_threads:staff")
    .on(
      "postgres_changes",
      {
        event: "INSERT",
        schema: "public",
        table: "chat_threads",
      },
      (payload) => {
        if (isChatThread(payload.new)) onChange(payload.new, "INSERT");
      },
    )
    .on(
      "postgres_changes",
      {
        event: "UPDATE",
        schema: "public",
        table: "chat_threads",
      },
      (payload) => {
        if (isChatThread(payload.new)) onChange(payload.new, "UPDATE");
      },
    );
}

export function threadPreview(thread: ChatThread): string {
  const sub = thread.subject?.trim();
  if (sub) return sub;
  return thread.kind === "parts" ? "Parts inquiry" : "Support";
}

export function formatChatTime(iso: string | null): string {
  if (!iso) return "";
  try {
    return new Date(iso).toLocaleString(undefined, {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return "";
  }
}
