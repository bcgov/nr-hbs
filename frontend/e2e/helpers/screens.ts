import { expect, type Page } from '@playwright/test';

import { gotoProtected } from '../utils';

/**
 * Open a registry screen with URL criteria (deep link) and wait for either
 * results or the empty state — i.e. the backend query round-tripped.
 */
export async function runScreen(page: Page, area: string, screenId: string, params: Record<string, string> = {}) {
  const qs = new URLSearchParams(params).toString();
  await gotoProtected(page, `/${area}/${screenId}${qs ? `?${qs}` : ''}`);
  await expect(
    page.locator('.fsp-search__table table, .bc-empty-state').first(),
  ).toBeVisible({ timeout: 60_000 });
}
