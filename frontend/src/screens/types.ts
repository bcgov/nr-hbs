import type { Capability } from '@/routes/access';

/**
 * Declarative screen definitions.
 *
 * HBS has ~200 legacy Struts screens (P-numbers). Rather than ~200 bespoke
 * page components, each legacy screen is declared once here and rendered by
 * the shared Carbon templates in components/screens (SearchScreen,
 * DetailScreen, FormScreen) — the same tile + DataTable + pagination layout as
 * nr-fsp-new's SearchPage / InboxPage. Bespoke pages are still used where a
 * screen genuinely needs custom interaction (file submission, reports, home).
 *
 * Every definition points at a backend registry id
 * (backend/src/main/java/.../catalog/*Catalog.java), so the SQL / proc behind
 * a screen is found by grepping that id.
 */

/** Functional areas = the legacy top tab bar; also the SideNav groups. */
export type ScreenArea =
  | 'queries'
  | 'scale-returns'
  | 'stratum-planner'
  | 'rating'
  | 'billing'
  | 'scale-control'
  | 'profiles'
  | 'admin';

export type FieldType =
  | 'text'
  | 'number'
  | 'date'
  | 'select'
  | 'yesno'
  | 'radio'
  | 'textarea'
  | 'client'
  | 'checkbox';

export interface Option {
  value: string;
  label: string;
}

export interface FieldDef {
  /** Request parameter / body key — must match the backend filter or arg name. */
  name: string;
  label: string;
  type?: FieldType;
  required?: boolean;
  /** Static options (select/radio). */
  options?: Option[];
  /**
   * Code list loaded from a registry query that returns {code, description}
   * rows (e.g. 'codes.orgUnits'). Rendered as a select with an "All" entry.
   */
  codeList?: string;
  maxLength?: number;
  placeholder?: string;
  helperText?: string;
  /** Upper-case as the user types (timber marks, licences, codes). */
  upper?: boolean;
  /** Initial value. */
  defaultValue?: string;
  /** Read-only on edit forms (primary keys). */
  readOnlyOnEdit?: boolean;
  /** Grid span 1–4 (default 1 of a 4-column grid). */
  span?: 1 | 2 | 3 | 4;
  /** Group heading — consecutive fields with the same group render under it. */
  group?: string;
}

/**
 * 'download' renders the value (an API path such as
 * /api/v1/hbs/statements/PSI/Q1234567.pdf) as a button that fetches it with
 * the user's bearer token and saves the file.
 */
export type ColumnFormat =
  | 'text' | 'date' | 'datetime' | 'number' | 'money' | 'volume' | 'yesno' | 'status' | 'download';

export interface ColumnDef {
  /** camelCase key in the row JSON (column alias in the backend SQL). */
  key: string;
  header: string;
  format?: ColumnFormat;
  /** Backend sort key (must be whitelisted in the QueryDefinition). */
  sortable?: boolean;
}

/** Navigate from a row/record to another screen, mapping fields to URL params. */
export interface LinkDef {
  /** Target screen id. */
  screen: string;
  /** URL param name → row key. */
  params: Record<string, string>;
  label?: string;
}

/** Run a legacy Jasper report, mapping criteria/row values to report prompts. */
export interface ReportLinkDef {
  reportId: string;
  label: string;
  /** Report prompt → criteria/row key (or a literal prefixed with '='). */
  params: Record<string, string>;
  format?: 'PDF' | 'CSV';
}

/** A write action (registry command) available on a screen. */
export interface ActionDef {
  id: string;
  label: string;
  command: string;
  capability: Capability;
  /** Body keys taken from the current record → command arg names. */
  params?: Record<string, string>;
  /** Fields prompted in a modal before running (e.g. a comment). */
  fields?: FieldDef[];
  confirm?: string;
  danger?: boolean;
  /** Screen to go to afterwards (defaults to staying + refreshing). */
  then?: LinkDef;
}

interface BaseScreen {
  /** Stable id; also the URL slug under the area. */
  id: string;
  /** Legacy screen id(s), shown in the page footer for support staff. */
  legacy: string;
  title: string;
  area: ScreenArea;
  /** Short description shown under the title. */
  description?: string;
  capability: Capability;
  /** Show in the area's SideNav submenu (default true for search/report screens). */
  nav?: boolean;
  /** Label in the SideNav (defaults to title). */
  navLabel?: string;
  /** Legacy behaviour notes surfaced as a dismissible info banner. */
  notes?: string;
}

export interface SearchScreenDef extends BaseScreen {
  kind: 'search';
  query: string;
  criteria: FieldDef[];
  columns: ColumnDef[];
  rowLink?: LinkDef;
  /** At least one of these criteria must be filled (legacy "enter at least one…"). */
  requireOneOf?: string[];
  reports?: ReportLinkDef[];
  /** Header actions, e.g. "Add Scale Site Profile". */
  createLink?: LinkDef & { capability: Capability };
  /** Run the search immediately on open (lists with no mandatory criteria). */
  autoSearch?: boolean;
  /** Row actions (commands). */
  rowActions?: ActionDef[];
}

export interface DetailSection {
  title: string;
  fields?: { key: string; label: string; format?: ColumnFormat }[];
  /** Child table from a list query keyed by the record. */
  table?: {
    query: string;
    /** Query param → record key. */
    params: Record<string, string>;
    columns: ColumnDef[];
    rowLink?: LinkDef;
  };
}

export interface DetailScreenDef extends BaseScreen {
  kind: 'detail';
  /** Single-row query; URL params are passed straight through. */
  query: string;
  /** URL params required to load the record. */
  keys: string[];
  sections: DetailSection[];
  actions?: ActionDef[];
  editLink?: LinkDef & { capability: Capability };
  reports?: ReportLinkDef[];
}

export interface FormScreenDef extends BaseScreen {
  kind: 'form';
  /** Command run on save. */
  command: string;
  fields: FieldDef[];
  /** For edit forms: single-row query that pre-fills the form from URL params. */
  loadQuery?: string;
  keys?: string[];
  /** Where to go after a successful save. */
  then?: LinkDef;
  submitLabel?: string;
}

export type ScreenDef = SearchScreenDef | DetailScreenDef | FormScreenDef;
