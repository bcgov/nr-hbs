import { ChartColumn, Home, Receipt, RecentlyViewed, Tag, UserFollow } from '@carbon/icons-react';

import type { Menu, MenuLeaf, MenuSection } from '@/routes/routePaths';

import { publicScreenPath } from './publicScreens';

/**
 * The public area's side nav: the legacy public menu (mainmenu_public.jsp) as
 * the public home page groups it, rendered by the same LayoutSideNav as the
 * signed-in app. Every destination is under /public. A screen entry is kept
 * only when that screen is actually public (publicScreenPath), so the menu
 * never offers a page the public API would refuse.
 */

/** A public registry screen, or nothing if it isn't public. */
const screen = (screenId: string, label: string): MenuLeaf[] => {
  const path = publicScreenPath(screenId);
  return path ? [{ id: `public-${screenId}`, label, path }] : [];
};

const SECTIONS: MenuSection[] = [
  {
    id: 'public-harvest-reports',
    label: 'Harvest Reports',
    icon: RecentlyViewed,
    items: [
      // The legacy menu's labels; longer ones are cut off at the nav's width.
      ...screen('billing-history', 'By Date of Invoice'),
      ...screen('scaling-history', 'By Date of Scale'),
    ],
  },
  {
    // The legacy menu listed invoice and statement copies separately, but both
    // open the same two screens; one section avoids two highlighted entries.
    id: 'public-copies',
    label: 'Invoices & Statements',
    icon: Receipt,
    items: [
      ...screen('issued-document', 'Single Document'),
      ...screen('issued-documents', 'Multiple Documents'),
    ],
  },
  {
    id: 'public-timber-marks',
    label: 'Timber Marks',
    icon: Tag,
    items: [...screen('timber-mark-search', 'Timber Mark Information')],
  },
  {
    id: 'public-stratum-advisor',
    label: 'Stratum Advisor',
    icon: ChartColumn,
    items: [
      ...screen('sampling-plans', 'Sampling Plans'),
      ...screen('sampling-populations', 'Populations'),
      ...screen('sampling-strata', 'Strata'),
    ],
  },
  {
    id: 'public-services',
    label: 'Public User Services',
    icon: UserFollow,
    items: [
      { id: 'public-access', label: 'How to get access', path: '/public/access' },
      { id: 'public-invoice-information', label: 'Invoice Information', path: '/public/invoice-information' },
    ],
  },
];

export const PUBLIC_MENU: Menu = {
  rootLinks: [{ id: 'public-home', label: 'Home', path: '/public', icon: Home }],
  sections: SECTIONS.filter((s) => s.items.length > 0),
};
