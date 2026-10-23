import { env } from '@/env';

import { readErrorMessage } from '@/services/apiFetch';
import {
  dispositionFilename,
  HBS_API,
  toQuery,
  type CodeOption,
  type PageableResponse,
  type PageRequest,
} from '@/services/common';
import type { Criteria, Row } from '@/services/screens';

/**
 * Client for the public (anonymous) read-only area — backend
 * `publicaccess.PublicController`, `/api/v1/hbs/public/**`.
 *
 * Deliberately NOT apiFetch: no Keycloak session refresh, no bearer token, no
 * active-org header and no cookies — the public endpoints ignore them anyway
 * and always answer as the anonymous viewer (FOI-severed). Works whether or
 * not the visitor is logged in.
 */

const API_BASE_URL = env.VITE_API_BASE_URL || '/api';
export const PUBLIC_API = `${HBS_API}/public`;

/** Thrown for a 429 (per-IP rate limit) so pages can say so plainly. */
const RATE_LIMITED = 'Too many requests. Please wait a moment and try again.';

export async function publicFetch(path: string, accept = 'application/json'): Promise<Response> {
  const url = /^https?:/.test(path) ? path : `${API_BASE_URL}${path}`;
  return fetch(url, { headers: { Accept: accept }, credentials: 'omit' });
}

async function fail(res: Response, label: string): Promise<never> {
  if (res.status === 429) throw new Error(RATE_LIMITED);
  const detail = await readErrorMessage(res);
  throw new Error(detail || `${label} failed (${res.status})`);
}

async function getJson<T>(path: string, label: string): Promise<T> {
  const res = await publicFetch(path);
  if (!res.ok) await fail(res, label);
  return res.json() as Promise<T>;
}

async function getBlob(path: string, label: string): Promise<{ blob: Blob; filename: string | null }> {
  const res = await publicFetch(path, '*/*');
  if (!res.ok) await fail(res, label);
  return { blob: await res.blob(), filename: dispositionFilename(res.headers.get('Content-Disposition') ?? '') };
}

const q = (queryId: string) => `${PUBLIC_API}/queries/${encodeURIComponent(queryId)}`;

export function runPublicQuery(
  queryId: string,
  criteria: Criteria,
  paging: PageRequest = {},
): Promise<PageableResponse<Row>> {
  return getJson(`${q(queryId)}${toQuery({ ...criteria, ...paging })}`, 'Search');
}

export function listPublicQuery(queryId: string, criteria: Criteria = {}): Promise<Row[]> {
  return getJson(`${q(queryId)}/list${toQuery(criteria)}`, 'Lookup');
}

export function onePublicQuery(queryId: string, keys: Criteria): Promise<Row> {
  return getJson(`${q(queryId)}/one${toQuery(keys)}`, 'Record load');
}

export function exportPublicQuery(queryId: string, criteria: Criteria) {
  return getBlob(`${q(queryId)}/export${toQuery(criteria)}`, 'Export');
}

/** "Download XML" of issued documents (P451 "Generate XML") — the only XML export the public area offers. */
export function exportPublicXml(_queryId: string, criteria: Criteria) {
  return getBlob(`${PUBLIC_API}/statements/export.xml${toQuery(criteria)}`, 'XML download');
}

export function exportPublicPdf(_path: string, criteria: Criteria) {
  return getBlob(`${PUBLIC_API}/statements/export.pdf${toQuery(criteria)}`, 'PDF download');
}

/** A `/v1/hbs/public/…` download path (row `documentUrl` / `documentXmlUrl`, `/api` stripped). */
export function downloadPublic(path: string, label: string) {
  if (!path.startsWith(`${PUBLIC_API}/`)) {
    return Promise.reject(new Error(`${label} is not available on the public site`));
  }
  return getBlob(path, label);
}

const codeCache = new Map<string, Promise<CodeOption[]>>();

/** A public code-list query ({code, description} rows), cached for the session. */
export function publicCodeList(queryId: string): Promise<CodeOption[]> {
  let p = codeCache.get(queryId);
  if (!p) {
    p = listPublicQuery(queryId).then((rows) =>
      rows.map((r) => ({
        code: String(r.code ?? ''),
        description: String(r.description ?? r.code ?? ''),
        ...(r.label != null ? { label: String(r.label) } : {}),
      })),
    );
    p.catch(() => codeCache.delete(queryId));
    codeCache.set(queryId, p);
  }
  return p;
}
