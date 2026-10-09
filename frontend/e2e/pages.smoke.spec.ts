import { test } from './fixtures';
import { gotoPage, PAGES } from './helpers/nav';
import { runScreen } from './helpers/screens';

// Every top-level HBS page renders its <h1> for an authenticated ministry
// user, and a handful of registry screens round-trip a real query against
// the target environment's shared Oracle (read-only).
for (const [id, def] of Object.entries(PAGES)) {
  test(`page renders: ${id}`, async ({ page }) => {
    await gotoPage(page, def);
  });
}

test('work queue deep link runs a search (summary returns in error)', async ({ page }) => {
  await runScreen(page, 'scale-returns', 'summary-returns', { returnType: 'P', status: 'ERR', generated: 'N' });
});

test('XML transmissions default search runs', async ({ page }) => {
  await runScreen(page, 'scale-returns', 'xml-transmissions');
});
