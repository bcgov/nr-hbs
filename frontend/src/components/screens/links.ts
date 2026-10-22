import { SCREENS_BY_ID, screenPath } from '@/screens/registry';
import type { LinkDef, ReportCondition, ReportLinkDef } from '@/screens/types';

/** Bespoke pages a registry screen may link to (routes in App.tsx). */
const BESPOKE_PATHS: Record<string, string> = {
  'detail-entry': '/scale-returns/detail-entry',
  'detail-bulk': '/scale-returns/detail-bulk',
};

const registryHref = (screenId: string): string | null => {
  const target = SCREENS_BY_ID.get(screenId);
  return target ? screenPath(target) : (BESPOKE_PATHS[screenId] ?? null);
};

/**
 * Builds the SPA URL for a LinkDef, pulling param values from a row/record.
 * `resolve` maps the target screen id to its path (default: the registry's
 * `/:area/:screenId`; the public area resolves to `/public/…` or no link).
 */
export function linkHref(
  link: LinkDef,
  source: Record<string, unknown>,
  resolve: (screenId: string) => string | null = registryHref,
): string | null {
  const base = resolve(link.screen);
  if (!base) return null;
  const qs = new URLSearchParams();
  for (const [param, key] of Object.entries(link.params)) {
    const v = key.startsWith('=') ? key.slice(1) : source[key];
    if (v !== undefined && v !== null && String(v) !== '') qs.set(param, String(v));
  }
  const q = qs.toString();
  return `${base}${q ? `?${q}` : ''}`;
}

/** Resolves report prompt values from criteria/record (literal when prefixed with '='). */
export function reportParams(report: ReportLinkDef, source: Record<string, unknown>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [prompt, key] of Object.entries(report.params)) {
    const v = key.startsWith('=') ? key.slice(1) : source[key];
    out[prompt] = v === undefined || v === null ? '' : String(v);
  }
  return out;
}

/** Whether a report button applies to the current criteria / record (see ReportLinkDef.showWhen). */
/** A report's download name from its `fileName` template, or null when it has none. */
export function reportFileName(report: ReportLinkDef, source: Record<string, unknown>, ext: string): string | null {
  if (!report.fileName) return null;
  const filled = report.fileName.replace(/\{(\w+)\}/g, (_, key: string) => {
    const v = source[key];
    if (v === undefined || v === null) return '';
    const s = String(v).trim();
    // An ISO timestamp reads better as its date.
    return /^\d{4}-\d{2}-\d{2}T/.test(s) ? s.slice(0, 10) : s;
  });
  const clean = filled
    .replace(/[\\/:*?"<>|]+/g, '-')
    .replace(/\bv(?=\s|$)/g, '') // "v" left without its version number
    .replace(/\s+/g, ' ')
    .trim();
  return clean ? `${clean}.${ext}` : null;
}

export function reportApplies(report: ReportLinkDef, source: Record<string, unknown>): boolean {
  return conditionsMet(report.showWhen, source);
}

/** True when every condition matches the record (or there are none). */
export function conditionsMet(
  showWhen: ReportCondition | ReportCondition[] | undefined,
  source: Record<string, unknown>,
): boolean {
  if (!showWhen) return true;
  const conditions = Array.isArray(showWhen) ? showWhen : [showWhen];
  return conditions.every(({ field, values }) => {
    const v = source[field];
    return v !== undefined && v !== null && values.includes(String(v));
  });
}
