import { readFileSync, writeFileSync } from 'node:fs';

import { test as base } from '@playwright/test';

import { authSessionJson, isAuthKey, SESSION_STORAGE_STATE, STORAGE_STATE } from './utils';

/** The saved sessionStorage snapshot (auth keys only), or null when there isn't one yet. */
const readSessionSnapshot = (): Record<string, string> | null => {
  try {
    const all = JSON.parse(readFileSync(SESSION_STORAGE_STATE, 'utf-8')) as Record<string, string>;
    return Object.fromEntries(Object.entries(all).filter(([key]) => isAuthKey(key)));
  } catch {
    return null;
  }
};

/**
 * Shared E2E test object. Use this instead of importing `test` straight from
 * `@playwright/test` so every spec gets the token persistence below.
 *
 * WHY: BC Gov SSO issues a **rotating** refresh token — each renewal mints a new
 * one and invalidates its predecessor. Every test context is created from the
 * same saved state (see playwright.config.ts), so without this, test 1 rotates
 * the shared refresh token and test 2 starts from the now-stale snapshot →
 * renewal refused → cascading auth failure across the whole suite.
 *
 * There are TWO halves to that state, and only one of them is Playwright's:
 *
 *   1. `storageState` (cookies + localStorage) — handled by `context.storageState`.
 *   2. **sessionStorage**, which is where oidc-client-ts keeps the tokens.
 *      Playwright does not capture it at all, so it is snapshotted by hand:
 *      injected with `addInitScript` before any page script runs, and written
 *      back after each test. Only the `oidc.` keys travel (see `isAuthKey`):
 *      the app also keeps each screen's criteria there, and carrying those
 *      over would start a test on a form an earlier test filled in.
 *
 * Safe because the suite runs serially (workers: 1) — no two contexts ever hold
 * the same refresh token concurrently. The `auth.setup` project is unaffected;
 * it saves both halves itself and imports the base `test`.
 */
export const test = base.extend<{ persistRotatedTokens: void }>({
  // Overridden rather than handled in an auto fixture: the init script has to
  // be registered on the context BEFORE the page exists, and overriding `page`
  // is the only way to guarantee that ordering.
  //
  // The second parameter is Playwright's `use`, renamed: it is passed
  // positionally, and calling it `use` inside a function named `page` makes
  // eslint's react-hooks rule read it as React 19's `use` hook and fail the
  // build. Nothing here is React.
  page: async ({ context }, runTest) => {
    const snapshot = readSessionSnapshot();
    if (snapshot) {
      await context.addInitScript((entries: Record<string, string>) => {
        for (const [key, value] of Object.entries(entries)) {
          window.sessionStorage.setItem(key, value);
        }
      }, snapshot);
    }

    const page = await context.newPage();
    await runTest(page);
  },

  persistRotatedTokens: [
    async ({ page, context }, use) => {
      await use();

      // Both may fail if a test tore its own context down; ignore and let the
      // next test fall back to the prior snapshot.
      const session = await page.evaluate(authSessionJson).catch(() => null);
      if (session) {
        writeFileSync(SESSION_STORAGE_STATE, session);
      }

      await context.storageState({ path: STORAGE_STATE }).catch(() => undefined);
    },
    { auto: true },
  ],
});

export { expect } from '@playwright/test';
export type { Page, Locator, Route } from '@playwright/test';
