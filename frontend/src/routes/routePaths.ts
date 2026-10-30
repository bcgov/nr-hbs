import {
  Analytics,
  Calculator,
  ChartColumn,
  Document,
  DocumentAdd,
  DocumentImport,
  DocumentTasks,
  Home,
  ListChecked,
  Password,
  Location,
  Receipt,
  RecentlyViewed,
  Search,
  Settings,
  Tag,
  UserMultiple,
  UserProfile,
  WarningAlt,
} from '@carbon/icons-react';
import type { ComponentType } from 'react';

import type { FamLoginUser } from '@/context/auth/types';
import { AREAS, SCREENS, screenPath } from '@/screens/registry';
import type { ScreenArea, ScreenDef } from '@/screens/types';

import { can, type Capability } from './access';

// The nav model: root links (Home) and bold section headings, each with an
// icon, the sections holding their destinations as plain labels. The
// collapsed rail shows one icon per root link and section. HBS's sections are
// the legacy top tab bar (one per area).

/** A Carbon icon component (sized by the caller; 16px by default). */
export type NavIcon = ComponentType<{ size?: number }>;

/** One destination in the nav. */
export type MenuLeaf = {
  id: string;
  label: string;
  path: string;
  /** Shown on root links and on the area landing-page tiles, not inside nav sections. */
  icon?: NavIcon;
  capability?: Capability;
};

/** A heading with its destinations. */
export type MenuSection = {
  id: string;
  label: string;
  /**
   * Shown beside the heading, and alone in the collapsed rail, which lists the
   * sections rather than every destination — clicking one opens the nav on
   * that section.
   */
  icon: NavIcon;
  items: MenuLeaf[];
};

export const AREA_ICONS: Record<ScreenArea, NavIcon> = {
  queries: Search,
  'scale-returns': ListChecked,
  'stratum-planner': ChartColumn,
  rating: Calculator,
  billing: Receipt,
  'scale-control': Analytics,
  profiles: UserProfile,
  admin: Settings,
};

/**
 * A destination's icon, read from what the screen is about — the legacy
 * titles are consistent enough that the same word always means the same kind
 * of record, so a screen picks up a sensible icon without the registry having
 * to name one.
 */
function iconFor(screen: ScreenDef): NavIcon {
  const t = (screen.navLabel ?? screen.title).toLowerCase();
  if (screen.kind === 'form') return DocumentAdd;
  if (/anomal|violation|late submission/.test(t)) return WarningAlt;
  if (/transmission|xml|batch/.test(t)) return DocumentImport;
  if (/invoice|statement|billing|bill|summariz|ratio/.test(t)) return Receipt;
  if (/history|change log/.test(t)) return RecentlyViewed;
  if (/rate/.test(t)) return Calculator;
  if (/sampl|strat|population|species/.test(t)) return ChartColumn;
  if (/timber mark|mark holder/.test(t)) return Tag;
  if (/site|transport|lds/.test(t)) return Location;
  if (/client|scaler|user/.test(t)) return UserMultiple;
  if (/parameter|alert|setting/.test(t)) return Settings;
  if (/return|tally|deck/.test(t)) return DocumentTasks;
  return screen.kind === 'detail' ? Document : Search;
}

/**
 * Bespoke (non-registry) pages that appear in the nav, keyed by the area
 * section they belong to.
 */
const EXTRA_LEAVES: Partial<Record<ScreenArea, MenuLeaf[]>> = {
  'scale-returns': [
    {
      id: 'submit-scale-data',
      label: 'Submit File of Detail Returns',
      path: '/scale-returns/submit',
      icon: DocumentImport,
      capability: 'XML_SUBMIT',
    },
    {
      // P002 "HBS Public Key" (legacy DownloadPublicKeyServlet).
      id: 'hbs-public-key',
      label: 'Download HBS Public Key',
      path: '/scale-returns/public-key',
      icon: Password,
      capability: 'ANY_USER',
    },
  ],
  profiles: [
    {
      // P362 Submit / P363 (legacy P002 "Scaler Profile" link for self service).
      id: 'scaler-key-request',
      label: 'Request Authentication Key',
      path: '/profiles/scaler-key-request',
      icon: Password,
      capability: 'SCALER_PROFILE_EDIT',
    },
  ],
  rating: [
    {
      id: 'stumpage-rate',
      label: 'Search for Stumpage Rate',
      path: '/rating/stumpage-rate',
      icon: Calculator,
      capability: 'RATING_VIEW',
    },
  ],
};

/** Links at the top level of the nav, above the sections: the work-queue dashboard. */
const ROOT_LINKS: MenuLeaf[] = [
  { id: 'Home', label: 'Home', path: '/home', icon: Home, capability: 'ANY_USER' },
];

const visibleTo = (user: FamLoginUser | null | undefined) => (l: MenuLeaf) =>
  !l.capability || can(user, l.capability);

/** The root links visible to the user. */
export function getRootLinks(user: FamLoginUser | null | undefined): MenuLeaf[] {
  return ROOT_LINKS.filter(visibleTo(user));
}

/**
 * The nav sections visible to the user: one per legacy tab (populated from the
 * screen registry). An entry is shown only when the user may open its screen —
 * the same `can()` check the screen applies, so the menu never offers a page
 * that answers Forbidden. A section left empty is dropped.
 */
export function getMenuSections(user: FamLoginUser | null | undefined): MenuSection[] {
  const visible = visibleTo(user);
  const sections: MenuSection[] = [];
  for (const area of AREAS) {
    if (!can(user, area.capability)) continue;
    sections.push({
      id: area.id,
      label: area.label,
      icon: AREA_ICONS[area.id],
      items: [
        ...SCREENS.filter(
          (s) => s.area === area.id && (s.nav ?? s.kind === 'search') && can(user, s.capability),
        ).map((s) => ({ id: s.id, label: s.navLabel ?? s.title, path: screenPath(s), icon: iconFor(s) })),
        ...(EXTRA_LEAVES[area.id] ?? []).filter(visible),
      ],
    });
  }
  return sections.filter((s) => s.items.length > 0);
}

/**
 * The id of the section the current page belongs to: the one listing `path`,
 * else — for a record or form opened from a list — the area its URL sits under.
 */
export function sectionIdForPath(
  user: FamLoginUser | null | undefined,
  path: string,
): string | undefined {
  const sections = getMenuSections(user);
  const exact = sections.find((s) => s.items.some((i) => i.path === path));
  if (exact) return exact.id;
  const area = path.split('/')[1];
  return sections.find((s) => s.id === area)?.id;
}
