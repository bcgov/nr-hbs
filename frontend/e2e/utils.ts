import path from 'node:path';

import { test, type BrowserContext, type Page, type Response, type Route } from '@playwright/test';

// E2E_BASE_URL is set by `.github/workflows/reusable-tests.yml` at step
// level (resolved from the PR slot or test/prod target). For local hand-runs,
// export it explicitly:
//   E2E_BASE_URL=http://localhost:3000 npm run e2e
// Failing fast here beats silently targeting a stale dev URL.
if (!process.env.E2E_BASE_URL) {
  throw new Error(
    'E2E_BASE_URL is not set. Export it before running Playwright ' +
      '(e.g. `E2E_BASE_URL=http://localhost:3000 npm run e2e`).',
  );
}

export const baseURL = process.env.E2E_BASE_URL;

/** Path to the saved auth state produced by auth.setup.ts. */
export const STORAGE_STATE = path.join(import.meta.dirname, '.auth', 'user.json');

export const SESSION_STORAGE_STATE = path.join(import.meta.dirname, '.auth', 'session.json');

/**
 * The sessionStorage keys worth carrying between tests: oidc-client-ts's
 * (`oidc.user:…` holds the tokens). Everything else in sessionStorage is the
 * app's own per-tab state — each screen's last criteria and search
 * (`hbs.screen.*`) — and carrying that over would start a test on a form a
 * previous test (or run) filled in.
 */
export const isAuthKey = (key: string): boolean => key.startsWith('oidc.');

/** sessionStorage as JSON, auth keys only — run in the page via `page.evaluate`. */
export const authSessionJson = (): string =>
  JSON.stringify(
    Object.fromEntries(
      Object.entries(window.sessionStorage).filter(([key]) => key.startsWith('oidc.')),
    ),
  );

/**
 * Unique-ish identifier suffix for test artifacts so concurrent runs and
 * leftover rows don't collide. Format: e2e-<base36 timestamp>-<rand>.
 */
export const uniqueSuffix = (): string => {
  const t = Date.now().toString(36);
  const r = Math.floor(Math.random() * 36 ** 4)
    .toString(36)
    .padStart(4, '0');
  return `e2e-${t}-${r}`;
};

type FulfillBody = NonNullable<NonNullable<Parameters<Route['fulfill']>[0]>['body']>;

interface CachedAsset {
  status: number;
  contentType: string;
  body: FulfillBody;
}

const staticAssetCache = new Map<string, CachedAsset>();

const STATIC_ASSET_PATH =
  /^\/(assets|fonts)\/|^\/config\.js$|\.(js|css|woff2?|ttf|otf|png|jpe?g|svg|ico)$/i;

/** True when `pathname` is a static frontend asset rather than an HTML route or API call. */
export const isStaticAssetPath = (pathname: string): boolean =>
  !pathname.startsWith('/api/') && STATIC_ASSET_PATH.test(pathname);

/**
 * Serve GET requests for static assets from an in-memory cache after the first
 * fetch from the deployment. Playwright creates a fresh incognito
 * BrowserContext per test, which otherwise wipes the browser HTTP cache and
 * makes the small Caddy + Coraza container re-serve the whole Vite bundle for
 * every test (nr-fta #53). Documents and `/api/*` always go to the server.
 */
export const fulfillWithAssetCache = async (route: Route): Promise<void> => {
  const request = route.request();
  if (request.method() !== 'GET' || request.resourceType() === 'document') {
    await route.continue();
    return;
  }

  const key = new URL(request.url()).pathname;
  const cached = staticAssetCache.get(key);
  if (cached) {
    await route.fulfill(cached);
    return;
  }

  try {
    const response = await route.fetch({ timeout: 10_000 });
    const body = await response.body();
    const status = response.status();
    const contentType = response.headers()['content-type'] ?? 'application/octet-stream';
    if (status === 200) {
      staticAssetCache.set(key, { status, contentType, body });
    }
    await route.fulfill({ status, contentType, body });
  } catch {
    await route.abort('timedout').catch(() => undefined);
  }
};

/**
 * Cache static subresources (`/assets/*`, `/fonts/*`, `/config.js`, images)
 * across the tests of this worker. Document and `/api/*` requests stay live.
 */
export const installStaticAssetCache = async (context: BrowserContext): Promise<void> => {
  const { origin } = new URL(baseURL);
  await context.route(
    (url) => url.origin === origin && isStaticAssetPath(url.pathname),
    (route) => fulfillWithAssetCache(route),
  );
};

const TRANSIENT_NAV_ERROR =
  /TimeoutError|Timeout \d+ms exceeded|net::ERR_CONNECTION_REFUSED|net::ERR_CONNECTION_RESET|net::ERR_TIMED_OUT|net::ERR_NETWORK_CHANGED|net::ERR_ABORTED|NS_ERROR_CONNECTION_REFUSED|NS_ERROR_NET_RESET|ECONNREFUSED/;

const isTransientNavigationError = (err: unknown): boolean => {
  const message = err instanceof Error ? err.message : String(err);
  const name = err instanceof Error ? err.name : '';
  return TRANSIENT_NAV_ERROR.test(`${name}: ${message}`);
};

/** How many times a navigation is tried, and the pause before each retry. */
const NAV_ATTEMPTS = 4;
const NAV_BACKOFF_MS = [1_500, 3_000, 5_000];

/**
 * Records a navigation that had to be retried, so a run shows how often the
 * connection to the deployment fails rather than hiding it: a line in the CI
 * log and a `navigation-retry` annotation on the test in the HTML report.
 */
const logNavigationRetry = (path: string, attempt: number, err: unknown): void => {
  const reason = (err instanceof Error ? err.message : String(err)).split('\n')[0];
  const description = `${path} attempt ${attempt} failed: ${reason}`;
  console.warn(`[navigation-retry] ${description}`);
  try {
    test.info().annotations.push({ type: 'navigation-retry', description });
  } catch {
    // Outside a running test there is nothing to annotate.
  }
};

/**
 * Navigate to `path`, retrying with backoff when the router or the pod briefly
 * refuses, drops or stalls the navigation (e.g. mid-redeploy). Any other error
 * fails at once.
 */
export const gotoWithRetry = async (page: Page, path: string): Promise<Response | null> => {
  for (let attempt = 1; ; attempt++) {
    try {
      return await page.goto(path, { timeout: 10_000, waitUntil: 'domcontentloaded' });
    } catch (err) {
      if (attempt >= NAV_ATTEMPTS || !isTransientNavigationError(err)) {
        throw err;
      }
      logNavigationRetry(path, attempt, err);
      await page.waitForTimeout(NAV_BACKOFF_MS[attempt - 1]);
    }
  }
};

/**
 * Navigate to a route on the protected side of the SPA and wait until the
 * Layout header has rendered — that means AuthProvider has finished
 * bootstrapping and we're past the white `<Loading withOverlay>` screen.
 *
 * Retries (as gotoWithRetry) when the document navigation fails or the bundle
 * never mounts, leaving `<div id="root"></div>` empty. A page that rendered
 * something else (the sign-in, Unauthorized, an error boundary) is not
 * retried. On final failure, dumps the URL and a body excerpt so we can tell
 * whether the SPA stayed on the loading overlay, redirected, crashed, etc.
 */
export const gotoProtected = async (page: Page, path: string): Promise<void> => {
  // Capture browser console messages and page errors during this navigation.
  const consoleMessages: string[] = [];
  const pageErrors: string[] = [];
  const onConsole = (msg: { type(): string; text(): string }) => {
    consoleMessages.push(`[${msg.type()}] ${msg.text()}`);
  };
  const onPageError = (err: Error) => {
    pageErrors.push(err.message);
  };
  page.on('console', onConsole);
  page.on('pageerror', onPageError);

  try {
    for (let attempt = 1; ; attempt++) {
      try {
        await page.goto(path, { timeout: 10_000, waitUntil: 'domcontentloaded' });
        await page.getByTestId('bc-header__header').waitFor({ timeout: 20_000 });
        break;
      } catch (err) {
        const currentUrl = page.url();
        const uncommitted =
          currentUrl === 'about:blank' || currentUrl.startsWith('chrome-error://');
        const rootEmpty = await page
          .evaluate(() => (document.getElementById('root')?.innerHTML.trim().length ?? 0) === 0)
          .catch(() => true);
        if (
          attempt >= NAV_ATTEMPTS ||
          (!uncommitted && !rootEmpty) ||
          !isTransientNavigationError(err)
        ) {
          throw err;
        }
        logNavigationRetry(path, attempt, err);
        await page.waitForTimeout(NAV_BACKOFF_MS[attempt - 1]);
      }
    }
  } catch (err) {
    const url = page.url();
    const title = await page.title().catch(() => '(unavailable)');
    const readyState = await page.evaluate(() => document.readyState).catch(() => '(unavailable)');
    const bodyHTML = await page
      .evaluate(() => document.body?.innerHTML?.slice(0, 1500) ?? '(no body)')
      .catch(() => '(unavailable)');
    const rootContent = await page
      .evaluate(() => document.getElementById('root')?.innerHTML?.slice(0, 800) ?? '(no #root)')
      .catch(() => '(unavailable)');
    const loadingVisible = await page
      .getByTestId('loading')
      .isVisible()
      .catch(() => false);
    throw new Error(
      `gotoProtected("${path}") failed to find the Layout header.\n` +
        `  Current URL  : ${url}\n` +
        `  Page title   : ${title}\n` +
        `  readyState   : ${readyState}\n` +
        `  Loading?     : ${loadingVisible}\n` +
        `  #root excerpt: ${rootContent}\n` +
        `  body excerpt : ${bodyHTML}\n` +
        `  Console (${consoleMessages.length}): ${consoleMessages.slice(-15).join(' | ') || '(none)'}\n` +
        `  Page errors (${pageErrors.length}): ${pageErrors.join(' | ') || '(none)'}\n` +
        `  Original     : ${err instanceof Error ? err.message : String(err)}`,
    );
  } finally {
    page.off('console', onConsole);
    page.off('pageerror', onPageError);
  }
};
