import { expect, test, type Locator, type Page } from './fixtures';
import { pageHeading } from './helpers/screens';
import { gotoProtected } from './utils';

/**
 * The side nav: Home at the root, area sections with icons on the headings
 * only, bold only for the page the user is on, and the collapsed icon rail.
 * Read-only.
 */

const nav = (page: Page): Locator => page.getByRole('navigation', { name: 'Main navigation' });
const section = (page: Page, label: string): Locator => nav(page).getByRole('button', { name: label, exact: true });
const fontWeight = (locator: Locator): Promise<number> =>
  locator.evaluate((el) => Number(getComputedStyle(el).fontWeight));

/** The nav opens expanded by default but remembers a collapse; make sure it is open. */
const ensureExpanded = async (page: Page): Promise<void> => {
  const open = page.getByRole('button', { name: 'Open menu' });
  if (await open.isVisible().catch(() => false)) await open.click();
  await expect(page.locator('.side-nav-drawer--open')).toBeVisible();
};

test.beforeEach(async ({ page }) => {
  await gotoProtected(page, '/home');
  await ensureExpanded(page);
});

test('Home is a root link, with no "General" section', async ({ page }) => {
  const home = page.getByTestId('side-nav-link-Home');
  await expect(home).toBeVisible();
  await expect(home).toHaveAttribute('href', /\/home$/);
  // Not nested inside any section's submenu.
  await expect(nav(page).locator('.cds--side-nav__menu').getByTestId('side-nav-link-Home')).toHaveCount(0);
  await expect(section(page, 'General')).toHaveCount(0);
});

test('section headings carry icons; their destinations do not', async ({ page }) => {
  await expect(section(page, 'Queries').locator('svg').first()).toBeVisible();
  await section(page, 'Queries').click();
  const queriesMenu = section(page, 'Queries').locator('xpath=following-sibling::ul[1]');
  await expect(queriesMenu.locator('a').first()).toBeVisible();
  await expect(queriesMenu.locator('svg')).toHaveCount(0);
});

test('only the page the user is on is bold', async ({ page }) => {
  const homeLabel = page.getByTestId('side-nav-link-Home').locator('.cds--side-nav__link-text');
  const queriesTitle = section(page, 'Queries').locator('.cds--side-nav__submenu-title');

  // On Home: Home bold, section headings regular.
  expect(await fontWeight(homeLabel)).toBeGreaterThanOrEqual(600);
  expect(await fontWeight(queriesTitle)).toBeLessThan(600);

  // Move to a screen through the nav: it becomes current and bold, Home doesn't.
  await section(page, 'Queries').click();
  const billing = page.getByTestId('side-nav-link-billing-history');
  await billing.click();
  await expect(page).toHaveURL(/\/queries\/billing-history/);
  await expect(pageHeading(page)).toHaveText('Mark Monthly Billing History');
  // Carbon marks the current menu item with a class, not aria-current.
  await expect(billing).toHaveClass(/cds--side-nav__link--current/);
  expect(await fontWeight(billing.locator('.side-nav-item__label'))).toBeGreaterThanOrEqual(600);
  expect(await fontWeight(homeLabel)).toBeLessThan(600);
  expect(await fontWeight(queriesTitle)).toBeLessThan(600);
});

test('the section of the current page opens on load', async ({ page }) => {
  await gotoProtected(page, '/queries/billing-history');
  await expect(section(page, 'Queries')).toHaveAttribute('aria-expanded', 'true');
  await expect(section(page, 'Billing')).toHaveAttribute('aria-expanded', 'false');
});

test('the collapsed rail navigates Home and opens a section', async ({ page }) => {
  await page.getByRole('button', { name: 'Close menu' }).click();
  await expect(page.locator('.side-nav-drawer--open')).toHaveCount(0);

  try {
    // Home stays a working link in the rail.
    await gotoProtected(page, '/queries');
    await page.getByTestId('side-nav-link-Home').click();
    await expect(page).toHaveURL(/\/home$/);

    // A section icon opens the nav on that section instead of navigating.
    await page.getByTestId('side-nav-rail-queries').click();
    await expect(page.locator('.side-nav-drawer--open')).toBeVisible();
    await expect(section(page, 'Queries')).toHaveAttribute('aria-expanded', 'true');
    await expect(page).toHaveURL(/\/home$/);
  } finally {
    // The open/closed choice is persisted; leave it open for the other specs.
    await ensureExpanded(page);
  }
});

test('Report an issue mails the HBS help desk', async ({ page }) => {
  await expect(page.getByTestId('side-nav-link-email-support')).toHaveAttribute(
    'href',
    'mailto:FORHVAP.HBSHELP@gov.bc.ca',
  );
});

test('an unknown route lands on Home', async ({ page }) => {
  await gotoProtected(page, '/no/such/page');
  await expect(page).toHaveURL(/\/home$/);
});
