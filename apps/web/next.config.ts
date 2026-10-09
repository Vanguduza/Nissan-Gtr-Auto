import type { NextConfig } from "next";

/** Workspace packages use ESM `.js` specifiers that map to `.ts` sources. */
/** Vercel rebuild trigger — keep Root Directory (apps/web) in the commit diff. */
const extensionAlias = {
  ".js": [".ts", ".tsx", ".js", ".jsx"],
  ".mjs": [".mts", ".mjs"],
} as const;

// The POS preview data source is development-only (lib/pos/preview-gateway.ts). Refuse to build
// a production bundle with it switched on.
if (process.env.NODE_ENV === "production" && process.env.NEXT_PUBLIC_POS_PREVIEW === "1") {
  throw new Error("NEXT_PUBLIC_POS_PREVIEW=1 is not allowed in production builds.");
}

const nextConfig: NextConfig = {
  transpilePackages: [
    "@gtr/ui",
    "@gtr/shared",
    "@gtr/supabase-client",
    "@gtr/documents",
  ],
  // App-owned public media may come from the currently configured Supabase
  // project. EPC catalog parts/diagram payloads are served through the R2
  // gateway and are intentionally not tied to a Supabase project hostname.
  images: {
    formats: ["image/avif", "image/webp"],
    remotePatterns: [
      {
        protocol: "https",
        hostname: "*.supabase.co",
        pathname: "/storage/v1/object/public/**",
      },
      {
        protocol: "http",
        hostname: "127.0.0.1",
        port: "54321",
        pathname: "/storage/v1/object/public/**",
      },
    ],
  },
  // Next 16 blocks cross-origin /_next/webpack-hmr by default. Visiting
  // http://127.0.0.1:3000 while the server advertises localhost prevents
  // client hydration — Menu and other "use client" controls stay dead SSR.
  allowedDevOrigins: ["127.0.0.1", "localhost"],
  webpack: (config) => {
    config.resolve.extensionAlias = {
      ...(config.resolve.extensionAlias ?? {}),
      ...extensionAlias,
    };
    return config;
  },
  // Next 16+: turbopack config (replaces experimental.turbo).
  turbopack: {
    resolveExtensions: [".tsx", ".ts", ".jsx", ".js", ".mjs", ".json"],
  },
};

export default nextConfig;
