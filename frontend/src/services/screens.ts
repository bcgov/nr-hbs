import { getActiveOrgClientNumber } from '@/context/org/useOrg';
import {
  getBlob,
  getJson,
  HBS_API,
  sendJson,
  toQuery,
  type CodeOption,
  type PageableResponse,
  type PageRequest,
} from '@/services/common';

/**
 * Client for the registry-driven endpoints behind most HBS screens
 * (backend QueryController). Ids are the backend QueryDefinition /
 * CommandDefinition ids, e.g. 'invoices.search', 'markHolderProfile.update'.
 */

export type Row = Record<string, unknown>;
export type Criteria = Record<string, string | undefined>;

export function runQuery(
  queryId: string,
  criteria: Criteria,
  paging: PageRequest = {},
): Promise<PageableResponse<Row>> {
  return getJson<PageableResponse<Row>>(
    `${HBS_API}/queries/${encodeURIComponent(queryId)}${toQuery({ ...criteria, ...paging })}`,
    'Search',
  );
}

export function listQuery(queryId: string, criteria: Criteria = {}): Promise<Row[]> {
  return getJson<Row[]>(
    `${HBS_API}/queries/${encodeURIComponent(queryId)}/list${toQuery(criteria)}`,
    'Lookup',
  );
}

export function oneQuery(queryId: string, keys: Criteria): Promise<Row> {
  return getJson<Row>(
    `${HBS_API}/queries/${encodeURIComponent(queryId)}/one${toQuery(keys)}`,
    'Record load',
  );
}

export function exportQuery(queryId: string, criteria: Criteria) {
  return getBlob(
    `${HBS_API}/queries/${encodeURIComponent(queryId)}/export${toQuery(criteria)}`,
    'Export',
  );
}

export function runCommand(commandId: string, body: Record<string, unknown>): Promise<Row> {
  return sendJson<Row>('POST', `${HBS_API}/commands/${encodeURIComponent(commandId)}`, body, 'Save');
}

// ── Available commands ──────────────────────────────────────────────

let commandsPromise: Promise<Set<string>> | null = null;
let commandsForOrg: string | null = null;

/**
 * Command ids registered on the backend that the caller may run (cached per
 * session). Screens reference some ids that are reserved for workflow
 * services not yet ported (docs/legacy-logic-to-port.md); their buttons and
 * save actions are hidden/disabled until the backend registers them.
 */
export function availableCommands(): Promise<Set<string>> {
  // Roles (and so available commands) change when a BCeID user switches
  // active client — re-fetch per org.
  const org = getActiveOrgClientNumber();
  if (!commandsPromise || commandsForOrg !== org) {
    commandsForOrg = org;
    commandsPromise = getJson<string[]>(`${HBS_API}/commands`, 'Command list')
      .then((ids) => new Set(ids))
      .catch(() => {
        commandsPromise = null;
        return new Set<string>();
      });
  }
  return commandsPromise;
}

// ── Code lists (cached per session — reference data) ────────────────────

const codeListCache = new Map<string, Promise<CodeOption[]>>();

/** A registry query returning {code, description} rows, cached for the session. */
export function codeList(queryId: string): Promise<CodeOption[]> {
  let p = codeListCache.get(queryId);
  if (!p) {
    p = listQuery(queryId).then((rows) =>
      rows.map((r) => ({ code: String(r.code ?? ''), description: String(r.description ?? r.code ?? '') })),
    );
    p.catch(() => codeListCache.delete(queryId));
    codeListCache.set(queryId, p);
  }
  return p;
}

// ── Reports (legacy Jasper units) ───────────────────────────────────────

export interface ReportDefinition {
  id: string;
  title: string;
  procedure: string | null;
  parameters: string[];
  subreports: string[];
}

export function listReports(): Promise<ReportDefinition[]> {
  return getJson<ReportDefinition[]>(`${HBS_API}/reports`, 'Report list');
}

export function runReport(reportId: string, parameters: Record<string, string>, format: 'PDF' | 'CSV' = 'PDF') {
  return getBlob(`${HBS_API}/reports/${encodeURIComponent(reportId)}`, 'Report', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ parameters, format }),
  });
}
