import { expect, test } from './fixtures';
import { expectHealthyPage, pageHeading } from './helpers/screens';
import { gotoProtected } from './utils';

/**
 * Opens every screen the E2E account is offered, area by area, and checks each
 * one renders cleanly: its heading shows, it isn't the Forbidden page, and
 * nothing it loads on open fails (no "Couldn't load data", no error toast).
 *
 * The list comes from the area landing pages' tiles, which show exactly the
 * screens the user may open (the same role check as the side nav), so the crawl
 * follows the account's roles rather than a hard-coded list. Opening a screen
 * only reads: list screens that search on open run their query, nothing is
 * submitted.
 *
 * One test per area with soft assertions, so a run reports every broken screen
 * in that area rather than stopping at the first.
 */
const AREAS = [
  'queries',
  'scale-returns',
  'stratum-planner',
  'rating',
  'billing',
  'scale-control',
  'profiles',
  'admin',
];

for (const area of AREAS) {
  test(`every ${area} screen opens cleanly`, async ({ page }) => {
    test.setTimeout(10 * 60_000);

    await gotoProtected(page, `/${area}`);
    if (await page.getByTestId('forbidden-title').isVisible().catch(() => false)) {
      test.skip(true, `The E2E account has no access to ${area}`);
    }
    const tiles = page.locator('.welcome__tile');
    await expect(pageHeading(page)).toBeVisible({ timeout: 30_000 });
    const labels = (await tiles.locator('.welcome__tile-label').allInnerTexts()).map((l) => l.trim());
    test.info().annotations.push({ type: 'screens', description: `${labels.length} in ${area}` });
    if (labels.length === 0) test.skip(true, `No ${area} screens for the E2E account`);

    for (const [i, label] of labels.entries()) {
      await test.step(label, async () => {
        await gotoProtected(page, `/${area}`);
        await page.locator('.welcome__tile').nth(i).click();
        await page.waitForURL((url) => url.pathname !== `/${area}`, { timeout: 30_000 });
        await expect.soft(pageHeading(page), `${label}: heading`).toBeVisible({ timeout: 30_000 });
        await expectHealthyPage(page, `${label} (${new URL(page.url()).pathname})`);
      });
    }
  });
}
