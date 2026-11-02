import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/staff",
  testMatch: "**/*.spec.ts",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  outputDir: "test-results/staff",
  use: { baseURL: "http://127.0.0.1:3000", trace: "retain-on-failure", screenshot: "only-on-failure" },
  reporter: process.env.CI ? [["github"], ["html", { outputFolder: "playwright-report/staff", open: "never" }]] : "list",
  projects: [{ name: "staff-desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 1000 } } }],
  webServer: [
    { command: "node tests/staff/backend-fixture.mjs", url: "http://127.0.0.1:54329/__fixture/health", timeout: 20_000, reuseExistingServer: false },
    { command: "corepack pnpm --filter @gtr/web dev --hostname 127.0.0.1", url: "http://127.0.0.1:3000/login", timeout: 120_000, reuseExistingServer: false,
      env: { NEXT_PUBLIC_SUPABASE_URL: "http://127.0.0.1:54329", NEXT_PUBLIC_SUPABASE_ANON_KEY: "sb_publishable_loopback_synthetic_staff_fixture_only", NEXT_PUBLIC_SITE_URL: "http://127.0.0.1:3000", NEXT_TELEMETRY_DISABLED: "1" } },
  ],
});
