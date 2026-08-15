import type { NextConfig } from "next";

/** Workspace packages use ESM `.js` specifiers that map to `.ts` sources. */
/** Vercel rebuild trigger — keep Root Directory (apps/web) in the commit diff. */
const extensionAlias = {
  ".js": [".ts", ".tsx", ".js", ".jsx"],
  ".mjs": [".mts", ".mjs"],
} as const;

const nextConfig: NextConfig = {
  transpilePackages: [
    "@gtr/ui",
    "@gtr/shared",
    "@gtr/supabase-client",
    "@gtr/documents",
  ],
  // Legacy web till removed — counter POS is apps/android-pos.
  async redirects() {
    return [
      { source: "/staff/pos", destination: "/staff", permanent: true },
      { source: "/staff/pos/:path*", destination: "/staff", permanent: true },
    ];
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
