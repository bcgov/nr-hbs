import { apiFetch, readErrorMessage } from '@/services/apiFetch';

/**
 * Shared service-layer helpers + wire types used by every HBS domain
 * service module (scaleReturns.ts, invoices.ts, …). Mirrors the helpers
 * nr-fsp-new keeps at the top of services/fspSearch.ts, pulled into their
 * own module because HBS has many more domain services.
 */

/** API root for every HBS resource. Caddy / Vite proxy `/api` → backend. */
export const HBS_API = '/v1/hbs';

// Mirrors backend ca.bc.gov.nrs.hbs.api.struct.v1.CodeOption.
export interface CodeOption {
  code: string;
  description: string;
}

// Mirrors backend ca.bc.gov.nrs.hbs.api.struct.v1.PageableResponse.
export interface PageableResponse<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

/** Paging + sort parameters every search endpoint accepts. */
export interface PageRequest {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

/**
 * Builds a query string from a flat criteria object, dropping blank /
 * null / undefined values so the backend sees only what the user filled.
 */
export function toQuery(params: object): string {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null) return;
    const s = String(v).trim();
    if (s !== '') qs.set(k, s);
  });
  const out = qs.toString();
  return out ? `?${out}` : '';
}

async function failWith(res: Response, label: string): Promise<never> {
  // readErrorMessage sanitises the body (drops raw SQL/stack/JSON), so
  // `detail` is a clean sentence or empty — never technical text.
  const detail = await readErrorMessage(res);
  throw new Error(detail || `${label} failed (${res.status})`);
}

export async function getJson<T>(path: string, label: string): Promise<T> {
  const res = await apiFetch(path);
  if (!res.ok) await failWith(res, label);
  return res.json() as Promise<T>;
}

export async function sendJson<T>(
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  body: unknown,
  label: string,
): Promise<T> {
  const res = await apiFetch(path, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) await failWith(res, label);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Multipart upload (file + optional JSON part named `meta`). */
export async function sendMultipart<T>(
  path: string,
  file: File,
  meta: unknown,
  label: string,
): Promise<T> {
  const form = new FormData();
  form.append('file', file);
  if (meta !== undefined) {
    form.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }));
  }
  const res = await apiFetch(path, { method: 'POST', body: form });
  if (!res.ok) await failWith(res, label);
  return res.json() as Promise<T>;
}

/** Fetches a binary download (PDF/CSV/XML) as a Blob + server filename. */
export async function getBlob(
  path: string,
  label: string,
  init: RequestInit = {},
): Promise<{ blob: Blob; filename: string | null }> {
  const res = await apiFetch(path, {
    ...init,
    headers: { Accept: '*/*', ...(init.headers ?? {}) },
  });
  if (!res.ok) await failWith(res, label);
  const disposition = res.headers.get('Content-Disposition') ?? '';
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  return { blob: await res.blob(), filename: match ? decodeURIComponent(match[1]) : null };
}

// ── Users (nr-user-lookup-api pass-through) ────────────────────────────

export interface UserSummary {
  userId: string;
  displayName?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
  idirGuid?: string | null;
  idirUserGuid?: string | null;
}

export interface UserSearchResponse {
  results: UserSummary[];
  total: number;
  page: number;
  size: number;
}

export interface UserSearchParams {
  userId?: string;
  firstName?: string;
  lastName?: string;
  size?: number;
}

/** GET /api/v1/hbs/users/search — passes through to nr-user-lookup-api. */
export function searchUsers(params: UserSearchParams): Promise<UserSearchResponse> {
  return getJson<UserSearchResponse>(
    `${HBS_API}/users/search${toQuery({ ...params, size: params.size && params.size > 0 ? params.size : undefined })}`,
    'User search',
  );
}

/**
 * POST /api/v1/hbs/users/resolve — batch-resolves user ids to display names.
 * Go through lib/userNameStore (batched + de-duped), never call directly.
 */
export function resolveUserNames(userIds: string[]): Promise<Record<string, string>> {
  return sendJson<Record<string, string>>('POST', `${HBS_API}/users/resolve`, { userIds }, 'User name resolve');
}
