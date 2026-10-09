import { expect, type Page } from '@playwright/test';

import { gotoProtected } from '../utils';

/**
 * HBS pages the smoke suite visits, keyed by a short id. `heading` is the
 * page's <h1> (the "page rendered" signal). Registry screens live at
 * /<area>/<screenId>; area landing pages at /<area>.
 *
 * The E2E IDIR account must hold at least HBS_MOF_USER for these to be
 * reachable (see docs/roles-and-security.md).
 */
export interface PageDef {
  path: string;
  heading: string | RegExp;
  navId?: string;
}

export const PAGES = {
  home: { path: '/home', heading: 'Harvest Billing System', navId: 'Home' },
  reports: { path: '/reports', heading: 'Reports', navId: 'Reports' },
  queries: { path: '/queries', heading: 'Queries' },
  scaleReturns: { path: '/scale-returns', heading: 'Scale Returns' },
  stratumPlanner: { path: '/stratum-planner', heading: 'Stratum Planner' },
  rating: { path: '/rating', heading: 'Rating' },
  billing: { path: '/billing', heading: 'Billing' },
  scaleControl: { path: '/scale-control', heading: 'Scale Control' },
  profiles: { path: '/profiles', heading: 'Profiles' },
  timberMark: { path: '/queries/timber-mark-search', heading: /Timber Mark/ },
  summaryReturns: { path: '/scale-returns/summary-returns', heading: /Summary Scale Returns?/ },
  detailWorkbench: { path: '/scale-returns/detail-workbench', heading: /./ },
  transmissions: { path: '/scale-returns/xml-transmissions', heading: /Transmissions/ },
  anomalies: { path: '/scale-control/scale-anomalies', heading: /Anomal/ },
} satisfies Record<string, PageDef>;

/** Navigate by URL and assert the page heading renders. */
export async function gotoPage(page: Page, def: PageDef): Promise<void> {
  await gotoProtected(page, def.path);
  await expect(page.getByRole('heading', { name: def.heading, level: 1 })).toBeVisible({
    timeout: 30_000,
  });
}
