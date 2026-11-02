/** Unverified behavior is available only against an explicitly local Supabase stack. */
export function isLocalDevelopmentEnvironment(): boolean {
  for (const key of ["ENVIRONMENT", "ENV", "NODE_ENV", "DENO_ENV"]) {
    const environment = Deno.env.get(key)?.trim().toLowerCase();
    if (["production", "prod", "staging"].includes(environment ?? "")) {
      return false;
    }
  }
  if (Deno.env.get("DENO_DEPLOYMENT_ID")?.trim()) return false;

  try {
    const url = new URL(Deno.env.get("SUPABASE_URL")?.trim() ?? "");
    return ["http:", "https:"].includes(url.protocol) &&
      ["localhost", "127.0.0.1", "[::1]", "host.docker.internal", "kong"]
        .includes(url.hostname.toLowerCase());
  } catch {
    return false;
  }
}

export function allowUnverifiedLocal(envFlag: string): boolean {
  return Deno.env.get(envFlag) === "1" && isLocalDevelopmentEnvironment();
}
