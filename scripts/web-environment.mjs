export const APPROVED_SUPABASE_HOST = "bicyjghgdnzlnjqxzoud.supabase.co";

/** Structural release checks. Native authentication still requires live acceptance. */
export function validateWebEnvironment(env) {
  const errors = [];
  const rawUrl = env.NEXT_PUBLIC_SUPABASE_URL?.trim();
  const key = env.NEXT_PUBLIC_SUPABASE_ANON_KEY?.trim();
  if (!rawUrl) errors.push("NEXT_PUBLIC_SUPABASE_URL is required.");
  else {
    try {
      const url = new URL(rawUrl);
      if (url.protocol !== "https:" || url.hostname !== APPROVED_SUPABASE_HOST || url.username || url.password || url.port || url.search || url.hash || url.pathname !== "/")
        errors.push("The public Supabase URL must identify the approved HTTPS project.");
    } catch { errors.push("The public Supabase URL is invalid."); }
  }
  if (!key) errors.push("NEXT_PUBLIC_SUPABASE_ANON_KEY is required.");
  else if (key.startsWith("sb_secret_")) errors.push("A secret key must never be embedded in a browser build.");
  else if (key.startsWith("sb_publishable_")) {
    if (!/^sb_publishable_[A-Za-z0-9_-]{20,}$/.test(key)) errors.push("The publishable key has an invalid format.");
  } else {
    try {
      const pieces = key.split(".");
      if (pieces.length !== 3) throw new Error("Invalid public-key format");
      const claims = JSON.parse(Buffer.from(pieces[1], "base64url").toString("utf8"));
      if (claims.role !== "anon") errors.push("Only an anon or publishable key may be embedded in a browser build.");
      if (claims.ref !== APPROVED_SUPABASE_HOST.split(".")[0]) errors.push("The public key belongs to a different Supabase project.");
      if (typeof claims.exp !== "number" || !Number.isFinite(claims.exp) || claims.exp <= Date.now() / 1000) errors.push("The legacy public key must have an unexpired expiry.");
    } catch { errors.push("Use the approved project's publishable or legacy anon key."); }
  }
  return errors;
}
