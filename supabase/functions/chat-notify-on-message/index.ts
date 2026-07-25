/**
 * Optional notify-on-message worker for in-app live chat.
 *
 * Thin: resolves message + thread with service_role and returns notify targets.
 * Does not send SMS/email unless channel secrets are present (stub otherwise).
 *
 * AuthZ: x-worker-secret ↔ WORKER_SHARED_SECRET (see _shared/worker_auth.ts).
 * Invoke via cron / Database Webhook / ops — never expose service_role to clients.
 * No ZIMRA / payroll tax / secrets in git.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { assertWorkerSecret } from "../_shared/worker_auth.ts";

type NotifyBody = {
  message_id?: string;
  messageId?: string;
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function serviceClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204 });
  }
  if (req.method !== "POST") {
    return json(405, { error: "POST required" });
  }

  const denied = assertWorkerSecret(req);
  if (denied) return denied;

  let body: NotifyBody;
  try {
    body = (await req.json()) as NotifyBody;
  } catch {
    return json(400, { error: "invalid JSON body" });
  }

  const messageId = (body.message_id ?? body.messageId ?? "").trim();
  if (!messageId) {
    return json(400, { error: "message_id required" });
  }

  const supabase = serviceClient();
  const { data: message, error: msgErr } = await supabase
    .from("chat_messages")
    .select("id, thread_id, sender_user_id, sender_kind, body, created_at")
    .eq("id", messageId)
    .maybeSingle();

  if (msgErr) {
    return json(500, { error: msgErr.message });
  }
  if (!message) {
    return json(404, { error: "message not found" });
  }

  const { data: thread, error: thrErr } = await supabase
    .from("chat_threads")
    .select(
      "id, customer_user_id, assigned_to, status, kind, subject, last_message_at",
    )
    .eq("id", message.thread_id)
    .maybeSingle();

  if (thrErr) {
    return json(500, { error: thrErr.message });
  }
  if (!thread) {
    return json(404, { error: "thread not found" });
  }

  const targets: Array<{ user_id: string; reason: string }> = [];
  if (message.sender_kind === "customer") {
    if (thread.assigned_to) {
      targets.push({
        user_id: thread.assigned_to,
        reason: "assigned_staff",
      });
    } else {
      targets.push({
        user_id: "staff_queue",
        reason: "open_unassigned",
      });
    }
  } else if (message.sender_kind === "staff") {
    targets.push({
      user_id: thread.customer_user_id,
      reason: "customer",
    });
  }

  // Delivery is intentionally stubbed — wire push/email later via channel secrets.
  return json(200, {
    ok: true,
    stub: true,
    message_id: message.id,
    thread_id: thread.id,
    sender_kind: message.sender_kind,
    targets,
    preview: String(message.body ?? "").slice(0, 120),
  });
});
