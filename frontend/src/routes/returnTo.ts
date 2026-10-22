/**
 * Deep links that must survive the sign-in round trip.
 *
 * The statement e-mails sent by legacy batch B2065 link straight to
 * `/StatementDelivery/transmissions/<id>.pdf|xml` (and old bookmarks to
 * `/StatementDelivery/statements/<type>/<number>.pdf|xml`). Sign-in always
 * returns to /authCallback, and a multi-client BCeID user is then sent to
 * /org-select, so the link is remembered here and resumed after both.
 *
 * Only those paths are accepted, so the stored value can never send the
 * browser off-site.
 */
const KEY = 'hbs.returnTo';

const RESUMABLE =
  /^\/StatementDelivery\/(?:transmissions\/[A-Za-z0-9._-]+|statements\/[A-Za-z]+\/[A-Za-z0-9._-]+)(?:\?[A-Za-z0-9=&_%-]*)?$/;

export function isResumable(path: string): boolean {
  return RESUMABLE.test(path);
}

export function rememberReturnTo(path: string): void {
  if (!isResumable(path)) return;
  try {
    sessionStorage.setItem(KEY, path);
  } catch {
    /* storage unavailable — the user lands on the home page instead */
  }
}

export function peekReturnTo(): string | null {
  try {
    const path = sessionStorage.getItem(KEY);
    return path && isResumable(path) ? path : null;
  } catch {
    return null;
  }
}

export function clearReturnTo(): void {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}
