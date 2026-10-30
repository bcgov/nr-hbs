import { expect, test } from '@playwright/test';

import { localDate, resultsOrEmpty, toast } from './helpers/screens';

/**
 * The anonymous side of HBS: the public read-only area (docs/public-access.md)
 * and what a visitor without a session gets elsewhere. These run with no saved
 * session at all — base Playwright `test`, empty storage — so nothing from the
 * E2E login leaks in. Read-only.
 */
test.use({ storageState: { cookies: [], origins: [] } });

const PUBLIC_SCREENS = [
  'billing-history',
  'scaling-history',
  'issued-document',
  'issued-documents',
  'timber-mark-search',
  'sampling-plans',
  'sampling-populations',
  'sampling-strata',
];

test('the public home lists the public reports and offers a login', async ({ page }) => {
  await page.goto('/public');
  await expect(page.getByTestId('hbs-public__header')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('heading', { level: 1, name: 'Harvest Billing System' })).toBeVisible();
  await expect(page.getByTestId('hbs-public__login')).toBeVisible();
  for (const section of ['Harvest Reports', 'Invoice Copies', 'Stratum Advisor']) {
    await expect(page.getByText(section, { exact: true }).first()).toBeVisible();
  }
});

for (const id of PUBLIC_SCREENS) {
  test(`public screen opens: ${id}`, async ({ page }) => {
    await page.goto(`/public/${id}`);
    await expect(page.getByTestId('hbs-public__header')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByTestId('forbidden-title')).toHaveCount(0);
    await expect(page.getByText('Couldn’t load data')).toHaveCount(0);
  });
}

test('a public issued-documents search runs, with no report buttons', async ({ page }) => {
  await page.goto(
    `/public/issued-documents?issueDateFrom=${localDate(30)}&issueDateTo=${localDate(0)}`,
  );
  await expect(resultsOrEmpty(page)).toBeVisible({ timeout: 60_000 });
  await expect(toast(page, 'error')).toHaveCount(0);
  // Reports are logged-in only (they can carry personal information).
  await expect(page.getByRole('button', { name: /^Print/ })).toHaveCount(0);
});

test('the "How to get access" page renders', async ({ page }) => {
  await page.goto('/public/access');
  await expect(page.getByRole('heading', { level: 1, name: 'How to get access' })).toBeVisible({
    timeout: 30_000,
  });
});

test('a protected page shows the login landing to a visitor', async ({ page }) => {
  await page.goto('/queries/billing-history');
  await expect(page.getByTestId('landing-title')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByTestId('landing-button__idir')).toBeVisible();
  await expect(page.getByTestId('landing-public-link')).toBeVisible();
});

test('the API refuses a request without a token', async ({ request }) => {
  const res = await request.get('/api/v1/hbs/queries/harvestHistory.billing');
  expect(res.status()).toBe(401);
});
