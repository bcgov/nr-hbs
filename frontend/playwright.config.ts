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
  // Tests finish in seconds against TEST (the whole suite runs in ~1.5 min),
  // so 30s is still generous and keeps a hang cheap: with one CI retry a stuck
  // test costs ~1 min, not ~3. Waits inside the helpers are capped below it.
  // The screen crawl and the PDF download set their own longer budgets.
  timeout: 30_000,
  testDir: './e2e',
  // Serial execution. We share one SSO refresh token via storageState
  // across runs; parallel workers race that refresh and intermittently leave
  // some contexts stuck on the white `<Loading>` overlay.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['line'], ['list', { printSteps: true }], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
      // A trace records every call's arguments, so a retried login would carry
      // the password and TOTP code into the uploaded report. Screenshots of the
      // Microsoft pages add nothing worth that.
      use: { ...devices['Desktop Chrome'], trace: 'off', screenshot: 'off' },
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
