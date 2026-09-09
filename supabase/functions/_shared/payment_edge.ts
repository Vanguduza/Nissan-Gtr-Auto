/**
 * Shared helpers for ContiPay / Paynow / EcoCash initiate + webhook flows.
 *
 * Security invariants:
 * - privileged paths never reflect arbitrary CORS origins;
 * - payment initiators require caller JWTs and use user-scoped Supabase clients;
 * - provider verification fails closed when production secrets are absent;
 * - unverified stubs are available only behind explicit local-only env flags.
 */

const ALLOWED_ORIGINS = new Set([
  "https://nissangtrauto.co.zw",
  "https://www.nissangtrauto.co.zw",
  "http://localhost:3000",
  "http://127.0.0.1:3000",
  "http://localhost:5173",
  "http://127.0.0.1:5173",
]);

const DEFAULT_ALLOWED_ORIGIN = "https://nissangtrauto.co.zw";

export const PAYNOW_INITIATE_URL =
  "https://www.paynow.co.zw/interface/initiatetransaction";
export const CONTIPAY_LIVE_BASE = "https://api-v2.contipay.co.zw";
export const CONTIPAY_UAT_BASE = "https://api2-test.contipay.co.zw";
export const CONTIPAY_ACQUIRE_PATH = "/acquire/payment";

export function corsHeaders(req: Request): Record<string, string> {
  const origin = req.headers.get("Origin");
  const allow =
    origin && ALLOWED_ORIGINS.has(origin) ? origin : DEFAULT_ALLOWED_ORIGIN;
  return {
    "Access-Control-Allow-Origin": allow,
    "Access-Control-Allow-Headers":
      "authorization, x-client-info, apikey, content-type",
    Vary: "Origin",
  };
}

export function jsonResponse(
  body: unknown,
  status: number,
  extraHeaders: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...extraHeaders },
  });
}

export function timingSafeEqualStr(a: string, b: string): boolean {
  const enc = new TextEncoder();
  const bufA = enc.encode(a);
  const bufB = enc.encode(b);
  const len = Math.max(bufA.length, bufB.length);
  let diff = bufA.length ^ bufB.length;
  for (let i = 0; i < len; i++) {
    diff |= (bufA[i] ?? 0) ^ (bufB[i] ?? 0);
  }
  return diff === 0;
}

export async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(input),
  );
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export async function sha512HexUpper(input: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-512",
    new TextEncoder().encode(input),
  );
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("")
    .toUpperCase();
}

export function requireBearerJwt(req: Request): string | null {
  const auth = req.headers.get("Authorization");
  if (!auth || !auth.startsWith("Bearer ") || auth.length < 16) return null;
  return auth;
}

export function isLocalUnverifiedAllowed(envFlag: string): boolean {
  return Deno.env.get(envFlag) === "1";
}

export function mergeRedirectMetadata(
  metadata: unknown,
  urls: {
    return_url?: string | null;
    cancel_url?: string | null;
    result_url?: string | null;
  },
): Record<string, unknown> {
  const base =
    metadata && typeof metadata === "object" && !Array.isArray(metadata)
      ? { ...(metadata as Record<string, unknown>) }
      : {};
  if (urls.return_url) base.return_url = urls.return_url;
  if (urls.cancel_url) base.cancel_url = urls.cancel_url;
  if (urls.result_url) base.result_url = urls.result_url;
  return base;
}

export function stubCheckoutUrl(
  returnUrl: string,
  psp: "contipay" | "paynow",
  intentId: string,
): string {
  try {
    const u = new URL(returnUrl);
    u.searchParams.set("psp", psp);
    u.searchParams.set("stub", "1");
    u.searchParams.set("intent_id", intentId);
    return u.toString();
  } catch {
    const sep = returnUrl.includes("?") ? "&" : "?";
    return `${returnUrl}${sep}psp=${psp}&stub=1&intent_id=${encodeURIComponent(intentId)}`;
  }
}

export function formatMoney2(amount: number | string): string {
  const n = typeof amount === "number" ? amount : Number(amount);
  if (!Number.isFinite(n)) throw new Error(`invalid amount: ${amount}`);
  return n.toFixed(2);
}

export async function paynowHashFromValues(
  values: Iterable<string>,
  integrationKey: string,
): Promise<string> {
  let concat = "";
  for (const value of values) concat += value;
  concat += integrationKey;
  return sha512HexUpper(concat);
}

export function parsePaynowMessage(
  raw: string,
): { keys: string[]; fields: Record<string, string>; orderedValues: string[] } {
  const keys: string[] = [];
  const fields: Record<string, string> = {};
  const orderedValues: string[] = [];
  const trimmed = raw.trim();
  if (!trimmed) return { keys, fields, orderedValues };

  if (trimmed.startsWith("{")) {
    const obj = JSON.parse(trimmed) as Record<string, unknown>;
    for (const key of Object.keys(obj)) {
      const value = obj[key];
      const text = value == null ? "" : String(value);
      keys.push(key);
      fields[key.toLowerCase()] = text;
      if (key.toLowerCase() !== "hash") orderedValues.push(text);
    }
    return { keys, fields, orderedValues };
  }

  for (const part of trimmed.split("&")) {
    if (!part) continue;
    const eq = part.indexOf("=");
    const rawKey = eq === -1 ? part : part.slice(0, eq);
    const rawVal = eq === -1 ? "" : part.slice(eq + 1);
    const key = decodeURIComponent(rawKey.replace(/\+/g, " "));
    const value = decodeURIComponent(rawVal.replace(/\+/g, " "));
    keys.push(key);
    fields[key.toLowerCase()] = value;
    if (key.toLowerCase() !== "hash") orderedValues.push(value);
  }
  return { keys, fields, orderedValues };
}

export async function verifyPaynowMessageHash(
  rawBody: string,
  integrationKey: string,
): Promise<{ ok: boolean; fields: Record<string, string> }> {
  const { fields, orderedValues } = parsePaynowMessage(rawBody);
  const provided = (fields.hash ?? "").trim();
  if (!provided) return { ok: false, fields };
  const expected = await paynowHashFromValues(orderedValues, integrationKey);
  return {
    ok: timingSafeEqualStr(expected.toUpperCase(), provided.toUpperCase()),
    fields,
  };
}

export type PaynowInitiateParams = {
  integrationId: string;
  integrationKey: string;
  reference: string;
  amount: string;
  additionalinfo?: string;
  returnurl: string;
  resulturl: string;
  authemail?: string;
  authphone?: string;
  authname?: string;
  initiateUrl?: string;
};

export type PaynowInitiateResult = {
  browserurl: string;
  pollurl: string;
  status: string;
  fields: Record<string, string>;
  raw: string;
};

export async function initiatePaynowTransaction(
  params: PaynowInitiateParams,
): Promise<PaynowInitiateResult> {
  const ordered: Array<[string, string]> = [
    ["id", String(params.integrationId)],
    ["reference", params.reference],
    ["amount", params.amount],
  ];
  if (params.additionalinfo) ordered.push(["additionalinfo", params.additionalinfo]);
  ordered.push(["returnurl", params.returnurl], ["resulturl", params.resulturl]);
  if (params.authemail) ordered.push(["authemail", params.authemail]);
  if (params.authphone) ordered.push(["authphone", params.authphone]);
  if (params.authname) ordered.push(["authname", params.authname]);
  ordered.push(["status", "Message"]);

  const hash = await paynowHashFromValues(
    ordered.map(([, value]) => value),
    params.integrationKey,
  );
  ordered.push(["hash", hash]);

  const body = ordered
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join("&");
  const res = await fetch(params.initiateUrl ?? PAYNOW_INITIATE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  const raw = await res.text();
  const { fields, orderedValues } = parsePaynowMessage(raw);
  const status = (fields.status ?? "").trim();
  if (status.toLowerCase() === "error" || !status) {
    const detail = fields.error ?? (raw.slice(0, 200) || String(res.status));
    throw new Error(`Paynow initiate failed: ${detail}`);
  }
  if (!fields.hash?.trim()) throw new Error("Paynow initiate response hash missing");
  const expected = await paynowHashFromValues(orderedValues, params.integrationKey);
  if (!timingSafeEqualStr(expected.toUpperCase(), fields.hash.toUpperCase())) {
    throw new Error("Paynow initiate response hash invalid");
  }
  const browserurl = fields.browserurl ?? "";
  const pollurl = fields.pollurl ?? "";
  if (!browserurl || !pollurl) {
    throw new Error(
      `Paynow initiate missing browserurl/pollurl: ${raw.slice(0, 200)}`,
    );
  }
  return { browserurl, pollurl, status, fields, raw };
}

export async function pollPaynowStatus(
  pollurl: string,
  integrationKey: string,
): Promise<Record<string, string>> {
  const res = await fetch(pollurl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "",
  });
  const raw = await res.text();
  const verified = await verifyPaynowMessageHash(raw, integrationKey);
  if (!verified.ok) throw new Error("Paynow poll response hash invalid");
  return verified.fields;
}

export function isPaynowSuccessStatus(
  status: string | undefined | null,
): boolean {
  const value = (status ?? "").trim().toLowerCase();
  return value === "paid" || value === "awaiting delivery" || value === "delivered";
}

export function isPaynowFailureStatus(
  status: string | undefined | null,
): boolean {
  const value = (status ?? "").trim().toLowerCase();
  return value === "cancelled" || value === "refunded" || value === "disputed";
}

export async function contipayHmacSha256Hex(
  rawBody: string,
  hmacSecret: string,
): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(hmacSecret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(rawBody),
  );
  return Array.from(new Uint8Array(signature))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export function contipayBaseUrl(): string {
  const override = Deno.env.get("CONTIPAY_API_BASE_URL")?.trim();
  if (override) return override.replace(/\/$/, "");
  const mode = (Deno.env.get("CONTIPAY_MODE") ?? "live").toLowerCase();
  return mode === "dev" || mode === "uat" || mode === "test"
    ? CONTIPAY_UAT_BASE
    : CONTIPAY_LIVE_BASE;
}

export type ContipayRedirectParams = {
  apiKey: string;
  apiSecret: string;
  merchantId: string | number;
  reference: string;
  amount: number | string;
  currencyCode: string;
  webhookUrl: string;
  successUrl: string;
  cancelUrl: string;
  description?: string;
  customer?: {
    firstName?: string;
    surname?: string;
    middleName?: string;
    nationalId?: string;
    email?: string;
    cell: string;
    countryCode?: string;
  };
};

export function extractContipayCheckoutUrl(payload: unknown): string | null {
  if (!payload || typeof payload !== "object") return null;
  const obj = payload as Record<string, unknown>;
  const candidates = [
    obj.paymentUrl,
    obj.payment_url,
    obj.redirectUrl,
    obj.redirect_url,
    obj.checkoutUrl,
    obj.checkout_url,
    obj.url,
    obj.browserUrl,
    obj.browser_url,
  ];
  for (const candidate of candidates) {
    if (typeof candidate === "string" && /^https?:\/\//i.test(candidate)) {
      return candidate;
    }
  }
  if (obj.data && typeof obj.data === "object") {
    return extractContipayCheckoutUrl(obj.data);
  }
  if (obj.result && typeof obj.result === "object") {
    return extractContipayCheckoutUrl(obj.result);
  }
  return null;
}

export async function initiateContipayRedirect(
  params: ContipayRedirectParams,
): Promise<{ checkoutUrl: string; raw: unknown }> {
  const cell = params.customer?.cell?.trim();
  if (!cell) throw new Error("ContiPay redirect requires customer.cell (phone)");
  const amountNum = typeof params.amount === "number" ? params.amount : Number(params.amount);
  if (!Number.isFinite(amountNum)) {
    throw new Error(`invalid ContiPay amount: ${params.amount}`);
  }

  const payload = {
    reference: params.reference,
    cod: false,
    coc: false,
    description: params.description ?? `Payment ${params.reference}`,
    amount: amountNum,
    customer: {
      firstName: params.customer?.firstName ?? "Customer",
      surname: params.customer?.surname ?? "Customer",
      middleName: params.customer?.middleName ?? "-",
      nationalId: params.customer?.nationalId ?? "-",
      email:
        params.customer?.email?.trim() ||
        `${cell.replace(/\D/g, "") || "customer"}@contipay.local`,
      cell,
      countryCode: params.customer?.countryCode ?? "ZW",
    },
    currencyCode: params.currencyCode,
    merchantId: Number(params.merchantId) || params.merchantId,
    webhookUrl: params.webhookUrl,
    successUrl: params.successUrl,
    cancelUrl: params.cancelUrl,
  };

  const auth = btoa(`${params.apiKey}:${params.apiSecret}`);
  const res = await fetch(`${contipayBaseUrl()}${CONTIPAY_ACQUIRE_PATH}`, {
    method: "PUT",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: `Basic ${auth}`,
    },
    body: JSON.stringify(payload),
  });
  const text = await res.text();
  let raw: unknown = text;
  try {
    raw = JSON.parse(text);
  } catch {
    // preserve provider text for diagnostics
  }
  if (!res.ok) {
    const msg =
      typeof raw === "object" && raw && "message" in raw
        ? String((raw as { message: unknown }).message)
        : text.slice(0, 300);
    throw new Error(`ContiPay initiate HTTP ${res.status}: ${msg}`);
  }
  const status =
    typeof raw === "object" && raw && "status" in raw
      ? String((raw as { status: unknown }).status).toLowerCase()
      : "";
  if (status === "error" || status === "failed") {
    const msg =
      typeof raw === "object" && raw && "message" in raw
        ? String((raw as { message: unknown }).message)
        : text.slice(0, 300);
    throw new Error(`ContiPay initiate failed: ${msg}`);
  }
  const checkoutUrl = extractContipayCheckoutUrl(raw);
  if (!checkoutUrl) {
    throw new Error(
      "ContiPay initiate succeeded but no payment/redirect URL in response — check ContiPay API shape with merchant docs",
    );
  }
  return { checkoutUrl, raw };
}

export function contipaySignatureFromHeaders(req: Request): string {
  for (const header of ["x-contipay-signature", "x-signature", "signature"]) {
    const value = (req.headers.get(header) ?? "").trim();
    if (value) return value.replace(/^sha256=/i, "").trim();
  }
  return "";
}

export function defaultWebhookUrl(functionName: string): string {
  const base = (Deno.env.get("SUPABASE_URL") ?? "").replace(/\/$/, "");
  return `${base}/functions/v1/${functionName}`;
}

export function normalizeEcocashMsisdn(raw: string): string | null {
  let digits = String(raw || "").replace(/\D/g, "");
  if (digits.startsWith("0") && digits.length === 10) {
    digits = `263${digits.slice(1)}`;
  }
  return /^263\d{9}$/.test(digits) ? digits : null;
}

export type EcoCashC2bResult = {
  ok: boolean;
  stub: boolean;
  providerReference: string | null;
  status: string;
  raw: unknown;
};

export async function initiateEcocashC2b(params: {
  apiKey: string;
  msisdn: string;
  amount: number;
  currency: string;
  reason: string;
  sourceReference: string;
  environment?: "sandbox" | "live";
}): Promise<EcoCashC2bResult> {
  const environment = params.environment === "live" ? "live" : "sandbox";
  const base = (
    Deno.env.get("ECOCASH_API_BASE_URL") ??
    "https://developers.ecocash.co.zw/api/ecocash_pay"
  ).replace(/\/$/, "");
  const path = environment === "live"
    ? Deno.env.get("ECOCASH_C2B_PATH_LIVE") ?? "/api/v2/payment/instant/c2b/live"
    : Deno.env.get("ECOCASH_C2B_PATH_SANDBOX") ?? "/api/v2/payment/instant/c2b/sandbox";
  const url = `${base}${path.startsWith("/") ? path : `/${path}`}`;
  const authMode = (Deno.env.get("ECOCASH_AUTH_HEADER") ?? "x-api-key")
    .trim()
    .toLowerCase();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
  };
  if (authMode === "bearer" || authMode === "authorization") {
    headers.Authorization = `Bearer ${params.apiKey}`;
  } else {
    headers["X-API-KEY"] = params.apiKey;
  }

  const res = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify({
      customerEcocashPhoneNumber: params.msisdn,
      amount: Number(params.amount.toFixed(2)),
      reason: params.reason.slice(0, 50),
      currency: params.currency,
      sourceReference: params.sourceReference,
    }),
  });
  const text = await res.text();
  let raw: unknown = {};
  try {
    raw = text ? JSON.parse(text) : {};
  } catch {
    raw = { raw: text };
  }
  if (!res.ok) {
    throw new Error(`EcoCash C2B HTTP ${res.status}: ${text.slice(0, 400)}`);
  }
  const obj = raw && typeof raw === "object"
    ? raw as Record<string, unknown>
    : {};
  const providerReference = String(
    obj.ecocashReference ??
      obj.transactionReference ??
      obj.reference ??
      obj.id ??
      "",
  ) || null;
  const status = String(
    obj.transactionStatus ?? obj.status ?? obj.message ?? "PENDING_CUSTOMER",
  );
  return { ok: true, stub: false, providerReference, status, raw };
}
