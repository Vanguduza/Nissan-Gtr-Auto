// Recovered from the deployed bundle (catalog-offline-release v1): the source was never committed.
// Gives a signed-in staff device the current encrypted offline catalogue: wraps the release's content
// key with the device's RSA-OAEP public key, signs a 6-hour download URL for the encrypted SQLite
// file in R2 and records the device grant (revoked devices are refused). R2 credentials come from
// function secrets, or catalog_r2_runtime_config() when they are not set.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { GetObjectCommand, S3Client } from "npm:@aws-sdk/client-s3@3";
import { getSignedUrl } from "npm:@aws-sdk/s3-request-presigner@3";
const B64 = (b: Uint8Array) => {
  let s = "";
  for (const x of b)s += String.fromCharCode(x);
  return btoa(s);
};
const fromB64 = (s: string) =>Uint8Array.from(atob(s), (c)=>c.charCodeAt(0));
const json = (status: number, body: unknown) =>new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store"
    }
  });
Deno.serve(async (req)=>{
  if (req.method !== "POST") return json(405, {
    error: "POST required"
  });
  const auth = req.headers.get("Authorization");
  if (!auth?.startsWith("Bearer ")) return json(401, {
    error: "sign in required"
  });
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!supabaseUrl || !serviceKey) return json(503, {
    error: "service unavailable"
  });
  const admin = createClient(supabaseUrl, serviceKey, {
    auth: {
      persistSession: false,
      autoRefreshToken: false
    }
  });
  const token = auth.replace(/^Bearer\s+/i, "").trim();
  const { data: userData, error: userError } = await admin.auth.getUser(token);
  const userId = userData.user?.id;
  if (userError || !userId) return json(401, {
    error: "invalid session"
  });
  const [{ data: profile }, { data: roles }] = await Promise.all([
    admin.from("profiles").select("is_staff").eq("id", userId).maybeSingle(),
    admin.from("staff_roles").select("role").eq("user_id", userId).limit(1)
  ]);
  if (profile?.is_staff !== true && (roles?.length ?? 0) === 0) return json(403, {
    error: "staff access required"
  });
  const body = await req.json().catch(()=>({}));
  const publicKeyB64 = String(body.device_public_key_spki_b64 ?? "").trim();
  const appFlavor = String(body.app_flavor ?? "").trim().toLowerCase();
  if (!publicKeyB64 || ![
    "phone",
    "tablet"
  ].includes(appFlavor)) return json(400, {
    error: "device key and app flavor required"
  });
  let spki: Uint8Array;
  try {
    spki = fromB64(publicKeyB64);
  } catch  {
    return json(400, {
      error: "invalid device public key"
    });
  }
  if (spki.byteLength < 256 || spki.byteLength > 1024) return json(400, {
    error: "invalid device public key"
  });
  let publicKey: CryptoKey;
  try {
    publicKey = await crypto.subtle.importKey("spki", spki, {
      name: "RSA-OAEP",
      hash: "SHA-256"
    }, false, [
      "encrypt"
    ]);
  } catch  {
    return json(400, {
      error: "RSA-OAEP SHA-256 public key required"
    });
  }
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", spki));
  const deviceHash = [
    ...digest
  ].map((x)=>x.toString(16).padStart(2, "0")).join("");
  const { data: releaseRows, error: releaseError } = await admin.rpc("catalog_offline_current_release_secret");
  const release = Array.isArray(releaseRows) ? releaseRows[0] : releaseRows;
  if (releaseError || !release?.release_id || !release?.content_key_b64) return json(503, {
    error: "offline catalogue release unavailable"
  });
  const { data: revoked } = await admin.rpc("catalog_offline_device_is_revoked", {
    p_release_id: release.release_id,
    p_user_id: userId,
    p_device_key_sha256: deviceHash,
    p_app_flavor: appFlavor
  });
  if (revoked === true) return json(403, {
    error: "device catalogue grant revoked"
  });
  let contentKey: Uint8Array;
  try {
    contentKey = fromB64(String(release.content_key_b64));
  } catch  {
    return json(503, {
      error: "offline catalogue key unavailable"
    });
  }
  if (contentKey.byteLength < 24 || contentKey.byteLength > 64) return json(503, {
    error: "offline catalogue key invalid"
  });
  const wrapped = new Uint8Array(await crypto.subtle.encrypt({
    name: "RSA-OAEP"
  }, publicKey, contentKey));
  let accountId = Deno.env.get("CLOUDFLARE_ACCOUNT_ID")?.trim() ?? "", accessKeyId = Deno.env.get("CLOUDFLARE_R2_ACCESS_KEY_ID")?.trim() ?? "", secretAccessKey = Deno.env.get("CLOUDFLARE_R2_SECRET_ACCESS_KEY")?.trim() ?? "", bucket = Deno.env.get("CLOUDFLARE_R2_BUCKET")?.trim() ?? "", endpoint = Deno.env.get("CLOUDFLARE_R2_ENDPOINT")?.trim() ?? "";
  if (!accountId || !accessKeyId || !secretAccessKey || !bucket) {
    const { data: cfg } = await admin.rpc("catalog_r2_runtime_config");
    if (cfg && typeof cfg === "object") {
      accountId = String(cfg.account_id ?? "").trim();
      accessKeyId = String(cfg.access_key_id ?? "").trim();
      secretAccessKey = String(cfg.secret_access_key ?? "").trim();
      bucket = String(cfg.bucket ?? "").trim();
      endpoint = String(cfg.endpoint ?? "").trim();
    }
  }
  if (!accountId || !accessKeyId || !secretAccessKey || !bucket) return json(503, {
    error: "catalogue storage unavailable"
  });
  const r2 = new S3Client({
    region: "auto",
    endpoint: endpoint || `https://${accountId}.r2.cloudflarestorage.com`,
    credentials: {
      accessKeyId,
      secretAccessKey
    }
  });
  const signedUrl = await getSignedUrl(r2, new GetObjectCommand({
    Bucket: bucket,
    Key: String(release.r2_object_key)
  }), {
    expiresIn: 21600
  });
  await admin.rpc("catalog_offline_record_device_grant", {
    p_release_id: release.release_id,
    p_user_id: userId,
    p_device_key_sha256: deviceHash,
    p_app_flavor: appFlavor
  });
  return json(200, {
    release_id: release.release_id,
    version: release.version,
    schema_version: release.schema_version,
    encrypted_size_bytes: Number(release.encrypted_size_bytes),
    encrypted_sha256: release.encrypted_sha256,
    sqlite_page_size: release.sqlite_page_size,
    encryption_format: release.encryption_format,
    vehicle_count: Number(release.vehicle_count),
    section_count: Number(release.section_count),
    diagram_count: Number(release.diagram_count),
    fitment_count: Number(release.fitment_count),
    image_count: Number(release.image_count),
    source_release: release.source_release,
    signed_url: signedUrl,
    url_expires_in: 21600,
    wrapped_key_b64: B64(wrapped),
    device_key_sha256: deviceHash
  });
});
