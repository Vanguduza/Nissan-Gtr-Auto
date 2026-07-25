/** In-app live chat DTOs (must match DB enums / tables). */
export const CHAT_THREAD_KINDS = ["support", "parts"] as const;
export type ChatThreadKind = (typeof CHAT_THREAD_KINDS)[number];

export const CHAT_THREAD_STATUSES = ["open", "assigned", "closed"] as const;
export type ChatThreadStatus = (typeof CHAT_THREAD_STATUSES)[number];

export const CHAT_SENDER_KINDS = ["customer", "staff", "system"] as const;
export type ChatSenderKind = (typeof CHAT_SENDER_KINDS)[number];

export const CHAT_PARTICIPANT_ROLES = ["customer", "staff"] as const;
export type ChatParticipantRole = (typeof CHAT_PARTICIPANT_ROLES)[number];

/** Staff roles that may claim / reply / close chat (matches `_chat_staff_roles`). */
export const CHAT_STAFF_ROLES = ["admin", "sales", "warehouse"] as const;
export type ChatStaffRole = (typeof CHAT_STAFF_ROLES)[number];

export type ChatThread = {
  id: string;
  customer_user_id: string;
  customer_id: string | null;
  kind: ChatThreadKind;
  status: ChatThreadStatus;
  subject: string | null;
  assigned_to: string | null;
  assigned_at: string | null;
  closed_at: string | null;
  closed_by: string | null;
  last_message_at: string | null;
  created_at: string;
  updated_at: string;
};

export type ChatMessage = {
  id: string;
  thread_id: string;
  sender_user_id: string;
  sender_kind: ChatSenderKind;
  body: string;
  created_at: string;
};

export type ChatParticipant = {
  thread_id: string;
  user_id: string;
  role: ChatParticipantRole;
  last_read_at: string | null;
  joined_at: string;
};

export type StartChatThreadInput = {
  kind?: ChatThreadKind;
  subject?: string | null;
  body?: string | null;
};

export function toStartChatThreadArgs(input: StartChatThreadInput = {}) {
  return {
    p_kind: input.kind ?? "support",
    p_subject: input.subject ?? null,
    p_body: input.body ?? null,
  } as const;
}

export function toClaimChatThreadArgs(threadId: string) {
  return { p_thread_id: threadId } as const;
}

export function toCloseChatThreadArgs(threadId: string) {
  return { p_thread_id: threadId } as const;
}

export function toMarkChatThreadReadArgs(threadId: string) {
  return { p_thread_id: threadId } as const;
}

export function toPostChatMessageArgs(threadId: string, body: string) {
  return { p_thread_id: threadId, p_body: body } as const;
}

export function toChatUnreadCountArgs(threadId?: string | null) {
  return { p_thread_id: threadId ?? null } as const;
}
