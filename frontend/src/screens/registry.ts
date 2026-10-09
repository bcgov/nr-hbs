import type { Capability } from '@/routes/access';

import type { ScreenArea, ScreenDef } from './types';

/**
 * Screen registry. Every file in ./areas exports `screens: ScreenDef[]` and
 * is picked up automatically (Vite glob import) — adding an area never
 * requires touching this file, the router or the SideNav.
 */
const modules = import.meta.glob<{ screens: ScreenDef[] }>('./areas/*.ts', { eager: true });

export const SCREENS: ScreenDef[] = Object.keys(modules)
  .sort()
  .flatMap((k) => modules[k].screens ?? []);

export const SCREENS_BY_ID = new Map<string, ScreenDef>(SCREENS.map((s) => [s.id, s]));

if (import.meta.env.DEV) {
  const seen = new Set<string>();
  for (const s of SCREENS) {
    if (seen.has(s.id)) console.error(`[screens] duplicate screen id "${s.id}"`);
    seen.add(s.id);
  }
}

export const screenPath = (s: Pick<ScreenDef, 'area' | 'id'>) => `/${s.area}/${s.id}`;

export interface AreaDef {
  id: ScreenArea;
  label: string;
  /** Legacy tab landing page. */
  legacy: string;
  /** Who sees the area in the nav (legacy tab visibility). */
  capability: Capability;
  description: string;
}

/** The legacy top tab bar, in legacy order. */
export const AREAS: AreaDef[] = [
  {
    id: 'queries',
    label: 'Queries',
    legacy: 'P400',
    capability: 'QUERIES_VIEW',
    description: 'Invoices, statements, transmissions, harvest history and timber mark information.',
  },
  {
    id: 'scale-returns',
    label: 'Scale Returns',
    legacy: 'P002',
    capability: 'SCALE_RETURNS_VIEW',
    description: 'Summary and detail scale returns, submissions, ledgers and volume estimates.',
  },
  {
    id: 'stratum-planner',
    label: 'Stratum Planner',
    legacy: 'P850',
    capability: 'SAMPLING_VIEW',
    description: 'Sampling plans, populations and strata.',
  },
  {
    id: 'rating',
    label: 'Rating',
    legacy: 'P199',
    capability: 'RATING_VIEW',
    description: 'Override rates, district default rates, waste rates and stumpage rates.',
  },
  {
    id: 'billing',
    label: 'Billing',
    legacy: 'P800',
    capability: 'BILLING_VIEW',
    description: 'Final bills, field-scaled decks, delivery and mark holder profiles.',
  },
  {
    id: 'scale-control',
    label: 'Scale Control',
    legacy: 'P900',
    capability: 'SCALE_CONTROL_VIEW',
    description: 'Late submissions, anomalies, change log, transport events and LDS registry.',
  },
  {
    id: 'profiles',
    label: 'Profiles',
    legacy: 'P300',
    capability: 'PROFILES_VIEW',
    description: 'Client, scale site, scaler and population profiles.',
  },
  {
    id: 'admin',
    label: 'Administration',
    legacy: 'P009 / P038 / cache',
    capability: 'ANY_USER',
    description: 'User data domains, processing parameters and reference-data cache.',
  },
];

export const AREAS_BY_ID = new Map(AREAS.map((a) => [a.id, a]));
