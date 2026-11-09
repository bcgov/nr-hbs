import path from 'node:path';

import { expect, test, type Page } from './fixtures';
import { pageHeading, resultRows, resultsOrEmpty } from './helpers/screens';
import { gotoProtected } from './utils';

/**
 * Screenshots for the user manual (docs/manual/images/). Not part of the test
 * suite: skipped unless MANUAL_SCREENSHOTS=1. Read-only — it opens screens and
 * runs searches, never saves.
 *
 *   E2E_BASE_URL=http://localhost:3000 MANUAL_SCREENSHOTS=1 \
 *     npx playwright test e2e/manual.screenshots.spec.ts --project=chromium --no-deps
 *
 * The signed-in user's name, user id and email are replaced with a sample
 * identity. Result rows show the environment's data: review the images before
 * publishing the manual.
 *
 * Then build the PDF: `npm run manual:pdf`.
 */

test.skip(!process.env.MANUAL_SCREENSHOTS, 'Set MANUAL_SCREENSHOTS=1 to capture manual screenshots');
test.use({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1.5 });

const OUT = path.join(import.meta.dirname, '..', '..', 'docs', 'manual', 'images');

const localDate = (daysAgo: number): string => {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** Swap the signed-in user's personal details for a sample identity. */
const anonymize = (page: Page) =>
  page.evaluate(() => {
    const set = (el: Element | null | undefined, text: string) => {
      if (el) el.textContent = text;
    };
    document.querySelectorAll('[data-testid="avatar-initials"]').forEach((el) => set(el, 'JS'));
    const data = document.querySelector('.user-data');
    if (data) {
      set(data.querySelector('.user-name'), 'Jordan Sample');
      data.querySelectorAll('p').forEach((p) => {
        const t = p.textContent ?? '';
        if (/^(IDIR|BCeID|Business BCeID)[^:]*:/i.test(t)) set(p, `${t.split(':')[0]}: JSAMPLE`);
        else if (t.startsWith('Email:')) set(p, 'Email: jordan.sample@gov.bc.ca');
      });
    }
    document.querySelectorAll('main *').forEach((el) => {
      const t = el.childElementCount === 0 ? (el.textContent ?? '') : '';
      if (/^Welcome, /.test(t)) set(el, 'Welcome, Jordan.');
      else if (/, your account doesn't have permission/.test(t)) set(el, t.replace(/^.*?, your account/, 'Jordan Sample, your account'));
    });
  });

/**
 * Screenshot at most `maxHeight` CSS pixels tall: from the top of the page, or
 * (`atResults`) one viewport framed on the search results panel.
 */
const snap = async (page: Page, name: string, maxHeight = 900, atResults = false) => {
  // Code lists in the criteria load after the page; wait for their "Loading…".
  await expect(page.getByText(/^Loading/)).toHaveCount(0, { timeout: 10_000 }).catch(() => undefined);
  if (atResults) {
    await page.locator('.fsp-search__table').first().evaluate((el) => {
      window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 260);
    });
  } else {
    await page.evaluate(() => window.scrollTo(0, 0));
  }
  // Let toasts, skeletons and transitions settle.
  await page.waitForTimeout(700);
  await anonymize(page);
  if (atResults) {
    await page.screenshot({ path: path.join(OUT, `${name}.png`), animations: 'disabled' });
    return;
  }
  const height = await page.evaluate(() => document.documentElement.scrollHeight);
  await page.screenshot({
    path: path.join(OUT, `${name}.png`),
    fullPage: true,
    clip: { x: 0, y: 0, width: 1440, height: Math.min(height, maxHeight) },
    animations: 'disabled',
  });
};

const ensureNavOpen = async (page: Page) => {
  const open = page.getByRole('button', { name: 'Open menu' });
  if (await open.isVisible().catch(() => false)) await open.click();
};

/** Open a screen; when `search`, run it (deep-link criteria or the Search button) and wait for results. */
/** With `search`, the screenshot frames the results; without, the top of the screen (the criteria). */
const shot = async (page: Page, name: string, url: string, opts: { search?: boolean; maxHeight?: number } = {}) => {
  await gotoProtected(page, url);
  await expect(pageHeading(page)).toBeVisible({ timeout: 15_000 });
  await ensureNavOpen(page);
  // The signed-in account must hold a role that opens this screen; if not,
  // leave this image out (never save the "no access" page with its name).
  if (/don't have access/i.test(await pageHeading(page).innerText())) {
    test.info().annotations.push({ type: 'not captured', description: `${name}: no role for ${url}` });
    return false;
  }
  if (opts.search) {
    if (!url.includes('?')) await page.getByRole('button', { name: 'Search', exact: true }).click();
    await expect(resultsOrEmpty(page)).toBeVisible({ timeout: 25_000 });
  }
  await snap(page, name, opts.maxHeight ?? 900, !!opts.search);
  return true;
};

test('getting started', async ({ page }) => {
  await gotoProtected(page, '/home');
  await ensureNavOpen(page);
  await expect(page.getByText('Quick links')).toBeVisible({ timeout: 15_000 });
  await snap(page, '01-home');

  await page.getByRole('button', { name: 'User settings' }).click();
  await expect(page.getByTestId('header-panel')).toBeVisible();
  await snap(page, '01-profile');
  await page.keyboard.press('Escape');

  // The page anyone sees when they open a screen their roles don't allow.
  await gotoProtected(page, '/admin/processing-parameters');
  await expect(page.getByText(/don't have access/i).first()).toBeVisible({ timeout: 15_000 });
  await snap(page, '01-forbidden');

  await shot(page, '01-search', '/scale-returns/summary-returns?returnType=P&status=ERR&generated=N', { search: true });
});

test('queries', async ({ page }) => {
  await shot(page, '02-issued-documents', `/queries/issued-documents?issueDateFrom=${localDate(30)}&issueDateTo=${localDate(0)}`, { search: true });
  await shot(page, '02-billing-history', '/queries/billing-history');
});

test('scale returns', async ({ page }) => {
  await shot(page, '03-submit-file', '/scale-returns/submit');
  await shot(page, '03-transmissions', '/scale-returns/xml-transmissions', { search: true });
  if (
    (await shot(page, '03-summary-returns', '/scale-returns/summary-returns?returnType=W&status=ERR', { search: true })) &&
    (await resultRows(page).count()) > 0
  ) {
    await resultRows(page).first().click();
    await expect(pageHeading(page)).toHaveText('Summary Scale Return', { timeout: 15_000 });
    await expect(page.getByTestId('loading')).toHaveCount(0, { timeout: 15_000 });
    await snap(page, '03-summary-return', 1500);
  }
});

test('stratum planner', async ({ page }) => {
  await shot(page, '04-sampling-plans', '/stratum-planner/sampling-plans');
});

test('rating', async ({ page }) => {
  await shot(page, '05-override-rules', '/rating/override-rules');
});

test('billing', async ({ page }) => {
  await shot(page, '06-final-bill-status', '/billing/final-bill-status');
});

test('scale control', async ({ page }) => {
  await shot(page, '07-scale-anomalies', '/scale-control/scale-anomalies');
});

test('profiles', async ({ page }) => {
  await shot(page, '08-mark-holder-profiles', '/profiles/mark-holder-profiles');
});

test('administration', async ({ page }) => {
  await shot(page, '09-user-data-domains', '/admin/user-data-domains');
});

test('public area', async ({ page }) => {
  await page.goto('/public');
  await expect(page.getByTestId('hbs-public__header')).toBeVisible({ timeout: 15_000 });
  await snap(page, '10-public-home');
});
