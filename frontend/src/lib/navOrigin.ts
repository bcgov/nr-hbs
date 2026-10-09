import { useLocation } from 'react-router-dom';

/**
 * Where a page was reached from, carried in the router's location state, so
 * its back link returns there rather than to its usual parent — nr-fta's
 * navOrigin. A list sets it with `navigate(to, { state: originState(...) })`.
 */
export interface NavOrigin {
  /** Path to return to (with its query string). */
  path: string;
  /** Says where — the back link reads "Back to {label}". */
  label: string;
}

export const originState = (origin: NavOrigin) => ({ origin });

/** The origin this page was opened from, or null when reached any other way. */
export function useNavOrigin(): NavOrigin | null {
  const { state } = useLocation();
  const origin = (state as { origin?: Partial<NavOrigin> } | null)?.origin;
  return typeof origin?.path === 'string' && typeof origin.label === 'string'
    ? { path: origin.path, label: origin.label }
    : null;
}

/** The current page as an origin for the pages it opens. */
export function useHereAsOrigin(label: string): NavOrigin {
  const { pathname, search } = useLocation();
  return { path: `${pathname}${search}`, label };
}
