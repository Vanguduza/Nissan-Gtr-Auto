# Staff browser acceptance

Run `pnpm exec playwright test --config playwright.staff.config.ts` from the repository root, after any other Next development server has stopped. Install Chromium with Playwright first.

The fixture listens only on `127.0.0.1:54329` and uses synthetic credentials and data. The application follows its normal password login, cookie storage, middleware profile check, staff context and screen code. There are no product routes, flags or authentication bypasses for these tests.

This suite proves browser behavior, route boundaries and response-contract presentation against a mocked backend. It does **not** prove real Auth, Storage, RLS, payroll journals or provider acceptance. Native PostgreSQL smoke tests in `supabase/tests/` cover the server authorization and financial invariants. Frozen Deno tests exercise the real PDF renderer. Screen captures are attached to Playwright results for desktop and phone review.
