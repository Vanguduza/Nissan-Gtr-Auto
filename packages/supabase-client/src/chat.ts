/**
 * Live chat typed helpers. Prefer RPCs + Realtime; regenerate
 * database.types.ts after `supabase db reset` to fold tables into Database.
 */
export type {
  ChatMessage,
  ChatParticipant,
  ChatThread,
  ChatThreadKind,
  ChatThreadStatus,
  ChatSenderKind,
  ChatParticipantRole,
  ChatStaffRole,
  StartChatThreadInput,
} from "@gtr/shared";

export {
  CHAT_THREAD_KINDS,
  CHAT_THREAD_STATUSES,
  CHAT_SENDER_KINDS,
  CHAT_PARTICIPANT_ROLES,
  CHAT_STAFF_ROLES,
  toStartChatThreadArgs,
  toClaimChatThreadArgs,
  toCloseChatThreadArgs,
  toMarkChatThreadReadArgs,
  toPostChatMessageArgs,
  toChatUnreadCountArgs,
} from "@gtr/shared";

import type { ChatMessage, ChatParticipant, ChatThread, ChatThreadKind, StartChatThreadInput } from "@gtr/shared";
import {
  CHAT_STAFF_ROLES,
  toClaimChatThreadArgs,
  toCloseChatThreadArgs,
  toMarkChatThreadReadArgs,
  toPostChatMessageArgs,
  toStartChatThreadArgs,
  toChatUnreadCountArgs,
} from "@gtr/shared";

/** Realtime table names published in `20260725100000_live_chat.sql`. */
export const CHAT_REALTIME_TABLES = [
  "chat_threads",
  "chat_messages",
  "chat_participants",
] as const;

export type ChatRealtimeTable = (typeof CHAT_REALTIME_TABLES)[number];

/** RPC names for PostgREST / supabase-js. */
export const CHAT_RPC = {
  start: "start_chat_thread",
  claim: "claim_chat_thread",
  close: "close_chat_thread",
  markRead: "mark_chat_thread_read",
  post: "post_chat_message",
  unreadCount: "chat_unread_count",
} as const;

export function startChatThreadArgs(input?: StartChatThreadInput) {
  return toStartChatThreadArgs(input);
}

export function claimChatThreadArgs(threadId: string) {
  return toClaimChatThreadArgs(threadId);
}

export function closeChatThreadArgs(threadId: string) {
  return toCloseChatThreadArgs(threadId);
}

export function markChatThreadReadArgs(threadId: string) {
  return toMarkChatThreadReadArgs(threadId);
}

export function postChatMessageArgs(threadId: string, body: string) {
  return toPostChatMessageArgs(threadId, body);
}

export function chatUnreadCountArgs(threadId?: string | null) {
  return toChatUnreadCountArgs(threadId);
}

export function isChatStaffRole(role: string): boolean {
  return (CHAT_STAFF_ROLES as readonly string[]).includes(role);
}

export function defaultChatThreadKind(): ChatThreadKind {
  return "support";
}

export type ChatThreadRow = ChatThread;
export type ChatMessageRow = ChatMessage;
export type ChatParticipantRow = ChatParticipant;
