import { readFileSync } from 'node:fs';

import { expect, test, type Page } from './fixtures';
import {
  columnHeaders,
  columnIndex,
  expectHealthyPage,
  localDate,
  pageHeading,
  resultRows,
  runScreen,
} from './helpers/screens';

/**
 * Searches that round-trip against the environment's Oracle, then follow the
 * results into records and downloads. Read-only. Data differs between
 * environments, so a step that needs a row skips (with a reason) when the
 * search comes back empty rather than failing.
 */

/** The text of a result cell, by row and column header. */
const cell = async (page: Page, row: number, header: string): Promise<string> => {
  const col = await columnIndex(page, header);
  expect(col, `column "${header}"`).toBeGreaterThanOrEqual(0);
  return (await resultRows(page).nth(row).locator('td').nth(col).innerText()).trim();
};

test.describe.serial('issued documents → download → timber mark', () => {
  let timberMark = '';

  test('issued documents of the last 30 days list PDF and XML under one Actions column', async ({ page }) => {
    await runScreen(page, 'queries', 'issued-documents', {
      issueDateFrom: localDate(30),
      issueDateTo: localDate(0),
    });
    await expectHealthyPage(page, 'issued documents');

    const rows = await resultRows(page).count();
    test.skip(rows === 0, 'No documents issued in the last 30 days in this environment');

    const headers = await columnHeaders(page);
    expect(headers).toContain('Actions');
    expect(headers).not.toContain('PDF');
    expect(headers).not.toContain('XML');

    const actions = resultRows(page).first().locator('td').nth(await columnIndex(page, 'Actions'));
    await expect(actions.getByRole('button', { name: 'PDF' })).toBeVisible();
    await expect(actions.getByRole('button', { name: 'XML' })).toBeVisible();

    timberMark = await cell(page, 0, 'Timber Mark');
  });

  test('a statement PDF downloads', async ({ page }) => {
    test.setTimeout(60_000);
    await runScreen(page, 'queries', 'issued-documents', {
      issueDateFrom: localDate(30),
      issueDateTo: localDate(0),
    });
    test.skip((await resultRows(page).count()) === 0, 'No documents issued in the last 30 days');

    const actions = resultRows(page).first().locator('td').nth(await columnIndex(page, 'Actions'));
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 45_000 }),
      actions.getByRole('button', { name: 'PDF' }).click(),
    ]);
    expect(download.suggestedFilename()).toMatch(/\.pdf$/i);
    expect(readFileSync(await download.path()).subarray(0, 4).toString()).toBe('%PDF');
  });

  test('a timber mark opens its record, without the summary strip', async ({ page }) => {
    test.skip(!timberMark, 'No timber mark from the issued documents step');

    await runScreen(page, 'queries', 'timber-mark-search', { timberMark });
    await expect(resultRows(page).first()).toBeVisible();
    await resultRows(page).first().click();

    await expect(page).toHaveURL(/\/queries\/timber-mark\?/);
    await expect(pageHeading(page)).toHaveText('Timber Mark Query');
    await expect(page.getByText('Timber Mark Information')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByLabel('Timber Mark Query summary')).toHaveCount(0);
    await expectHealthyPage(page, 'timber mark record');
  });
});

test('an XML transmission opens its record', async ({ page }) => {
  await runScreen(page, 'scale-returns', 'xml-transmissions');
  await expectHealthyPage(page, 'XML transmissions');
  test.skip((await resultRows(page).count()) === 0, 'No transmissions match the default search');

  await resultRows(page).first().click();
  await expect(page).toHaveURL(/xml-transmission-detail/);
  await expect(pageHeading(page)).toBeVisible();
  await expectHealthyPage(page, 'XML transmission record');
});

test('scale site summary searches the last 30 days', async ({ page }) => {
  await runScreen(page, 'queries', 'scale-site-summary', {
    returnType: 'P',
    groupBy: 'DISTRICTSCALESITE',
    scaleDateFrom: localDate(30),
    scaleDateTo: localDate(0),
  });
  await expectHealthyPage(page, 'scale site summary');
});

test('summary returns in error open their record', async ({ page }) => {
  await runScreen(page, 'scale-returns', 'summary-returns', { returnType: 'W', status: 'ERR' });
  await expectHealthyPage(page, 'summary returns');
  test.skip((await resultRows(page).count()) === 0, 'No weight-scale summary returns in error');

  await resultRows(page).first().click();
  await expect(pageHeading(page)).toBeVisible();
  await expectHealthyPage(page, 'summary return record');
});
