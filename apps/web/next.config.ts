import type { NextConfig } from "next";

/** Workspace packages use ESM `.js` specifiers that map to `.ts` sources. */
const extensionAlias = {
  ".js": [".ts", ".tsx", ".js", ".jsx"],
  ".mjs": [".mts", ".mjs"],
} as const;

const nextConfig: NextConfig = {
  transpilePackages: ["@gtr/ui", "@gtr/shared", "@gtr/supabase-client"],
  webpack: (config) => {
    config.resolve.extensionAlias = {
      ...(config.resolve.extensionAlias ?? {}),
      ...extensionAlias,
    };
    return config;
  },
  // Turbopack (if enabled) needs the same remapping.
  experimental: {
    turbo: {
      resolveExtensions: [
        ".tsx",
        ".ts",
        ".jsx",
        ".js",
        ".mjs",
        ".json",
      ],
    },
  },
};

export default nextConfig;
