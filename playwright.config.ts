import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/web",
  fullyParallel: false,
  workers: 2,
  retries: process.env.CI ? 1 : 0,
  timeout: 45_000,
  use: { baseURL: "http://127.0.0.1:3000", trace: "retain-on-failure", screenshot: "only-on-failure" },
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    { name: "phone", use: { ...devices["Desktop Chrome"], viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true } },
  ],
  webServer: {
    command: "corepack pnpm --filter @gtr/web dev --hostname 127.0.0.1",
    url: "http://127.0.0.1:3000",
    timeout: 120_000,
    reuseExistingServer: false,
    env: {
      NEXT_PUBLIC_SUPABASE_URL: "https://bicyjghgdnzlnjqxzoud.supabase.co",
      // Browser requests are intercepted with explicit fixtures. Never publish
      // this fixture value or treat these tests as provider acceptance.
      NEXT_PUBLIC_SUPABASE_ANON_KEY: "sb_publishable_ci_fixture_only_not_for_deployment",
      NEXT_PUBLIC_SITE_URL: "http://127.0.0.1:3000",
      NEXT_TELEMETRY_DISABLED: "1",
    },
  },
});
