import { createContext, useContext } from 'react';

import type { NavOrigin } from '@/lib/navOrigin';
import { getBlob, type CodeOption, type PageableResponse, type PageRequest } from '@/services/common';
import {
  codeList,
  exportQuery,
  exportQueryPdf,
  exportQueryXml,
  listQuery,
  oneQuery,
  runQuery,
  type Criteria,
  type Row,
} from '@/services/screens';
import { SCREENS_BY_ID, screenPath } from '@/screens/registry';

/**
 * Where the shared screen templates (SearchScreen, DetailScreen, FieldInput,
 * DownloadCell) get their data from, and where their links go.
 *
 * The default is the logged-in API (bearer token, `/api/v1/hbs/queries/…`,
 * registry screen paths). The public read-only area (pages/public) provides
 * an anonymous implementation — no token, `/api/v1/hbs/public/…`, links only
 * to other public screens — so the same templates render the legacy public
 * pages without a second copy of them.
 */
export interface ScreenApi {
  runQuery: (queryId: string, criteria: Criteria, paging?: PageRequest) => Promise<PageableResponse<Row>>;
  listQuery: (queryId: string, criteria?: Criteria) => Promise<Row[]>;
  oneQuery: (queryId: string, keys: Criteria) => Promise<Row>;
  exportQuery: (queryId: string, criteria: Criteria) => Promise<{ blob: Blob; filename: string | null }>;
  exportQueryXml: (
    queryId: string,
    criteria: Criteria,
    path?: string,
  ) => Promise<{ blob: Blob; filename: string | null }>;
  /** A search's combined PDF (`SearchScreenDef.pdfExport`, an API path under /v1/hbs). */
  exportQueryPdf: (path: string, criteria: Criteria) => Promise<{ blob: Blob; filename: string | null }>;
  codeList: (queryId: string) => Promise<CodeOption[]>;
  /** Download an API path (`/v1/hbs/…`, the `/api` prefix already stripped). */
  download: (path: string, label: string) => Promise<{ blob: Blob; filename: string | null }>;
  /** SPA path of a screen id, or null when the screen can't be linked to here. */
  screenHref: (screenId: string) => string | null;
  /** Back link for a record reached without an origin (null = the screen's area landing). */
  home?: NavOrigin;
}

export const authenticatedScreenApi: ScreenApi = {
  runQuery,
  listQuery,
  oneQuery,
  exportQuery,
  exportQueryXml,
  exportQueryPdf,
  codeList,
  download: (path, label) => getBlob(path, label),
  screenHref: (screenId) => {
    const target = SCREENS_BY_ID.get(screenId);
    return target ? screenPath(target) : null;
  },
};

export const ScreenApiContext = createContext<ScreenApi>(authenticatedScreenApi);

export const useScreenApi = (): ScreenApi => useContext(ScreenApiContext);
