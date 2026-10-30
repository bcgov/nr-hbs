import { expect, test, type Page } from './fixtures';
import { labelText, localDate, openScreen, pageHeading, resultsOrEmpty, toast } from './helpers/screens';
import { gotoProtected } from './utils';

/**
 * How the search screens behave before and around a search: labels, required
 * markers, validation, toasts and dependent fields. Everything here is checked
 * in the browser; the only queries run are the ones a deep link triggers.
 */

/** The required marker's colour against Carbon's error-text colour, both resolved in the page. */
const markerColours = (page: Page, inputId: string) =>
  page.locator(`label[for="${inputId}"] .hbs-required-marker`).evaluate((marker) => {
    const probe = document.createElement('span');
    probe.style.color = 'var(--cds-text-error)';
    marker.parentElement!.appendChild(probe);
    const expected = getComputedStyle(probe).color;
    probe.remove();
    return { actual: getComputedStyle(marker).color, expected };
  });

test.describe('history screens', () => {
  for (const s of [
    { id: 'billing-history', title: 'Mark Monthly Billing History', from: 'billingFrom', to: 'billingTo' },
    { id: 'scaling-history', title: 'Mark Monthly Scaling History', from: 'scalingFrom', to: 'scalingTo' },
  ]) {
    test(`${s.id}: no subtitle, interval labels, red required markers`, async ({ page }) => {
      await openScreen(page, 'queries', s.id);
      await expect(pageHeading(page)).toHaveText(s.title);
      await expect(page.locator('.fta-page-title__subtitle')).toHaveCount(0);

      expect(await labelText(page, `${s.id}-${s.from}`)).toBe('From *');
      expect(await labelText(page, `${s.id}-${s.to}`)).toBe('To * (up to 12 months)');

      const { actual, expected } = await markerColours(page, `${s.id}-${s.from}`);
      expect(actual).toBe(expected);
      const labelColour = await page
        .locator(`label[for="${s.id}-${s.from}"]`)
        .evaluate((el) => getComputedStyle(el).color);
      expect(actual, 'the asterisk stands out from the label text').not.toBe(labelColour);
    });

    test(`${s.id}: searching with nothing filled names the required fields`, async ({ page }) => {
      await openScreen(page, 'queries', s.id);
      await page.getByRole('button', { name: 'Search', exact: true }).click();
      await expect(page.getByText('From is required')).toBeVisible();
      await expect(page.getByText('To is required')).toBeVisible();
      await expect(resultsOrEmpty(page)).toHaveCount(0);
    });
  }

  test('a missing "one of" criterion warns, and the warning closes on leaving the page', async ({ page }) => {
    // The interval alone isn't enough: one of region, holder, file type, … is required.
    const from = localDate(60).slice(0, 7) + '-01';
    const to = localDate(0);
    await gotoProtected(page, `/queries/billing-history?billingFrom=${from}&billingTo=${to}`);
    const warning = toast(page, 'warning');
    await expect(warning).toContainText('More criteria needed');

    // Stays while the user is on the page to fix the criteria …
    await page.locator('#billing-history-billingFrom').click();
    await page.waitForTimeout(1_000);
    await expect(warning).toBeVisible();

    // … and closes when they go elsewhere.
    await page.getByTestId('side-nav-link-Home').click();
    await expect(page).toHaveURL(/\/home$/);
    await expect(warning).toHaveCount(0);
  });
});

test('Clear all empties the criteria', async ({ page }) => {
  await openScreen(page, 'queries', 'timber-mark-search');
  const mark = page.locator('#timber-mark-search-timberMark');
  await mark.fill('ab12');
  await expect(mark).toHaveValue('AB12');
  await page.getByRole('button', { name: 'Clear all' }).click();
  await expect(mark).toHaveValue('');
});

test('Scale Site Summary: the client fields wait for a client association', async ({ page }) => {
  await openScreen(page, 'queries', 'scale-site-summary');
  const client = page.locator('#scale-site-summary-clientNumber');
  const location = page.locator('#scale-site-summary-clientLocation');

  // "All" (the default) takes no client.
  await expect(client).toBeDisabled();
  await expect(location).toBeDisabled();
  await expect(page.getByText('Choose a Client Association other than All first')).toBeVisible();

  const association = (value: string) =>
    page.locator(`label[for="scale-site-summary-clientAssociation-${value}"]`);
  await association('MARK HOLDER').click();
  await expect(client).toBeEnabled();
  await expect(location).toBeEnabled();

  await location.fill('00');
  await association('ALL').click();
  await expect(location).toBeDisabled();
  await expect(location).toHaveValue('');
});

test.describe('Transmissions of Issued Statements', () => {
  test('has no "Recently Issued" checkbox; searching needs a From Date', async ({ page }) => {
    await openScreen(page, 'queries', 'statement-transmissions');
    await expect(page.getByText('Recently Issued (last 30 days)')).toHaveCount(0);
    await expect(page.getByRole('checkbox')).toHaveCount(0);
    await page.getByRole('button', { name: 'Search', exact: true }).click();
    await expect(toast(page, 'warning')).toContainText('From Date');
  });

  test('the Home "Recent" link searches the last 30 days', async ({ page }) => {
    await gotoProtected(page, '/home');
    const link = page.getByRole('link', { name: 'Recent Transmissions of Issued Statements' });
    test.skip((await link.count()) === 0, 'The E2E account does not see the statements block on Home');
    await link.click();
    await expect(page).toHaveURL(new RegExp(`/queries/statement-transmissions\\?fromDate=${localDate(30)}`));
    await expect(page.locator('#statement-transmissions-fromDate')).toHaveValue(localDate(30));
    await expect(resultsOrEmpty(page)).toBeVisible({ timeout: 20_000 });
  });
});
