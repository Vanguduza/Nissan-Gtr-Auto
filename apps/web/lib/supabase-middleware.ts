import { createServerClient } from "@supabase/ssr";
import { NextResponse, type NextRequest } from "next/server";
import type { Database } from "@gtr/supabase-client";

export type MiddlewareSupabase = {
  response: NextResponse;
  userId: string | null;
  isStaff: boolean;
};

/**
 * Cookie-aware Supabase client for Next middleware.
 * Always call getUser() so tokens refresh onto the response cookies.
 */
export async function createMiddlewareSupabase(
  request: NextRequest,
): Promise<MiddlewareSupabase> {
  let response = NextResponse.next({ request });

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anon) {
    return { response, userId: null, isStaff: false };
  }

  const supabase = createServerClient<Database>(url, anon, {
    cookies: {
      getAll() {
        return request.cookies.getAll();
      },
      setAll(cookiesToSet) {
        cookiesToSet.forEach(({ name, value }) => {
          request.cookies.set(name, value);
        });
        response = NextResponse.next({ request });
        cookiesToSet.forEach(({ name, value, options }) => {
          response.cookies.set(name, value, options);
        });
      },
    },
  });

  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user) {
    return { response, userId: null, isStaff: false };
  }

  const { data: profile } = await supabase
    .from("profiles")
    .select("is_staff")
    .eq("id", user.id)
    .maybeSingle();

  return {
    response,
    userId: user.id,
    isStaff: Boolean(profile?.is_staff),
  };
}

export function isStaffSurfacePath(pathname: string): boolean {
  return (
    pathname === "/staff" ||
    pathname.startsWith("/staff/") ||
    pathname === "/procurement" ||
    pathname.startsWith("/procurement/")
  );
}

/** Same-origin relative paths only (open-redirect guard). */
export function safeAuthNextPath(raw: string | null | undefined): string | null {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//")) return null;
  return raw;
}
