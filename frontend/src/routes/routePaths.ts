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
  Location,
  Receipt,
  RecentlyViewed,
  Report,
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

// The nav model is nr-fta's: bold section headings, each holding destinations
// that carry their own icon; the collapsed rail shows one icon per section.
// HBS's sections are the legacy top tab bar (one per area).

/** A Carbon icon component (sized by the caller; 16px by default). */
export type NavIcon = ComponentType<{ size?: number }>;

/** One destination in the nav. */
export type MenuLeaf = {
  id: string;
  label: string;
  path: string;
  icon?: NavIcon;
  capability?: Capability;
};

/** A heading with its destinations. */
export type MenuSection = {
  id: string;
  label: string;
  /**
   * Shown in the collapsed rail, which lists the sections rather than every
   * destination — clicking one opens the nav on that section.
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
  ],
};

/** The section for HBS's own pages: the work-queue dashboard and the report runner. */
const GENERAL: MenuSection = {
  id: 'general',
  label: 'General',
  icon: Home,
  items: [
    { id: 'Home', label: 'Home', path: '/home', icon: Home, capability: 'ANY_USER' },
    { id: 'Reports', label: 'Reports', path: '/reports', icon: Report, capability: 'ANY_USER' },
  ],
};

/**
 * The nav sections visible to the user: General, then one per legacy tab
 * (populated from the screen registry). An entry is shown only when the user
 * may open its screen — the same `can()` check the screen applies, so the menu
 * never offers a page that answers Forbidden. A section left empty is dropped.
 */
export function getMenuSections(user: FamLoginUser | null | undefined): MenuSection[] {
  const visible = (l: MenuLeaf) => !l.capability || can(user, l.capability);
  const sections: MenuSection[] = [{ ...GENERAL, items: GENERAL.items.filter(visible) }];
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
