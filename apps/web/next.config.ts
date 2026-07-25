import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  transpilePackages: ["@gtr/ui", "@gtr/shared", "@gtr/supabase-client"],
  // Workspace packages use ESM `.js` import specifiers pointing at `.ts` sources.
  webpack: (config) => {
    config.resolve.extensionAlias = {
      ...(config.resolve.extensionAlias ?? {}),
      ".js": [".ts", ".tsx", ".js", ".jsx"],
      ".mjs": [".mts", ".mjs"],
    };
    return config;
  },
};

export default nextConfig;
