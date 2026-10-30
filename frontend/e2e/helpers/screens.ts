import { expect, type Locator, type Page } from '@playwright/test';

import { gotoProtected } from '../utils';

/**
 * The results table once the search has finished. While a search runs, the
 * panel shows Carbon's skeleton — itself a `table` inside `.fsp-search__table`
 * — so every results locator excludes `.cds--skeleton`, or a failed search
 * would pass for a finished one.
 */
const RESULTS_TABLE = '.fsp-search__table table:not(.cds--skeleton)';

/** A search screen's results table, or its "no records" empty state — the search round-tripped. */
export const resultsOrEmpty = (page: Page): Locator =>
  page.locator(`${RESULTS_TABLE}, .bc-empty-state`).first();

/** The results table's body rows. */
export const resultRows = (page: Page): Locator => page.locator(`${RESULTS_TABLE} tbody tr`);

/** The app's toast (NotificationProvider), optionally of one kind. */
export const toast = (page: Page, kind?: 'error' | 'warning' | 'success' | 'info'): Locator =>
  page.locator(kind ? `.cds--toast-notification--${kind}` : '.cds--toast-notification');

/** The page heading every screen opens with. */
export const pageHeading = (page: Page): Locator => page.getByRole('heading', { level: 1 }).first();

/**
 * Open a registry screen with URL criteria (deep link) and wait for either
 * results or the empty state — i.e. the backend query round-tripped.
 */
export async function runScreen(page: Page, area: string, screenId: string, params: Record<string, string> = {}) {
  const qs = new URLSearchParams(params).toString();
  await gotoProtected(page, `/${area}/${screenId}${qs ? `?${qs}` : ''}`);
  await expect(resultsOrEmpty(page)).toBeVisible({ timeout: 60_000 });
}

/** Open a registry screen without running a search. */
export async function openScreen(page: Page, area: string, screenId: string) {
  await gotoProtected(page, `/${area}/${screenId}`);
  await expect(pageHeading(page)).toBeVisible({ timeout: 30_000 });
}

/** A field's label text, whitespace collapsed ("To * (up to 12 months)"). */
export async function labelText(page: Page, inputId: string): Promise<string> {
  const text = await page.locator(`label[for="${inputId}"]`).first().innerText();
  return text.replace(/\s+/g, ' ').trim();
}

/** The index of a results column by its header text, or -1. */
export async function columnIndex(page: Page, header: string): Promise<number> {
  const headers = await page.locator(`${RESULTS_TABLE} thead th`).allInnerTexts();
  return headers.findIndex((h) => h.trim() === header);
}

/** The headers of the results table. */
export async function columnHeaders(page: Page): Promise<string[]> {
  const headers = await page.locator(`${RESULTS_TABLE} thead th`).allInnerTexts();
  return headers.map((h) => h.trim()).filter(Boolean);
}

/** Local yyyy-mm-dd `days` before today — the format the date criteria take. */
export const localDate = (daysAgo = 0): string => {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/**
 * Let the page's own requests settle, then fail if it is showing a load error,
 * a failed-search toast or the Forbidden page. `label` names the page in the
 * failure message. Soft assertions, so a crawl reports every broken page.
 */
export async function expectHealthyPage(page: Page, label: string): Promise<void> {
  await page.waitForLoadState('networkidle', { timeout: 30_000 }).catch(() => undefined);
  await expect.soft(page.getByTestId('forbidden-title'), `${label}: Forbidden page`).toHaveCount(0);
  await expect
    .soft(page.getByText('Couldn’t load data'), `${label}: "Couldn’t load data"`)
    .toHaveCount(0);
  await expect.soft(toast(page, 'error'), `${label}: error toast`).toHaveCount(0);
}
