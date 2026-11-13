import { defineConfig, devices } from '@playwright/test';

import { baseURL, STORAGE_STATE } from './e2e/utils';

/**
 * Playwright E2E config — mirrors nr-rept's structure.
 *
 * Auth flow:
 *   1. `npm run e2e:login` runs the `setup` project headed, parks at the IDIR
 *      login page, and saves cookies + localStorage to e2e/.auth/user.json
 *      once you successfully sign in.
 *   2. All other projects start from that storageState so each test boots
 *      already-authenticated.
 *
 * Override the target with E2E_BASE_URL (e.g. http://localhost:3000 for local).
 */
export default defineConfig({
  // Ceiling per test attempt. Most tests finish in seconds against TEST, but
  // this leaves room for gotoWithRetry/gotoProtected to ride out a refused or
  // stalled navigation (up to 4 tries with backoff) and for the screen crawl.
  // Each search adds its own 45s on top (expectSearchDone in helpers/screens).
  // Same approach as nr-fta #53.
  timeout: 90_000,
  testDir: './e2e',
  // Serial execution. We share one SSO refresh token via storageState
  // across runs; parallel workers race that refresh and intermittently leave
  // some contexts stuck on the white `<Loading>` overlay.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  // Per-test retries on top of the per-navigation retries in e2e/utils.ts.
  // There is no maxFailures: the run completes, so the report shows every
  // failing test rather than the first one (CI re-runs the suite at most once
  // more, see "Run Playwright" in .github/workflows/reusable-tests.yml).
  retries: process.env.CI ? 2 : 0,
  reporter: [['line'], ['list', { printSteps: true }], ['html', { open: 'never' }]],
  use: {
    baseURL,
    // Never trace in CI. Every trace embeds the context's storageState, i.e.
    // the e2e account's SSO cookies and tokens, and CI uploads
    // playwright-report as an artifact. Playwright offers no way to scrub it.
    // Reproduce locally with traces instead.
    trace: process.env.CI ? 'off' : 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
      // Sign-in spans Keycloak → Azure AD → TOTP, and the TOTP helper can
      // wait up to ~30s for a fresh code window, so it can't fit in 30s.
      // (Each wait inside it is still capped at 30s.)
      timeout: 60_000,
      // No trace, screenshot or video for sign-in. A trace records every
      // action's arguments verbatim (Playwright redacts nothing), so a retried
      // setup would write the IDIR password into playwright-report, which CI
      // uploads as an artifact. A screenshot can catch the MFA page. Debug
      // sign-in locally instead.
      use: { ...devices['Desktop Chrome'], trace: 'off', screenshot: 'off', video: 'off' },
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
    {
      name: 'Google Chrome',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome',
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
    {
      name: 'safari',
      use: {
        ...devices['Desktop Safari'],
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
    {
      name: 'Microsoft Edge',
      use: {
        ...devices['Desktop Edge'],
        channel: 'msedge',
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
  ],
});
