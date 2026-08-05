/**
 * hr-onboarding-create-auth — create/link Supabase Auth user after complete_hr_onboarding
 * when employees.user_id is absent. Staff JWT + HR/admin only. Temp password never
 * returned to the client — delivered via hr_credential_outbox + existing gateways.
 *
 * Hardening: in-process + DB single-flight (claim_hr_auth_provision); orphan Auth
 * user cleanup when link_employee_auth_user fails after createUser; scrub stale
 * credential outbox plaintext (scrub_hr_credential_outbox_bodies).
 *
 * Mirrors auth-otp Admin createUser + channel fail-closed stubs.
 * No ZIMRA / payroll-tax identity flows.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { jsonErr, jsonOk } from "../_shared/channel_env.ts";
import {
  allowAuthOtpLocalStub,
  assertOtpChannelAllowed,
} from "../_shared/auth_otp_env.ts";
import { getSmsGatewayConfig, sendSms } from "../_shared/sms_gateway.ts";
import { getEmailSendConfig, sendEmail } from "../_shared/email_send.ts";
import {
  getWhatsAppCloudConfig,
  sendWhatsAppText,
} from "../_shared/whatsapp_cloud.ts";

/** Same-isolate concurrent POSTs for one employee share one promise. */
const inFlightByEmployee = new Map<string, Promise<Response>>();

type Body = {
  employee_id?: string;
  /** When true and employee already linked, rotate temp password + re-deliver. Default false. */
  force_reset?: boolean;
};

type ChannelResult = {
  channel: "email" | "sms" | "whatsapp";
  status: "sent" | "stub" | "failed" | "skipped";
  outbox_id?: string;
  error?: string;
};

function normalizeEmail(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const v = raw.trim().toLowerCase();
  if (!v || !v.includes("@")) return null;
  return v;
}

function normalizeE164(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  let v = raw.trim().replace(/[\s\-()]/g, "");
  if (!v) return null;
  if (!/^\+?[0-9]{8,15}$/.test(v)) return null;
  if (!v.startsWith("+")) v = `+${v}`;
  return v;
}

function isUuid(raw: unknown): raw is string {
  return typeof raw === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
      .test(raw);
}

/** URL-safe temp password — never logged or returned to client. */
function randomTempPassword(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(18));
  const b64 = btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  return `Gtr!${b64}`;
}

function serviceClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false, autoRefreshToken: false } },
  );
}

/** Resolve existing GoTrue user by email via Admin API (no password in response). */
async function deleteAuthUserBestEffort(userId: string): Promise<void> {
  try {
    const supabase = serviceClient();
    const { error } = await supabase.auth.admin.deleteUser(userId);
    if (error) {
      console.error("orphan auth delete failed:", userId, error.message);
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error("orphan auth delete threw:", userId, msg);
  }
}

async function findAuthUserIdByEmail(email: string): Promise<string | null> {
  const base = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!base || !key) return null;
  const res = await fetch(
    `${base}/auth/v1/admin/users?email=${encodeURIComponent(email)}`,
    {
      headers: {
        Authorization: `Bearer ${key}`,
        apikey: key,
      },
    },
  );
  if (!res.ok) return null;
  const json = await res.json().catch(() => null) as
    | { users?: Array<{ id?: string; email?: string }> }
    | { id?: string; email?: string }
    | null;
  if (!json) return null;
  if (Array.isArray((json as { users?: unknown }).users)) {
    const hit = (json as { users: Array<{ id?: string; email?: string }> })
      .users
      .find((u) => normalizeEmail(u.email) === email);
    return hit?.id ?? null;
  }
  if (typeof (json as { id?: string }).id === "string") {
    return (json as { id: string }).id;
  }
  return null;
}

function userClientFromAuth(authHeader: string): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false, autoRefreshToken: false },
    },
  );
}

function credentialBody(args: {
  fullName: string;
  employeeCode: string;
  email: string;
  tempPassword: string;
}): string {
  return [
    `Welcome to Nissan GTR Auto, ${args.fullName}.`,
    `Employee #: ${args.employeeCode}`,
    `Login email: ${args.email}`,
    `Temporary password: ${args.tempPassword}`,
    "Sign in at the staff portal and change your password immediately.",
  ].join("\n");
}

async function enqueueAndSend(
  supabase: SupabaseClient,
  args: {
    employeeId: string;
    userId: string;
    channel: "email" | "sms" | "whatsapp";
    recipient: string;
    body: string;
  },
): Promise<ChannelResult> {
  const { data: outboxId, error: enqErr } = await supabase.rpc(
    "enqueue_hr_credential_outbox",
    {
      p_employee_id: args.employeeId,
      p_user_id: args.userId,
      p_channel: args.channel,
      p_recipient: args.recipient,
      p_body: args.body,
    },
  );
  if (enqErr || !outboxId) {
    return {
      channel: args.channel,
      status: "failed",
      error: enqErr?.message ?? "enqueue failed",
    };
  }

  const localStub = allowAuthOtpLocalStub();

  try {
    if (args.channel === "email") {
      const gate = assertOtpChannelAllowed("email");
      if (!gate.ok) {
        await supabase.rpc("complete_hr_credential_outbox", {
          p_id: outboxId,
          p_success: false,
          p_error: gate.error,
        });
        return {
          channel: "email",
          status: "failed",
          outbox_id: outboxId,
          error: gate.error,
        };
      }
      if (gate.stub) {
        await supabase.rpc("complete_hr_credential_outbox", {
          p_id: outboxId,
          p_success: true,
          p_provider_message_id: `stub-email-${crypto.randomUUID()}`,
        });
        return { channel: "email", status: "stub", outbox_id: outboxId };
      }
      const emailCfg = getEmailSendConfig();
      if (!emailCfg) {
        await supabase.rpc("complete_hr_credential_outbox", {
          p_id: outboxId,
          p_success: false,
          p_error: "Email gateway misconfigured",
        });
        return {
          channel: "email",
          status: "failed",
          outbox_id: outboxId,
          error: "Email gateway misconfigured",
        };
      }
      const result = await sendEmail(emailCfg, {
        to: args.recipient,
        subject: "GTR Auto staff login credentials",
        text: args.body,
      });
      await supabase.rpc("complete_hr_credential_outbox", {
        p_id: outboxId,
        p_success: true,
        p_provider_message_id: result.messageId,
      });
      return { channel: "email", status: "sent", outbox_id: outboxId };
    }

    if (args.channel === "sms") {
      const gate = assertOtpChannelAllowed("phone");
      if (!gate.ok) {
        await supabase.rpc("complete_hr_credential_outbox", {
          p_id: outboxId,
          p_success: false,
          p_error: gate.error,
        });
        return {
          channel: "sms",
          status: "failed",
          outbox_id: outboxId,
          error: gate.error,
        };
      }
      if (gate.stub) {
        await supabase.rpc("complete_hr_credential_outbox", {
          p_id: outboxId,
          p_success: true,
          p_provider_message_id: `stub-sms-${crypto.randomUUID()}`,
        });
        return { channel: "sms", status: "stub", outbox_id: outboxId };
      }
      const smsCfg = getSmsGatewayConfig();
      if (!smsCfg) {
        await supabase.rpc("complete_hr_credential_outbox", {
          p_id: outboxId,
          p_success: false,
          p_error: "SMS gateway misconfigured",
        });
        return {
          channel: "sms",
          status: "failed",
          outbox_id: outboxId,
          error: "SMS gateway misconfigured",
        };
      }
      const result = await sendSms(smsCfg, args.recipient, args.body);
      await supabase.rpc("complete_hr_credential_outbox", {
        p_id: outboxId,
        p_success: true,
        p_provider_message_id: result.messageId,
      });
      return { channel: "sms", status: "sent", outbox_id: outboxId };
    }

    // whatsapp — fail-closed like email/SMS (no silent skip in production)
    const waCfg = getWhatsAppCloudConfig();
    if (!waCfg) {
      if (localStub) {
        await supabase.rpc("complete_hr_credential_outbox", {
          p_id: outboxId,
          p_success: true,
          p_provider_message_id: `stub-wa-${crypto.randomUUID()}`,
        });
        return { channel: "whatsapp", status: "stub", outbox_id: outboxId };
      }
      const err =
        "WHATSAPP_ACCESS_TOKEN / WHATSAPP_PHONE_NUMBER_ID unset — WA refuse (set AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)";
      await supabase.rpc("complete_hr_credential_outbox", {
        p_id: outboxId,
        p_success: false,
        p_error: err,
      });
      return {
        channel: "whatsapp",
        status: "failed",
        outbox_id: outboxId,
        error: err,
      };
    }
    const result = await sendWhatsAppText(waCfg, args.recipient, args.body);
    await supabase.rpc("complete_hr_credential_outbox", {
      p_id: outboxId,
      p_success: true,
      p_provider_message_id: result.messageId,
    });
    return { channel: "whatsapp", status: "sent", outbox_id: outboxId };
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    await supabase.rpc("complete_hr_credential_outbox", {
      p_id: outboxId,
      p_success: false,
      p_error: msg.slice(0, 500),
    });
    return {
      channel: args.channel,
      status: "failed",
      outbox_id: outboxId,
      error: msg.slice(0, 200),
    };
  }
}

async function handleCreateAuth(req: Request): Promise<Response> {
  if (req.method !== "POST") return jsonErr("POST required", 405);

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return jsonErr("Authorization Bearer JWT required", 401);
  }

  const caller = userClientFromAuth(auth);
  const { data: userData, error: userErr } = await caller.auth.getUser();
  if (userErr || !userData.user) {
    return jsonErr("unauthorized", 401);
  }

  const { data: allowed, error: roleErr } = await caller.rpc("has_staff_role", {
    roles: ["admin", "hr"],
  });
  if (roleErr) return jsonErr(roleErr.message, 400);
  if (!allowed) return jsonErr("hr or admin role required", 403);

  const body = (await req.json().catch(() => ({}))) as Body;
  if (!isUuid(body.employee_id)) {
    return jsonErr("employee_id UUID required", 400);
  }

  const employeeId = body.employee_id;
  const existingFlight = inFlightByEmployee.get(employeeId);
  if (existingFlight) return existingFlight;

  const flight = (async (): Promise<Response> => {
    const supabase = serviceClient();
    const forceReset = body.force_reset === true;
    const claimToken = crypto.randomUUID();
    let claimed = false;
    let createdOrphanUserId: string | null = null;

    try {
      // Shrink plaintext window for any stuck pending/sending credential bodies.
      await supabase.rpc("scrub_hr_credential_outbox_bodies", {
        p_max_age_seconds: 90,
      });

      const { data: emp, error: empErr } = await supabase
        .from("employees")
        .select("id, user_id, email, phone_e164, full_name, employee_code, status")
        .eq("id", employeeId)
        .maybeSingle();
      if (empErr) return jsonErr(empErr.message, 400);
      if (!emp) return jsonErr("employee not found", 404);
      if (emp.status === "terminated") {
        return jsonErr("cannot create auth for terminated employee", 400);
      }

      const email = normalizeEmail(emp.email);
      if (!email) {
        return jsonErr("employee email required to create auth user", 400);
      }
      const phone = normalizeE164(emp.phone_e164);
      const fullName = String(emp.full_name ?? "").trim() || "Staff";
      const employeeCode = String(emp.employee_code ?? "");

      let userId = emp.user_id as string | null;
      const tempPassword = randomTempPassword();
      let created = false;

      if (userId && !forceReset) {
        return jsonOk({
          ok: true,
          employee_id: emp.id,
          user_id: userId,
          created: false,
          must_change_password: true,
          channels: [],
          message:
            "Employee already linked — pass force_reset:true to rotate password and re-deliver credentials.",
        });
      }

      if (!userId) {
        const { data: claimRaw, error: claimErr } = await supabase.rpc(
          "claim_hr_auth_provision",
          {
            p_employee_id: employeeId,
            p_claim_token: claimToken,
            p_ttl_seconds: 120,
          },
        );
        if (claimErr) return jsonErr(claimErr.message, 400);
        const claim = claimRaw as {
          ok?: boolean;
          reason?: string;
          user_id?: string;
        } | null;
        if (!claim?.ok) {
          if (claim?.reason === "already_linked" && claim.user_id) {
            return jsonOk({
              ok: true,
              employee_id: employeeId,
              user_id: claim.user_id,
              created: false,
              must_change_password: true,
              channels: [],
              message:
                "Employee already linked — pass force_reset:true to rotate password and re-deliver credentials.",
            });
          }
          if (claim?.reason === "in_flight") {
            return jsonErr(
              "auth provisioning already in progress for this employee — retry shortly",
              409,
            );
          }
          if (claim?.reason === "terminated") {
            return jsonErr("cannot create auth for terminated employee", 400);
          }
          if (claim?.reason === "not_found") {
            return jsonErr("employee not found", 404);
          }
          return jsonErr(claim?.reason ?? "auth provision claim failed", 409);
        }
        claimed = true;

        // Refuse email collision takeover: never rotate an existing Auth user's
        // password before a successful create+link.
        const collisionId = await findAuthUserIdByEmail(email);
        if (collisionId) {
          const { data: otherEmp } = await supabase
            .from("employees")
            .select("id, employee_code")
            .eq("user_id", collisionId)
            .maybeSingle();
          if (otherEmp) {
            return jsonErr(
              `email already linked to employee ${otherEmp.employee_code ?? otherEmp.id}`,
              409,
            );
          }
          return jsonErr(
            "email already registered in Auth — set draft payload.user_id to link that account, or use a different email. Refusing password reset on collision.",
            409,
          );
        }

        const { data: createdUser, error: createErr } = await supabase.auth.admin
          .createUser({
            email,
            password: tempPassword,
            email_confirm: true,
            user_metadata: { full_name: fullName, employee_code: employeeCode },
          });
        if (createErr || !createdUser.user) {
          return jsonErr(createErr?.message ?? "auth createUser failed", 400);
        }
        userId = createdUser.user.id;
        created = true;
        createdOrphanUserId = userId;

        const { error: linkErr } = await supabase.rpc("link_employee_auth_user", {
          p_employee_id: emp.id,
          p_user_id: userId,
          p_phone_e164: phone,
          p_full_name: fullName,
        });
        if (linkErr) {
          console.error("link_employee_auth_user failed:", linkErr.message);
          await deleteAuthUserBestEffort(userId);
          createdOrphanUserId = null;
          return jsonErr(
            `auth user created but link failed (orphan cleaned): ${linkErr.message}`,
            400,
          );
        }
        createdOrphanUserId = null;
      } else {
        // force_reset on already-linked employee only
        const { error: updErr } = await supabase.auth.admin.updateUserById(
          userId,
          {
            password: tempPassword,
            email,
            email_confirm: true,
            user_metadata: { full_name: fullName, employee_code: employeeCode },
          },
        );
        if (updErr) return jsonErr(updErr.message, 400);

        const { error: linkErr } = await supabase.rpc("link_employee_auth_user", {
          p_employee_id: emp.id,
          p_user_id: userId,
          p_phone_e164: phone,
          p_full_name: fullName,
        });
        if (linkErr) {
          console.error("link_employee_auth_user failed:", linkErr.message);
          return jsonErr(linkErr.message, 400);
        }
      }

      const message = credentialBody({
        fullName,
        employeeCode,
        email,
        tempPassword,
      });

      const channels: ChannelResult[] = [];

      const emailResult = await enqueueAndSend(supabase, {
        employeeId: emp.id,
        userId: userId!,
        channel: "email",
        recipient: email,
        body: message,
      });
      channels.push(emailResult);

      if (phone) {
        channels.push(
          await enqueueAndSend(supabase, {
            employeeId: emp.id,
            userId: userId!,
            channel: "sms",
            recipient: phone,
            body: message,
          }),
        );
        channels.push(
          await enqueueAndSend(supabase, {
            employeeId: emp.id,
            userId: userId!,
            channel: "whatsapp",
            recipient: phone,
            body: message,
          }),
        );
      } else {
        channels.push({ channel: "sms", status: "skipped" });
        channels.push({ channel: "whatsapp", status: "skipped" });
      }

      // Never echo temp password or outbox body to the client.
      return jsonOk({
        ok: true,
        employee_id: emp.id,
        user_id: userId,
        created,
        must_change_password: true,
        channels,
      });
    } finally {
      if (createdOrphanUserId) {
        await deleteAuthUserBestEffort(createdOrphanUserId);
      }
      if (claimed) {
        await supabase.rpc("release_hr_auth_provision", {
          p_employee_id: employeeId,
          p_claim_token: claimToken,
        });
      }
    }
  })();

  inFlightByEmployee.set(employeeId, flight);
  try {
    return await flight;
  } finally {
    inFlightByEmployee.delete(employeeId);
  }
}

Deno.serve(async (req) => {
  try {
    return await handleCreateAuth(req);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error("hr-onboarding-create-auth error:", msg);
    return jsonErr(msg, 500);
  }
});
