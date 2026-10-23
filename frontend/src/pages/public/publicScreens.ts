import { screens as queryScreens } from '@/screens/areas/queries';
import { screens as samplingScreens } from '@/screens/areas/stratum-planner';
import type { DetailScreenDef, FieldDef, ScreenDef, SearchScreenDef } from '@/screens/types';

/**
 * The public (anonymous) read-only area — what the legacy app showed a visitor
 * who had not logged in (`include/mainmenu_public.jsp`, legacy `Public_User`).
 * See docs/public-access.md.
 *
 * Each public screen is DERIVED from the logged-in screen of the same id, so
 * the labels, columns and legacy P-numbers stay identical; the derivation only
 * takes away what the public may not use: reports, commands, edit links,
 * client pickers (they need a login), criteria/tables whose query is not
 * public, and links to screens that are not public.
 *
 * PUBLIC_QUERIES mirrors the backend whitelist
 * (backend/.../publicaccess/PublicQueries.java) — keep the two in sync. The
 * backend is the gate; this list only keeps the SPA from offering things that
 * would answer 404.
 */
export const PUBLIC_QUERIES = new Set<string>([
  'codes.orgUnits',
  'codes.queries.fileTypes',
  'codes.queries.species',
  'codes.queries.products',
  'codes.queries.grades',
  'codes.sampling.planStatuses',
  'harvestHistory.billing',
  'harvestHistory.scaling',
  'issuedDocuments.search',
  'timberMarks.search',
  'timberMarks.detail',
  'timberMarks.appraisals',
  'timberMarks.species',
  'timberMarks.stumpageRates',
  'timberMarks.spgRates',
  'sampling.plans.search',
  'sampling.plans.detail',
  'sampling.stratumPlans.list',
  'sampling.stratumPlans.detail',
  'sampling.stratumComposition.list',
  'sampling.planComparison.list',
  'sampling.populations.search',
  'sampling.strata.search',
  'sampling.populationStats.search',
  'sampling.stratumStats.search',
]);

/** Criteria the public may not use, per screen (backend PublicQueries drops them too). */
const DROPPED_CRITERIA: Record<string, string[]> = {
  // Transmission ID came only from the logged-in P453 links.
  'issued-documents': ['transmissionId'],
  // "In Associated Districts" is the user's HBS data domain — there is no user.
  'sampling-plans': ['districtScope'],
};

const FOI_NOTE =
  'Personal information of individual (non-company) clients is not releasable on the public site and is shown as "Not Releasable".';

/** Notes shown on the public version of a screen (legacy public-site rules). */
const PUBLIC_NOTES: Record<string, string> = {
  'billing-history': FOI_NOTE,
  'scaling-history': FOI_NOTE,
  'issued-document': `${FOI_NOTE} Statements older than seven years cannot be retrieved.`,
  'issued-documents': `Please enter at least one date range. Statements older than seven years cannot be retrieved. ${FOI_NOTE}`,
  'timber-mark-search': FOI_NOTE,
  'sampling-plans': 'Only Active sampling plans are shown on the public site.',
};

/** Screens of the logged-in app offered publicly, in legacy public-menu order. */
const PUBLIC_SCREEN_IDS = [
  'billing-history',
  'scaling-history',
  'issued-document',
  'issued-documents',
  'timber-mark-search',
  'timber-mark',
  'sampling-plans',
  'sampling-plan',
  'stratum-plan',
  'sampling-populations',
  'sampling-strata',
  'sampling-population-stats',
  'sampling-stratum-stats',
];

/** A client picker needs a login (forest-client API); the public types the number. */
const publicField = (f: FieldDef): FieldDef =>
  f.type === 'client' ? { ...f, type: 'text', maxLength: f.maxLength ?? 8, helperText: f.helperText ?? '8 digits' } : f;

const fieldAllowed = (screenId: string) => (f: FieldDef) =>
  !(DROPPED_CRITERIA[screenId] ?? []).includes(f.name) && (!f.codeList || PUBLIC_QUERIES.has(f.codeList));

function toPublic(s: ScreenDef): ScreenDef | null {
  if (s.kind === 'form' || !PUBLIC_QUERIES.has(s.query)) return null;
  const notes = PUBLIC_NOTES[s.id] ?? s.notes;
  if (s.kind === 'search') {
    const criteria = s.criteria.filter(fieldAllowed(s.id)).map(publicField);
    const names = new Set(criteria.map((f) => f.name));
    const out: SearchScreenDef = {
      ...s,
      notes,
      criteria,
      requireOneOf: s.requireOneOf?.filter((n) => names.has(n)),
      reports: undefined,
      rowActions: undefined,
      createLink: undefined,
      // Only the issued-documents "Download XML" / "Download PDF" (P451 "Generate XML / PDF")
      // have public endpoints.
      xmlExport: s.query === 'issuedDocuments.search' && s.xmlExport ? s.xmlExport : undefined,
      pdfExport: s.query === 'issuedDocuments.search' && s.pdfExport ? s.pdfExport : undefined,
    };
    return out;
  }
  const out: DetailScreenDef = {
    ...s,
    notes,
    sections: s.sections.filter((sec) => !sec.table || PUBLIC_QUERIES.has(sec.table.query)),
    actions: undefined,
    editLink: undefined,
    reports: undefined,
  };
  return out;
}

const SOURCE = new Map<string, ScreenDef>([...queryScreens, ...samplingScreens].map((s) => [s.id, s]));

export const PUBLIC_SCREENS: ScreenDef[] = PUBLIC_SCREEN_IDS.map((id) => SOURCE.get(id))
  .filter((s): s is ScreenDef => !!s)
  .map(toPublic)
  .filter((s): s is ScreenDef => !!s);

export const PUBLIC_SCREENS_BY_ID = new Map(PUBLIC_SCREENS.map((s) => [s.id, s]));

export const publicScreenPath = (screenId: string): string | null =>
  PUBLIC_SCREENS_BY_ID.has(screenId) ? `/public/${screenId}` : null;
