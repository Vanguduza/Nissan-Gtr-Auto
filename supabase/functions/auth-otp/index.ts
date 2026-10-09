import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import type { SupabaseClient, User } from "npm:@supabase/supabase-js@2.105.0";
import {
  anonClient, AuthEdgeError, authFailureCode, authFailureStatus, enforceAuthRateLimit,
  getAuthUser, isEmailConfirmed, isPendingSignup, isPhoneConfirmed, jsonResponse,
  normalizeCode, normalizeE164, normalizeEmail, resolveAuthUserId, serviceClient, sessionPayload,
} from "../_shared/auth_edge.ts";

type Body = { action?: string; email?: string; phone_e164?: string; channel?: "email" | "phone"; code?: string; email_code?: string; phone_code?: string; password?: string; full_name?: string; device_id?: string };
const PENDING_TTL_MS = 24 * 60 * 60 * 1000;
function fullName(raw: unknown): string | null { if (typeof raw !== "string") return null; const value = raw.trim(); return value ? value.slice(0,160) : null; }
function pendingExpired(user: User): boolean { const created = Date.parse(user.created_at); return Number.isFinite(created) && Date.now() - created > PENDING_TTL_MS; }

async function ensurePendingSignupUser(service: SupabaseClient, email: string, phone: string | null, name: string | null): Promise<User> {
  let userId = await resolveAuthUserId(service, email, phone);
  if (userId) {
    let user = await getAuthUser(service, userId);
    if (!isPendingSignup(user)) throw new AuthEdgeError("An account already exists for these details. Sign in instead.",409,"ACCOUNT_EXISTS");
    if (pendingExpired(user)) {
      const { error } = await service.auth.admin.deleteUser(user.id);
      if (error) throw new AuthEdgeError("Unable to restart expired signup",503,"SIGNUP_CLEANUP_FAILED");
      userId = null;
    } else {
      if (normalizeEmail(user.email) !== email) throw new AuthEdgeError("Pending signup email does not match",409,"SIGNUP_IDENTIFIER_MISMATCH");
      const existingPhone = normalizeE164(user.phone);
      if (phone && existingPhone && existingPhone !== phone) throw new AuthEdgeError("Pending signup phone does not match",409,"SIGNUP_IDENTIFIER_MISMATCH");
      const updates = {
        app_metadata: { ...(user.app_metadata ?? {}), gtr_provisioned_via: "supabase_auth_edge", gtr_signup_pending: true, gtr_phone_required: Boolean(phone || existingPhone) },
        user_metadata: { ...(user.user_metadata ?? {}), ...(name ? { full_name: name } : {}) },
        ...(phone && !existingPhone ? { phone } : {}),
      };
      const { data, error } = await service.auth.admin.updateUserById(user.id, updates);
      if (error || !data.user) throw new AuthEdgeError("Unable to update pending signup",503,"SIGNUP_UPDATE_FAILED");
      return data.user;
    }
  }
  const { data, error } = await service.auth.admin.createUser({
    email, ...(phone ? { phone } : {}), email_confirm: false, ...(phone ? { phone_confirm: false } : {}),
    app_metadata: { gtr_provisioned_via: "supabase_auth_edge", gtr_signup_pending: true, gtr_phone_required: Boolean(phone) },
    user_metadata: name ? { full_name: name } : {},
  });
  if (error || !data.user) {
    const status = error?.status === 422 ? 409 : 400;
    throw new AuthEdgeError(status === 409 ? "An account already exists for these details. Sign in instead." : "Unable to start signup", status, status === 409 ? "ACCOUNT_EXISTS" : "SIGNUP_CREATE_FAILED");
  }
  return data.user;
}

async function sendSignupOtp(req: Request, service: SupabaseClient, channel: "email" | "phone", email: string, phone: string | null, deviceId: unknown) {
  const auth = anonClient();
  const identifier = channel === "email" ? email : phone;
  if (!identifier) throw new AuthEdgeError("Valid phone_e164 required",400,"PHONE_REQUIRED");
  await enforceAuthRateLimit(service, req, "signup_request", `${channel}:${identifier}`, deviceId);
  const result = channel === "email"
    ? await auth.auth.signInWithOtp({ email, options: { shouldCreateUser: false } })
    : await auth.auth.signInWithOtp({ phone: identifier, options: { shouldCreateUser:false, channel:"sms" } });
  if (result.error) {
    const code = authFailureCode(result.error, channel === "email" ? "EMAIL_OTP_SEND_FAILED" : "PHONE_OTP_SEND_FAILED");
    throw new AuthEdgeError(
      channel === "email" ? "Unable to send email verification code." : "Unable to send phone verification code.",
      result.error.status === 429 ? 429 : (result.error.status ?? 0) >= 500 ? 503 : 400,
      code,
    );
  }
  return { [channel]: { sent: true } };
}

async function verifyOne(req: Request, service: SupabaseClient, expectedUserId: string, channel: "email"|"phone", identifier: string, code: string, deviceId: unknown): Promise<void> {
  await enforceAuthRateLimit(service, req, "signup_verify", `${channel}:${identifier}`, deviceId);
  const auth = anonClient();
  const result = channel === "email" ? await auth.auth.verifyOtp({ email:identifier, token:code, type:"email" }) : await auth.auth.verifyOtp({ phone:identifier, token:code, type:"sms" });
  if (result.error || !result.data.user || !result.data.session) throw new AuthEdgeError("Invalid or expired verification code",401,"OTP_INVALID_OR_EXPIRED");
  if (result.data.user.id !== expectedUserId) {
    try { await auth.auth.signOut({scope:"local"}); } catch { /* no-op */ }
    throw new AuthEdgeError("Verification identity mismatch",401,"OTP_IDENTITY_MISMATCH");
  }
  try { await auth.auth.signOut({scope:"local"}); } catch { /* no-op */ }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return jsonResponse(req,{ok:true});
  try {
    if (req.method !== "POST") return jsonResponse(req,{error:"POST required",code:"METHOD_NOT_ALLOWED"},405);
    const body = await req.json().catch(()=>({})) as Body;
    const action = (body.action ?? "").trim().toLowerCase();
    const email = normalizeEmail(body.email);
    const phone = normalizeE164(body.phone_e164);
    const service = serviceClient();

    if (action === "request") {
      if (!email) return jsonResponse(req,{error:"Valid email required",code:"EMAIL_REQUIRED"},400);
      const requestedChannel = body.channel ?? "email";
      if (requestedChannel !== "email" && requestedChannel !== "phone") {
        return jsonResponse(req,{error:"channel must be email or phone",code:"INVALID_CHANNEL"},400);
      }
      if (requestedChannel === "phone" && !phone) {
        return jsonResponse(req,{error:"Valid phone_e164 required",code:"PHONE_REQUIRED"},400);
      }
      const name = fullName(body.full_name);
      const user = await ensurePendingSignupUser(service,email,phone,name);
      const channels = await sendSignupOtp(req,service,requestedChannel,email,phone,body.device_id);
      return jsonResponse(req,{ok:true,user_id:user.id,channels,requested_channel:requestedChannel,verification_required:{email:true,phone:Boolean(phone)}});
    }

    if (action === "verify") {
      if (!email) return jsonResponse(req,{error:"Valid email required",code:"EMAIL_REQUIRED"},400);
      const userId = await resolveAuthUserId(service,email,phone);
      if (!userId) return jsonResponse(req,{error:"Signup session not found",code:"SIGNUP_NOT_FOUND"},404);
      const pending = await getAuthUser(service,userId);
      if (!isPendingSignup(pending)) return jsonResponse(req,{error:"Signup is already complete",code:"SIGNUP_ALREADY_COMPLETE"},409);
      const explicitCode=normalizeCode(body.code), emailCode=normalizeCode(body.email_code), phoneCode=normalizeCode(body.phone_code);
      let verifiedAny=false;
      if (body.channel === "email") {
        if (!explicitCode) return jsonResponse(req,{error:"Valid code required",code:"CODE_REQUIRED"},400);
        await verifyOne(req,service,userId,"email",email,explicitCode,body.device_id); verifiedAny=true;
      } else if (body.channel === "phone") {
        if (!phone) return jsonResponse(req,{error:"Valid phone_e164 required",code:"PHONE_REQUIRED"},400);
        if (!explicitCode) return jsonResponse(req,{error:"Valid code required",code:"CODE_REQUIRED"},400);
        await verifyOne(req,service,userId,"phone",phone,explicitCode,body.device_id); verifiedAny=true;
      } else {
        if (emailCode) { await verifyOne(req,service,userId,"email",email,emailCode,body.device_id); verifiedAny=true; }
        if (phoneCode) { if (!phone) return jsonResponse(req,{error:"Valid phone_e164 required",code:"PHONE_REQUIRED"},400); await verifyOne(req,service,userId,"phone",phone,phoneCode,body.device_id); verifiedAny=true; }
      }
      if (!verifiedAny) return jsonResponse(req,{error:"Provide channel + code, or email_code / phone_code",code:"CODE_REQUIRED"},400);
      const latest = await getAuthUser(service,userId);
      const phoneRequired = latest.app_metadata?.gtr_phone_required === true;
      const verification = {email:isEmailConfirmed(latest),phone:phoneRequired ? isPhoneConfirmed(latest) : null};
      return jsonResponse(req,{ok:true,verified:verification,signup_ready:verification.email && (!phoneRequired || verification.phone === true)});
    }

    if (action === "complete_signup") {
      if (!email) return jsonResponse(req,{error:"Valid email required",code:"EMAIL_REQUIRED"},400);
      const password=typeof body.password === "string" ? body.password : "";
      if (password.length < 8) return jsonResponse(req,{error:"Password must be at least 8 characters",code:"PASSWORD_TOO_SHORT"},400);
      await enforceAuthRateLimit(service,req,"signup_verify",`complete:${email}`,body.device_id);
      const userId=await resolveAuthUserId(service,email,phone);
      if (!userId) return jsonResponse(req,{error:"Signup session not found",code:"SIGNUP_NOT_FOUND"},404);
      const user=await getAuthUser(service,userId);
      if (!isPendingSignup(user)) return jsonResponse(req,{error:"Signup is already complete",code:"SIGNUP_ALREADY_COMPLETE"},409);
      if (!isEmailConfirmed(user)) return jsonResponse(req,{error:"Verify your email first",code:"EMAIL_NOT_VERIFIED"},409);
      const phoneRequired=user.app_metadata?.gtr_phone_required === true;
      if (phoneRequired && !isPhoneConfirmed(user)) return jsonResponse(req,{error:"Verify your phone number first",code:"PHONE_NOT_VERIFIED"},409);
      const {data:customerId,error:customerError}=await service.rpc("ensure_customer_for_user",{p_uid:user.id});
      if (customerError || !customerId) throw new AuthEdgeError("Customer provisioning failed",503,"CUSTOMER_PROVISION_FAILED");
      const name=fullName(body.full_name);
      const {data:updated,error:updateError}=await service.auth.admin.updateUserById(user.id,{
        password,
        app_metadata:{...(user.app_metadata??{}),gtr_provisioned_via:"supabase_auth_edge",gtr_signup_pending:false,gtr_signup_completed_at:new Date().toISOString()},
        user_metadata:{...(user.user_metadata??{}),...(name?{full_name:name}:{})},
      });
      if (updateError || !updated.user) throw new AuthEdgeError("Unable to complete Supabase Auth account",503,"SIGNUP_FINALIZE_FAILED");
      await service.from("profiles").update({...(phone?{phone_e164:phone}:{}),...(name?{full_name:name}:{}),updated_at:new Date().toISOString()}).eq("id",user.id);
      const auth=anonClient();
      const signed=await auth.auth.signInWithPassword({email,password});
      if (signed.error || !signed.data.session || !signed.data.user) throw new AuthEdgeError("Account created but session mint failed. Sign in again.",503,"SESSION_MINT_FAILED");
      return jsonResponse(req,{ok:true,customer_id:customerId,email,phone_e164:normalizeE164(updated.user.phone),...sessionPayload(signed.data.session,signed.data.user)});
    }

    if (action === "complete_login") {
      const password=typeof body.password === "string" ? body.password : "";
      if (!password) return jsonResponse(req,{error:"Password required",code:"PASSWORD_REQUIRED"},400);
      if (!email && !phone) return jsonResponse(req,{error:"Email or phone_e164 required",code:"IDENTIFIER_REQUIRED"},400);
      const identifier=email?`email:${email}`:`phone:${phone!}`;
      await enforceAuthRateLimit(service,req,"login",identifier,body.device_id);
      const auth=anonClient();
      const signed=email ? await auth.auth.signInWithPassword({email,password}) : await auth.auth.signInWithPassword({phone:phone!,password});
      if (signed.error || !signed.data.session || !signed.data.user) return jsonResponse(req,{error:"Invalid credentials",code:"INVALID_CREDENTIALS"},401);
      if (isPendingSignup(signed.data.user)) {
        try { await auth.auth.signOut({scope:"local"}); } catch { /* no-op */ }
        return jsonResponse(req,{error:"Complete signup verification first",code:"SIGNUP_INCOMPLETE"},403);
      }
      return jsonResponse(req,{ok:true,email:normalizeEmail(signed.data.user.email),phone_e164:normalizeE164(signed.data.user.phone),...sessionPayload(signed.data.session,signed.data.user)});
    }
    return jsonResponse(req,{error:"action must be request, verify, complete_signup, or complete_login",code:"INVALID_ACTION"},400);
  } catch (error) {
    if (error instanceof AuthEdgeError) return jsonResponse(req,{error:error.message,code:error.code},error.status);
    const authLike=error as {status?:number;code?:string;message?:string};
    console.error("auth-otp",authLike?.code??"error",authLike?.message??String(error));
    return jsonResponse(req,{error:"Authentication service error",code:authFailureCode(authLike,"AUTH_SERVICE_ERROR")},authFailureStatus(authLike));
  }
});
