import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { HSTS_HEADER_VALUE, isLocalDevHost } from "@/lib/site-url";
import {
  createMiddlewareSupabase,
  isStaffSurfacePath,
} from "@/lib/supabase-middleware";

/**
 * 1. Production HTTPS + HSTS (local http left alone).
 * 2. Edge gate for /staff + /procurement — session + profiles.is_staff
 *    before StaffGate paints (cookies via @supabase/ssr).
 */
export async function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const local = isLocalDevHost(request.nextUrl.hostname);

  if (!local) {
    const forwarded = request.headers.get("x-forwarded-proto");
    const isHttps =
      forwarded === "https" || request.nextUrl.protocol === "https:";

    if (!isHttps) {
      const httpsUrl = request.nextUrl.clone();
      httpsUrl.protocol = "https:";
      return NextResponse.redirect(httpsUrl, 308);
    }
  }

  if (!isStaffSurfacePath(pathname)) {
    const passthrough = NextResponse.next();
    if (!local) {
      passthrough.headers.set("Strict-Transport-Security", HSTS_HEADER_VALUE);
    }
    return passthrough;
  }

  const { response, userId, isStaff } = await createMiddlewareSupabase(request);

  if (!local) {
    response.headers.set("Strict-Transport-Security", HSTS_HEADER_VALUE);
  }

  if (!userId) {
    const login = request.nextUrl.clone();
    login.pathname = "/login";
    login.search = "";
    login.searchParams.set("next", `${pathname}${search}`);
    return NextResponse.redirect(login);
  }

  if (!isStaff) {
    const account = request.nextUrl.clone();
    account.pathname = "/account";
    account.search = "";
    account.searchParams.set("notice", "staff-only");
    return NextResponse.redirect(account);
  }

  return response;
}

export const config = {
  matcher: [
    /*
     * All app routes + pages. Skip hashed static assets (HSTS still pins on
     * document responses). Favicon etc. still go through if linked as routes.
     */
    "/((?!_next/static|_next/image|favicon.ico).*)",
  ],
};
