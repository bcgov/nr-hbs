import type { Capability } from '@/routes/access';
import type { ROLE_TYPE } from '@/context/auth/types';

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
  /** Offer this option only while another field holds one of these values. */
  showWhen?: { field: string; values: string[] };
  /**
   * Don't offer this option to a user holding any of `roles` unless they also
   * hold one of `unlessRoles` (legacy option lists that varied by role). The
   * backend refuses the value for those users too.
   */
  hiddenFor?: { roles: ROLE_TYPE[]; unlessRoles?: ROLE_TYPE[] };
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
  /**
   * Role-based initial value (legacy per-role default criteria, e.g. P032Action
   * setDefaultSearchCriteria): the first entry whose `roles` the user holds,
   * and none of whose `unlessRoles`, replaces `defaultValue` (search screens).
   */
  roleDefaults?: { roles: ROLE_TYPE[]; unlessRoles?: ROLE_TYPE[]; value: string }[];
  /** Read-only on edit forms (primary keys). */
  readOnlyOnEdit?: boolean;
  /** Grid span 1–4 (default 1 of a 4-column grid). */
  span?: 1 | 2 | 3 | 4;
  /**
   * 'center' centres the field vertically in its row — for a checkbox beside
   * inputs that carry helper text, where the default bottom alignment would
   * line it up with the helper text instead of the inputs.
   */
  align?: 'center';
  /** Group heading — consecutive fields with the same group render under it. */
  group?: string;
  /** Words after the label and its required marker, e.g. "To * (up to 12 months)". */
  labelSuffix?: string;
  /** Editable only while another field holds one of these values; otherwise disabled and cleared. */
  enabledWhen?: { field: string; values: string[] };
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
  /**
   * A 'download' column holding several files (e.g. "Actions": PDF and XML):
   * one button per entry, reading the row's `key`. `key` above is then only
   * the column's id.
   */
  downloads?: { key: string; label: string }[];
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

export interface ReportCondition {
  field: string;
  values: string[];
}

/** Run a legacy Jasper report, mapping criteria/row values to report prompts. */
export interface ReportLinkDef {
  reportId: string;
  label: string;
  /** Report prompt → criteria/row key (or a literal prefixed with '='). */
  params: Record<string, string>;
  format?: 'PDF' | 'CSV';
  /**
   * Download name without extension, `{key}` filled from the record / criteria,
   * e.g. 'Weigh Slip {ddn} v{version}'. Empty values drop out; without it the
   * server's name (the report id) is used.
   */
  fileName?: string;
  /**
   * Show only when this criteria/record field has one of these values (legacy
   * per-type result pages); with several conditions, all must hold.
   */
  showWhen?: ReportCondition | ReportCondition[];
}

/** Report-only prompts asked in a modal, then one of several reports run with them. */
export interface ReportDialogDef {
  /** The button that opens the modal. */
  label: string;
  heading: string;
  /** Kept for the session, so closing and re-opening the modal keeps them. */
  fields: FieldDef[];
  /** One modal button each; params read the criteria and these fields. */
  reports: ReportLinkDef[];
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
  /**
   * Record values the action is offered for (all must match), e.g. the legacy
   * pages showed Activate only on a Pending rule. The backend enforces the same
   * rule; this only hides a button that would be refused.
   */
  showWhen?: ReportCondition | ReportCondition[];
  /**
   * Read-only "what will happen" call shown in the confirmation modal (legacy
   * P054-style confirmation screens): runs `command` with the mapped params plus
   * `action` = this action's id and renders the returned versions and their
   * transactions.
   */
  preview?: { command: string; params: Record<string, string> };
}

/**
 * A command run on the rows the user ticks — the legacy "Hold Selected",
 * "Cancel … in Selected Strata" buttons. The selected rows are sent as
 * `rows: [{ …rowParams }]` plus `params`; the command answers
 * `{ results: [{ label, status: PROCESSED | NOTPROCESSED | ERROR, message }] }`,
 * reported per row.
 */
export interface BulkActionDef {
  id: string;
  label: string;
  command: string;
  capability: Capability;
  /** Body key → row key, for each selected row. */
  rowParams: Record<string, string>;
  /** Further body keys from the screen's record (detail) or last search criteria (search); '=' = literal. */
  params?: Record<string, string>;
  /**
   * Rows the action applies to (all conditions must match) — mirrors the legacy
   * check-box / status rule; other rows can't be ticked for it and the backend
   * re-checks each row.
   */
  showWhen?: ReportCondition | ReportCondition[];
  confirm?: string;
  danger?: boolean;
}

/**
 * Per-record action availability served by the backend (legacy button-visibility
 * tags): `command` is run read-only with the mapped params and returns
 * `{ [key]: string[] }`; only actions (and the edit link / extra links) whose id
 * is in that list are shown.
 */
export interface ActionsAvailableDef {
  command: string;
  params: Record<string, string>;
  key: string;
  /** Id in the list that enables the edit link. */
  editAction?: string;
}

interface BaseScreen {
  /** Stable id; also the URL slug under the area. */
  id: string;
  /** Legacy screen id(s), for traceability (docs/areas/); not shown to users. */
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
  /** One button opening a modal of report-only prompts, with a button per report (P403 average rates). */
  reportDialog?: ReportDialogDef;
  /**
   * "Download XML" of the results: `true` = the query's legacy-schema XML (P431/P441 "Send XML");
   * a string = another API path under /api/v1/hbs taking the same criteria (P451 "Generate XML").
   */
  xmlExport?: boolean | string;
  /**
   * "Download PDF" of the results: an API path under /api/v1/hbs taking the same criteria
   * (P451/P456 "Generate PDF").
   */
  pdfExport?: string;
  /** Header actions, e.g. "Add Scale Site Profile". */
  createLink?: LinkDef & { capability: Capability };
  /** Run the search immediately on open (lists with no mandatory criteria). */
  autoSearch?: boolean;
  /** Row actions (commands). */
  rowActions?: ActionDef[];
  /** Commands on the ticked rows of the current page (a check-box column appears). */
  bulkActions?: BulkActionDef[];
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
    /** Commands on the ticked rows (a check-box column appears); `params` read the screen's record. */
    bulkActions?: BulkActionDef[];
  };
}

export interface DetailScreenDef extends BaseScreen {
  kind: 'detail';
  /** Single-row query; URL params are passed straight through. */
  query: string;
  /** URL params required to load the record. */
  keys: string[];
  /** Pin the record's key fields and status above the tabs (default true). */
  tombstone?: boolean;
  sections: DetailSection[];
  actions?: ActionDef[];
  editLink?: LinkDef & { capability: Capability };
  /**
   * Further header buttons to bespoke pages (e.g. child-grid editors): SPA `path`
   * with URL params from the record, optionally gated by an available action id.
   */
  extraLinks?: { path: string; params: Record<string, string>; label: string; capability: Capability; requiresAction?: string }[];
  /** Backend-decided per-record action availability. */
  actionsAvailable?: ActionsAvailableDef;
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
