import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0?target=deno";
import { getEmailSendConfig, sendEmail } from "../_shared/email_send.ts";

type EmailData = {
  token?: string;
  token_hash?: string;
  redirect_to?: string;
  email_action_type?: string;
  site_url?: string;
  token_new?: string;
  token_hash_new?: string;
  old_email?: string;
  old_phone?: string;
  provider?: string;
  factor_type?: string;
};

type EmailHookPayload = {
  user: {
    email?: string | null;
    new_email?: string | null;
    phone?: string | null;
    app_metadata?: Record<string, unknown>;
  };
  email_data: EmailData;
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

function verifyHook(raw: string, req: Request): EmailHookPayload {
  const configured = Deno.env.get("SEND_EMAIL_HOOK_SECRET")?.trim() ?? "";
  const secrets = configured.split("|").map((s) => s.trim()).filter(Boolean);
  if (!secrets.length) throw new Error("SEND_EMAIL_HOOK_SECRET is not configured");
  const headers = Object.fromEntries(req.headers.entries());
  let lastError: unknown = null;
  for (const configuredSecret of secrets) {
    const secret = configuredSecret.replace(/^v1,whsec_/, "");
    try {
      return new Webhook(secret).verify(raw, headers) as EmailHookPayload;
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError ?? new Error("invalid Auth hook signature");
}

function confirmationUrl(data: EmailData, tokenHash: string | undefined): string | null {
  if (!tokenHash) return null;
  const base = (Deno.env.get("SUPABASE_URL") ?? "").replace(/\/$/, "");
  if (!base) return null;
  const action = data.email_action_type ?? "email";
  const params = new URLSearchParams({ token: tokenHash, type: action });
  if (data.redirect_to) params.set("redirect_to", data.redirect_to);
  return `${base}/auth/v1/verify?${params.toString()}`;
}

function subjectFor(action: string): string {
  const subjects: Record<string, string> = {
    signup: "Verify your Nissan GTR Auto email",
    magiclink: "Your Nissan GTR Auto verification code",
    recovery: "Reset your Nissan GTR Auto password",
    invite: "You are invited to Nissan GTR Auto",
    email_change: "Confirm your new Nissan GTR Auto email",
    email: "Your Nissan GTR Auto verification code",
    reauthentication: "Verify your Nissan GTR Auto identity",
    password_changed_notification: "Your Nissan GTR Auto password was changed",
    email_changed_notification: "Your Nissan GTR Auto email was changed",
    phone_changed_notification: "Your Nissan GTR Auto phone number was changed",
    identity_linked_notification: "A sign-in method was linked to your account",
    identity_unlinked_notification: "A sign-in method was removed from your account",
    mfa_factor_enrolled_notification: "A verification method was added to your account",
    mfa_factor_unenrolled_notification: "A verification method was removed from your account",
  };
  return subjects[action] ?? "Nissan GTR Auto account notification";
}

function bodyFor(
  action: string,
  token: string | undefined,
  link: string | null,
  data: EmailData,
  pendingSignup: boolean,
): string {
  const codeLine = token ? `\nVerification code: ${token}\n` : "";
  const safeLink = link && !pendingSignup ? `\nContinue securely: ${link}\n` : "";
  const footer = "\nIf you did not request this, ignore this message and contact Nissan GTR Auto support if you are concerned.\n";

  switch (action) {
    case "signup":
    case "magiclink":
    case "email":
      return `Use this code to verify your Nissan GTR Auto email.${codeLine}${safeLink}${footer}`;
    case "recovery":
      return `A password reset was requested for your Nissan GTR Auto account.${codeLine}${safeLink}${footer}`;
    case "reauthentication":
      return `Use this code to verify your identity for a sensitive account action.${codeLine}${footer}`;
    case "invite":
      return `You have been invited to Nissan GTR Auto.${codeLine}${safeLink}${footer}`;
    case "email_change":
      return `Confirm the requested change to your Nissan GTR Auto email address.${codeLine}${safeLink}${footer}`;
    case "password_changed_notification":
      return `The password for your Nissan GTR Auto account was changed.${footer}`;
    case "email_changed_notification":
      return `Your Nissan GTR Auto email address was changed${data.old_email ? ` from ${data.old_email}` : ""}.${footer}`;
    case "phone_changed_notification":
      return `Your Nissan GTR Auto phone number was changed${data.old_phone ? ` from ${data.old_phone}` : ""}.${footer}`;
    case "identity_linked_notification":
      return `A ${data.provider || "new"} sign-in method was linked to your Nissan GTR Auto account.${footer}`;
    case "identity_unlinked_notification":
      return `A ${data.provider || "sign-in"} method was removed from your Nissan GTR Auto account.${footer}`;
    case "mfa_factor_enrolled_notification":
      return `A ${data.factor_type || "verification"} method was added to your Nissan GTR Auto account.${footer}`;
    case "mfa_factor_unenrolled_notification":
      return `A ${data.factor_type || "verification"} method was removed from your Nissan GTR Auto account.${footer}`;
    default:
      return `There was a security-related update to your Nissan GTR Auto account.${codeLine}${safeLink}${footer}`;
  }
}

async function sendOne(
  to: string,
  action: string,
  token: string | undefined,
  tokenHash: string | undefined,
  payload: EmailHookPayload,
) {
  const config = getEmailSendConfig();
  if (!config) throw new Error("Email gateway is not configured");
  const pendingSignup = payload.user.app_metadata?.gtr_signup_pending === true;
  const link = confirmationUrl(payload.email_data, tokenHash);
  await sendEmail(config, {
    to,
    subject: subjectFor(action),
    text: bodyFor(action, token, link, payload.email_data, pendingSignup),
  });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: { http_code: 405, message: "POST required" } }, 405);

  try {
    const raw = await req.text();
    const payload = verifyHook(raw, req);
    const action = (payload.email_data?.email_action_type ?? "").trim();
    const currentEmail = payload.user?.email?.trim() ?? "";
    const newEmail = payload.user?.new_email?.trim() ?? "";
    if (!action) return json({ error: { http_code: 422, message: "email_action_type missing" } }, 422);

    if (action === "email_change") {
      const { token, token_hash, token_new, token_hash_new } = payload.email_data;
      if (token_hash_new && currentEmail) {
        await sendOne(currentEmail, action, token, token_hash_new, payload);
      }
      if (newEmail) {
        const newToken = token_new || token;
        if (!newToken) throw new Error("email change token missing");
        await sendOne(newEmail, action, newToken, token_hash, payload);
      } else if (currentEmail && !token_hash_new) {
        await sendOne(currentEmail, action, token_new || token, token_hash, payload);
      }
      return json({});
    }

    if (!currentEmail) {
      return json({ error: { http_code: 422, message: "Auth hook payload missing email" } }, 422);
    }

    await sendOne(
      currentEmail,
      action,
      payload.email_data.token,
      payload.email_data.token_hash,
      payload,
    );
    return json({});
  } catch (error) {
    console.error("auth-send-email-hook", error instanceof Error ? error.message : String(error));
    return json({ error: { http_code: 401, message: "Auth email hook verification or delivery failed" } }, 401);
  }
});
