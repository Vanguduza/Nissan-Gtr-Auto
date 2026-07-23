import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  transpilePackages: ["@gtr/ui", "@gtr/shared", "@gtr/supabase-client"],
};

export default nextConfig;
