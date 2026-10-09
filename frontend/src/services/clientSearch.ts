import { apiFetch, readErrorMessage } from '@/services/apiFetch';
import { HBS_API, type PageableResponse } from './common';

// Forest client lookups. The backend proxies nr-forest-client-api (its API
// key stays server-side): /clients/search, /clients/{n}, /clients/{n}/locations.

// Mirrors backend ca.bc.gov.nrs.hbs.api.struct.v1.ClientSearchResult — a
// client, or (from getClientLocations) one of its locations. clientName is
// the display form ("SURNAME, FIRST MIDDLE" for an individual); render it
// verbatim. Location fields are null on a client-level row.
export interface ClientSearchResult {
  clientNumber: string | null;
  clientAcronym: string | null;
  clientName: string | null;
  legalFirstName: string | null;
  legalMiddleName: string | null;
  clientTypeCode: string | null;
  clientStatusCode: string | null;
  clientLocnCode: string | null;
  clientLocnName: string | null;
  city: string | null;
  locationExpired: boolean | null;
}

async function getJson<T>(path: string, fallback: string): Promise<T> {
  const res = await apiFetch(path);
  if (!res.ok) {
    const detail = await readErrorMessage(res);
    throw new Error(detail || `${fallback} (${res.status})`);
  }
  return res.json() as Promise<T>;
}

/**
 * Find clients by number, acronym or name. An all-digit term is a client
 * number (the backend zero-pads it); anything else is a ranked search — exact
 * acronym, or a fuzzy match on the full name.
 */
export function searchClients(
  term: string,
  page = 0,
  size = 10,
): Promise<PageableResponse<ClientSearchResult>> {
  const qs = new URLSearchParams({ term: term.trim(), page: String(page), size: String(size) });
  return getJson(`${HBS_API}/clients/search?${qs}`, 'Client search failed');
}

/** One client by number (no location). */
export function getClient(clientNumber: string): Promise<ClientSearchResult> {
  return getJson(`${HBS_API}/clients/${encodeURIComponent(clientNumber.trim())}`, 'Client lookup failed');
}

/** A client's locations, one row each (carrying the client's own fields). */
export function getClientLocations(
  clientNumber: string,
  page = 0,
  size = 10,
): Promise<PageableResponse<ClientSearchResult>> {
  const qs = new URLSearchParams({ page: String(page), size: String(size) });
  return getJson(
    `${HBS_API}/clients/${encodeURIComponent(clientNumber.trim())}/locations?${qs}`,
    'Client location lookup failed',
  );
}

/**
 * Type-ahead suggestions for the client picker: up to 15 clients for a term
 * of 3+ characters (or any all-digit client number). [] for shorter terms.
 */
export async function searchClientsAuto(term: string): Promise<ClientSearchResult[]> {
  const t = term.trim();
  if (t.length < 3 && !/^\d+$/.test(t)) return [];
  const page = await searchClients(t, 0, 15);
  return page.content;
}
