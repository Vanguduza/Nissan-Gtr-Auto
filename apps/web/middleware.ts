import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { HSTS_HEADER_VALUE, isLocalDevHost } from "@/lib/site-url";

/**
 * Production HTTPS enforcement (defense-in-depth alongside Vercel TLS redirects).
 * Local `http://127.0.0.1:3000` / `localhost` is left alone.
 */
export function middleware(request: NextRequest) {
  if (isLocalDevHost(request.nextUrl.hostname)) {
    return NextResponse.next();
  }

  const forwarded = request.headers.get("x-forwarded-proto");
  const isHttps =
    forwarded === "https" || request.nextUrl.protocol === "https:";

  if (!isHttps) {
    const httpsUrl = request.nextUrl.clone();
    httpsUrl.protocol = "https:";
    return NextResponse.redirect(httpsUrl, 308);
  }

  const response = NextResponse.next();
  response.headers.set("Strict-Transport-Security", HSTS_HEADER_VALUE);
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
