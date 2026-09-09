import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0?target=deno";
import { getSmsGatewayConfig, sendSms } from "../_shared/sms_gateway.ts";

type SmsHookPayload = {
  user: { phone?: string | null };
  sms: { otp?: string | null };
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

function verifyHook(raw: string, req: Request): SmsHookPayload {
  const configured = Deno.env.get("SEND_SMS_HOOK_SECRET")?.trim() ?? "";
  const secrets = configured.split("|").map((s) => s.trim()).filter(Boolean);
  if (!secrets.length) throw new Error("SEND_SMS_HOOK_SECRET is not configured");
  const headers = Object.fromEntries(req.headers.entries());
  let lastError: unknown = null;
  for (const configuredSecret of secrets) {
    const secret = configuredSecret.replace(/^v1,whsec_/, "");
    try {
      return new Webhook(secret).verify(raw, headers) as SmsHookPayload;
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError ?? new Error("invalid Auth hook signature");
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: { http_code: 405, message: "POST required" } }, 405);

  try {
    const raw = await req.text();
    const payload = verifyHook(raw, req);
    const phone = payload.user?.phone?.trim() ?? "";
    const otp = payload.sms?.otp?.trim() ?? "";
    if (!phone || !/^[0-9]{6,10}$/.test(otp)) {
      return json({ error: { http_code: 422, message: "Auth hook payload missing phone or OTP" } }, 422);
    }

    const config = getSmsGatewayConfig();
    if (!config) {
      return json({ error: { http_code: 503, message: "SMS gateway is not configured" } }, 503);
    }

    await sendSms(
      config,
      phone,
      `Nissan GTR Auto verification code: ${otp}. It expires shortly. Do not share this code.`,
    );
    return json({});
  } catch (error) {
    console.error("auth-send-sms-hook", error instanceof Error ? error.message : String(error));
    return json({ error: { http_code: 401, message: "Auth SMS hook verification or delivery failed" } }, 401);
  }
});
