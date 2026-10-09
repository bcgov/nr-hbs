import { SCREENS_BY_ID, screenPath } from '@/screens/registry';
import type { LinkDef, ReportLinkDef } from '@/screens/types';

/** Builds the SPA URL for a LinkDef, pulling param values from a row/record. */
export function linkHref(link: LinkDef, source: Record<string, unknown>): string | null {
  const target = SCREENS_BY_ID.get(link.screen);
  if (!target) return null;
  const qs = new URLSearchParams();
  for (const [param, key] of Object.entries(link.params)) {
    const v = key.startsWith('=') ? key.slice(1) : source[key];
    if (v !== undefined && v !== null && String(v) !== '') qs.set(param, String(v));
  }
  const q = qs.toString();
  return `${screenPath(target)}${q ? `?${q}` : ''}`;
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
